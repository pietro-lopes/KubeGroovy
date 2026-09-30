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
//   Suggestion types, values, and wrapper rules come from suggestions.json.
//   Project-exported item, fluid, and tag icons are loaded from the current
//   project's local/kubegroovy/export/icons-32 directory.
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
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
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
    /** Temporary, low-volume timing diagnostics for icon startup/completion. */
    static boolean DEBUG_TIMINGS = true

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

    static void debugTiming(String message) {
        if (!DEBUG_TIMINGS) return
        try {
            LOG.info("[itemstack-suggest][timing] " + message)
        } catch (Throwable ignored) {}
    }

    static String elapsed(long startedNanos) {
        return elapsedNanos(System.nanoTime() - startedNanos)
    }

    static String elapsedNanos(long nanos) {
        return String.format(java.util.Locale.ROOT, "%.2fms", nanos / 1_000_000.0d)
    }

    static String iconCacheSizes() {
        return "cachedSuggestions=" + ICONS.size() + " cachedFiles=" + FILE_ICONS.size()
            + " indexedExports=" + EXPORTED_ICON_FILES.size()
    }

    static String projectIconPreloadStatus(File projectRoot) {
        try {
            String root = projectRoot?.getCanonicalPath()
            java.util.concurrent.CompletableFuture<Void> future = root == null ? null : PROJECT_ICON_PRELOADS.get(root)
            if (future == null) return "not-scheduled"
            if (!future.isDone()) return "running"
            return future.isCompletedExceptionally() ? "failed" : "complete"
        } catch (Throwable ignored) {
            return "unknown"
        }
    }
    private static File configFile = null
    private static long configStamp = -1L
    private static Map<String, List<String>> values = [:]
    private static List<Map> delegates = []
    private static Map<String, Integer> genericDelegates = [:]
    private static final Map<String, String> ICON_DIRECTORY_CACHE = new java.util.concurrent.ConcurrentHashMap<String, String>()
    private static final Map<String, String> TAG_DIRECTORY_CACHE = new java.util.concurrent.ConcurrentHashMap<String, String>()
    private static final Pattern AS_AFTER = Pattern.compile("as\\s+([\\w.\$]+(?:\\s*<[^>]+>)?(?:\\[\\])?)")
    private static final Pattern AS_CAST_TEXT = Pattern.compile("\"[^\"]*\"\\s+as\\s+([\\w.\$]+(?:\\s*<[^>]+>)?(?:\\[\\])?)")
    private static final Pattern AS_AFTER_ANY = Pattern.compile("as\\s+([\\w.\$]+(?:\\s*<[^>]+>)?(?:\\[\\])?)")
    // Suggestion ids resolve to icons once per project/type. The watched file
    // stamps let newly-exported or updated icons replace a cached fallback.
    private static final Map<String, Icon> ICONS = new java.util.concurrent.ConcurrentHashMap<String, Icon>()
    private static final Map<String, List<File>> ICON_CANDIDATES = new java.util.concurrent.ConcurrentHashMap<String, List<File>>()
    private static final Map<String, String> ICON_CANDIDATE_STAMPS = new java.util.concurrent.ConcurrentHashMap<String, String>()
    // File-level cache also serves the shared bindings logo and notices file updates.
    private static final Map<String, Icon> FILE_ICONS = new java.util.concurrent.ConcurrentHashMap<String, Icon>()
    private static final Map<String, Object> FILE_ICON_LOCKS = new java.util.concurrent.ConcurrentHashMap<String, Object>()
    private static final Map<String, java.util.concurrent.CompletableFuture<Void>> PROJECT_ICON_PRELOADS =
        new java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.CompletableFuture<Void>>()
    // Preload indexes export-relative paths so completion avoids resolving
    // several canonical candidate paths for every displayed suggestion.
    private static final Map<String, File> EXPORTED_ICON_FILES = new java.util.concurrent.ConcurrentHashMap<String, File>()
    private static String iconDir = null
    private static File bindingsLogoFile = null
    private static File bindingsLogoSvgFile = null
    private static File bindingsLogoDarkSvgFile = null

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
            clearSuggestionIconCache()
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

    /** Configure the legacy icon directory and the sibling bindings-plugin logo fallback. */
    static void setIconDir(String dir) {
        try {
            File directory = new File(dir).getAbsoluteFile()
            iconDir = directory.getAbsolutePath()
            File scriptDirectory = directory.getParentFile()
            File bindingsDirectory = scriptDirectory == null ? null
                : new File(scriptDirectory, "../bindings").getCanonicalFile()
            bindingsLogoFile = bindingsDirectory == null ? null : new File(bindingsDirectory, "logo.png")
            bindingsLogoSvgFile = bindingsDirectory == null ? null : new File(bindingsDirectory, "logo.svg")
            bindingsLogoDarkSvgFile = bindingsDirectory == null ? null : new File(bindingsDirectory, "logo-dark.svg")
            clearSuggestionIconCache()
            preloadBindingsLogos()
        } catch (Throwable ignored) {}
    }

    private static void clearSuggestionIconCache() {
        ICONS.clear()
        ICON_CANDIDATES.clear()
        ICON_CANDIDATE_STAMPS.clear()
        ICON_DIRECTORY_CACHE.clear()
        TAG_DIRECTORY_CACHE.clear()
    }

    static File projectRoot(PsiFile file) {
        try {
            File root = projectRootForProject(file?.getProject())
            if (root != null) preloadProjectIcons(root)
            return root
        } catch (Throwable ignored) {}
        return null
    }

    static File projectRootForProject(Project project) {
        try {
            String basePath = project?.getBasePath()
            if (basePath != null && !basePath.isBlank()) return new File(basePath).getCanonicalFile()
        } catch (Throwable ignored) {}
        return null
    }

    /** Start an asynchronous preload for every open project's exported icon tree. */
    static void preloadOpenProjects() {
        long started = System.nanoTime()
        try {
            Project[] projects = ProjectManager.getInstance().getOpenProjects()
            int scheduled = 0
            for (Project project : projects) {
                File root = projectRootForProject(project)
                if (root != null) {
                    preloadProjectIcons(root)
                    scheduled++
                }
            }
            debugTiming("open-project preload scheduling projects=" + projects.length + " scheduled=" + scheduled + " elapsed=" + elapsed(started))
        } catch (Throwable error) {
            debugTiming("open-project preload scheduling failed elapsed=" + elapsed(started) + " error=" + error)
        }
    }

    /**
     * Pre-decode exported item, fluid, block, and tag icons off the UI thread.
     * The same file cache is used by normal completion, so requests made while
     * preloading is in progress wait only for their own icon, not the full scan.
     */
    static java.util.concurrent.CompletableFuture<Void> preloadProjectIcons(File projectRoot) {
        if (projectRoot == null) return java.util.concurrent.CompletableFuture.completedFuture(null)
        String rootPath
        try { rootPath = projectRoot.getCanonicalPath() } catch (Throwable ignored) { return java.util.concurrent.CompletableFuture.completedFuture(null) }
        java.util.concurrent.CompletableFuture<Void> created = new java.util.concurrent.CompletableFuture<Void>()
        java.util.concurrent.CompletableFuture<Void> existing = PROJECT_ICON_PRELOADS.putIfAbsent(rootPath, created)
        if (existing != null) return existing

        debugTiming("project icon preload started root=" + rootPath)
        try {
            java.util.concurrent.ForkJoinPool.commonPool().execute({
                java.util.concurrent.atomic.AtomicInteger warmed = new java.util.concurrent.atomic.AtomicInteger()
                long preloadStarted = System.nanoTime()
                try {
                    File exportRoot = new File(rootPath, "local/kubegroovy/export")
                    for (String scaleDirectory : ["icons-32", "icon-32"]) {
                        File scaleRoot = new File(exportRoot, scaleDirectory).getCanonicalFile()
                        List<String> categories = ["item", "fluid", "block", "tags/item", "tags/block", "tags/fluid"]
                        for (String category : categories) {
                            long categoryStarted = System.nanoTime()
                            int before = warmed.get()
                            File categoryRoot = new File(scaleRoot, category).getCanonicalFile()
                            if (!categoryRoot.toPath().startsWith(scaleRoot.toPath()) || !categoryRoot.isDirectory()) {
                                debugTiming("preload scan root=" + scaleDirectory + "/" + category + " files=0 cached=0 elapsed=" + elapsed(categoryStarted) + " (missing)")
                                continue
                            }
                            java.util.concurrent.atomic.AtomicInteger files = new java.util.concurrent.atomic.AtomicInteger()
                            categoryRoot.eachFileRecurse(groovy.io.FileType.FILES) { File file ->
                                try {
                                    if (!file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".png")) return
                                    files.incrementAndGet()
                                    File canonical = file.getCanonicalFile()
                                    if (canonical.toPath().startsWith(categoryRoot.toPath()) && loadIcon(canonical) != null) {
                                        warmed.incrementAndGet()
                                        String relative = scaleRoot.toPath().relativize(canonical.toPath()).toString()
                                            .replace('\\', '/')
                                        EXPORTED_ICON_FILES.put(exportedIconIndexKey(rootPath, scaleDirectory, relative), canonical)
                                    }
                                } catch (Throwable ignored) {}
                            }
                            debugTiming("preload scan root=" + scaleDirectory + "/" + category + " files=" + files.get()
                                + " cached=" + (warmed.get() - before) + " elapsed=" + elapsed(categoryStarted))
                        }
                    }
                    debug("preloaded " + warmed.get() + " exported icons for " + rootPath)
                    debugTiming("project icon preload finished root=" + rootPath + " cached=" + warmed.get() + " elapsed=" + elapsed(preloadStarted))
                    if (warmed.get() == 0) PROJECT_ICON_PRELOADS.remove(rootPath, created)
                    created.complete(null)
                } catch (Throwable error) {
                    PROJECT_ICON_PRELOADS.remove(rootPath, created)
                    created.completeExceptionally(error)
                    debugTiming("project icon preload failed root=" + rootPath + " cached=" + warmed.get()
                        + " elapsed=" + elapsed(preloadStarted) + " error=" + error)
                    debug("icon preload failed for " + rootPath + " (" + error + ")")
                }
            } as Runnable)
        } catch (Throwable error) {
            PROJECT_ICON_PRELOADS.remove(rootPath, created)
            created.completeExceptionally(error)
            debugTiming("project icon preload dispatch failed root=" + rootPath + " error=" + error)
        }
        return created
    }

    /** Warm the shared SVG fallback too, without delaying LivePlugin startup. */
    static void preloadBindingsLogos() {
        try {
            java.util.concurrent.ForkJoinPool.commonPool().execute({
                long started = System.nanoTime()
                int cached = 0
                for (File logo : [bindingsLogoSvgFile, bindingsLogoDarkSvgFile, bindingsLogoFile,
                                  bindingsLogoFile == null ? null : new File(bindingsLogoFile.getParentFile(), "logo-dark.png")]) {
                    if (logo != null && loadIcon(logo) != null) cached++
                }
                debugTiming("bindings logo preload cached=" + cached + " elapsed=" + elapsed(started))
            } as Runnable)
        } catch (Throwable error) {
            debugTiming("bindings logo preload dispatch failed error=" + error)
        }
    }

    /** Returns the exporter-relative path for an item, fluid, or TagKey suggestion. */
    static String exportedIconRelativePath(String id, String requestedType) {
        if (id == null || requestedType == null) return null
        boolean prefixedTag = id.startsWith("#")
        String identifier = prefixedTag ? id.substring(1) : id
        int separator = identifier.indexOf(':')
        if (separator <= 0 || separator != identifier.lastIndexOf(':')) return null
        String namespace = identifier.substring(0, separator)
        String path = identifier.substring(separator + 1)
        if (!(namespace ==~ /[a-z0-9_.-]+/) || !(path ==~ /[a-z0-9\/._-]+/)) return null

        String tagDirectory = tagDirectoryForSpec(requestedType, prefixedTag)
        if (prefixedTag || tagDirectory != null) {
            if (tagDirectory == null) return null
            return "tags/${tagDirectory}/${namespace}/${path}.png"
        }
        String iconDirectory = iconDirectoryForSpec(requestedType)
        return "${iconDirectory}/${namespace}/${path}.png"
    }

    private static String tagDirectoryForSpec(String requestedType, boolean followFixedDelegates) {
        String type = normalizeType(requestedType)
        String cacheKey = type + "|" + followFixedDelegates
        String cached = TAG_DIRECTORY_CACHE.get(cacheKey)
        if (cached != null) return cached.isEmpty() ? null : cached
        String directory = tagDirectoryForSpec(type, followFixedDelegates, new LinkedHashSet<String>())
        TAG_DIRECTORY_CACHE.put(cacheKey, directory ?: "")
        return directory
    }

    private static String tagDirectoryForSpec(String requestedType, boolean followFixedDelegates, Set<String> path) {
        String type = normalizeType(requestedType)
        if (type.isEmpty() || !path.add(type)) return null
        try {
            String raw = rawTypeName(type)
            if (raw == "net.minecraft.tags.TagKey") {
                List<String> args = typeArguments(type)
                if (args.isEmpty()) return null
                String element = rawTypeName(args[0])
                if (element == "net.minecraft.world.item.Item") return "item"
                if (element == "net.minecraft.world.level.block.Block") return "block"
                if (element == "net.minecraft.world.level.material.Fluid") return "fluid"
                return null
            }
            Map config = loadConfig()
            Integer argumentIndex = ((Map<String, Integer>) config.genericDelegates).get(raw)
            if (argumentIndex != null) {
                List<String> args = typeArguments(type)
                if (argumentIndex >= 0 && argumentIndex < args.size()) {
                    String directory = tagDirectoryForSpec(args[argumentIndex], followFixedDelegates, path)
                    if (directory != null) return directory
                }
            }
            if (followFixedDelegates) {
                for (Map rule : (List<Map>) config.delegates) {
                    String source = normalizeType(rule.source?.toString())
                    if (type == source || raw == source) {
                        String directory = tagDirectoryForSpec(rule.target?.toString(), true, path)
                        if (directory != null) return directory
                    }
                }
            }
            return null
        } catch (Throwable ignored) {
            return null
        } finally {
            path.remove(type)
        }
    }

    private static String iconDirectoryForSpec(String requestedType) {
        String type = normalizeType(requestedType)
        String cached = ICON_DIRECTORY_CACHE.get(type)
        if (cached != null) return cached
        String directory = iconDirectoryForSpec(type, new LinkedHashSet<String>()) ?: "item"
        ICON_DIRECTORY_CACHE.put(type, directory)
        return directory
    }

    private static String iconDirectoryForSpec(String requestedType, Set<String> path) {
        String type = normalizeType(requestedType)
        if (type.isEmpty() || !path.add(type)) return null
        try {
            String raw = rawTypeName(type)
            if (raw == "net.minecraft.world.level.material.Fluid") return "fluid"
            if (raw == "net.minecraft.tags.TagKey") return null
            Map config = loadConfig()
            Integer argumentIndex = ((Map<String, Integer>) config.genericDelegates).get(raw)
            if (argumentIndex != null) {
                List<String> args = typeArguments(type)
                if (argumentIndex >= 0 && argumentIndex < args.size()) {
                    String directory = iconDirectoryForSpec(args[argumentIndex], path)
                    if (directory != null) return directory
                }
            }
            for (Map rule : (List<Map>) config.delegates) {
                String source = normalizeType(rule.source?.toString())
                if (type == source || raw == source) {
                    String directory = iconDirectoryForSpec(rule.target?.toString(), path)
                    if (directory != null) return directory
                }
            }
            return "item"
        } catch (Throwable ignored) {
            return "item"
        } finally {
            path.remove(type)
        }
    }

    static List<File> exportedIconFiles(File projectRoot, String id, Collection<String> requestedTypes) {
        if (projectRoot == null || requestedTypes == null) return []
        LinkedHashSet<String> relativePaths = new LinkedHashSet<String>()
        for (String type : requestedTypes) {
            String relative = exportedIconRelativePath(id, type)
            if (relative != null) relativePaths.add(relative)
        }
        List<File> files = []
        try {
            String projectRootPath = projectRoot.getCanonicalPath()
            File base = new File(projectRootPath, "local/kubegroovy/export").getCanonicalFile()
            for (String folder : ["icons-32", "icon-32"]) {
                File root = new File(base, folder).getCanonicalFile()
                for (String relative : relativePaths) {
                    File file = new File(root, relative).getCanonicalFile()
                    if (file.toPath().startsWith(root.toPath())) {
                        files.add(file)
                        EXPORTED_ICON_FILES.put(exportedIconIndexKey(projectRootPath, folder, relative), file)
                    }
                }
            }
        } catch (Throwable ignored) {}
        return files
    }

    static List<File> iconFilesForSuggestion(String id, Collection<String> requestedTypes, File projectRoot) {
        return iconCandidateFilesForSuggestion(id, requestedTypes, projectRoot).findAll { it.isFile() }.toList()
    }

    private static List<File> iconCandidateFilesForSuggestion(String id, Collection<String> requestedTypes, File projectRoot) {
        LinkedHashMap<String, File> files = new LinkedHashMap<String, File>()
        for (File file : exportedIconFiles(projectRoot, id, requestedTypes)) files.put(file.getAbsolutePath(), file)
        if (iconDir != null && id != null) {
            String unprefixed = id.startsWith("#") ? id.substring(1) : id
            List<String> names = [iconFileName(id), unprefixed + ".png"]
            if (unprefixed.startsWith("minecraft:")) names.add(unprefixed.substring("minecraft:".length()) + ".png")
            for (String name : names) {
                File file = safeIconFile(new File(iconDir), name)
                if (file != null) files.put(file.getAbsolutePath(), file)
            }
        }
        for (File logo : [bindingsLogoSvgFile, bindingsLogoDarkSvgFile, bindingsLogoFile,
                          bindingsLogoFile == null ? null : new File(bindingsLogoFile.getParentFile(), "logo-dark.png")]) {
            if (logo != null) files.put(logo.getAbsolutePath(), logo)
        }
        return files.values().toList()
    }

    private static String exportedIconIndexKey(String projectRootPath, String scaleDirectory, String relativePath) {
        return projectRootPath + "|" + scaleDirectory + "/" + relativePath.replace('\\', '/')
    }

    private static List<File> indexedExportedIconFiles(String projectRootPath, Collection<String> relativePaths) {
        if (projectRootPath == null || projectRootPath.isEmpty() || relativePaths == null) return []
        LinkedHashMap<String, File> files = new LinkedHashMap<String, File>()
        for (String relative : relativePaths) {
            for (String folder : ["icons-32", "icon-32"]) {
                File file = EXPORTED_ICON_FILES.get(exportedIconIndexKey(projectRootPath, folder, relative))
                if (file != null) files.put(file.getAbsolutePath(), file)
            }
        }
        return files.values().toList()
    }

    private static List<String> exportedIconPathsForSuggestion(String id, Collection<String> requestedTypes) {
        LinkedHashSet<String> paths = new LinkedHashSet<String>()
        for (String type : requestedTypes ?: []) {
            String relative = exportedIconRelativePath(id, normalizeType(type))
            if (relative != null) paths.add(relative)
        }
        return new ArrayList<String>(paths)
    }

    private static File safeIconFile(File root, String relative) {
        try {
            File canonicalRoot = root.getCanonicalFile()
            File canonicalFile = new File(canonicalRoot, relative).getCanonicalFile()
            return canonicalFile.toPath().startsWith(canonicalRoot.toPath()) ? canonicalFile : null
        } catch (Throwable ignored) {
            return null
        }
    }

    /**
     * Loads project-exported icons first, then legacy LivePlugin icons, and
     * finally the vector logo shipped in the sibling bindings plugin folder.
     */
    static Icon iconFor(String id, Collection<String> requestedTypes, File projectRoot) {
        long totalStarted = System.nanoTime()
        long keyStarted = totalStarted
        long keyNanos = 0L
        long stampNanos = 0L
        long candidatesNanos = 0L
        long loadingNanos = 0L
        String outcome = "missing"
        try {
            if (id == null) return null
            String projectRootPath = ""
            // projectRootForProject already canonicalizes roots; absolute-path
            // lookup avoids resolving the same project directory for every id.
            try { if (projectRoot != null) projectRootPath = projectRoot.getAbsolutePath() } catch (Throwable ignored) {}
            List<String> relativePaths = exportedIconPathsForSuggestion(id, requestedTypes)
            String cacheKey = suggestionIconCacheKey(id, relativePaths, projectRootPath)
            keyNanos = System.nanoTime() - keyStarted
            Icon cached = ICONS.get(cacheKey)
            if (cached != null) {
                List<File> watched = ICON_CANDIDATES.get(cacheKey)
                String stamp = ICON_CANDIDATE_STAMPS.get(cacheKey)
                long stampStarted = System.nanoTime()
                String currentStamp = watched == null ? null : iconCandidateStamp(watched)
                stampNanos = System.nanoTime() - stampStarted
                if (watched != null && stamp != null && stamp == currentStamp) {
                    outcome = "suggestion-cache-hit"
                    return cached
                }
                ICONS.remove(cacheKey, cached)
                ICON_CANDIDATES.remove(cacheKey)
                ICON_CANDIDATE_STAMPS.remove(cacheKey)
            }

            long candidatesStarted = System.nanoTime()
            List<File> indexedCandidates = indexedExportedIconFiles(projectRootPath, relativePaths)
            candidatesNanos += System.nanoTime() - candidatesStarted
            for (int i = 0; i < indexedCandidates.size(); i++) {
                File file = indexedCandidates.get(i)
                long loadStarted = System.nanoTime()
                Icon icon = loadIcon(file)
                loadingNanos += System.nanoTime() - loadStarted
                if (icon != null) {
                    List<File> watched = new ArrayList<File>(indexedCandidates.subList(0, i + 1))
                    ICON_CANDIDATES.put(cacheKey, watched)
                    ICON_CANDIDATE_STAMPS.put(cacheKey, iconCandidateStamp(watched))
                    ICONS.put(cacheKey, icon)
                    outcome = "preloaded-index=" + file.getName()
                    return icon
                }
            }

            candidatesStarted = System.nanoTime()
            List<File> candidates = iconCandidateFilesForSuggestion(id, requestedTypes, projectRoot)
            candidatesNanos += System.nanoTime() - candidatesStarted
            for (int i = 0; i < candidates.size(); i++) {
                File file = candidates.get(i)
                long loadStarted = System.nanoTime()
                Icon icon = loadIcon(file)
                loadingNanos += System.nanoTime() - loadStarted
                if (icon != null) {
                    List<File> watched = new ArrayList<File>(candidates.subList(0, i + 1))
                    ICON_CANDIDATES.put(cacheKey, watched)
                    ICON_CANDIDATE_STAMPS.put(cacheKey, iconCandidateStamp(watched))
                    ICONS.put(cacheKey, icon)
                    outcome = "icon=" + file.getName()
                    return icon
                }
            }
        } catch (Throwable ignored) {} finally {
            long elapsedNanos = System.nanoTime() - totalStarted
            if (elapsedNanos >= 50_000_000L) {
                debugTiming("icon lookup id=" + id + " outcome=" + outcome + " total=" + elapsed(totalStarted)
                    + " key=" + String.format(java.util.Locale.ROOT, "%.2fms", keyNanos / 1_000_000.0d)
                    + " stamp=" + String.format(java.util.Locale.ROOT, "%.2fms", stampNanos / 1_000_000.0d)
                    + " candidates=" + String.format(java.util.Locale.ROOT, "%.2fms", candidatesNanos / 1_000_000.0d)
                    + " load=" + String.format(java.util.Locale.ROOT, "%.2fms", loadingNanos / 1_000_000.0d))
            }
        }
        return null
    }

    private static String suggestionIconCacheKey(String id, Collection<String> assetPaths, String root) {
        // Cache by the resolved export paths, not the source PSI type. This
        // makes Item and ResourceKey<Item> (and other delegates) share the
        // same per-suggestion result when they resolve to the same asset.
        String identifier = id.startsWith("#") ? id.substring(1) : id
        return root + "|" + identifier + "|" + assetPaths.join(",")
    }

    private static String iconCandidateStamp(Collection<File> files) {
        StringBuilder stamp = new StringBuilder()
        for (File file : files) {
            stamp.append(file.getAbsolutePath()).append('|')
            if (file.isFile()) {
                stamp.append(file.lastModified()).append('|').append(file.length())
            } else {
                stamp.append("missing")
            }
            stamp.append('\n')
        }
        return stamp.toString()
    }

    private static Icon loadIcon(File file) {
        long loadStarted = System.nanoTime()
        long lockWaitStarted = 0L
        long lockWaitNanos = 0L
        long decodeNanos = 0L
        try {
            if (file == null || !file.isFile()) return null
            File canonical = file.getCanonicalFile()
            String cacheKey = canonical.getAbsolutePath() + "|" + canonical.lastModified() + "|" + canonical.length()
            Icon cached = FILE_ICONS.get(cacheKey)
            if (cached != null) return cached
            Object lock = FILE_ICON_LOCKS.get(cacheKey)
            if (lock == null) {
                Object candidate = new Object()
                Object previous = FILE_ICON_LOCKS.putIfAbsent(cacheKey, candidate)
                lock = previous == null ? candidate : previous
            }
            lockWaitStarted = System.nanoTime()
            synchronized (lock) {
                lockWaitNanos = System.nanoTime() - lockWaitStarted
                cached = FILE_ICONS.get(cacheKey)
                if (cached != null) return cached
                long decodeStarted = System.nanoTime()
                Icon loaded = loadIconUncached(canonical)
                decodeNanos = System.nanoTime() - decodeStarted
                if (loaded != null) FILE_ICONS.put(cacheKey, loaded)
                return loaded
            }
        } catch (Throwable ignored) {
            return null
        } finally {
            long totalNanos = System.nanoTime() - loadStarted
            if (totalNanos >= 10_000_000L) {
                long otherNanos = Math.max(0L, totalNanos - decodeNanos - lockWaitNanos)
                debugTiming("icon file load path=" + file?.getAbsolutePath() + " total=" + elapsed(loadStarted)
                    + " decode=" + String.format(java.util.Locale.ROOT, "%.2fms", decodeNanos / 1_000_000.0d)
                    + " lockWait=" + String.format(java.util.Locale.ROOT, "%.2fms", lockWaitNanos / 1_000_000.0d)
                    + " file/key=" + String.format(java.util.Locale.ROOT, "%.2fms", otherNanos / 1_000_000.0d))
            }
        }
    }

    private static Icon loadIconUncached(File canonical) {
        try {
            if (canonical.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".svg")) {
                Icon vector = IconLoader.findIcon(canonical.toURI().toURL(), true)
                if (vector == null || vector.getIconWidth() < 2 || vector.getIconHeight() < 2
                    || vector.getIconWidth() > 48 || vector.getIconHeight() > 48) return null
                return vector
            }
            ImageIcon raw = new ImageIcon(canonical.getAbsolutePath())
            if (raw.getIconWidth() <= 0) return null
            Icon icon = raw
            if (raw.getIconWidth() != 16 || raw.getIconHeight() != 16) {
                java.awt.image.BufferedImage scaled = new java.awt.image.BufferedImage(16, 16,
                    java.awt.image.BufferedImage.TYPE_INT_ARGB)
                java.awt.Graphics2D graphics = scaled.createGraphics()
                try {
                    graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                        java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC)
                    graphics.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING,
                        java.awt.RenderingHints.VALUE_RENDER_QUALITY)
                    graphics.drawImage(raw.getImage(), 0, 0, 16, 16, null)
                } finally {
                    graphics.dispose()
                }
                icon = new ImageIcon(scaled)
            }
            return icon
        } catch (Throwable ignored) {
            return null
        }
    }

    /** Registry ids remain useful for the old per-plugin icon directory. */
    static String iconFileName(String id) {
        try {
            String name = (id.startsWith("#") ? id.substring(1) : id)
                .replace(":", "__").replace("\"", "'").replace("/", "___")
            return name + ".png"
        } catch (Throwable ignored) {
            return id + ".png"
        }
    }

    static String classifyType(PsiType type) {
        String name = psiTypeName(type)
        return resolveSuggestionTypeName(name)
    }

    /** Resolve cast names against the FQNs represented in the JSON catalog.
     * This expands unqualified names recursively, including generic arguments,
     * when PSI cannot resolve them in the current Groovy script scope. */
    static String resolveSuggestionTypeName(String requestedType) {
        if (requestedType == null) return null
        String normalized = normalizeType(requestedType)
        if (normalized.isEmpty()) return null
        if (!suggestionsForType(normalized).isEmpty()) return normalized

        Map config = loadConfig()
        LinkedHashSet<String> registeredRawTypes = new LinkedHashSet<String>()
        ((Map<String, List<String>>) config.values).keySet().each { key -> registeredRawTypes.add(rawTypeName(key)) }
        ((List<Map>) config.delegates).each { rule ->
            if (rule.source != null) registeredRawTypes.add(rawTypeName(rule.source.toString()))
            if (rule.target != null) registeredRawTypes.add(rawTypeName(rule.target.toString()))
        }
        ((Map<String, Integer>) config.genericDelegates).keySet().each { key -> registeredRawTypes.add(rawTypeName(key)) }

        String expanded = expandSuggestionTypeNames(normalized, registeredRawTypes, 0)
        return expanded != null && !suggestionsForType(expanded).isEmpty() ? expanded : null
    }

    private static String expandSuggestionTypeNames(String requestedType, Set<String> registeredRawTypes, int depth) {
        if (requestedType == null || depth > 64) return null
        String normalized = normalizeType(requestedType)
        if (normalized.isEmpty()) return null

        if (normalized.endsWith("[]")) {
            String component = expandSuggestionTypeNames(normalized.substring(0, normalized.length() - 2),
                registeredRawTypes, depth + 1)
            return component == null ? null : component + "[]"
        }

        String raw = rawTypeName(normalized)
        String resolvedRaw = expandSuggestionRawTypeName(raw, registeredRawTypes)
        if (resolvedRaw == null) return null
        if (raw == normalized) return resolvedRaw

        List<String> arguments = typeArguments(normalized)
        if (arguments.isEmpty()) return null
        List<String> expandedArguments = new ArrayList<String>(arguments.size())
        for (String argument : arguments) {
            String expandedArgument = expandSuggestionTypeNames(argument, registeredRawTypes, depth + 1)
            if (expandedArgument == null) return null
            expandedArguments.add(expandedArgument)
        }
        return resolvedRaw + "<" + expandedArguments.join(",") + ">"
    }

    private static String expandSuggestionRawTypeName(String requestedRaw, Set<String> registeredRawTypes) {
        if (requestedRaw == null || requestedRaw.isEmpty()) return null
        if (requestedRaw.contains(".")) return requestedRaw

        String match = null
        for (String candidateRaw : registeredRawTypes) {
            int separator = candidateRaw.lastIndexOf('.')
            String simpleName = separator < 0 ? candidateRaw : candidateRaw.substring(separator + 1)
            if (simpleName != requestedRaw) continue
            if (match != null && match != candidateRaw) return null
            match = candidateRaw
        }
        return match == null ? requestedRaw : match
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
            String resolved = classifyType(typeExpr.getType())
            if (resolved != null) return resolved
        } catch (Throwable ignored) {}
        try {
            String text = normalizeType(typeExpr.getText())
            PsiElement resolved = typeExpr instanceof GrReferenceExpression ? ((GrReferenceExpression) typeExpr).resolve() : null
            if (resolved instanceof PsiClass) {
                String qn = ((PsiClass) resolved).getQualifiedName()
                if (qn != null) text = qn + (text.contains("<") ? text.substring(text.indexOf('<')) : "")
            }
            String supported = resolveSuggestionTypeName(text)
            if (supported != null) return supported
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
            for (int i = 0; i < 12 && e != null && !(e instanceof PsiFile); i++) {
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
                            String supported = resolveSuggestionTypeName(textType)
                            if (supported != null) return supported
                        }
                    } catch (Throwable ignored) {}
                    return null
                }
            }
        } catch (Throwable ignored) {}
        try {
            PsiElement e = lit
            for (int i = 0; i < 12 && e != null && !(e instanceof PsiFile); i++) {
                try {
                    String text = e.getText()
                    int base = e.getTextRange().getStartOffset()
                    Matcher m = AS_CAST_TEXT.matcher(text)
                    while (m.find()) {
                        if (base + m.start() <= offset && offset <= base + m.end()) {
                            String textType = resolveSuggestionTypeName(m.group(1))
                            if (textType != null) return textType
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
        long completionStarted = System.nanoTime()
        try {
            if (ItemStackSupport.sideForFile(parameters.getOriginalFile()) == null) return
            long inferStarted = System.nanoTime()
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
            long inferNanos = System.nanoTime() - inferStarted
            if (specs.isEmpty()) {
                if (inferNanos >= 5_000_000L) {
                    ItemStackSupport.debugTiming("completion no type match infer=" + ItemStackSupport.elapsedNanos(inferNanos)
                        + " total=" + ItemStackSupport.elapsed(completionStarted))
                }
                return
            }
            long suggestionsStarted = System.nanoTime()
            List<String> offerIds = new ArrayList<String>()
            for (String s : specs) {
                try {
                    for (String id : ItemStackSupport.idsForSpec(s)) {
                        offerIds.add(id)
                    }
                } catch (Throwable ignored) {}
            }
            long suggestionsNanos = System.nanoTime() - suggestionsStarted
            long projectStarted = System.nanoTime()
            def sink = result.withPrefixMatcher(ItemStackSupport.literalPrefix(position, offset))
            File projectRoot = ItemStackSupport.projectRoot(parameters.getOriginalFile())
            long projectNanos = System.nanoTime() - projectStarted
            long iconsStarted = System.nanoTime()
            int iconsApplied = 0
            for (String id : offerIds) {
                def el = LookupElementBuilder.create(id).withInsertHandler(ITEM_INSERT)
                try {
                    Icon icon = ItemStackSupport.iconFor(id, specs, projectRoot)
                    if (icon != null) {
                        el = el.withIcon(icon)
                        iconsApplied++
                    }
                    else ItemStackSupport.debug("no exported icon or bindings logo for '" + id + "'")
                } catch (Throwable ignored) {}
                sink.addElement(el)
            }
            long iconsNanos = System.nanoTime() - iconsStarted
            ItemStackSupport.debug("offered " + offerIds.size() + " ids at offset " + offset)
            ItemStackSupport.debugTiming("completion specs=" + specs.size() + " suggestions=" + offerIds.size()
                + " iconsApplied=" + iconsApplied + " infer=" + ItemStackSupport.elapsedNanos(inferNanos)
                + " catalog=" + ItemStackSupport.elapsedNanos(suggestionsNanos)
                + " project/preload=" + ItemStackSupport.elapsedNanos(projectNanos)
                + " icon-pass=" + ItemStackSupport.elapsedNanos(iconsNanos)
                + " total=" + ItemStackSupport.elapsed(completionStarted)
                + " preload=" + ItemStackSupport.projectIconPreloadStatus(projectRoot)
                + " " + ItemStackSupport.iconCacheSizes())
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
    ItemStackSupport.preloadOpenProjects()
} catch (Throwable ignored) {}
def itemStackDescriptor = new DefaultPluginDescriptor(PluginId.getId("LivePlugin"), ItemStackCompletionContributor.classLoader)
def itemStackPoint = ExtensionPointName.create("com.intellij.completion.contributor")
itemStackPoint.point.registerExtension(
    new CompletionContributorEP("Groovy", ItemStackCompletionContributor.name, itemStackDescriptor),
    LoadingOrder.FIRST, pluginDisposable)
