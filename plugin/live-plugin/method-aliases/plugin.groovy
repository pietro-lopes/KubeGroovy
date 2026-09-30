// depends-on-plugin com.intellij.java
// depends-on-plugin org.intellij.groovy
//
// Method-alias synthetics (LivePlugin script).
//
// SCOPE
//   Only .groovy use-sites under kubegroovy/{startup,client,server}_scripts/**.
//
// WHAT IT DOES
//   Reads the project's local/kubegroovy/live-plugin/method-aliases/aliases.json
//   and, for each entry, clones every
//   overload named `from` on the given class under the name `to` with an
//   identical signature. The aliases exist at runtime (bridge mapping); this
//   only teaches the editor so alias calls resolve, complete, and show in
//   signature help. Both names stay visible. If a real `to` with the same
//   erased signature exists, the real one wins and no synthetic is made.
//   Constructors are skipped.
//
//   object-overload reads the same project-local aliases.json so its
//   Object-rewritten signatures are emitted under alias names too — neither
//   provider can see the other's synthetics, so the cross product is computed
//   from the shared file, not from augment output.
//
// LOOKUP / COST
//   Entries are indexed once per file change into Map<classFQN, List<entry>>;
//   The contributor bails fast when the class has no entries or nameHint matches
//   nothing. Only own/declared methods are scanned (never getMethods()).
//
// RUNNING IT
//   compile.bat checks it compiles; install.bat copies the script to
//   LivePlugin; reload it from the LivePlugin tool window.

import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import com.intellij.psi.ResolveState
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.scope.ElementClassHint
import com.intellij.psi.scope.NameHint
import com.intellij.psi.scope.PsiScopeProcessor
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightParameter

class MethodAliasEntry {
    String type
    String from
    String to
}

class MethodAliasConfig {
    Map<String, List<MethodAliasEntry>> byClass = new LinkedHashMap<String, List<MethodAliasEntry>>()

    boolean isEmpty() {
        try {
            return byClass.isEmpty()
        } catch (Throwable ignored) {
            return true
        }
    }

    List<MethodAliasEntry> forClass(String fqn) {
        try {
            List<MethodAliasEntry> l = byClass.get(fqn)
            return l != null ? l : Collections.emptyList()
        } catch (Throwable ignored) {
            return Collections.emptyList()
        }
    }
}

class MethodAliasSupport {
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

    static final Logger LOG = Logger.getInstance("MethodAlias")
    static boolean DEBUG = false
    static final String SYNTHETIC_KIND = "MethodAlias"
    private static File configFile = null
    private static final Map<File, String> configStamps = new HashMap<>()
    private static final Map<File, MethodAliasConfig> configs = new HashMap<>()

    static void debug(String message) {
        if (!DEBUG) return
        try {
            LOG.info("[method-alias] " + message)
        } catch (Throwable ignored) {}
    }

    static void setConfigFile(File f) {
        try {
            configFile = f
        } catch (Throwable ignored) {}
    }

    static File configFileFor(PsiElement place) {
        if (configFile != null) return configFile
        String root = place?.getProject()?.getBasePath()
        return root == null || root.isBlank() ? null : new File(root, "local/kubegroovy/live-plugin/method-aliases/aliases.json")
    }

    static synchronized MethodAliasConfig load(PsiElement place = null) {
        File source = configFileFor(place)
        MethodAliasConfig cached = configs.get(source) ?: new MethodAliasConfig()
        try {
            if (source == null || !source.isFile()) return cached
            String stamp = source.lastModified() + ":" + source.length()
            if (stamp == configStamps.get(source)) return cached
            Map parsed = null
            try {
                Object o = new ObjectMapper().readValue(source, Map.class)
                if (o instanceof Map) parsed = (Map) o
            } catch (Throwable t) {
                debug("config parse failed, keeping last good (" + t + ")")
                return cached
            }
            if (parsed == null) return cached
            MethodAliasConfig next = new MethodAliasConfig()
            try {
                Object aliases = parsed.get("aliases")
                if (aliases instanceof Collection) {
                    for (def e : (Collection) aliases) {
                        try {
                            if (!(e instanceof Map)) continue
                            Map m = (Map) e
                            Object c = m.get("class")
                            Object f = m.get("from")
                            Object t = m.get("to")
                            if (c == null || f == null || t == null) continue
                            MethodAliasEntry entry = new MethodAliasEntry()
                            entry.type = c.toString().trim()
                            entry.from = f.toString().trim()
                            entry.to = t.toString().trim()
                            if (entry.type.isEmpty() || entry.from.isEmpty() || entry.to.isEmpty()) continue
                            if (entry.from.equals(entry.to)) continue
                            List<MethodAliasEntry> l = next.byClass.get(entry.type)
                            if (l == null) {
                                l = new ArrayList<MethodAliasEntry>()
                                next.byClass.put(entry.type, l)
                            }
                            l.add(entry)
                        } catch (Throwable ignored2) {}
                    }
                }
            } catch (Throwable ignored) {}
            cached = next
            configs.put(source, next)
            configStamps.put(source, stamp)
            debug("config loaded classes=" + next.byClass.size())
        } catch (Throwable ignored) {}
        return cached
    }

    static List<PsiMethod> ownMethods(PsiClass cls) {
        if (cls == null) return Collections.emptyList()
        try {
            def own = cls.getOwnMethods()
            if (own instanceof Collection) return new ArrayList<PsiMethod>((Collection<PsiMethod>) own)
        } catch (Throwable ignored) {}
        List<PsiMethod> out = new ArrayList<PsiMethod>()
        try {
            for (PsiElement child : cls.getChildren()) {
                if (child instanceof PsiMethod) out.add((PsiMethod) child)
            }
        } catch (Throwable ignored) {}
        return out
    }

    static boolean isOurs(PsiMethod m) {
        if (!(m instanceof GrLightMethodBuilder)) return false
        try {
            return SYNTHETIC_KIND.equals(((GrLightMethodBuilder) m).getMethodKind())
        } catch (Throwable ignored) {
            return false
        }
    }

    static String erasedName(PsiType type) {
        try {
            String text = type.getCanonicalText()
            if (text == null || text.isEmpty()) return "java.lang.Object"
            StringBuilder out = new StringBuilder(text.length())
            int depth = 0
            for (int i = 0; i < text.length(); i++) {
                String ch = text.substring(i, i + 1)
                if (ch == "<") depth++
                else if (ch == ">") depth = Math.max(0, depth - 1)
                else if (depth == 0) out.append(ch)
            }
            return out.toString().replace('$', '.').trim()
        } catch (Throwable ignored) {
            return "java.lang.Object"
        }
    }

    static String erasedKey(PsiMethod method, String nameOverride) {
        def params = []
        try {
            params = method.getParameterList()?.getParameters() ?: []
        } catch (Throwable ignored) {}
        List<String> erased = new ArrayList<String>()
        for (def p : params) {
            try {
                erased.add(erasedName(p.getType()))
            } catch (Throwable ignored) {
                erased.add("java.lang.Object")
            }
        }
        String name = nameOverride
        if (name == null) {
            try {
                name = method.getName()
            } catch (Throwable ignored) {}
        }
        return (name != null ? name : "") + "(" + String.join(",", erased) + ")"
    }

    static GrLightMethodBuilder buildAlias(PsiMethod original, PsiClass containing, String alias) {
        def mgr = containing.getManager()
        GrLightMethodBuilder synth = new GrLightMethodBuilder(mgr, alias)
        synth.setContainingClass(containing)
        try {
            synth.getModifierList().copyModifiers(original)
        } catch (Throwable ignored) {
            try {
                List<String> mods = new ArrayList<String>()
                mods.add("public")
                try {
                    if (original.hasModifierProperty("static")) mods.add("static")
                } catch (Throwable ignored2) {}
                synth.setModifiers(mods.toArray(new String[0]))
            } catch (Throwable ignored2) {}
        }
        try {
            for (def tp : original.getTypeParameters()) {
                try {
                    synth.addTypeParameter(tp)
                } catch (Throwable ignored2) {
                    try {
                        synth.addTypeParameter(tp.getName())
                    } catch (Throwable ignored3) {}
                }
            }
        } catch (Throwable ignored) {}
        try {
            synth.setReturnType(original.getReturnType())
        } catch (Throwable ignored) {}
        try {
            def throwns = original.getThrowsList()?.getReferencedTypes()
            if (throwns != null) {
                for (def ex : throwns) {
                    try {
                        if (ex instanceof PsiClassType) synth.addException((PsiClassType) ex)
                    } catch (Throwable ignored2) {}
                }
            }
        } catch (Throwable ignored) {}
        try {
            def origParams = original.getParameterList()?.getParameters() ?: []
            for (int i = 0; i < origParams.length; i++) {
                def p = origParams[i]
                try {
                    String pname = null
                    try {
                        pname = p.getName()
                    } catch (Throwable ignored2) {}
                    if (pname == null || pname.isEmpty()) pname = "arg" + i
                    PsiType ptype = null
                    try {
                        ptype = p.getType()
                    } catch (Throwable ignored2) {}
                    GrLightParameter np = new GrLightParameter(pname, ptype, synth)
                    try {
                        if (p instanceof GrParameter && ((GrParameter) p).isOptional()) np.setOptional(true)
                    } catch (Throwable ignored2) {}
                    synth.addParameter(np)
                } catch (Throwable ignored2) {}
            }
        } catch (Throwable ignored) {}
        try {
            PsiElement nav = null
            try {
                nav = original.getNavigationElement()
            } catch (Throwable ignored2) {}
            synth.setNavigationElement(nav != null ? nav : original)
        } catch (Throwable ignored) {}
        try {
            synth.setMethodKind(SYNTHETIC_KIND)
        } catch (Throwable ignored) {}
        try {
            synth.setOriginInfo(SYNTHETIC_KIND)
        } catch (Throwable ignored) {}
        return synth
    }
}

class MethodAliasContributor extends NonCodeMembersContributor implements DumbAware {
    @Override
    void processDynamicElements(PsiType qualifierType, PsiScopeProcessor processor, PsiElement place, ResolveState state) {
        if (MethodAliasSupport.sideForFile(place?.getContainingFile()) == null) return
        def kind = processor.getHint(ElementClassHint.KEY)
        if (kind != null && !kind.shouldProcess(ElementClassHint.DeclarationKind.METHOD)) return
        String nameHint = processor.getHint(NameHint.KEY)?.getName(state)
        // Walk substituted supertypes so inherited and generic methods keep working.
        List<PsiType> pending = [qualifierType]
        Set<PsiClass> seen = new HashSet<PsiClass>()
        while (!pending.isEmpty()) {
            PsiType current = pending.remove(0)
            if (!(current instanceof PsiClassType)) continue
            def resolved = ((PsiClassType) current).resolveGenerics()
            PsiClass cls = resolved.getElement()
            if (cls == null || !seen.add(cls)) continue
            ResolveState memberState = state.put(PsiSubstitutor.KEY, resolved.getSubstitutor())
            for (PsiMethod method : methodsFor(cls, nameHint, place)) {
                if (!processor.execute(method, memberState)) return
            }
            pending.addAll(Arrays.asList(current.getSuperTypes()))
        }
    }

    private static List<PsiMethod> methodsFor(PsiClass cls, String nameHint, PsiElement place) {
        MethodAliasConfig cfg = MethodAliasSupport.load(place)
        if (cfg == null || cfg.isEmpty()) return Collections.emptyList()
        String fqn = null
        try {
            fqn = cls.getQualifiedName()
        } catch (Throwable ignored) {}
        if (fqn == null) return Collections.emptyList()
        List<MethodAliasEntry> entries = cfg.forClass(fqn)
        if (entries.isEmpty()) return Collections.emptyList()
        List<PsiMethod> own
        try {
            own = MethodAliasSupport.ownMethods(cls)
        } catch (Throwable ignored) {
            return Collections.emptyList()
        }
        List result = new ArrayList()
        Set<String> made = new HashSet<String>()
        for (MethodAliasEntry entry : entries) {
            if (entry == null) continue
            try {
                if (nameHint != null && !nameHint.equals(entry.to)) continue
                for (PsiMethod original : own) {
                    if (original == null || MethodAliasSupport.isOurs(original)) continue
                    try {
                        try {
                            if (original.isConstructor()) continue
                        } catch (Throwable ignored2) {}
                        String mname = original.getName()
                        if (!entry.from.equals(mname)) continue
                        String key = MethodAliasSupport.erasedKey(original, entry.to)
                        if (!made.add(key)) continue
                        // Real `to` with the same signature wins.
                        boolean collision = false
                        for (PsiMethod other : own) {
                            if (other == null || other == original || MethodAliasSupport.isOurs(other)) continue
                            try {
                                try {
                                    if (other.isConstructor()) continue
                                } catch (Throwable ignored2) {}
                                if (!entry.to.equals(other.getName())) continue
                                if (key.equals(MethodAliasSupport.erasedKey(other, null))) {
                                    collision = true
                                    break
                                }
                            } catch (Throwable ignored2) {}
                        }
                        if (collision) continue
                        result.add(MethodAliasSupport.buildAlias(original, cls, entry.to))
                    } catch (Throwable ignored2) {}
                }
            } catch (Throwable ignored2) {}
        }
        return result
    }
}

// --- LivePlugin registration (unregistered automatically via pluginDisposable) ---
def methodAliasPoint = NonCodeMembersContributor.EP_NAME.point
methodAliasPoint.registerExtension(new MethodAliasContributor(), pluginDisposable)
