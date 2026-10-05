// depends-on-plugin com.intellij.java
// depends-on-plugin org.intellij.groovy
// Schema-driven recipe types, scoped to KubeGroovy script use sites.

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.*
import com.intellij.psi.impl.light.LightPsiClassBuilder
import com.intellij.psi.impl.light.LightFieldBuilder
import com.intellij.psi.impl.light.LightReferenceListBuilder
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.scope.ElementClassHint
import com.intellij.psi.scope.NameHint
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.TypeConversionUtil
import groovy.json.JsonSlurper
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor
import org.jetbrains.plugins.groovy.lang.typing.GrTypeCalculator

class RecipeConfig {
    Map data = [lookups: [:], namespaces: [:], shortcuts: [:], recipes: [:]]
    Map<String, RecipeIdentity> identities = [:]
    long psiStamp = -1
    long revision = 0
    Map overloads = [targets: [], wrappers: [], typeRules: [], enumsToObject: true]
}

/** Light classes carry identity through normal Groovy local-variable inference. */
class RecipeIdentity extends LightPsiClassBuilder {
    final String kind
    final String id
    final RecipeConfig catalog
    final PsiElement anchor
    List<PsiMethod> ownMethods = []
    List<PsiField> ownFields = []
    PsiClass delegateClass
    long revision = -1
    boolean building = false

    RecipeIdentity(PsiElement anchor, String kind, String id, RecipeConfig catalog) {
        super(anchor, RecipeSupport.identityName(kind, id, catalog))
        this.anchor = anchor
        this.kind = kind
        this.id = id
        this.catalog = catalog
        getModifierList().addModifier(PsiModifier.PUBLIC)
    }

    @Override String getQualifiedName() { 'kubegroovy.ide.' + getName() }
    @Override PsiFile getContainingFile() { anchor.getContainingFile() }
    @Override PsiElement getContext() { anchor }
    @Override PsiElement getSourceElement() { anchor }
    @Override PsiAnnotation[] getAnnotations() { PsiAnnotation.EMPTY_ARRAY }
    @Override LightReferenceListBuilder getExtendsList() {
        if (anchor != null) refresh()
        def list = new LightReferenceListBuilder(manager, PsiReferenceList.Role.EXTENDS_LIST)
        if (delegateClass != null) list.addReference(delegateClass)
        return list
    }
    @Override PsiField[] getFields() {
        synchronized (anchor.project) { refresh(); return ownFields.toArray(new PsiField[0]) }
    }
    @Override PsiMethod[] getMethods() {
        synchronized (anchor.project) { refresh(); return ownMethods.toArray(new PsiMethod[0]) }
    }

    void refresh() {
        synchronized (anchor.project) {
            if (building) return
            RecipeSupport.load(anchor)
            if (revision == catalog.revision) return
            building = true
            try {
                ownMethods.clear()
                ownFields.clear()
                RecipeSupport.populate(this)
                revision = catalog.revision
            } finally { building = false }
        }
    }
}

/** Listing map properties must not eagerly construct every recipe's constructors and setters. */
class RecipeLookupField extends LightFieldBuilder {
    final RecipeIdentity owner
    final Map lookup
    RecipeLookupField(String name, RecipeIdentity owner, Map lookup) {
        super(name, RecipeSupport.plain('java.lang.Object', owner.anchor), owner)
        this.owner = owner
        this.lookup = lookup
        setContainingClass(owner).getModifierList().addModifier(PsiModifier.PUBLIC)
    }
    @Override PsiType getType() {
        owner == null ? super.getType() : RecipeSupport.target(lookup, owner.anchor, owner.catalog)
    }
    @Override PsiElement getSourceElement() { owner.anchor }
    @Override PsiAnnotation[] getAnnotations() { PsiAnnotation.EMPTY_ARRAY }
}

class RecipeSupport {
    static final String EVENT = 'dev.uncandango.kubegroovy.recipe.RecipesKubeEvent'
    static final String FUNCTION = 'dev.uncandango.kubegroovy.recipe.RecipeTypeFunction'
    static final String RECIPE = 'dev.uncandango.kubegroovy.recipe.KubeRecipe'
    static final Key<Map> PARAM = Key.create('kubegroovy.recipe.parameter')
    static final Key<List<String>> SUGGESTIONS = (Key<List<String>>) (Key.findKeyByName('kubegroovy.recipe.suggestionTypes') ?: Key.create('kubegroovy.recipe.suggestionTypes'))
    static final Key<String> DOC = Key.create('kubegroovy.recipe.documentation')
    static final Key<Map<String, Object>> CACHE = Key.create('kubegroovy.recipe.catalog')
    static final Key<File> CONFIG_FILE = Key.create('kubegroovy.recipe.configFile')
    static final Key<File> OVERLOAD_FILE = Key.create('kubegroovy.recipe.overloadFile')
    static final Set<String> SELF = ['id', 'group', 'merge', 'set', 'setValue', 'serializeChanges',
        'ingredientAction', 'damageIngredient', 'replaceIngredient', 'customIngredientAction',
        'keepIngredient', 'consumeIngredient', 'modifyResult'] as Set

    static String sideForFile(PsiFile file) {
        if (!(file instanceof GroovyFile)) return null
        def original = file.getOriginalFile()
        String path = original?.getVirtualFile()?.getPath()?.replace('\\', '/')
        if (path == null || !path.endsWith('.groovy')) return null
        def match = (path =~ /(?:^|\/)kubegroovy\/(startup|client|server)_scripts\/.+\.groovy$/)
        match.find() ? match.group(1) : null
    }

    static RecipeConfig load(PsiElement place) {
        def project = place.getProject()
        synchronized (project) {
            Map cache = project.getUserData(CACHE)
            if (cache == null) { cache = [:]; project.putUserData(CACHE, cache) }
            String root = project.getBasePath()
            File file = project.getUserData(CONFIG_FILE) ?: (root == null ? null : new File(root, 'local/kubegroovy/live-plugin/recipes/recipes.json'))
            File overloadFile = project.getUserData(OVERLOAD_FILE) ?: (root == null ? null : new File(root, 'local/kubegroovy/live-plugin/object-overload/overloads.json'))
            String stamp = file?.isFile() ? file.lastModified().toString() + ':' + file.length() : 'missing'
            stamp += ':' + (overloadFile?.isFile() ? overloadFile.lastModified().toString() + ':' + overloadFile.length() : 'missing')
            RecipeConfig config = (RecipeConfig) cache.config
            if (config == null) { config = new RecipeConfig(); cache.config = config }
            if (cache.stamp != stamp) {
                try {
                    if (file?.isFile()) {
                        def parsed = new JsonSlurper().parse(file, 'UTF-8')
                        validate(parsed)
                        config.data = parsed
                        config.revision++
                        config.identities.clear()
                    }
                    if (overloadFile?.isFile()) {
                        def overloads = new JsonSlurper().parse(overloadFile, 'UTF-8')
                        if (!(overloads instanceof Map) || !(overloads.targets instanceof List))
                            throw new IllegalArgumentException('Invalid coercion catalog')
                        config.overloads = overloads
                        config.revision++
                        config.identities.clear()
                    }
                } catch (ProcessCanceledException canceled) { throw canceled }
                catch (Exception ignored) { /* Retain this project's last valid catalog. */ }
                cache.stamp = stamp
            }
            long psiStamp = PsiModificationTracker.getInstance(project).getModificationCount()
            if (config.psiStamp != psiStamp) { config.identities.clear(); config.psiStamp = psiStamp }
            return config
        }
    }

    /** Project-scoped override for IDE fixtures; production uses the project-local catalog. */
    static void setConfigFile(com.intellij.openapi.project.Project project, File file) {
        project.putUserData(CONFIG_FILE, file)
        project.putUserData(CACHE, null)
    }

    static void setOverloadFile(com.intellij.openapi.project.Project project, File file) {
        project.putUserData(OVERLOAD_FILE, file)
        project.putUserData(CACHE, null)
    }

    static void validate(def parsed) {
        if (!(parsed instanceof Map) || parsed.schemaVersion != 1 ||
            !['lookups', 'namespaces', 'shortcuts', 'recipes'].every { parsed[it] instanceof Map })
            throw new IllegalArgumentException('Invalid recipe catalog')
        parsed.recipes.each { id, recipe ->
            if (!(id instanceof String) || !(recipe instanceof Map) || recipe.id != id ||
                !(recipe.factoryType instanceof String) || !(recipe.constructors instanceof List) || !(recipe.methods instanceof List))
                throw new IllegalArgumentException('Invalid recipe entry')
            (recipe.constructors + recipe.methods).each { method ->
                if (!(method instanceof Map) || !(method.name instanceof String) || !(method.parameters instanceof List))
                    throw new IllegalArgumentException('Invalid recipe method')
                method.parameters.each { param ->
                    if (!(param instanceof Map) || !(param.name instanceof String) || !(param.description instanceof String))
                        throw new IllegalArgumentException('Invalid recipe parameter')
                    validateInput(param.type)
                }
            }
        }
        parsed.lookups.each { key, target ->
            if (!(key instanceof String) || !(target instanceof Map) ||
                !(target.kind in ['recipe', 'namespace']) || !(target.id instanceof String) ||
                !(target.kind == 'recipe' ? parsed.recipes.containsKey(target.id) : parsed.namespaces.containsKey(target.id)))
                throw new IllegalArgumentException('Invalid recipe lookup')
        }
        parsed.namespaces.each { name, members ->
            if (!(name instanceof String) || !(members instanceof Map) ||
                !members.every { key, id -> key instanceof String && parsed.recipes.containsKey(id) })
                throw new IllegalArgumentException('Invalid recipe namespace')
        }
        if (!parsed.shortcuts.every { name, id -> name instanceof String && parsed.recipes.containsKey(id) })
            throw new IllegalArgumentException('Invalid recipe shortcuts')
    }

    static void validateInput(def input) {
        if (!(input instanceof Map) || !(input.kind in ['class', 'array', 'union']) ||
            !(input.name instanceof String) || !(input.arguments instanceof List) ||
            (input.kind == 'array' && input.arguments.size() != 1))
            throw new IllegalArgumentException('Invalid recipe input type')
        input.arguments.each { validateInput(it) }
    }

    static PsiType plain(String text, PsiElement place) {
        try { JavaPsiFacade.getElementFactory(place.getProject()).createTypeFromText(text, place) }
        catch (ProcessCanceledException canceled) { throw canceled }
        catch (Exception ignored) { PsiType.getJavaLangObject(place.getManager(), place.getResolveScope()) }
    }

    static RecipeIdentity identity(PsiType type) {
        def resolved = type instanceof PsiClassType ? type.resolve() : null
        resolved instanceof RecipeIdentity ? (RecipeIdentity) resolved : null
    }

    static String className(PsiType type) {
        type instanceof PsiClassType ? type.resolve()?.getQualifiedName() : null
    }

    static PsiType target(Map target, PsiElement place, RecipeConfig config) {
        target == null ? null : type(target.kind == 'recipe' ? 'callable' : 'namespace', target.id, place, config)
    }

    static String readableId(String id) {
        id.split(/(?:_|[^\p{javaJavaIdentifierPart}])+/).findAll { it }.collect {
            it.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + it.substring(1)
        }.join('')
    }

    static String identityName(String kind, String id, RecipeConfig config) {
        if (kind == 'map') return 'KgyRecipes'
        String prefix = kind == 'result' ? 'KgyRecipe' : kind == 'callable' ? 'KgyRecipeFunction' : 'KgyRecipeNamespace'
        String suffix = readableId(id)
        def ids = kind == 'namespace' ? config.data.namespaces.keySet() : config.data.recipes.keySet()
        // Keep ordinary names readable, but preserve identity for colliding normalized IDs.
        if (ids.count { readableId(it) == suffix } > 1)
            suffix += '_' + id.getBytes('UTF-8').encodeHex().toString()
        return prefix + suffix
    }

    /** A concrete factory class carries schema typing when its schemas share the same method API. */
    static PsiClass specializedFactory(String id, PsiElement place, RecipeConfig config) {
        String name = config.data.recipes[id]?.factoryType
        if (name == null || name == RECIPE) return null
        def schemas = config.data.recipes.values().findAll { it.factoryType == name }
        def methods = { recipe -> recipe.methods.collect { [it.name, it.parameters.collect { param -> param.type }] } as Set }
        def signatures = methods(config.data.recipes[id])
        if (schemas.any { methods(it) != signatures }) return null
        def facade = JavaPsiFacade.getInstance(place.project)
        def factory = facade.findClass(name, place.resolveScope)
        def base = facade.findClass(RECIPE, place.resolveScope)
        factory != null && !factory.isInterface() && base != null && factory.isInheritor(base, true) ? factory : null
    }

    static RecipeIdentity identityClass(String kind, String id, PsiElement place, RecipeConfig config) {
        String key = kind + ':' + id
        synchronized (place.project) {
            RecipeIdentity cls = config.identities[key]
            if (cls == null) {
                // Use the original file as anchor, not a transient completion-copy expression.
                PsiElement anchor = place.getContainingFile().getOriginalFile()
                cls = new RecipeIdentity(anchor, kind, id, config)
                config.identities[key] = cls
            }
            return cls
        }
    }

    static PsiType type(String kind, String id, PsiElement place, RecipeConfig config) {
        def factory = kind == 'result' ? specializedFactory(id, place, config) : null
        JavaPsiFacade.getElementFactory(place.project).createType(factory ?: identityClass(kind, id, place, config))
    }

    static void populate(RecipeIdentity cls) {
        def config = cls.catalog
        def place = cls.anchor
        Map recipe = config.data.recipes[cls.id]
        String underlying = cls.kind == 'map' ? 'java.util.Map' : cls.kind == 'namespace' ?
            'dev.uncandango.kubegroovy.recipe.NamespaceFunction' : cls.kind == 'callable' ? FUNCTION : (recipe?.factoryType ?: RECIPE)
        cls.delegateClass = JavaPsiFacade.getInstance(place.getProject()).findClass(underlying, place.getResolveScope())
        if (cls.delegateClass == null && cls.kind == 'result')
            cls.delegateClass = JavaPsiFacade.getInstance(place.getProject()).findClass(RECIPE, place.getResolveScope())
        // Constructors are direct members; native APIs remain inherited for runtime-class assignability.
        if (cls.delegateClass != null) {
            cls.ownMethods.addAll(cls.delegateClass.getAllMethods().findAll {
                it.hasModifierProperty(PsiModifier.PUBLIC) && !it.isConstructor() &&
                    !(cls.kind in ['callable', 'namespace'] && it.name == 'call')
            })
            cls.ownFields.addAll(cls.delegateClass.getAllFields().findAll { it.hasModifierProperty(PsiModifier.PUBLIC) })
        }
        if (cls.kind == 'map') {
            config.data.lookups.each { String name, Map lookup ->
                if (validName(name) && !cls.ownFields.any { it.name == name }) {
                    cls.ownFields.add(new RecipeLookupField(name, cls, lookup))
                }
            }
        } else if (cls.kind == 'namespace') {
            config.data.namespaces[cls.id]?.each { String name, String id ->
                if (validName(name) && !nativeProperty(cls, name) && cls.delegateClass?.findMethodsByName(name, true)?.length == 0)
                    cls.ownFields.add(new RecipeLookupField(name, cls, [kind:'recipe', id:id]))
            }
            cls.ownMethods.add(method(cls, 'call', plain('java.util.Set<java.lang.String>', place), [], []))
            cls.ownMethods.add(method(cls, 'call', plain('java.lang.Object', place), [[name:'name']], [plain('java.lang.String', place)]))
        } else if (recipe != null) {
            def entries = cls.kind == 'callable' ? recipe.constructors : recipe.methods
            entries.each { Map entry ->
                List<List<PsiType>> alternatives = entry.parameters.collect { inputTypes(it.type, place, config) }
                List<List<PsiType>> signatures = [[]]
                for (def choices : alternatives) {
                    // Bound overload expansion; fallback remains applicable and retains metadata.
                    if (signatures.size() * choices.size() > 64) {
                        signatures = [entry.parameters.collect { plain('java.lang.Object', place) }]
                        break
                    }
                    signatures = signatures.collectMany { prefix -> choices.collect { prefix + [it] } }
                }
                Set<String> seen = [] as Set
                signatures.each { parameters ->
                    String signature = parameters.collect { it.canonicalText }.join(',')
                    // Keep native signatures; schema dispatch still supports distinct argument shapes.
                    if (cls.kind == 'result' && cls.delegateClass?.findMethodsByName(entry.name, true)?.any { nativeMethod ->
                        nativeMethod.parameterList.parameters.collect { TypeConversionUtil.erasure(it.type).canonicalText } ==
                            parameters.collect { TypeConversionUtil.erasure(it).canonicalText }
                    }) return
                    if (seen.add(signature)) {
                        def result = type('result', cls.id, place, config)
                        def generated = method(cls, entry.name, result, entry.parameters, parameters)
                        if (cls.kind == 'result' && entry.primaryName instanceof String)
                            generated.putUserData(DOC, generated.getUserData(DOC) + '\nPreferred setter: ' + entry.primaryName)
                        cls.ownMethods.add(generated)
                    }
                }
            }
        }
    }

    static boolean nativeProperty(RecipeIdentity cls, String name) {
        cls.refresh()
        if (!name || cls.delegateClass == null) return false
        String cap = name.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + name.substring(1)
        cls.delegateClass.findFieldByName(name, true) != null || ['get' + cap, 'is' + cap].any { getter ->
            cls.delegateClass.findMethodsByName(getter, true).any {
                it.hasModifierProperty(PsiModifier.PUBLIC) && it.parameterList.parametersCount == 0
            }
        }
    }

    static boolean validName(String name) { name ==~ /[\p{javaJavaIdentifierStart}][\p{javaJavaIdentifierPart}]*/ }

    static List<PsiType> inputTypes(Map input, PsiElement place, RecipeConfig config) {
        if (input.kind == 'union') return input.arguments.collectMany { inputTypes(it, place, config) }.unique { it.canonicalText }
        if (input.kind == 'array') return inputTypes(input.arguments[0], place, config).collect { it.createArrayType() }
        // A union inside a generic is represented permissively while retaining the container shape.
        def exact = plain(inputText(input), place)
        List<PsiType> types = [exact]
        types.add(plain(rewriteInput(input, place, config.overloads), place))
        return types.unique { it.canonicalText }
    }

    static String inputText(Map input) {
        if (input.kind == 'union') return 'java.lang.Object'
        if (input.kind == 'array') return inputText(input.arguments[0]) + '[]'
        input.name + (input.arguments ? '<' + input.arguments.collect { inputText(it) }.join(',') + '>' : '')
    }

    static String rewriteInput(Map input, PsiElement place, Map overloads) {
        if (input.kind == 'union') return 'java.lang.Object'
        if (input.kind == 'array') return rewriteInput(input.arguments[0], place, overloads) + '[]'
        def nativeClass = JavaPsiFacade.getInstance(place.project).findClass(input.name, place.resolveScope)
        if ((overloads.targets ?: []).contains(input.name) || (overloads.enumsToObject && nativeClass?.isEnum()))
            return 'java.lang.Object'
        def rule = (overloads.typeRules ?: []).find { it.type == input.name }
        if (rule != null && input.arguments) {
            List<String> rewritten = input.arguments.withIndex().collect { child, i ->
                (rule.args ?: []).contains(i) ? rewriteInput(child, place, overloads) : inputText(child)
            }
            if (rule.action == 'replace') {
                def required = rule.requireEligibleArg
                if (required != null && (required >= input.arguments.size() || rewritten[required] == inputText(input.arguments[required])))
                    return inputText(input)
                return rule.replaceWith + '<' + rewritten.join(',') + '>'
            }
            return input.name + '<' + rewritten.join(',') + '>'
        }
        if ((overloads.wrappers ?: []).contains(input.name) && input.arguments.size() == 1) {
            String rewritten = rewriteInput(input.arguments[0], place, overloads)
            if (rewritten != inputText(input.arguments[0])) return rewritten
        }
        if (input.arguments && input.name in ['java.util.List', 'java.util.Collection', 'java.util.Set', 'java.util.Map'])
            return input.name + '<' + input.arguments.collect { rewriteInput(it, place, overloads) }.join(',') + '>'
        return inputText(input)
    }

    static GrLightMethodBuilder method(RecipeIdentity cls, String name, PsiType returns, List metadata, List<PsiType> types) {
        def result = new GrLightMethodBuilder(cls.manager, name)
        result.setContainingClass(cls).addModifier(PsiModifier.PUBLIC).setReturnType(returns)
        metadata.eachWithIndex { Map param, int i ->
            String paramName = validName(param.name) ? param.name : 'arg' + (i + 1)
            def parameter = result.addAndGetParameter(paramName, types[i])
            parameter.putUserData(PARAM, param)
            if (param.type instanceof Map) parameter.putUserData(SUGGESTIONS, suggestionTexts(param.type))
        }
        result.putUserData(DOC, cls.id + '\n' + metadata.collect {
            it.name + ': ' + (it.description ?: types[metadata.indexOf(it)].presentableText) +
                (it.defaultValue ? ' (default: ' + it.defaultValue + ')' : '')
        }.join('\n'))
        result.setOriginInfo('KubeGroovy recipe ' + cls.id)
        return result
    }

    static List<String> suggestionTexts(Map input) {
        input.kind == 'union' ? input.arguments.collectMany { suggestionTexts(it) } : [inputText(input)]
    }

    static String literal(GrExpression expression) {
        expression instanceof GrLiteral && expression.getValue() instanceof String ? expression.getValue() : null
    }

    static PsiType reference(GrReferenceExpression ref) {
        if (sideForFile(ref.containingFile) == null) return null
        def qualifier = ref.qualifierExpression
        if (qualifier == null) return null
        PsiType receiver = qualifier.getType()
        String name = ref.referenceName
        def config = load(ref)
        if (className(receiver) == EVENT) {
            if (name == 'recipes') return type('map', '', ref, config)
            String id = config.data.shortcuts[name]
            if (id != null) return type('callable', id, ref, config)
        }
        def cls = identity(receiver)
        if (cls?.kind == 'map') return target(cls.catalog.data.lookups[name], ref, cls.catalog)
        if (cls?.kind == 'namespace' && name != null && !nativeProperty(cls, name) && cls.delegateClass?.findMethodsByName(name, true)?.length == 0) {
            String id = cls.catalog.data.namespaces[cls.id]?.get(name)
            if (id != null) return type('callable', id, ref, cls.catalog)
        }
        return null
    }

    static PsiType call(GrMethodCallExpression call) {
        if (sideForFile(call.containingFile) == null) return null
        def invoked = call.invokedExpression
        def config = load(call)
        def arguments = call.expressionArguments
        if (invoked instanceof GrReferenceExpression) {
            def receiver = invoked.qualifierExpression?.getType()
            String name = invoked.referenceName
            if (className(receiver) == EVENT && name == 'getRecipes' && arguments.length == 0)
                return type('map', '', call, config)
            if (className(receiver) == EVENT && config.data.shortcuts[name] != null)
                return type('result', config.data.shortcuts[name], call, config)
            def cls = identity(receiver)
            if (cls?.kind == 'map') {
                cls.refresh()
                if (name == 'get' && arguments.length == 1)
                    return target(cls.catalog.data.lookups[literal(arguments[0])], call, cls.catalog)
                if (cls.delegateClass?.findMethodsByName(name, true)?.length > 0) return null
                Map lookup = cls.catalog.data.lookups[name]
                if (lookup?.kind == 'recipe') return type('result', lookup.id, call, cls.catalog)
            }
            if (cls?.kind == 'namespace') {
                cls.refresh()
                if (name != 'call' && cls.delegateClass?.findMethodsByName(name, true)?.length > 0) return null
                if (cls.delegateClass != null) {
                    String id = cls.catalog.data.namespaces[cls.id]?.get(name)
                    if (id != null) return type('result', id, call, cls.catalog)
                }
            }
            if ((cls?.kind == 'result' || specializedId(receiver, call, config) != null) && SELF.contains(name)) {
                def resolved = call.resolveMethod()
                def original = resolved?.navigationElement
                if (resolved?.containingClass?.qualifiedName == RECIPE ||
                    (original instanceof PsiMethod && original.containingClass?.qualifiedName == RECIPE)) return receiver
            }
            if (name == 'call') {
                PsiType specialized = invokeIdentity(identity(receiver), arguments, call)
                if (specialized != null) return specialized
            }
        }
        return invokeIdentity(identity(invoked.getType()), arguments, call)
    }

    static String specializedId(PsiType receiver, PsiElement place, RecipeConfig config) {
        String name = className(receiver)
        if (name == null || name == RECIPE || identity(receiver) != null) return null
        def entry = config.data.recipes.find { id, recipe -> recipe.factoryType == name }
        entry != null && specializedFactory(entry.key, place, config) != null ? entry.key : null
    }

    static PsiType invokeIdentity(RecipeIdentity cls, def arguments, PsiElement place) {
        if (cls?.kind == 'callable') return type('result', cls.id, place, cls.catalog)
        if (cls?.kind == 'namespace' && arguments.length == 1) {
            String id = cls.catalog.data.namespaces[cls.id]?.get(literal(arguments[0]))
            if (id != null) return type('callable', id, place, cls.catalog)
        }
        return null
    }
}

class RecipeReferenceCalculator implements GrTypeCalculator<GrReferenceExpression> {
    @Override PsiType getType(GrReferenceExpression ref) { RecipeSupport.reference(ref) }
}

class RecipeCallCalculator implements GrTypeCalculator<GrMethodCallExpression> {
    @Override PsiType getType(GrMethodCallExpression call) { RecipeSupport.call(call) }
}

class RecipeIndexCalculator implements GrTypeCalculator<GrIndexProperty> {
    @Override PsiType getType(GrIndexProperty index) {
        if (RecipeSupport.sideForFile(index.containingFile) == null) return null
        def cls = RecipeSupport.identity(index.invokedExpression?.getType())
        def args = index.argumentList?.expressionArguments
        if (cls?.kind != 'map' || args == null || args.length != 1) return null
        RecipeSupport.target(cls.catalog.data.lookups[RecipeSupport.literal(args[0])], index, cls.catalog)
    }
}

/** Direct event/map calls also resolve as methods, for completion and parameter information. */
class RecipeMembersContributor extends NonCodeMembersContributor {
    @Override void processDynamicElements(PsiType qualifierType, PsiScopeProcessor processor, PsiElement place, ResolveState state) {
        if (RecipeSupport.sideForFile(place.containingFile) == null) return
        def kinds = processor.getHint(ElementClassHint.KEY)
        if (kinds != null && !kinds.shouldProcess(ElementClassHint.DeclarationKind.METHOD)) return
        String wanted = processor.getHint(NameHint.KEY)?.getName(state)
        def config = RecipeSupport.load(place)
        def cls = RecipeSupport.identity(qualifierType)
        if (cls?.kind in ['map', 'namespace']) cls.refresh()
        String recipeId = RecipeSupport.specializedId(qualifierType, place, config)
        if (recipeId != null) {
            def result = RecipeSupport.identityClass('result', recipeId, place, config)
            for (def method : result.getMethods().findAll { it.containingClass == result }) {
                if (wanted != null && wanted != method.name) continue
                def augmented = new GrLightMethodBuilder(place.manager, method.name)
                augmented.setContainingClass(((PsiClassType) qualifierType).resolve()).addModifier(PsiModifier.PUBLIC).setReturnType(method.returnType)
                for (def parameter : method.parameterList.parameters) {
                    def copied = augmented.addAndGetParameter(parameter.name, parameter.type)
                    copied.putUserData(RecipeSupport.PARAM, parameter.getUserData(RecipeSupport.PARAM))
                    copied.putUserData(RecipeSupport.SUGGESTIONS, parameter.getUserData(RecipeSupport.SUGGESTIONS))
                }
                augmented.putUserData(RecipeSupport.DOC, method.getUserData(RecipeSupport.DOC))
                augmented.setOriginInfo(method.originInfo)
                if (!processor.execute(augmented, state)) return
            }
            return
        }
        Map lookups = RecipeSupport.className(qualifierType) == RecipeSupport.EVENT ?
            config.data.shortcuts.collectEntries { name, id -> [(name):[kind:'recipe', id:id]] } :
            cls?.kind == 'map' ? cls.catalog.data.lookups :
            cls?.kind == 'namespace' ? cls.catalog.data.namespaces[cls.id].collectEntries { name, id -> [(name):[kind:'recipe', id:id]] } : [:]
        for (def entry : lookups.entrySet()) {
            if (entry.value.kind != 'recipe' || !RecipeSupport.validName(entry.key)) continue
            if (wanted != null && wanted != entry.key) continue
            if (cls?.kind in ['map', 'namespace'] && cls.delegateClass?.findMethodsByName(entry.key, true)?.length > 0) continue
            def callable = RecipeSupport.identity(RecipeSupport.type('callable', entry.value.id, place, config))
            if (callable == null) continue
            for (def method : callable.getMethods().findAll { it.name == 'call' }) {
                def renamed = new GrLightMethodBuilder(place.manager, entry.key)
                renamed.setContainingClass(callable).addModifier(PsiModifier.PUBLIC).setReturnType(method.returnType)
                for (def parameter : method.parameterList.parameters) {
                    def copied = renamed.addAndGetParameter(parameter.name, parameter.type)
                    copied.putUserData(RecipeSupport.PARAM, parameter.getUserData(RecipeSupport.PARAM))
                    copied.putUserData(RecipeSupport.SUGGESTIONS, parameter.getUserData(RecipeSupport.SUGGESTIONS))
                }
                renamed.putUserData(RecipeSupport.DOC, method.getUserData(RecipeSupport.DOC))
                renamed.setOriginInfo(method.originInfo)
                if (!processor.execute(renamed, state)) return
            }
        }
    }
}

class RecipeDocumentationProvider extends AbstractDocumentationProvider {
    @Override String generateDoc(PsiElement element, PsiElement originalElement) {
        if (RecipeSupport.sideForFile(originalElement?.containingFile) == null) return null
        String doc = element?.getUserData(RecipeSupport.DOC)
        doc == null ? null : '<pre>' + StringUtil.escapeXmlEntities(doc) + '</pre>'
    }
    @Override String generateHoverDoc(PsiElement element, PsiElement originalElement) { generateDoc(element, originalElement) }
}

// --- LivePlugin registration (unregistered automatically via pluginDisposable) ---
try {
    ExtensionPointName.create('org.intellij.groovy.membersContributor').point.registerExtension(new RecipeMembersContributor(), pluginDisposable)
    ExtensionPointName.create('com.intellij.documentationProvider').point.registerExtension(new RecipeDocumentationProvider(), pluginDisposable)
    [[GrReferenceExpression, new RecipeReferenceCalculator()], [GrMethodCallExpression, new RecipeCallCalculator()],
        [GrIndexProperty, new RecipeIndexCalculator()]].each { pair ->
        GrTypeCalculator.EP.addExplicitExtension(pair[0], pair[1])
        Disposer.register(pluginDisposable, { -> GrTypeCalculator.EP.removeExplicitExtension(pair[0], pair[1]) } as Disposable)
    }
    Disposer.register(pluginDisposable, { ->
        com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects().each { project ->
            project.putUserData(RecipeSupport.CACHE, null)
        }
    } as Disposable)
} catch (MissingPropertyException ignored) { /* IDE fixtures load classes without LivePlugin registration. */ }
