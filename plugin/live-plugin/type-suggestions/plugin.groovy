// depends-on-plugin com.intellij.java
// depends-on-plugin org.intellij.groovy
//
// Type-argument suggestions (LivePlugin script).
//
// SCOPE
//   Only .groovy use-sites under kubegroovy/{startup,client,server}_scripts/**.
//
// WHAT IT DOES
//   When the argument at the caret expects a supported type — including via
//   a sibling overload, e.g. itemStackOverload("") where overloads are
//   (ItemStack) and (Object) — completion offers ids for that type and
//   inserts the plain quoted id ("apple"). The overload (or the runtime
//   conversion) takes it from there, so no `as ...` cast is added in
//   argument position. It also fires inside a plain string coerced with
//   `as`, e.g. def testing = "<caret>" as ItemStack
//   (there only the literal is replaced, keeping your cast).
//   Supported types (see SPECS below, later to be driven by JSON):
//     - ItemStack -> apple, dirt, stone, flint_and_steel
//     - Registry / ResourceKey<Registry<?>> wrapper -> item, fluid, block, entity_type
//
// HOW INTELLIJ SEES CODE (the one idea behind this file)
//   The IDE parses every file into a PSI tree: nodes like GrCall (a method
//   call), GrArgumentList, GrLiteral (a string/number literal), PsiMethod,
//   PsiClass. A plugin never edits raw text blindly; it walks this tree with
//   PsiTreeUtil, asks nodes for their types, and registers small hooks:
//     - CompletionContributor: "when completion is invoked here, add items".
//     - InsertHandler: "after the user picks my item, rewrite the document".
//   LivePlugin loads this file at runtime: top-level statements run once and
//   register those hooks; `pluginDisposable` unregisters them on reload, so
//   re-running never stacks duplicates.
//
// RUNNING IT
//   compile.bat checks it compiles; install.bat copies it to LivePlugin;
//   reload it from the LivePlugin tool window.

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionContributorEP
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import com.intellij.psi.PsiEllipsisType
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import com.intellij.psi.ResolveResult
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import java.util.regex.Matcher
import java.util.regex.Pattern
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList
import javax.swing.Icon
import javax.swing.ImageIcon
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrTypeCastExpression
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes
import groovy.json.JsonSlurper
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiClassType

/**
 * Static helpers shared by the contributor below. Kept static and stateless
 * (except the icon cache) so every IDE callback can call them safely.
 *
 * <p>A theme of this file: every PSI call is wrapped in try/catch returning a
 * safe default. IDE trees can be half-built while typing, and a single
 * exception would kill the whole completion popup — better to show nothing
 * than to crash it.
 */
class ItemStackSupport {
    // Check the use-site, never the declaration being resolved. Completion copies
    // retain their original file; scratch files and unknown contexts fail closed.
    static String sideForFile(PsiFile file) {
        if (!(file instanceof GroovyFile)) return null
        PsiFile original = file.getOriginalFile()
        if (!(original instanceof GroovyFile)) return null
        String path = original.getVirtualFile()?.getPath()?.replace('\\', '/')
        if (path == null || !path.endsWith('.groovy')) return null
        def match = (path =~ /(?:^|\/)kubegroovy\/(startup|client|server)_scripts\/.+\.groovy$/)
        return match.find() ? match.group(1) : null
    }

    /**
     * Platform logger. Output lands in idea.log (Help > Show Log in Finder),
     * tagged with "ItemStackSuggestions". Levels: LOG.info / LOG.warn /
     * LOG.error("msg", throwable). Prefer the {@link #debug} helper below for
     * tracing so one flag silences everything.
     */
    static final Logger LOG = Logger.getInstance("ItemStackSuggestions")
    /**
     * Flip to true while learning/debugging to trace every trigger decision
     * (fires on each completion invocation, so leave false normally).
     */
    static boolean DEBUG = false

    /**
     * Trace helper: logs only when {@link #DEBUG} is true. Example:
     * <pre>
     *   ItemStackSupport.DEBUG = true          // top of your experiment
     *   ItemStackSupport.debug("index=" + index)
     * </pre>
     * then watch idea.log while invoking completion.
     */
    static void debug(String message) {
        if (!DEBUG) return
        try {
            LOG.info("[itemstack-suggest] " + message)
        } catch (Throwable ignored) {}
    }
    private static File configFile = null
    private static long configStamp = -1L
    private static Map<String, List<String>> values = [:]
    private static List<Map> delegates = []
    private static Map<String, Integer> genericDelegates = [:]
    private static final Pattern AS_AFTER = Pattern.compile("as\\s+([\\w.\$]+(?:\\s*<[^>]+>)?(?:\\[\\])?)")
    private static final Pattern AS_CAST_TEXT = Pattern.compile("\"[^\"]*\"\\s+as\\s+([\\w.\$]+(?:\\s*<[^>]+>)?(?:\\[\\])?)")
    private static final Pattern AS_AFTER_ANY = Pattern.compile("as\\s+([\\w.\$]+(?:\\s*<[^>]+>)?(?:\\[\\])?)")
    private static final Map<String, Icon> ICONS = new java.util.concurrent.ConcurrentHashMap<String, Icon>()
    private static String iconDir = null

    static synchronized void setConfigFile(File file) {
        configFile = file
        configStamp = -1L
    }

    /** Reload atomically. A malformed update leaves the last valid catalog active. */
    static synchronized Map loadConfig() {
        long attemptedStamp = -1L
        try {
            if (configFile == null || !configFile.isFile()) return [values: values, delegates: delegates, genericDelegates: genericDelegates]
            attemptedStamp = configFile.lastModified()
            if (attemptedStamp == configStamp) return [values: values, delegates: delegates, genericDelegates: genericDelegates]
            def parsed = new JsonSlurper().parse(configFile)
            if (!(parsed instanceof Map) || (parsed.schemaVersion as Integer) != 1
                || !(parsed.values instanceof Map) || !(parsed.delegates instanceof Collection)
                || !(parsed.genericDelegates instanceof Map)) {
                configStamp = attemptedStamp
                return [values: values, delegates: delegates, genericDelegates: genericDelegates]
            }
            Map<String, List<String>> nextValues = new LinkedHashMap<String, List<String>>()
            ((Map) parsed.values).each { k, v ->
                if (k != null && v instanceof Collection) nextValues.put(normalizeType(k.toString()), ((Collection) v).collect { it == null ? null : it.toString() }.findAll { it != null })
            }
            List<Map> nextDelegates = []
            ((Collection) parsed.delegates).each { d ->
                if (d instanceof Map && d.source != null && d.target != null) {
                    nextDelegates.add([source: normalizeType(d.source.toString()), target: normalizeType(d.target.toString()), prefix: d.prefix == null ? "" : d.prefix.toString()])
                }
            }
            Map<String, Integer> nextGeneric = new LinkedHashMap<String, Integer>()
            ((Map) parsed.genericDelegates).each { k, v ->
                try { if (k != null && v != null) nextGeneric.put(normalizeType(k.toString()), Integer.valueOf(v.toString())) } catch (Throwable ignored) {}
            }
            values = nextValues
            delegates = nextDelegates
            genericDelegates = nextGeneric
            configStamp = attemptedStamp
        } catch (Throwable t) {
            if (attemptedStamp >= 0L) configStamp = attemptedStamp
            debug("suggestion config reload failed; keeping previous catalog (" + t + ")")
        }
        return [values: values, delegates: delegates, genericDelegates: genericDelegates]
    }

    static String normalizeType(String type) {
        if (type == null) return ""
        return type.replaceAll("\\s+", "").replace('$', '.')
    }

    static String psiTypeName(PsiType type) {
        if (type == null) return null
        try {
            if (type instanceof PsiArrayType) return normalizeType(psiTypeName(((PsiArrayType) type).getComponentType()) + "[]")
            if (type instanceof PsiEllipsisType) return normalizeType(psiTypeName(((PsiEllipsisType) type).getComponentType()) + "[]")
            if (type instanceof PsiClassType) {
                PsiClassType ct = (PsiClassType) type
                PsiClass resolved = ct.resolve()
                String raw = resolved?.getQualifiedName()
                if (raw == null) raw = ct.getClassName()
                if (raw == null) return null
                PsiType[] args = ct.getParameters()
                if (args == null || args.length == 0) return normalizeType(raw)
                List<String> names = []
                for (PsiType arg : args) {
                    String n = psiTypeName(arg)
                    if (n == null) return null
                    names.add(n)
                }
                return normalizeType(raw + "<" + names.join(",") + ">")
            }
            String canonical = type.getCanonicalText()
            return canonical == null ? null : normalizeType(canonical)
        } catch (Throwable ignored) { return null }
    }

    static String rawTypeName(String type) {
        String normalized = normalizeType(type)
        int generic = normalized.indexOf('<')
        return generic < 0 ? normalized : normalized.substring(0, generic)
    }

    static List<String> typeArguments(String type) {
        String normalized = normalizeType(type)
        int start = normalized.indexOf('<')
        int end = normalized.lastIndexOf('>')
        if (start < 0 || end <= start) return []
        List<String> result = []
        int depth = 0
        int tokenStart = start + 1
        for (int i = start + 1; i < end; i++) {
            char c = normalized.charAt(i)
            if (c == '<') depth++
            else if (c == '>') depth--
            else if (c == ',' && depth == 0) { result.add(normalized.substring(tokenStart, i)); tokenStart = i + 1 }
        }
        result.add(normalized.substring(tokenStart, end))
        return result
    }

    static List<String> suggestionsForType(String requestedType) {
        Map config = loadConfig()
        Map<String, List<String>> catalogValues = (Map<String, List<String>>) config.values
        List<Map> catalogDelegates = (List<Map>) config.delegates
        Map<String, Integer> generic = (Map<String, Integer>) config.genericDelegates
        return resolveSuggestions(requestedType, catalogValues, catalogDelegates, generic, new LinkedHashSet<String>())
    }

    private static List<String> resolveSuggestions(String requestedType, Map<String, List<String>> catalogValues,
        List<Map> catalogDelegates, Map<String, Integer> generic, Set<String> path) {
        String type = normalizeType(requestedType)
        if (type.isEmpty() || path.contains(type)) return []
        path.add(type)
        List<String> result = new ArrayList<String>()
        try {
            result.addAll(catalogValues.getOrDefault(type, []))
            String raw = rawTypeName(type)
            if (raw != type) result.addAll(catalogValues.getOrDefault(raw, []))
            for (Map rule : catalogDelegates) {
                String source = normalizeType(rule.source?.toString())
                if (type == source || raw == source) {
                    String target = normalizeType(rule.target?.toString())
                    String prefix = rule.prefix == null ? "" : rule.prefix.toString()
                    resolveSuggestions(target, catalogValues, catalogDelegates, generic, path).each { result.add(prefix + it) }
                }
            }
            Integer argumentIndex = generic.get(raw)
            if (argumentIndex != null) {
                List<String> args = typeArguments(type)
                if (argumentIndex >= 0 && argumentIndex < args.size() && args[argumentIndex]) {
                    result.addAll(resolveSuggestions(args[argumentIndex], catalogValues, catalogDelegates, generic, path))
                }
            }
        } finally {
            path.remove(type)
        }
        return result
    }

    /**
     * Tells the icon loader where icons/<id>.png lives. Called once from the
     * script body with LivePlugin's `pluginPath` (the folder this script runs
     * from). If it is never called, {@link #iconFor} just returns null.
     */
    static void setIconDir(String dir) {
        try {
            iconDir = dir
        } catch (Throwable ignored) {}
    }

    /**
     * File lookup for a completion id. Registry ids flatten to the exporter
     * layout ({@code minecraft:apple} -> {@code minecraft__apple.png}, see
     * ItemIconExporter.escapeKey); legacy short names ({@code apple.png})
     * still resolve as a fallback. A lookup entry shows an {@code Icon} via
     * {@code LookupElementBuilder.withIcon(...)} — plain Swing, so animated
     * GIF/APNG will NOT animate here; static PNG is the supported route.
     *
     * @return the icon, or null when the file is missing/unreadable (caller
     *         then shows a text-only entry — never fail completion for art).
     */
    static String iconFileName(String id) {
        try {
            String name = id.replace(":", "__").replace("\"", "'").replace("/", "___")
            return name + ".png"
        } catch (Throwable ignored) {
            return id + ".png"
        }
    }

    static Icon iconFor(String id) {
        try {
            Icon cached = ICONS.get(id)
            if (cached != null) return cached
            if (iconDir == null) return null
            File f = new File(iconDir, iconFileName(id))
            if (!f.isFile()) f = new File(iconDir, id + ".png")
            if (!f.isFile() && id.startsWith("minecraft:")) f = new File(iconDir, id.substring("minecraft:".length()) + ".png")
            if (!f.isFile()) return null
            ImageIcon raw = new ImageIcon(f.getAbsolutePath())
            if (raw.getIconWidth() <= 0) return null
            Icon icon = raw
            if (raw.getIconWidth() != 16 || raw.getIconHeight() != 16) {
                icon = new ImageIcon(raw.getImage().getScaledInstance(16, 16, java.awt.Image.SCALE_SMOOTH))
            }
            ICONS.put(id, icon)
            return icon
        } catch (Throwable ignored) {
            return null
        }
    }

    static String classifyType(PsiType type) {
        String name = psiTypeName(type)
        return name != null && !suggestionsForType(name).isEmpty() ? name : null
    }

    static List<String> idsForSpec(String spec) {
        return suggestionsForType(spec)
    }

    static String specForParam(PsiMethod method, int index, PsiSubstitutor substitutor = null,
        PsiElement expression = null, PsiElement position = null, int offset = -1) {
        if (method == null || index < 0) return null
        try {
            def params = method.getParameterList()?.getParameters()
            if (params == null || params.length == 0) return null
            if (index < params.length) {
                try {
                    PsiType paramType = params[index].getType()
                    if (substitutor != null) paramType = substitutor.substitute(paramType)
                    String s = typeAtArgument(paramType, expression, position, offset)
                    if (s != null) return s
                } catch (Throwable ignored) {}
            }
            if (index >= params.length - 1) {
                try {
                    PsiType comp = varargItemType(method)
                    if (comp != null) {
                        if (substitutor != null) comp = substitutor.substitute(comp)
                        String s = classifyType(comp)
                        if (s != null) return s
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return null
    }

    static String denotesSpec(GrExpression typeExpr) {
        if (typeExpr == null) return null
        try {
            String resolved = psiTypeName(typeExpr.getType())
            if (resolved != null && !suggestionsForType(resolved).isEmpty()) return resolved
        } catch (Throwable ignored) {}
        try {
            String text = normalizeType(typeExpr.getText())
            PsiElement resolved = typeExpr instanceof GrReferenceExpression ? ((GrReferenceExpression) typeExpr).resolve() : null
            if (resolved instanceof PsiClass) {
                String qn = ((PsiClass) resolved).getQualifiedName()
                if (qn != null) text = qn + (text.contains("<") ? text.substring(text.indexOf('<')) : "")
            }
            if (!suggestionsForType(text).isEmpty()) return text
        } catch (Throwable ignored) {}
        return null
    }

    /** Resolve the scalar type selected inside recursively nested list/map literals. */
    static String typeAtArgument(PsiType expected, PsiElement expression, PsiElement position, int offset) {
        if (expected == null) return null
        try {
            List<GrListOrMap> containers = []
            PsiElement e = position
            while (e != null && e != expression && !(e instanceof PsiFile)) {
                if (e instanceof GrListOrMap) containers.add((GrListOrMap) e)
                e = e.getParent()
            }
            if (expression instanceof GrListOrMap && !containers.contains(expression)) containers.add((GrListOrMap) expression)
            Collections.reverse(containers)
            PsiType selected = expected
            for (GrListOrMap container : containers) {
                selected = unwrapSuggestionWrapper(selected)
                if (selected instanceof PsiArrayType || selected instanceof PsiEllipsisType) {
                    selected = selected instanceof PsiEllipsisType
                        ? ((PsiEllipsisType) selected).getComponentType()
                        : ((PsiArrayType) selected).getComponentType()
                } else if (isMapType(selected)) {
                    PsiType mapType = matchingSupertype(selected,
                        ["java.util.Map", "java.util.SortedMap", "java.util.NavigableMap"] as Set<String>, new LinkedHashSet<String>())
                    List<String> args = typeArguments(psiTypeName(mapType))
                    int side = mapSide(container, position, offset)
                    if (args.size() < 2 || side < 0) return null
                    selected = typeFromArgument(args[side], mapType, side)
                } else if (isCollectionType(selected)) {
                    PsiType collectionType = matchingSupertype(selected,
                        ["java.util.Collection", "java.lang.Iterable"] as Set<String>, new LinkedHashSet<String>())
                    List<String> args = typeArguments(psiTypeName(collectionType))
                    if (args.isEmpty()) return null
                    selected = typeFromArgument(args[0], collectionType, 0)
                } else {
                    return null
                }
            }
            return classifyType(selected)
        } catch (Throwable ignored) { return null }
    }

    private static PsiType unwrapSuggestionWrapper(PsiType type) {
        Map config = loadConfig()
        Map<String, Integer> generic = (Map<String, Integer>) config.genericDelegates
        Set<String> path = new LinkedHashSet<String>()
        PsiType selected = type
        while (selected != null) {
            String name = psiTypeName(selected)
            if (name == null || !path.add(normalizeType(name))) return null
            Integer index = generic.get(rawTypeName(name))
            List<String> args = typeArguments(name)
            if (index == null) return selected
            if (index < 0 || index >= args.size()) return null
            selected = typeFromArgument(args[index], selected, index)
        }
        return null
    }

    private static PsiType typeFromArgument(String name, PsiType parent, int index) {
        try {
            if (parent instanceof PsiClassType) {
                PsiType[] params = ((PsiClassType) parent).getParameters()
                if (index >= 0 && index < params.length) return params[index]
            }
        } catch (Throwable ignored) {}
        return null
    }

    private static boolean isMapType(PsiType type) {
        return matchingSupertype(type,
            ["java.util.Map", "java.util.SortedMap", "java.util.NavigableMap"] as Set<String>, new LinkedHashSet<String>()) != null
    }

    private static boolean isCollectionType(PsiType type) {
        return matchingSupertype(type,
            ["java.util.Collection", "java.lang.Iterable"] as Set<String>, new LinkedHashSet<String>()) != null
    }

    private static PsiType matchingSupertype(PsiType type, Set<String> names, Set<String> visited) {
        try {
            if (!(type instanceof PsiClassType)) return null
            PsiClass cls = ((PsiClassType) type).resolve()
            String qn = cls?.getQualifiedName()
            if (qn == null || !visited.add(psiTypeName(type))) return null
            if (names.contains(qn)) return type
            for (PsiType parent : type.getSuperTypes()) {
                PsiType match = matchingSupertype(parent, names, visited)
                if (match != null) return match
            }
        } catch (Throwable ignored) { }
        return null
    }

    static int mapSide(GrListOrMap container, PsiElement position, int offset) {
        try {
            PsiElement e = position
            while (e != null && e != container) {
                String simple = e.getClass().getSimpleName()
                if (simple.contains("NamedArgument")) {
                    def value = e.getClass().getMethod("getExpression").invoke(e)
                    return value != null && value.getTextRange().contains(offset) ? 1 : 0
                }
                if (simple.contains("MapEntry")) {
                    def value = e.getClass().getMethod("getValue").invoke(e)
                    if (value == null) value = e.getClass().getMethod("getValueExpression").invoke(e)
                    return value != null && value.getTextRange().contains(offset) ? 1 : 0
                }
                e = e.getParent()
            }
        } catch (Throwable ignored) {}
        // Fallback for incomplete PSI while the user is typing: the last direct
        // colon in the current map entry determines whether the caret is in K or V.
        try {
            String text = container.getText()
            int relative = offset - container.getTextRange().getStartOffset()
            int depth = 0
            char quote = 0
            int entryStart = 0
            for (int i = 0; i < Math.min(relative, text.length()); i++) {
                char c = text.charAt(i)
                if (quote != 0) { if (c == quote && (i == 0 || text.charAt(i - 1) != '\\')) quote = 0; continue }
                if (c == '\'' || c == '"') { quote = c; continue }
                if (c == '[' || c == '(' || c == '{') depth++
                else if (c == ']' || c == ')' || c == '}') depth--
                else if (c == ',' && depth == 1) entryStart = i + 1
            }
            int colon = text.indexOf(':', entryStart)
            return colon >= 0 && colon < relative ? 1 : 0
        } catch (Throwable ignored) { return -1 }
    }

    /**
     * The ItemStack component of a varargs declaration, or null when the
     * method is not varargs. Handles both PSI shapes: an ellipsis type
     * ({@code ItemStack...}) and a plain array with {@code isVarArgs()}
     * ({@code ItemStack[]}), which is how some Groovy varargs surface.
     */
    static PsiType varargItemType(PsiMethod method) {
        try {
            def params = method.getParameterList()?.getParameters()
            if (params == null || params.length == 0) return null
            PsiType last = params[params.length - 1].getType()
            if (last instanceof PsiEllipsisType) {
                try {
                    return ((PsiEllipsisType) last).getComponentType()
                } catch (Throwable ignored) {
                    return null
                }
            }
            boolean vargs = false
            try {
                vargs = method.isVarArgs()
            } catch (Throwable ignored) {}
            if (vargs) {
                try {
                    if (last.getArrayDimensions() > 0) return last.getDeepComponentType()
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return null
    }

    /**
     * Every same-named method visible at this reference, regardless of whether
     * the current arguments make the call resolve to it. That is the point:
     * {@code resolveMethod()}/{@code multiResolveGroovy()} only return
     * <em>applicable</em> overloads, so {@code itemStackOverload("")} yields
     * just {@code (Object)} and the {@code (ItemStack)} sibling stays hidden.
     * {@code getVariants()} lists everything visible under that name (script
     * methods, owner, imports, GDK, synthetics), unfiltered.
     */
    static List<PsiMethod> namedVariants(GrReferenceExpression ref, String name) {
        List<PsiMethod> out = new ArrayList<PsiMethod>()
        if (ref == null || name == null) return out
        try {
            Object[] variants = ref.getVariants()
            if (variants == null) return out
            for (def v : variants) {
                try {
                    def el = v
                    if (el instanceof GroovyResolveResult) el = ((GroovyResolveResult) el).getElement()
                    else if (el instanceof ResolveResult) el = ((ResolveResult) el).getElement()
                    if (el instanceof PsiMethod && name.equals(((PsiMethod) el).getName())) out.add((PsiMethod) el)
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        // (per-variant detail omitted; count below tells enough)
        ItemStackSupport.debug("namedVariants count=" + out.size())
        return out
    }

    /**
     * Same-named methods found through classes instead of reference variants.
     * Needed because getVariants() can return nothing during completion.
     * Searches containing classes of already-resolved candidates plus the
     * qualifier's class (for `obj.foo("")`), including inherited members.
     */
    static List<PsiMethod> classOverloads(GrReferenceExpression ref, Collection<PsiMethod> seeds, String name) {
        List<PsiMethod> out = new ArrayList<PsiMethod>()
        Set<PsiClass> classes = new LinkedHashSet<PsiClass>()
        try {
            if (seeds != null) {
                for (PsiMethod m : seeds) {
                    try {
                        PsiClass c = m.getContainingClass()
                        if (c != null) classes.add(c)
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}
        try {
            if (ref != null) {
                def q = ref.getQualifierExpression()
                if (q != null) {
                    PsiType qt = q.getType()
                    if (qt instanceof PsiClassType) {
                        PsiClass qc = ((PsiClassType) qt).resolve()
                        if (qc != null) classes.add(qc)
                    }
                }
            }
        } catch (Throwable ignored) {}
        for (PsiClass cls : classes) {
            try {
                PsiMethod[] found = cls.findMethodsByName(name, true)
                if (found != null) {
                    ItemStackSupport.debug("class overloads=" + found.length)
                    out.addAll(Arrays.asList(found))
                }
            } catch (Throwable ignored) {}
        }
        return out
    }

    /**
     * Second trigger shape: the caret sits in a string literal coerced with
     * `as ItemStack`, e.g. {@code def testing = "<caret>" as ItemStack} — no
     * method call involved, so the argument-index logic cannot apply.
     *
     * <p>Why three shapes? The exact PSI node for `as` differs across IDE
     * versions, so we accept all of them:
     * <ol>
     *   <li>binary {@code as} expression ({@code GrBinaryExpression} whose
     *       operation token is {@code kAS}), caret on the value (left) side;</li>
     *   <li>{@code GrTypeCastExpression} ancestor (operand holds the caret,
     *       cast type denotes ItemStack);</li>
     *   <li>text fallback: some ancestor's text matches
     *       {@code "..." as [... .]ItemStack} with the caret inside the match
     *       (offset-checked, so a sibling declaration can't false-positive).</li>
     * </ol>
     */
    static boolean isSupportedCoercedLiteral(PsiElement position, int offset) {
        return getCoercedSpec(position, offset) != null
    }

    static String quoteSuggestion(String id) {
        StringBuilder out = new StringBuilder('"')
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i)
            switch (c) {
                case '\\': out.append("\\\\"); break
                case '"': out.append("\\\""); break
                case '$': out.append('\$'); break
                case '\n': out.append("\\n"); break
                case '\r': out.append("\\r"); break
                case '\t': out.append("\\t"); break
                default:
                    if (Character.isISOControl(c)) out.append(String.format("\\u%04x", (int) c))
                    else out.append(c)
            }
        }
        return out.append('"').toString()
    }

    static TextRange mapEntryPartRange(PsiElement position, int offset, boolean key) {
        try {
            GrListOrMap map = PsiTreeUtil.getParentOfType(position, GrListOrMap.class, false)
            if (map == null || !map.isMap()) return null
            PsiElement e = position
            while (e != null && e != map) {
                String simple = e.getClass().getSimpleName()
                if (simple.contains("NamedArgument")) {
                    def selected = key ? e.getClass().getMethod("getLabel").invoke(e) : e.getClass().getMethod("getExpression").invoke(e)
                    if (selected != null && selected.getTextRange().contains(offset)) return selected.getTextRange()
                    return null
                }
                if (simple.contains("MapEntry")) {
                    def selected = e.getClass().getMethod(key ? "getKey" : "getValue").invoke(e)
                    if (selected != null && selected.getTextRange().contains(offset)) return selected.getTextRange()
                }
                e = e.getParent()
            }
        } catch (Throwable ignored) {}
        return null
    }

    static TextRange listElementPartRange(PsiElement position, int offset) {
        try {
            GrListOrMap list = PsiTreeUtil.getParentOfType(position, GrListOrMap.class, false)
            if (list == null || list.isMap()) return null
            PsiElement element = position
            while (element != null && element.getParent() != list) element = element.getParent()
            if (element instanceof GrExpression && element.getTextRange().contains(offset)) return element.getTextRange()
        } catch (Throwable ignored) { }
        return null
    }

    /**
     * The text typed so far inside the string literal, e.g. `"app` -> "app".
     * Completion needs this because IntelliJ's default prefix logic doesn't
     * work well inside string literals, so we compute it from the literal's
     * text and the caret offset and hand it to
     * {@code result.withPrefixMatcher(prefix)}.
     *
     * <p>Two things are stripped: the IDE's dummy identifier
     * ("IntellijIdeaRulezzz" — IntelliJ temporarily inserts it so the half-
     * typed file still parses while completing) and any GString
     * interpolation start ($...). Outside a literal (empty argument slot) it
     * returns "" so everything is offered.
     */
    static String literalPrefix(PsiElement position, int offset) {
        try {
            GrLiteral lit = PsiTreeUtil.getParentOfType(position, GrLiteral.class, false)
            if (lit == null || !lit.getTextRange().contains(offset)) return ""
            String text = lit.getText()
            int openLen = 1
            if (text.startsWith('"""') || text.startsWith("'''")) openLen = 3
            if (text.length() < openLen + 1) return ""
            int rel = offset - lit.getTextRange().getStartOffset()
            if (rel <= openLen) return ""
            String sub = text.substring(openLen, Math.min(rel, text.length()))
            int dummy = sub.toLowerCase(java.util.Locale.ROOT).indexOf("intellijidearulezzz")
            if (dummy >= 0) sub = sub.substring(0, dummy)
            int interp = sub.indexOf('$')
            if (interp >= 0) sub = sub.substring(0, interp)
            return sub
        } catch (Throwable ignored) {
            return ""
        }
    }

    /**
     * True when the document text right after {@code endOffset} (skipping
     * whitespace) reads {@code as ItemStack} (short or qualified name).
     * Text-based on purpose: whatever PSI node `as` parses into, the text
     * after a literal tells us whether a cast is already written there.
     * Uses {@code lookingAt} (match at the start) rather than
     * {@code String.matches} (whole-string match), because the 60-char window
     * usually spans line breaks that {@code .*} cannot cross.
     */
    static boolean asItemStackFollows(String docText, int endOffset) {
        try {
            int i = endOffset
            while (i < docText.length() && Character.isWhitespace(docText.charAt(i))) i++
            String tail = docText.substring(i, Math.min(i + 60, docText.length()))
            return AS_AFTER.matcher(tail).lookingAt()
        } catch (Throwable ignored) {
            return false
        }
    }

    static boolean asSupportedCastFollows(String docText, int endOffset) {
        try {
            int i = endOffset
            while (i < docText.length() && Character.isWhitespace(docText.charAt(i))) i++
            String tail = docText.substring(i, Math.min(i + 80, docText.length()))
            if (AS_AFTER.matcher(tail).lookingAt()) return true
            return AS_AFTER_ANY.matcher(tail).lookingAt()
        } catch (Throwable ignored) {
            return false
        }
    }

    static String getCoercedSpec(PsiElement position, int offset) {
        GrLiteral lit = null
        try {
            lit = PsiTreeUtil.getParentOfType(position, GrLiteral.class, false)
            if (lit == null || !lit.getTextRange().contains(offset)) return null
            try {
                if (!lit.isString()) return null
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {
            return null
        }
        try {
            PsiElement p = lit.getParent()
            if (p instanceof GrBinaryExpression
                && ((GrBinaryExpression) p).getOperationTokenType() == GroovyTokenTypes.kAS) {
                GrExpression left = ((GrBinaryExpression) p).getLeftOperand()
                if (left != null && left.getTextRange().contains(offset)) {
                    return denotesSpec(((GrBinaryExpression) p).getRightOperand())
                }
                return null
            }
        } catch (Throwable ignored) {}
        try {
            PsiElement e = lit
            for (int i = 0; i < 3 && e != null && !(e instanceof PsiFile); i++) {
                e = e.getParent()
                if (e instanceof GrTypeCastExpression) {
                    GrExpression operand = ((GrTypeCastExpression) e).getOperand()
                    if (operand == null || !operand.getTextRange().contains(offset)) return null
                    def castType = ((GrTypeCastExpression) e).getCastTypeElement()
                    if (castType == null) return null
                    try {
                        PsiType t = castType.getType()
                        if (t != null) {
                            String s = classifyType(t)
                            if (s != null) return s
                        }
                    } catch (Throwable ignored) {}
                    try {
                        String rt = castType.getText()?.trim()
                        if (rt != null) {
                            String textType = normalizeType(rt)
                            if (!suggestionsForType(textType).isEmpty()) return textType
                        }
                    } catch (Throwable ignored) {}
                    return null
                }
            }
        } catch (Throwable ignored) {}
        try {
            PsiElement e = lit
            for (int i = 0; i < 4 && e != null && !(e instanceof PsiFile); i++) {
                try {
                    String text = e.getText()
                    int base = e.getTextRange().getStartOffset()
                    Matcher m = AS_CAST_TEXT.matcher(text)
                    while (m.find()) {
                        if (base + m.start() <= offset && offset <= base + m.end()) {
                            String textType = normalizeType(m.group(1))
                            if (!suggestionsForType(textType).isEmpty()) return textType
                        }
                    }

                } catch (Throwable ignored) {}
                e = e.getParent()
            }
        } catch (Throwable ignored) {}
        return null
    }
}

/**
 * Supplies the completion items. The IDE calls {@link #addCompletions} on
 * every BASIC completion inside Groovy code; we return silently unless one of
 * the two trigger shapes matches, otherwise the list would be polluted
 * everywhere.
 */
class ItemStackCompletionProvider extends CompletionProvider<CompletionParameters> {
    /**
     * Runs AFTER IntelliJ inserts the picked lookup string. It must repair the
     * document into the final form, because the raw inserted word ("apple")
     * is not valid code on its own. Steps and why this order:
     * <ol>
     *   <li>No import is added: every branch below inserts a bare quoted id
     *       resolved by runtime coercion, so no ItemStack reference is ever
     *       emitted and no import is needed.</li>
     *   <li>{@code commitDocument()} after each edit batch — the document and
     *       the PSI tree are separate copies; PSI queries (findElementAt)
     *       only see committed text.</li>
     *   <li>If the caret is in a `"..." as ...` literal, replace just the
     *       literal with the quoted id, keeping the user's explicit cast
     *       (never produce `"apple" as ItemStack as ItemStack`). The check is
     *       deliberately text-based (literal followed by `as ItemStack`) rather
     *       than PSI-shape-based, because the `as` node type varies — a shape
     *       miss here used to fall through and garble the line.</li>
     *   <li>Else walk up to the argument expression (the child of
     *       {@code GrArgumentList}) and replace it whole with
     *       the quoted id; with no argument yet, insert at caret. No cast is added in argument position.</li>
     * </ol>
     * Insert handlers already run inside a write action, so direct
     * {@code insertString}/{@code replaceString} calls are legal here.
     */
    static final InsertHandler<LookupElement> ITEM_INSERT = { InsertionContext ctx, LookupElement item ->
        if (ItemStackSupport.sideForFile(ctx.getFile()) == null) return
        String id = null
        try {
            id = item.getLookupString()
        } catch (Throwable ignored) {}
        if (id == null || id.isEmpty()) return
        ItemStackSupport.debug("insert id=" + id)
        try {
            def doc = ctx.getDocument()
            ctx.commitDocument()
            // 2. Locate the string literal at/before the caret, if any.
            //    Check both sides: at a token boundary findElementAt may
            //    return a neighbour instead of the literal's contents.
            PsiFile file = ctx.getFile()
            int caret = ctx.getTailOffset()
            GrLiteral lit = null
            try {
                List<PsiElement> leaves = new ArrayList<PsiElement>()
                try {
                    PsiElement atEl = file.findElementAt(caret)
                    if (atEl != null) leaves.add(atEl)
                    if (caret > 0) {
                        PsiElement before = file.findElementAt(caret - 1)
                        if (before != null && !leaves.contains(before)) leaves.add(before)
                    }
                } catch (Throwable ignored) {}
                for (PsiElement leaf : leaves) {
                    GrLiteral cand = PsiTreeUtil.getParentOfType(leaf, GrLiteral.class, false)
                    if (cand == null) continue
                    boolean strOk = false
                    try {
                        strOk = cand.isString()
                    } catch (Throwable ignored) {}
                    if (strOk) {
                        lit = cand
                        break
                    }
                }
            } catch (Throwable ignored) {}
            if (lit != null) ItemStackSupport.debug("found literal range=" + lit.getTextRange())
            else ItemStackSupport.debug("no literal at caret=" + caret)
            Editor editor = ctx.getEditor()
            String docText = ""
            try {
                docText = doc.getText()
            } catch (Throwable ignored) {}
            // 3. Literal already followed by `as ...`: replace just the
            //    literal, keep the user's cast.
            if (lit != null && ItemStackSupport.asSupportedCastFollows(docText, lit.getTextRange().getEndOffset())) {
                ItemStackSupport.debug("insert branch=literal-only")
                String quoted = ItemStackSupport.quoteSuggestion(id)
                TextRange r = lit.getTextRange()
                doc.replaceString(r.getStartOffset(), r.getEndOffset(), quoted)
                editor.getCaretModel().moveToOffset(r.getStartOffset() + quoted.length())
                ctx.commitDocument()
                return
            }
            TextRange mapKey = ItemStackSupport.mapEntryPartRange(position, caret, true)
            if (mapKey != null) {
                String quoted = ItemStackSupport.quoteSuggestion(id)
                doc.replaceString(mapKey.getStartOffset(), mapKey.getEndOffset(), quoted)
                editor.getCaretModel().moveToOffset(mapKey.getStartOffset() + quoted.length())
                ctx.commitDocument()
                return
            }
            TextRange mapValue = ItemStackSupport.mapEntryPartRange(position, caret, false)
            if (mapValue != null) {
                String quoted = ItemStackSupport.quoteSuggestion(id)
                doc.replaceString(mapValue.getStartOffset(), mapValue.getEndOffset(), quoted)
                editor.getCaretModel().moveToOffset(mapValue.getStartOffset() + quoted.length())
                ctx.commitDocument()
                return
            }
            TextRange listElement = ItemStackSupport.listElementPartRange(position, caret)
            if (listElement != null) {
                String quoted = ItemStackSupport.quoteSuggestion(id)
                doc.replaceString(listElement.getStartOffset(), listElement.getEndOffset(), quoted)
                editor.getCaretModel().moveToOffset(listElement.getStartOffset() + quoted.length())
                ctx.commitDocument()
                return
            }
            GrListOrMap activeMap = PsiTreeUtil.getParentOfType(position, GrListOrMap.class, false)
            if (activeMap != null && activeMap.isMap()) {
                int mapSide = ItemStackSupport.mapSide(activeMap, position, caret)
                if ((mapSide == 0 && mapKey == null) || (mapSide == 1 && mapValue == null)) {
                    String quoted = ItemStackSupport.quoteSuggestion(id)
                    doc.insertString(caret, quoted)
                    editor.getCaretModel().moveToOffset(caret + quoted.length())
                    ctx.commitDocument()
                    return
                }
            } else if (activeMap != null) {
                String quoted = ItemStackSupport.quoteSuggestion(id)
                doc.insertString(caret, quoted)
                editor.getCaretModel().moveToOffset(caret + quoted.length())
                ctx.commitDocument()
                return
            }
            // 3b. Literal inside a list passed as the argument, e.g.
            //     someMethod(["<caret>"]): the list itself is the varargs
            //     argument, so replace just the item instead
            //     of nuking the whole list.
            boolean inList = false
            if (lit != null) {
                try {
                    PsiElement w = lit
                    for (int i = 0; i < 6 && w != null && !(w instanceof PsiFile); i++) {
                        w = w.getParent()
                        if (w == null) break
                        if (w instanceof GrArgumentList) break
                        if (w instanceof GrListOrMap) {
                            inList = true
                            break
                        }
                    }
                } catch (Throwable ignored) {}
            }
            // In method arguments the id goes in bare: an ItemStack-typed
            // overload (or the runtime conversion) takes it from there, so no
            // `as ItemStack` is added. Only the literal-only branch above keeps
            // a cast, and that's one the user wrote themselves.
            String replacement = ItemStackSupport.quoteSuggestion(id)
            if (inList) {
                ItemStackSupport.debug("insert branch=replace-list-item")
                TextRange r = lit.getTextRange()
                doc.replaceString(r.getStartOffset(), r.getEndOffset(), replacement)
                editor.getCaretModel().moveToOffset(r.getStartOffset() + replacement.length())
                ctx.commitDocument()
                return
            }
            GrExpression arg = null
            try {
                PsiElement e = (PsiElement) (lit != null ? lit : null)
                if (e == null) {
                    try {
                        e = file.findElementAt(caret)
                    } catch (Throwable ignored) {}
                }
                while (e != null && !(e instanceof PsiFile)) {
                    def p = e.getParent()
                    if (p instanceof GrArgumentList) {
                        if (e instanceof GrExpression) arg = (GrExpression) e
                        break
                    }
                    e = p
                }
            } catch (Throwable ignored) {}
            if (arg != null) {
                ItemStackSupport.debug("insert branch=replace-arg")
                TextRange r = arg.getTextRange()
                doc.replaceString(r.getStartOffset(), r.getEndOffset(), replacement)
                editor.getCaretModel().moveToOffset(r.getStartOffset() + replacement.length())
            } else if (lit != null) {
                // ...a string literal with no cast after it: replace the
                // literal (inserting mid-literal is what garbled the line, so
                // that path is deliberately gone here).
                ItemStackSupport.debug("insert branch=replace-literal")
                TextRange r = lit.getTextRange()
                doc.replaceString(r.getStartOffset(), r.getEndOffset(), replacement)
                editor.getCaretModel().moveToOffset(r.getStartOffset() + replacement.length())
            } else {
                // No literal, no argument (empty slot): insert at caret.
                ItemStackSupport.debug("insert branch=insert-at-caret")
                doc.insertString(caret, replacement)
                editor.getCaretModel().moveToOffset(caret + replacement.length())
            }
            ctx.commitDocument()
        } catch (Throwable ignored) {}
    } as InsertHandler<LookupElement>

    /**
     * The actual trigger. Flow: find innermost enclosing {@code GrCall} ->
     * figure out which argument index holds the caret (arg ranges contain it;
     * empty parens/trailing comma count as "next index = args.length") ->
     * resolve the call ({@code resolveMethod()} plus all
     * {@code multiResolveGroovy(false)} overloads) and check the parameter at
     * that index via {@link ItemStackSupport#specForParam}. If no call
     * matched, try the standalone `"..." as ...` literal shape. On a
     * match, offer one bare lookup element per id (display = just the id;
     * the insert handler builds the real code).
     */
    @Override
    protected void addCompletions(CompletionParameters parameters, ProcessingContext context, CompletionResultSet result) {
        try {
            if (ItemStackSupport.sideForFile(parameters.getOriginalFile()) == null) return
            PsiElement position = parameters.getPosition()
            int offset = parameters.getOffset()
            List<String> specs = new ArrayList<String>()
            GrCall call = PsiTreeUtil.getParentOfType(position, GrCall.class, false)
            if (call != null) {
                GrArgumentList argList = null
                try {
                    argList = call.getArgumentList()
                } catch (Throwable ignored) {}
                GrExpression[] args = []
                try {
                    args = call.getExpressionArguments() ?: []
                } catch (Throwable ignored) {}
                int index = -1
                for (int i = 0; i < args.length; i++) {
                    try {
                        if (args[i].getTextRange().contains(offset)) {
                            index = i
                            break
                        }
                    } catch (Throwable ignored) {}
                }
                if (index < 0) {
                    // Empty parens or trailing comma: caret sits directly in the arg list.
                    try {
                        if (argList != null && argList.getTextRange().contains(offset)) index = args.length
                    } catch (Throwable ignored) {}
                }
                if (index >= 0) {
                    Map<PsiMethod, PsiSubstitutor> seen = new LinkedHashMap<PsiMethod, PsiSubstitutor>()
                    try {
                        try {
                            PsiMethod single = call.resolveMethod()
                            if (single != null) seen.put(single, null)
                        } catch (Throwable ignored) {}
                        try {
                            GroovyResolveResult[] variants = call.multiResolveGroovy(false)
                            if (variants != null) {
                                for (def v : variants) {
                                    try {
                                        def el = v.getElement()
                                        if (el instanceof PsiMethod) {
                                            PsiSubstitutor subst = null
                                            try { subst = v.getSubstitutor() } catch (Throwable ignored) {}
                                            if (!seen.containsKey((PsiMethod) el) || subst != null) seen.put((PsiMethod) el, subst)
                                        }
                                    } catch (Throwable ignored) {}
                                }
                            }
                        } catch (Throwable ignored) {}
                        ItemStackSupport.debug("index=" + index + " resolved=" + seen.size())
                        for (def resolved : seen.entrySet()) {
                            try {
                                GrExpression argument = index < args.length ? args[index] : null
                                String s = ItemStackSupport.specForParam(resolved.key, index, resolved.value, argument, position, offset)
                                if (s != null) specs.add(s)
                            } catch (Throwable ignored) {}
                        }
                    } catch (Throwable ignored) {}
                    if (specs.isEmpty() && call instanceof GrMethodCallExpression) {
                        // The call can currently resolve to a non-matching overload
                        // (e.g. the Object sibling): scan EVERY same-named overload,
                        // via reference variants plus containing/qualifier classes
                        // (getVariants() can come back empty during completion).
                        GrReferenceExpression ref = null
                        String refName = null
                        try {
                            def inv = ((GrMethodCallExpression) call).getInvokedExpression()
                            if (inv instanceof GrReferenceExpression) {
                                ref = (GrReferenceExpression) inv
                                refName = ref.getReferenceName()
                            }
                        } catch (Throwable ignored) {}
                        ItemStackSupport.debug("invoked=" + refName)
                        Set<PsiMethod> extra = new HashSet<PsiMethod>()
                        if (refName != null) {
                            try {
                                extra.addAll(ItemStackSupport.namedVariants(ref, refName))
                            } catch (Throwable ignored) {}
                            try {
                                extra.addAll(ItemStackSupport.classOverloads(ref, seen, refName))
                            } catch (Throwable ignored) {}
                        }
                        ItemStackSupport.debug("overloads=" + extra.size())
                        for (PsiMethod m : extra) {
                            try {
                                GrExpression argument = index < args.length ? args[index] : null
                                String s = ItemStackSupport.specForParam(m, index, null, argument, position, offset)
                                if (s != null) {
                                    ItemStackSupport.debug("match via overload " + m.getName() + m.getParameterList().getText() + " spec=" + s)
                                    specs.add(s)
                                }
                            } catch (Throwable ignored) {}
                        }
                    }
                }
            }
            // `def testing = "<caret>" as ItemStack` (no call involved).
            if (specs.isEmpty()) {
                try {
                    String coerced = ItemStackSupport.getCoercedSpec(position, offset)
                    if (coerced != null) specs.add(coerced)
                } catch (Throwable ignored) {}
                if (specs.isEmpty()) {
                    try {
                        if (ItemStackSupport.isSupportedCoercedLiteral(position, offset)) {
                            String coerced = ItemStackSupport.getCoercedSpec(position, offset)
                            if (coerced != null) specs.add(coerced)
                        }
                    } catch (Throwable ignored) {}
                }
            }
            ItemStackSupport.debug("offset=" + offset + " hasCall=" + (call != null) + " specs=" + specs)
            if (specs.isEmpty()) return
            List<String> offerIds = new ArrayList<String>()
            for (String s : specs) {
                try {
                    for (String id : ItemStackSupport.idsForSpec(s)) {
                        offerIds.add(id)
                    }
                } catch (Throwable ignored) {}
            }
            def sink = result.withPrefixMatcher(ItemStackSupport.literalPrefix(position, offset))
            for (String id : offerIds) {
                def el = LookupElementBuilder.create(id).withInsertHandler(ITEM_INSERT)
                try {
                    Icon icon = ItemStackSupport.iconFor(id)
                    if (icon != null) el = el.withIcon(icon)
                    else ItemStackSupport.debug("no icon for '" + id + "' (icons/" + ItemStackSupport.iconFileName(id) + " missing?)")
                } catch (Throwable ignored) {}
                sink.addElement(el)
            }
            ItemStackSupport.debug("offered " + offerIds.size() + " ids at offset " + offset)
        } catch (Throwable ignored) {}
    }
}

/**
 * Glue between the IDE and the provider above. {@code extend(BASIC, ...)}
 * says: "for Groovy basic completion, also run my provider". Other
 * contributors still run — we only ADD items, never filter here.
 */
class ItemStackCompletionContributor extends CompletionContributor {
    ItemStackCompletionContributor() {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), new ItemStackCompletionProvider())
    }
}

// --- LivePlugin registration (unregistered automatically via pluginDisposable) ---
// These bottom statements are the script body: LivePlugin executes them once
// on load (on the EDT). They publish our contributor to the IDE's
// "com.intellij.completion.contributor" extension point for Groovy, ordered
// FIRST. Passing pluginDisposable (a LivePlugin-provided handle) ties the
// registration's lifetime to this script run, so reloading replaces instead
// of duplicating the contributor. The CompletionContributorEP constructor is
// marked @TestOnly upstream but is the established LivePlugin pattern.
try {
    ItemStackSupport.setIconDir(new File(pluginPath?.toString() ?: "", "icons").getAbsolutePath())
    ItemStackSupport.setConfigFile(new File(pluginPath?.toString() ?: "", "suggestions.json"))
} catch (Throwable ignored) {}
def itemStackDescriptor = new DefaultPluginDescriptor(PluginId.getId("LivePlugin"), ItemStackCompletionContributor.classLoader)
def itemStackPoint = ExtensionPointName.create("com.intellij.completion.contributor")
itemStackPoint.point.registerExtension(
    new CompletionContributorEP("Groovy", ItemStackCompletionContributor.name, itemStackDescriptor),
    LoadingOrder.FIRST, pluginDisposable)
