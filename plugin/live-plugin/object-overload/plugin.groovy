// depends-on-plugin com.intellij.java
// depends-on-plugin org.intellij.groovy
//
// Object-overload synthetics (LivePlugin script).
//
// WHAT IT DOES
//   Reads overloads.json (next to this file) and synthesizes additional
//   overloads for every method that takes a configured target type: the
//   matching parameter is rewritten to Object / Object[] / List<Object>
//   (per param shape), so string ids resolve at edit time, e.g.
//   Ingredient.of("minecraft:apple") resolves via a synthetic
//   of(Object...) alongside the real of(ItemStack...).
//
//   Rules, all recursive inside-out (List<Holder<ItemStack>> -> List<Object>):
//     targets   FQCNs rewritten wholesale (scalar/array/collection shapes).
//     wrappers  transparent FQCNs unwrapped via first type arg (Holder<X> -> X).
//               Unwrap failure (raw/unresolvable) leaves the param as-is.
//     typeRules per-generic rules: keep (Map/List: keep outer, rewrite listed
//               arg indices) or replace (HolderSet<X> -> List<rewritten X>,
//               gated by requireEligibleArg resolving to a target).
//   If nothing in a method's params rewrites, or the rewritten signature
//   collides with a real overload, no synthetic is made (real code wins).
//   Alias-aware: the sibling method-aliases/aliases.json is indexed too, so
//   rewritten signatures are emitted under alias names as well
//   (alias x rewrite cross product — neither provider sees the other's
//   synthetics, so it is computed from the shared file).
//   "enumsToObject":true rewrites any enum-typed param (PsiClass.isEnum,
//   no listing needed) plus type variables bounded by Enum/an enum, so
//   inherited generic methods like Enum.compareTo(E) get an Object sibling.
//
// HOW IT LOOKS UP (efficiency)
//   The JSON is parsed once per file modification into HashSets (targets,
//   wrappers) and a LinkedHashMap keyed by FQN (typeRules): every per-type
//   decision is O(1). Per method, a cheap string prefilter (param canonical
//   text containing a configured simple name) gates the expensive
//   resolve-based recursion, so the 99% of untouched methods cost ~nothing.
//   False prefilter positives only cost time; the resolve path decides.
//
// RUNNING IT
//   compile.bat checks it compiles; install.bat copies script + json to
//   LivePlugin; reload it from the LivePlugin tool window.

import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiCapturedWildcardType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiEllipsisType
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.PsiWildcardType
import com.intellij.psi.augment.PsiAugmentProvider
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightParameter

/**
 * One entry of the `typeRules` JSON array.
 */
class OverloadRule {
    String type
    String action = "keep" // "keep" | "replace"
    List<Integer> args = new ArrayList<Integer>()
    String replaceWith = null
    Integer requireEligibleArg = null
}

/**
 * Parsed overloads.json. All lookup structures are hash-based (see file doc).
 */
class ObjectOverloadConfig {
    Set<String> targets = new HashSet<String>()
    Set<String> wrappers = new HashSet<String>()
    Map<String, OverloadRule> rules = new LinkedHashMap<String, OverloadRule>()
    String scalar = "java.lang.Object"
    String array = "java.lang.Object[]"
    String collection = "java.util.List<java.lang.Object>"
    boolean enumsToObject = false
    Set<String> triggerNames = new HashSet<String>()

    boolean isEmpty() {
        try {
            return targets.isEmpty() && rules.isEmpty()
        } catch (Throwable ignored) {
            return true
        }
    }
}

/**
 * Per-rewrite workspace: config plus the PSI factory/scope the new types are
 * created in, plus lazily parsed replacement types with safe fallbacks.
 */
class OverloadCtx {
    final ObjectOverloadConfig cfg
    final PsiManager manager
    final PsiElementFactory factory
    final com.intellij.openapi.project.Project project
    final com.intellij.psi.search.GlobalSearchScope scope
    private PsiType scalarCached = null
    private boolean scalarDone = false
    private PsiType collectionCached = null
    private boolean collectionDone = false

    OverloadCtx(ObjectOverloadConfig cfg, PsiClass anchor) {
        this.cfg = cfg
        PsiManager m = null
        PsiElementFactory f = null
        com.intellij.openapi.project.Project p = null
        com.intellij.psi.search.GlobalSearchScope s = null
        try {
            m = anchor.getManager()
            p = anchor.getProject()
            f = JavaPsiFacade.getInstance(p).getElementFactory()
            s = anchor.getResolveScope()
        } catch (Throwable ignored) {}
        this.manager = m
        this.factory = f
        this.project = p
        this.scope = s
    }

    boolean usable() {
        return factory != null && scope != null && project != null
    }

    PsiClass findClass(String fqn) {
        if (fqn == null || project == null || scope == null) return null
        try {
            return JavaPsiFacade.getInstance(project).findClass(fqn, scope)
        } catch (Throwable ignored) {
            return null
        }
    }

    PsiType objectType() {
        try {
            PsiType t = factory.createTypeByFQClassName("java.lang.Object", scope)
            if (t != null) return t
        } catch (Throwable ignored) {}
        try {
            return PsiType.getJavaLangObject(manager, scope)
        } catch (Throwable ignored) {
            return null
        }
    }

    PsiType scalarType() {
        if (scalarDone) return scalarCached
        scalarDone = true
        try {
            scalarCached = ObjectOverloadSupport.parseTypeString(cfg.scalar, this)
        } catch (Throwable ignored) {
            scalarCached = null
        }
        if (scalarCached == null) {
            try {
                scalarCached = objectType()
            } catch (Throwable ignored) {
                scalarCached = null
            }
        }
        return scalarCached
    }

    PsiType collectionType() {
        if (collectionDone) return collectionCached
        collectionDone = true
        try {
            collectionCached = ObjectOverloadSupport.parseTypeString(cfg.collection, this)
        } catch (Throwable ignored) {
            collectionCached = null
        }
        if (collectionCached == null) {
            try {
                PsiClass list = findClass("java.util.List")
                PsiType obj = objectType()
                if (list != null && obj != null) collectionCached = factory.createType(list, obj)
            } catch (Throwable ignored) {
                collectionCached = null
            }
        }
        return collectionCached
    }
}

class ObjectOverloadSupport {
    static final Logger LOG = Logger.getInstance("ObjectOverload")
    static boolean DEBUG = false
    static final String SYNTHETIC_KIND = "ObjectOverloadRewritten"
    private static File configFile = null
    private static long configStamp = -1L
    private static ObjectOverloadConfig cached = new ObjectOverloadConfig()
    // Alias index: classFQN -> (aliasName -> realName), from the sibling
    // method-aliases/aliases.json. Neither provider sees the other's
    // synthetics, so the alias x rewrite cross product is computed from this
    // shared file instead of from augment output.
    private static File aliasFile = null
    private static long aliasStamp = -1L
    private static Map<String, Map<String, String>> aliasIndex = new LinkedHashMap<String, Map<String, String>>()

    static void debug(String message) {
        if (!DEBUG) return
        try {
            LOG.info("[object-overload] " + message)
        } catch (Throwable ignored) {}
    }

    static void setConfigFile(File f) {
        try {
            configFile = f
        } catch (Throwable ignored) {}
    }

    static String simpleName(String fqn) {
        if (fqn == null) return null
        try {
            int i = fqn.lastIndexOf('.')
            String s = i >= 0 ? fqn.substring(i + 1) : fqn
            int g = s.indexOf('<')
            if (g >= 0) s = s.substring(0, g)
            return s
        } catch (Throwable ignored) {
            return fqn
        }
    }

    /**
     * Load (or reload on modification) overloads.json. Any failure keeps
     * serving the last good config — resolution must never break.
     */
    static synchronized ObjectOverloadConfig load() {
        try {
            if (configFile == null || !configFile.isFile()) return cached
            long stamp = configFile.lastModified()
            if (stamp == configStamp) return cached
            Map parsed = null
            try {
                Object o = new ObjectMapper().readValue(configFile, Map.class)
                if (o instanceof Map) parsed = (Map) o
            } catch (Throwable t) {
                debug("config parse failed, keeping last good (" + t + ")")
                return cached
            }
            if (parsed == null) return cached
            ObjectOverloadConfig next = new ObjectOverloadConfig()
            try {
                Object t = parsed.get("targets")
                if (t instanceof Collection) {
                    for (def e : (Collection) t) {
                        if (e != null) next.targets.add(e.toString().trim())
                    }
                }
            } catch (Throwable ignored) {}
            try {
                Object w = parsed.get("wrappers")
                if (w instanceof Collection) {
                    for (def e : (Collection) w) {
                        if (e != null) next.wrappers.add(e.toString().trim())
                    }
                }
            } catch (Throwable ignored) {}
            try {
                Object rules = parsed.get("typeRules")
                if (rules instanceof Collection) {
                    for (def e : (Collection) rules) {
                        try {
                            if (!(e instanceof Map)) continue
                            Map m = (Map) e
                            Object tn = m.get("type")
                            if (tn == null) continue
                            OverloadRule r = new OverloadRule()
                            r.type = tn.toString().trim()
                            Object a = m.get("action")
                            if (a != null) r.action = a.toString().trim()
                            Object args = m.get("args")
                            if (args instanceof Collection) {
                                for (def ai : (Collection) args) {
                                    try {
                                        r.args.add(Integer.parseInt(ai.toString().trim()))
                                    } catch (Throwable ignored2) {}
                                }
                            }
                            Object rw = m.get("replaceWith")
                            if (rw != null) r.replaceWith = rw.toString().trim()
                            Object rea = m.get("requireEligibleArg")
                            if (rea != null) {
                                try {
                                    r.requireEligibleArg = Integer.parseInt(rea.toString().trim())
                                } catch (Throwable ignored2) {}
                            }
                            next.rules.put(r.type, r)
                        } catch (Throwable ignored2) {}
                    }
                }
            } catch (Throwable ignored) {}
            try {
                Object rw = parsed.get("rewrite")
                if (rw instanceof Map) {
                    Map m = (Map) rw
                    if (m.get("scalar") != null) next.scalar = m.get("scalar").toString().trim()
                    if (m.get("array") != null) next.array = m.get("array").toString().trim()
                    if (m.get("collection") != null) next.collection = m.get("collection").toString().trim()
                }
            } catch (Throwable ignored) {}
            try {
                Object eo = parsed.get("enumsToObject")
                if (eo instanceof Boolean) next.enumsToObject = ((Boolean) eo).booleanValue()
                else if (eo != null) next.enumsToObject = Boolean.parseBoolean(eo.toString().trim())
            } catch (Throwable ignored) {}
            for (String fqn : next.targets) {
                String s = simpleName(fqn)
                if (s != null) next.triggerNames.add(s)
            }
            for (String fqn : next.wrappers) {
                String s = simpleName(fqn)
                if (s != null) next.triggerNames.add(s)
            }
            for (Map.Entry<String, OverloadRule> e : next.rules.entrySet()) {
                try {
                    String s = simpleName(e.getKey())
                    if (s != null) next.triggerNames.add(s)
                    if (e.getValue() != null && e.getValue().replaceWith != null) {
                        String r2 = simpleName(e.getValue().replaceWith)
                        if (r2 != null) next.triggerNames.add(r2)
                    }
                } catch (Throwable ignored) {}
            }
            cached = next
            configStamp = stamp
            debug("config loaded targets=" + next.targets.size() + " wrappers=" + next.wrappers.size() + " rules=" + next.rules.size())
        } catch (Throwable ignored) {}
        return cached
    }

    static void setAliasFile(File f) {
        try {
            aliasFile = f
        } catch (Throwable ignored) {}
    }

    /**
     * Load (or reload on modification) the sibling aliases.json into
     * classFQN -> (alias -> real). Absent/broken file means no aliases —
     * never fail resolution over it.
     */
    static synchronized Map<String, Map<String, String>> aliasLoad() {
        try {
            if (aliasFile == null || !aliasFile.isFile()) return aliasIndex
            long stamp = aliasFile.lastModified()
            if (stamp == aliasStamp) return aliasIndex
            Map parsed = null
            try {
                Object o = new ObjectMapper().readValue(aliasFile, Map.class)
                if (o instanceof Map) parsed = (Map) o
            } catch (Throwable t) {
                debug("alias parse failed, keeping last good (" + t + ")")
                return aliasIndex
            }
            if (parsed == null) return aliasIndex
            Map<String, Map<String, String>> next = new LinkedHashMap<String, Map<String, String>>()
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
                            String cs = c.toString().trim()
                            String fs = f.toString().trim()
                            String ts = t.toString().trim()
                            if (cs.isEmpty() || fs.isEmpty() || ts.isEmpty()) continue
                            if (fs.equals(ts)) continue
                            Map<String, String> byAlias = next.get(cs)
                            if (byAlias == null) {
                                byAlias = new LinkedHashMap<String, String>()
                                next.put(cs, byAlias)
                            }
                            if (!byAlias.containsKey(ts)) byAlias.put(ts, fs)
                        } catch (Throwable ignored2) {}
                    }
                }
            } catch (Throwable ignored) {}
            aliasIndex = next
            aliasStamp = stamp
            debug("alias index loaded classes=" + next.size())
        } catch (Throwable ignored) {}
        return aliasIndex
    }

    static Map<String, String> aliasesFor(String classFqn) {
        try {
            Map<String, String> m = aliasLoad().get(classFqn)
            return m != null ? m : Collections.emptyMap()
        } catch (Throwable ignored) {
            return Collections.emptyMap()
        }
    }
    // Own declared methods without triggering augment recursion (getMethods()
    // includes augments). Same pattern as context-dropper.
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

    /**
     * Cheap string prefilter: any param canonical text containing a configured
     * simple name? Gates the resolve-based recursion below.
     */
    static boolean maybeRelevant(PsiMethod m, ObjectOverloadConfig cfg) {
        if (m == null || cfg == null) return false
        if (cfg.enumsToObject) {
            // Enum-ness needs resolve() (isEnum); no text shape identifies it,
            // so the flag widens the prefilter and the resolve path decides.
            try {
                def ps = m.getParameterList()?.getParameters()
                return ps != null && ps.length > 0
            } catch (Throwable ignored) {
                return false
            }
        }
        if (cfg.triggerNames.isEmpty()) return false
        try {
            def params = m.getParameterList()?.getParameters() ?: []
            for (def p : params) {
                String canon = null
                try {
                    PsiType pt = p.getType()
                    if (pt != null) canon = pt.getCanonicalText()
                } catch (Throwable ignored) {}
                if (canon == null || canon.isEmpty()) continue
                for (String n : cfg.triggerNames) {
                    try {
                        if (canon.contains(n)) return true
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}
        return false
    }

    static String eraseGenerics(String text) {
        if (text == null || text.isEmpty()) return text
        try {
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
            return text
        }
    }

    /**
     * Parse a config type string: `FQN`, `FQN[]`, `FQN<A>` or `FQN<A,B>`
     * (one generic level — enough for rewrite values). Null on any failure.
     */
    static PsiType parseTypeString(String s, OverloadCtx c) {
        if (s == null || c == null || c.factory == null || c.scope == null) return null
        try {
            String text = s.trim()
            if (text.isEmpty()) return null
            int dims = 0
            while (text.endsWith("[]")) {
                dims++
                text = text.substring(0, text.length() - 2).trim()
            }
            PsiType base = null
            int lt = text.indexOf('<')
            if (lt < 0) {
                PsiClass k = c.findClass(text)
                if (k == null) return null
                base = c.factory.createType(k)
            } else {
                String outer = text.substring(0, lt).trim()
                if (!text.endsWith(">")) return null
                String inner = text.substring(lt + 1, text.length() - 1).trim()
                List<String> parts = new ArrayList<String>()
                int depth = 0
                StringBuilder cur = new StringBuilder()
                for (int i = 0; i < inner.length(); i++) {
                    String ch = inner.substring(i, i + 1)
                    if (ch == "<") depth++
                    else if (ch == ">") depth = Math.max(0, depth - 1)
                    if (ch == "," && depth == 0) {
                        parts.add(cur.toString().trim())
                        cur = new StringBuilder()
                    } else {
                        cur.append(ch)
                    }
                }
                parts.add(cur.toString().trim())
                PsiClass outerCls = c.findClass(outer)
                if (outerCls == null) return null
                List<PsiType> argTypes = new ArrayList<PsiType>()
                for (String part : parts) {
                    if (part.contains("<")) return null
                    String p = part
                    int pd = 0
                    while (p.endsWith("[]")) {
                        pd++
                        p = p.substring(0, p.length() - 2).trim()
                    }
                    PsiClass pc = c.findClass(p)
                    if (pc == null) return null
                    PsiType pt = c.factory.createType(pc)
                    for (int i = 0; i < pd; i++) pt = pt.createArrayType()
                    argTypes.add(pt)
                }
                base = c.factory.createType(outerCls, argTypes.toArray(new PsiType[0]))
            }
            for (int i = 0; i < dims; i++) base = base.createArrayType()
            return base
        } catch (Throwable ignored) {
            return null
        }
    }

    static PsiType ellipsisOf(PsiType comp) {
        if (comp == null) return null
        try {
            return new PsiEllipsisType(comp)
        } catch (Throwable ignored) {}
        try {
            return comp.createArrayType()
        } catch (Throwable ignored) {
            return null
        }
    }

    /**
     * Unwrap wrappers/wildcards to the proper (first non-wrapper) class FQN,
     * or null when it cannot be determined. Used for eligibility checks.
     */
    static String unwrapProper(PsiType t, ObjectOverloadConfig cfg, Set<String> seen) {
        if (t == null || cfg == null) return null
        try {
            String key = t.getCanonicalText()
            if (key == null || !seen.add(key)) return null
        } catch (Throwable ignored) {
            return null
        }
        try {
            if (t instanceof PsiEllipsisType) {
                PsiType comp = null
                try {
                    comp = ((PsiEllipsisType) t).getComponentType()
                } catch (Throwable ignored) {}
                return unwrapProper(comp, cfg, seen)
            }
            if (t instanceof PsiArrayType) {
                PsiType deep = null
                try {
                    deep = ((PsiArrayType) t).getDeepComponentType()
                } catch (Throwable ignored) {}
                return unwrapProper(deep, cfg, seen)
            }
            if (t instanceof PsiWildcardType) {
                PsiType bound = null
                try {
                    bound = ((PsiWildcardType) t).getExtendsBound()
                } catch (Throwable ignored) {}
                if (bound == null) {
                    try {
                        bound = ((PsiWildcardType) t).getSuperBound()
                    } catch (Throwable ignored) {}
                }
                return unwrapProper(bound, cfg, seen)
            }
            if (t instanceof PsiCapturedWildcardType) {
                PsiType upper = null
                try {
                    upper = ((PsiCapturedWildcardType) t).getUpperBound()
                } catch (Throwable ignored) {}
                return unwrapProper(upper, cfg, seen)
            }
            if (t instanceof PsiClassType) {
                PsiClass cls = null
                try {
                    cls = ((PsiClassType) t).resolve()
                } catch (Throwable ignored) {}
                String fqn = null
                try {
                    if (cls != null) fqn = cls.getQualifiedName()
                } catch (Throwable ignored) {}
                if (fqn == null) {
                    try {
                        fqn = eraseGenerics(t.getCanonicalText())
                    } catch (Throwable ignored) {}
                }
                if (fqn == null) return null
                if (cfg.wrappers.contains(fqn)) {
                    PsiType[] params = []
                    try {
                        params = ((PsiClassType) t).getParameters() ?: []
                    } catch (Throwable ignored) {}
                    if (params.length == 0) return null
                    return unwrapProper(params[0], cfg, seen)
                }
                return fqn
            }
        } catch (Throwable ignored) {}
        return null
    }

    static PsiClass resolveToClass(PsiType t) {
        try {
            if (t instanceof PsiClassType) return ((PsiClassType) t).resolve()
        } catch (Throwable ignored) {}
        return null
    }

    static boolean isEnumClass(PsiClass cls) {
        if (cls == null) return false
        try {
            return cls.isEnum()
        } catch (Throwable ignored) {
            return false
        }
    }

    /**
     * Type variable bounded by java.lang.Enum or an enum class, e.g. the E in
     * Enum.compareTo(E). Lets the flag cover inherited generic methods like
     * EquipmentSlot.MAINHAND.compareTo("offhand").
     */
    static boolean isEnumBounded(PsiTypeParameter tp, OverloadCtx c) {
        if (tp == null || c == null) return false
        try {
            PsiClassType[] bounds = tp.getExtendsListTypes()
            if (bounds == null) return false
            for (PsiClassType b : bounds) {
                if (b == null) continue
                try {
                    String bf = unwrapProper(b, c.cfg, new HashSet<String>())
                    if (bf == null) continue
                    if ("java.lang.Enum".equals(bf)) return true
                    if (isEnumClass(c.findClass(bf))) return true
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return false
    }

    static boolean isEligible(PsiType t, OverloadCtx c) {
        if (t == null || c == null) return false
        try {
            String fqn = unwrapProper(t, c.cfg, new HashSet<String>())
            if (fqn != null && c.cfg.targets.contains(fqn)) return true
        } catch (Throwable ignored) {}
        try {
            if (c.cfg.enumsToObject && isEnumClass(resolveToClass(t))) return true
        } catch (Throwable ignored) {}
        return false
    }

    /**
     * Rewrite a type, or null when unchanged/unresolvable (caller keeps the
     * original — the "bail out, leave as wrapped type" rule).
     */
    static PsiType rewriteType(PsiType t, OverloadCtx c, Set<String> seen) {
        if (t == null || c == null) return null
        try {
            String key = t.getCanonicalText()
            if (key == null || !seen.add(key)) return null
        } catch (Throwable ignored) {
            return null
        }
        try {
            // NOTE: PsiEllipsisType extends PsiArrayType — check ellipsis first.
            if (t instanceof PsiEllipsisType) {
                PsiType comp = null
                try {
                    comp = ((PsiEllipsisType) t).getComponentType()
                } catch (Throwable ignored) {}
                PsiType nc = rewriteType(comp, c, seen)
                if (nc == null) return null
                return ellipsisOf(nc)
            }
            if (t instanceof PsiArrayType) {
                PsiType deep = null
                int dims = 0
                try {
                    deep = ((PsiArrayType) t).getDeepComponentType()
                } catch (Throwable ignored) {}
                try {
                    dims = ((PsiArrayType) t).getArrayDimensions()
                } catch (Throwable ignored) {}
                PsiType nd = rewriteType(deep, c, seen)
                if (nd == null) return null
                PsiType r = nd
                for (int i = 0; i < dims; i++) {
                    try {
                        r = r.createArrayType()
                    } catch (Throwable ignored) {
                        return null
                    }
                }
                return r
            }
            if (t instanceof PsiWildcardType) return null
            if (t instanceof PsiCapturedWildcardType) return null
            if (t instanceof PsiClassType) return rewriteClassType((PsiClassType) t, c, seen)
        } catch (Throwable ignored) {}
        return null
    }

    static PsiType rewriteClassType(PsiClassType t, OverloadCtx c, Set<String> seen) {
        PsiClass cls = null
        try {
            cls = t.resolve()
        } catch (Throwable ignored) {}
        String fqn = null
        try {
            if (cls != null) fqn = cls.getQualifiedName()
        } catch (Throwable ignored) {}
        if (fqn == null) {
            try {
                fqn = eraseGenerics(t.getCanonicalText())
            } catch (Throwable ignored) {}
        }
        if (fqn == null) return null
        PsiType[] params = []
        try {
            params = t.getParameters() ?: []
        } catch (Throwable ignored) {}
        // 1. Wrappers are transparent: Holder<X> rewrites as X rewrites.
        if (c.cfg.wrappers.contains(fqn)) {
            if (params.length == 0) return null
            return rewriteType(params[0], c, seen)
        }
        // 2a. Enum handling (flag): any enum class rewrites without listing it.
        if (c.cfg.enumsToObject) {
            try {
                if (cls != null && cls.isEnum()) return c.scalarType()
            } catch (Throwable ignored) {}
            try {
                if (cls instanceof PsiTypeParameter && isEnumBounded((PsiTypeParameter) cls, c)) {
                    return c.scalarType()
                }
            } catch (Throwable ignored) {}
        }
        // 2. Direct target hit: scalar replacement (array shapes are rebuilt
        //    by the caller, which yields the configured `array` type).
        if (c.cfg.targets.contains(fqn)) {
            try {
                return c.scalarType()
            } catch (Throwable ignored) {
                return null
            }
        }
        // 3. Per-generic rules (exact FQN match — add entries for subclasses
        //    explicitly; hierarchy walks are skipped on purpose, see file doc).
        OverloadRule r = null
        try {
            r = c.cfg.rules.get(fqn)
        } catch (Throwable ignored) {}
        if (r != null) return applyRule(t, cls, params, r, c, seen)
        // 4. Collection fallback: a Collection inheritor (no specific rule)
        //    whose element is eligible becomes the configured `collection`.
        //    Eligibility is required — Set<String> must stay untouched.
        try {
            if (cls != null && params.length > 0 && c.factory != null) {
                PsiClass coll = c.findClass("java.util.Collection")
                if (coll != null && cls.isInheritor(coll, true) && isEligible(params[0], c)) {
                    return c.collectionType()
                }
            }
        } catch (Throwable ignored) {}
        return null
    }

    static PsiType applyRule(PsiClassType t, PsiClass cls, PsiType[] params, OverloadRule r, OverloadCtx c, Set<String> seen) {
        if (r == null || c == null || c.factory == null) return null
        try {
            if ("replace".equals(r.action)) {
                if (r.replaceWith == null) return null
                PsiClass rep = c.findClass(r.replaceWith)
                if (rep == null) return null
                if (r.requireEligibleArg != null) {
                    int ei = r.requireEligibleArg.intValue()
                    if (ei < 0 || ei >= params.length) return null
                    if (!isEligible(params[ei], c)) return null
                }
                List<PsiType> out = new ArrayList<PsiType>()
                for (int idx : r.args) {
                    if (idx < 0 || idx >= params.length) continue
                    PsiType nw = null
                    try {
                        nw = rewriteType(params[idx], c, seen)
                    } catch (Throwable ignored) {}
                    out.add(nw != null ? nw : params[idx])
                }
                if (out.isEmpty()) return c.factory.createType(rep)
                return c.factory.createType(rep, out.toArray(new PsiType[0]))
            }
            // Default: keep the outer type, rewrite listed arg indices.
            if (cls == null || params.length == 0) return null
            PsiType[] next = new PsiType[params.length]
            for (int i = 0; i < params.length; i++) next[i] = params[i]
            boolean changed = false
            for (int idx : r.args) {
                if (idx < 0 || idx >= params.length) continue
                PsiType nw = null
                try {
                    nw = rewriteType(params[idx], c, seen)
                } catch (Throwable ignored) {}
                if (nw != null) {
                    next[idx] = nw
                    changed = true
                }
            }
            if (!changed) return null
            return c.factory.createType(cls, next)
        } catch (Throwable ignored) {
            return null
        }
    }

    static String erasedName(PsiType type) {
        try {
            String text = type.getCanonicalText()
            if (text == null || text.isEmpty()) return "java.lang.Object"
            return eraseGenerics(text)
        } catch (Throwable ignored) {
            return "java.lang.Object"
        }
    }

    static String keyFor(String name, boolean ctor, List<PsiType> types) {
        List<String> erased = new ArrayList<String>()
        try {
            for (PsiType pt : types) erased.add(erasedName(pt))
        } catch (Throwable ignored) {}
        String prefix = ctor ? "<ctor>" : name
        return prefix + "(" + String.join(",", erased) + ")"
    }

    static String ownKey(PsiMethod method) {
        def params = []
        try {
            params = method.getParameterList()?.getParameters() ?: []
        } catch (Throwable ignored) {}
        List<PsiType> types = new ArrayList<PsiType>()
        for (def p : params) {
            try {
                types.add(p.getType())
            } catch (Throwable ignored) {
                try {
                    types.add(null)
                } catch (Throwable ignored2) {}
            }
        }
        boolean ctor = false
        try {
            ctor = method.isConstructor()
        } catch (Throwable ignored) {}
        String name = ""
        try {
            name = method.getName()
        } catch (Throwable ignored) {}
        return keyFor(name, ctor, types)
    }

    static boolean hasCollision(PsiClass cls, PsiMethod original, String wanted) {
        if (cls == null || wanted == null) return false
        boolean wantCtor = false
        try {
            wantCtor = original.isConstructor()
        } catch (Throwable ignored) {}
        for (PsiMethod other : ownMethods(cls)) {
            if (other == null || other == original || isOurs(other)) continue
            try {
                if (other.isConstructor() != wantCtor) continue
                if (wanted.equals(ownKey(other))) return true
            } catch (Throwable ignored) {}
        }
        return false
    }

    static GrLightMethodBuilder buildRewritten(PsiMethod original, PsiClass containing, List<PsiType> newTypes, String nameOverride) {
        def mgr = containing.getManager()
        String synthName = nameOverride
        if (synthName == null) {
            try {
                synthName = original.getName()
            } catch (Throwable ignored) {}
        }
        GrLightMethodBuilder synth = new GrLightMethodBuilder(mgr, synthName)
        synth.setContainingClass(containing)
        boolean ctor = false
        try {
            ctor = original.isConstructor()
        } catch (Throwable ignored) {}
        synth.setConstructor(ctor)
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
            if (!ctor) {
                synth.setReturnType(original.getReturnType())
            } else {
                synth.setReturnType((PsiType) null)
            }
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
                        ptype = (newTypes != null && i < newTypes.size() && newTypes[i] != null)
                            ? newTypes[i] : p.getType()
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

class ObjectOverloadAugmentProvider extends PsiAugmentProvider implements DumbAware {
    @Override
    protected List getAugments(PsiElement element, Class type, String nameHint) {
        if (!(element instanceof PsiClass)) return Collections.emptyList()
        try {
            if (!type.isAssignableFrom(PsiMethod.class)) return Collections.emptyList()
        } catch (Throwable ignored) {
            return Collections.emptyList()
        }
        ObjectOverloadConfig cfg = ObjectOverloadSupport.load()
        if (cfg == null || cfg.isEmpty()) return Collections.emptyList()
        PsiClass cls = (PsiClass) element
        OverloadCtx c = new OverloadCtx(cfg, cls)
        if (!c.usable()) return Collections.emptyList()
        String classFqn = null
        try {
            classFqn = cls.getQualifiedName()
        } catch (Throwable ignored) {}
        Map<String, String> byAlias = ObjectOverloadSupport.aliasesFor(classFqn)
        List<PsiMethod> own
        try {
            own = ObjectOverloadSupport.ownMethods(cls)
        } catch (Throwable ignored) {
            return Collections.emptyList()
        }
        List result = new ArrayList()
        Set<String> made = new HashSet<String>()
        for (PsiMethod original : own) {
            if (original == null || ObjectOverloadSupport.isOurs(original)) continue
            try {
                String mname = original.getName()
                if (mname == null) continue
                // Output names: the real name plus any alias pointing at it.
                // The alias provider's synthetics are invisible here (own
                // methods only), so the cross product comes from this index.
                List<String> outNames = new ArrayList<String>()
                if (nameHint == null || nameHint.equals(mname)) outNames.add(mname)
                try {
                    for (Map.Entry<String, String> e : byAlias.entrySet()) {
                        try {
                            if (mname.equals(e.getValue())
                                && (nameHint == null || nameHint.equals(e.getKey()))
                                && !outNames.contains(e.getKey())) outNames.add(e.getKey())
                        } catch (Throwable ignored2) {}
                    }
                } catch (Throwable ignored2) {}
                if (outNames.isEmpty()) continue
                if (!ObjectOverloadSupport.maybeRelevant(original, cfg)) continue
                def origParams = original.getParameterList()?.getParameters() ?: []
                List<PsiType> newTypes = new ArrayList<PsiType>()
                boolean changed = false
                for (def p : origParams) {
                    PsiType orig = null
                    try {
                        orig = p.getType()
                    } catch (Throwable ignored2) {}
                    PsiType nw = null
                    try {
                        nw = ObjectOverloadSupport.rewriteType(orig, c, new HashSet<String>())
                    } catch (Throwable ignored2) {}
                    if (nw != null) {
                        changed = true
                        newTypes.add(nw)
                    } else {
                        newTypes.add(orig)
                    }
                }
                if (!changed) continue
                boolean ctor = false
                try {
                    ctor = original.isConstructor()
                } catch (Throwable ignored2) {}
                if (ctor) {
                    // Constructors carry the class name; aliases don't apply.
                    String key = ObjectOverloadSupport.keyFor(mname, true, newTypes)
                    if (!made.add(key)) continue
                    if (ObjectOverloadSupport.hasCollision(cls, original, key)) continue
                    result.add(ObjectOverloadSupport.buildRewritten(original, cls, newTypes, mname))
                    continue
                }
                for (String outName : outNames) {
                    try {
                        String key = ObjectOverloadSupport.keyFor(outName, false, newTypes)
                        if (!made.add(key)) continue
                        if (ObjectOverloadSupport.hasCollision(cls, original, key)) continue
                        result.add(ObjectOverloadSupport.buildRewritten(original, cls, newTypes, outName))
                    } catch (Throwable ignored2) {}
                }
            } catch (Throwable ignored) {}
        }
        return result
    }
}

// --- LivePlugin registration (unregistered automatically via pluginDisposable) ---
try {
    ObjectOverloadSupport.setConfigFile(new File(pluginPath?.toString() ?: "", "overloads.json"))
} catch (Throwable ignored) {}
try {
    ObjectOverloadSupport.setAliasFile(new File(pluginPath?.toString() ?: "", "../method-aliases/aliases.json").getCanonicalFile())
} catch (Throwable ignored) {}
def objectOverloadPoint = PsiAugmentProvider.EP_NAME.point
objectOverloadPoint.registerExtension(new ObjectOverloadAugmentProvider(), pluginDisposable)
