// depends-on-plugin com.intellij.java
// depends-on-plugin org.intellij.groovy
//
// Type suggestions for kubegroovy/{startup,client,server}_scripts/**/*.groovy.
// suggestions.json supplies values and delegation rules. Completion inserts a
// quoted id into the selected argument/container part and retains explicit casts.
// Helpers share this file so generated installs and LivePlugin need only plugin.groovy.
// Compile with compile.bat; fixture commands and icon layout are documented in ../README.md.

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
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiEnumConstant
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import java.util.function.Function
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiInvalidElementAccessException
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
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiFileRange
import com.intellij.openapi.util.Segment
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrSafeCastExpression
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.attribute.BasicFileAttributes
import static TypeSuggestionSupport.*

/**
 * Stateless use-site checks, type-name syntax and diagnostics shared by this script.
 * Completion copies are scoped through their original Groovy file, never a resolved declaration.
 */
@CompileStatic
class TypeSuggestionSupport {
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

	/** Diagnostics retain their existing defaults; all messages go to idea.log. */
	static final Logger LOG = Logger.getInstance("TypeSuggestions")
	static boolean DEBUG = false
	static boolean DEBUG_TIMINGS = true

	static void debug(String message) {
		if (DEBUG) LOG.info("[type-suggest] " + message)
	}

	static void debugTiming(String message) {
		if (DEBUG_TIMINGS) LOG.info("[type-suggest][timing] " + message)
	}

	static String elapsed(long startedNanos) {
		return elapsedNanos(System.nanoTime() - startedNanos)
	}

	static String elapsedNanos(long nanos) {
		return String.format(Locale.ROOT, "%.2fms", nanos / 1_000_000.0d)
	}

	/**
	 * Unavailable PSI/indexes, file access and reflective adapters can fail gracefully.
	 * All other failures, including IDE cancellation and programming errors, propagate.
	 */
	static void recover(Exception error) {
		if (!(error instanceof IndexNotReadyException || error instanceof PsiInvalidElementAccessException
			|| error instanceof IOException || error instanceof UncheckedIOException
			|| error instanceof SecurityException || error instanceof ReflectiveOperationException)) throw error
		debug("suggestion context unavailable (" + error + ")")
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
		} catch (Exception error) { recover(error); return null }
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
}

/**
 * Watches each project's local JSON file and publishes immutable catalogs under a single lock.
 * Invalid or missing updates retain the last valid snapshot. Each snapshot preserves
 * registration order and duplicates and can be used throughout one completion request.
 */
@CompileStatic
class TypeSuggestionCatalog {
	private static File configFile
	private static final Snapshot EMPTY = new Snapshot(0L, [:], [], [:])
	private static final Map<File, String> configStamps = new HashMap<>()
	private static final Map<File, Snapshot> configs = new HashMap<>()
	// Type-dependent icon caches use this generation across all projects.
	private static long nextGeneration = 1L

	static synchronized void setConfigFile(File file) {
		configFile = file
		configStamps.remove(file)
	}

	static File configFileFor(PsiElement place) {
		if (configFile != null) return configFile
		String root = place?.getProject()?.getBasePath()
		return root == null || root.isBlank() ? null : new File(root, "local/kubegroovy/live-plugin/type-suggestions/suggestions.json")
	}

	/** Attempts each timestamp/length revision once, including malformed updates. */
	static synchronized Snapshot loadConfig(PsiElement place = null) {
		File source = configFileFor(place)
		Snapshot current = configs.get(source) ?: EMPTY
		if (source == null || !source.isFile()) return current
		String stamp = source.lastModified() + ":" + source.length()
		if (stamp == configStamps.get(source)) return current
		try {
			Snapshot parsed = parse(new JsonSlurper().parse(source), nextGeneration)
			if (parsed != null) {
				current = parsed
				configs.put(source, parsed)
				nextGeneration++
			}
		} catch (IOException | groovy.json.JsonException error) {
			debug("suggestion config reload failed; keeping previous catalog (" + error + ")")
		} finally {
			configStamps.put(source, stamp)
		}
		return current
	}

	private static Snapshot parse(Object parsed, long generation) {
		if (!(parsed instanceof Map)) return null
		Map<?, ?> document = (Map<?, ?>) parsed
		Object schema = document.get("schemaVersion")
		boolean supported = schema instanceof Number ? ((Number) schema).intValue() == 1 : schema?.toString() == "1"
		if (!supported || !(document.get("values") instanceof Map)
			|| !(document.get("delegates") instanceof Collection) || !(document.get("genericDelegates") instanceof Map)) return null

		Map<String, List<String>> values = new LinkedHashMap<>()
		for (Map.Entry<?, ?> entry : ((Map<?, ?>) document.get("values")).entrySet()) {
			if (entry.key == null || !(entry.value instanceof Collection)) continue
			List<String> ids = new ArrayList<>()
			for (Object value : (Collection<?>) entry.value) {
				if (value != null) ids.add(value.toString())
			}
			values.put(normalizeType(entry.key.toString()), ids)
		}
		List<DelegateRule> delegates = new ArrayList<>()
		for (Object candidate : (Collection<?>) document.get("delegates")) {
			if (!(candidate instanceof Map)) continue
			Map<?, ?> rule = (Map<?, ?>) candidate
			if (rule.get("source") == null || rule.get("target") == null) continue
			delegates.add(new DelegateRule(normalizeType(rule.get("source").toString()),
				normalizeType(rule.get("target").toString()), rule.get("prefix")?.toString() ?: ""))
		}
		Map<String, Integer> genericDelegates = new LinkedHashMap<>()
		for (Map.Entry<?, ?> entry : ((Map<?, ?>) document.get("genericDelegates")).entrySet()) {
			if (entry.key == null || entry.value == null) continue
			try {
				genericDelegates.put(normalizeType(entry.key.toString()), Integer.valueOf(entry.value.toString()))
			} catch (NumberFormatException ignored) {
				// An invalid wrapper index is ignored, as in the generated schema's previous reader.
			}
		}
		Map<String, List<String>> builderTypes = new LinkedHashMap<>()
		Object builders = document.get("registryBuilderTypes")
		if (builders instanceof Map) {
			for (Map.Entry<?, ?> entry : ((Map<?, ?>) builders).entrySet()) {
				if (entry.key == null || !(entry.value instanceof Collection)) continue
				List<String> names = new ArrayList<>()
				for (Object name : (Collection<?>) entry.value) if (name instanceof String) names.add((String) name)
				builderTypes.put(normalizeType(entry.key.toString()), names)
			}
		}
		return new Snapshot(generation, values, delegates, genericDelegates, builderTypes)
	}

	/** Ordered fixed delegation; the prefix is applied to every recursively resolved value. */
	static final class DelegateRule {
		final String source
		final String target
		final String prefix

		DelegateRule(String source, String target, String prefix) {
			this.source = source
			this.target = target
			this.prefix = prefix
		}
	}

	/**
	 * One immutable catalog generation. Recursion tracks the active branch, allowing
	 * another delegate to revisit the same type without losing duplicate suggestions.
	 */
	static final class Snapshot {
		final long generation
		final Map<String, List<String>> values
		final List<DelegateRule> delegates
		final Map<String, Integer> genericDelegates
		final Map<String, List<String>> registryBuilderTypes
		private final Set<String> registeredRawTypes

		Snapshot(long generation, Map<String, List<String>> values,
			List<DelegateRule> delegates, Map<String, Integer> genericDelegates,
			Map<String, List<String>> registryBuilderTypes = [:]) {
			this.generation = generation
			Map<String, List<String>> copiedValues = new LinkedHashMap<>()
			for (Map.Entry<String, List<String>> entry : values.entrySet()) {
				copiedValues.put(entry.key, Collections.unmodifiableList(new ArrayList<String>(entry.value)))
			}
			this.values = Collections.unmodifiableMap(copiedValues)
			this.delegates = Collections.unmodifiableList(new ArrayList<DelegateRule>(delegates))
			this.genericDelegates = Collections.unmodifiableMap(new LinkedHashMap<String, Integer>(genericDelegates))
			Map<String, List<String>> copiedBuilders = new LinkedHashMap<>()
			for (Map.Entry<String, List<String>> entry : registryBuilderTypes.entrySet()) {
				copiedBuilders.put(entry.key, Collections.unmodifiableList(new ArrayList<String>(entry.value)))
			}
			this.registryBuilderTypes = Collections.unmodifiableMap(copiedBuilders)
			Set<String> names = new LinkedHashSet<>()
			for (String type : values.keySet()) names.add(rawTypeName(type))
			for (DelegateRule rule : delegates) {
				names.add(rawTypeName(rule.source))
				names.add(rawTypeName(rule.target))
			}
			for (String type : genericDelegates.keySet()) names.add(rawTypeName(type))
			this.registeredRawTypes = Collections.unmodifiableSet(names)
		}

		/** Exact values, raw values, fixed delegates, then the configured generic argument. */
		List<String> suggestionsForType(String requestedType, Function<String, List<String>> fallback = null) {
			return resolveSuggestions(requestedType, new LinkedHashSet<String>(), fallback)
		}

		private List<String> resolveSuggestions(String requestedType, Set<String> path, Function<String, List<String>> fallback) {
			String type = normalizeType(requestedType)
			if (type.isEmpty() || !path.add(type)) return []
			List<String> result = new ArrayList<>()
			try {
				result.addAll(values.getOrDefault(type, Collections.<String>emptyList()))
				String raw = rawTypeName(type)
				if (raw != type) result.addAll(values.getOrDefault(raw, Collections.<String>emptyList()))
				// Exported runtime names override enum constant names inferred by the IDE.
				if (!values.containsKey(type) && !values.containsKey(raw) && fallback != null) result.addAll(fallback.apply(raw))
				for (DelegateRule rule : delegates) {
					if (type == rule.source || raw == rule.source) {
						for (String id : resolveSuggestions(rule.target, path, fallback)) result.add(rule.prefix + id)
					}
				}
				Integer index = genericDelegates.get(raw)
				if (index != null) {
					List<String> arguments = typeArguments(type)
					if (index >= 0 && index < arguments.size() && !arguments[index].isEmpty()) {
						result.addAll(resolveSuggestions(arguments[index], path, fallback))
					}
				}
			} finally {
				path.remove(type)
			}
			return result
		}

		/**
		 * Returns a supported fully qualified type, or null for unknown/ambiguous names.
		 * Catalog names also resolve short cast names when incomplete PSI cannot do so.
		 */
		String resolveTypeName(String requestedType, Function<String, List<String>> fallback = null) {
			String normalized = normalizeType(requestedType)
			if (normalized.isEmpty()) return null
			if (!suggestionsForType(normalized, fallback).isEmpty()) return normalized
			String expanded = expandTypeNames(normalized, 0)
			return expanded != null && !suggestionsForType(expanded, fallback).isEmpty() ? expanded : null
		}

		private String expandTypeNames(String requestedType, int depth) {
			if (requestedType == null || depth > 64) return null
			String normalized = normalizeType(requestedType)
			if (normalized.isEmpty()) return null
			if (normalized.endsWith("[]")) {
				String component = expandTypeNames(normalized.substring(0, normalized.length() - 2), depth + 1)
				return component == null ? null : component + "[]"
			}
			String raw = rawTypeName(normalized)
			String expandedRaw = expandRawName(raw)
			if (expandedRaw == null) return null
			if (raw == normalized) return expandedRaw
			List<String> arguments = typeArguments(normalized)
			if (arguments.isEmpty()) return null
			List<String> expandedArguments = new ArrayList<>()
			for (String argument : arguments) {
				String expanded = expandTypeNames(argument, depth + 1)
				if (expanded == null) return null
				expandedArguments.add(expanded)
			}
			return expandedRaw + "<" + expandedArguments.join(",") + ">"
		}

		private String expandRawName(String requestedRaw) {
			if (requestedRaw.contains(".")) return requestedRaw
			String match = null
			for (String candidate : registeredRawTypes) {
				String simple = candidate.substring(candidate.lastIndexOf('.') + 1)
				if (simple != requestedRaw) continue
				if (match != null && match != candidate) return null
				match = candidate
			}
			return match == null ? requestedRaw : match
		}
	}
}

/**
 * Owns icon discovery, background preloading and caches for this loaded script.
 * Export paths are canonicalized before indexing. File decoding is shared across
 * delegates and preloading; catalog generations isolate type-dependent results.
 */
@CompileStatic
class TypeSuggestionIcons {
	private static final Map<String, String> ICON_DIRECTORY_CACHE = new ConcurrentHashMap<>()
	private static final Map<String, String> TAG_DIRECTORY_CACHE = new ConcurrentHashMap<>()
	private static final Map<String, CachedIcon> ICONS = new ConcurrentHashMap<>()
	private static final Map<String, Icon> FILE_ICONS = new ConcurrentHashMap<>()
	private static final Map<String, Object> FILE_ICON_LOCKS = new ConcurrentHashMap<>()
	private static final Map<String, CompletableFuture<Void>> PROJECT_ICON_PRELOADS = new ConcurrentHashMap<>()
	private static final Map<String, File> EXPORTED_ICON_FILES = new ConcurrentHashMap<>()
	private static final Map<String, String> PROJECT_ROOT_PATHS = new ConcurrentHashMap<>()
	private static final Map<String, File> EXPORT_ICON_ROOTS = new ConcurrentHashMap<>()
	private static volatile String iconDir
	private static volatile File bindingsLogoFile
	private static volatile File bindingsLogoSvgFile
	private static volatile File bindingsLogoDarkSvgFile
	private static long catalogGeneration = -1L

	static String iconCacheSizes() {
		return "cachedSuggestions=" + ICONS.size() + " cachedFiles=" + FILE_ICONS.size() +
			" indexedExports=" + EXPORTED_ICON_FILES.size()
	}

	static String projectIconPreloadStatus(File projectRoot) {
		try {
			String root = projectRoot?.getCanonicalPath()
			java.util.concurrent.CompletableFuture<Void> future = root == null ? null : PROJECT_ICON_PRELOADS.get(root)
			if (future == null) return "not-scheduled"
			if (!future.isDone()) return "running"
			return future.isCompletedExceptionally() ? "failed" : "complete"
		} catch (Exception error) {
			recover(error)
			return "unknown"
		}
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
		} catch (Exception error) { recover(error) }
	}

	private static void clearSuggestionIconCache() {
		ICONS.clear()
		ICON_DIRECTORY_CACHE.clear()
		TAG_DIRECTORY_CACHE.clear()
	}

	static File projectRoot(PsiFile file) {
		try {
			File root = projectRootForProject(file?.getProject())
			if (root != null) preloadProjectIcons(root)
			return root
		} catch (Exception error) { recover(error) }
		return null
	}

	static File projectRootForProject(Project project) {
		try {
			String basePath = project?.getBasePath()
			if (basePath != null && !basePath.isBlank()) return new File(basePath).getCanonicalFile()
		} catch (Exception error) { recover(error) }
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
		} catch (Exception error) {
			debugTiming("open-project preload scheduling failed elapsed=" + elapsed(started) + " error=" + error)
			recover(error)
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
		try { rootPath = projectRoot.getCanonicalPath() } catch (Exception error) { recover(error); return java.util.concurrent.CompletableFuture.completedFuture(null) }
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
									if (!file.getName().toLowerCase(Locale.ROOT).endsWith(".png")) return
									files.incrementAndGet()
									File canonical = file.getCanonicalFile()
									if (canonical.toPath().startsWith(categoryRoot.toPath()) && loadIcon(canonical) != null) {
										warmed.incrementAndGet()
										String relative = scaleRoot.toPath().relativize(canonical.toPath()).toString()
											.replace('\\', '/')
										EXPORTED_ICON_FILES.put(exportedIconIndexKey(rootPath, scaleDirectory, relative), canonical)
									}
								} catch (Exception error) { recover(error) }
							}
							debugTiming("preload scan root=" + scaleDirectory + "/" + category + " files=" + files.get()
								+ " cached=" + (warmed.get() - before) + " elapsed=" + elapsed(categoryStarted))
						}
					}
					debug("preloaded " + warmed.get() + " exported icons for " + rootPath)
					debugTiming("project icon preload finished root=" + rootPath + " cached=" + warmed.get() + " elapsed=" + elapsed(preloadStarted))
					if (warmed.get() == 0) PROJECT_ICON_PRELOADS.remove(rootPath, created)
					created.complete(null)
				} catch (Exception error) {
					PROJECT_ICON_PRELOADS.remove(rootPath, created)
					created.completeExceptionally(error)
					debugTiming("project icon preload failed root=" + rootPath + " cached=" + warmed.get()
						+ " elapsed=" + elapsed(preloadStarted) + " error=" + error)
					debug("icon preload failed for " + rootPath + " (" + error + ")")
				}
			} as Runnable)
		} catch (Exception error) {
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
		} catch (Exception error) {
			debugTiming("bindings logo preload dispatch failed error=" + error)
			recover(error)
		}
	}

	/** Returns the exporter-relative path for an item, fluid, or TagKey suggestion. */
	static String exportedIconRelativePath(String id, String requestedType,
		TypeSuggestionCatalog.Snapshot catalog = TypeSuggestionCatalog.loadConfig()) {
		if (id == null || requestedType == null) return null
		activateCatalog(catalog)
		boolean prefixedTag = id.startsWith("#")
		String identifier = prefixedTag ? id.substring(1) : id
		int separator = identifier.indexOf(':')
		if (separator <= 0 || separator != identifier.lastIndexOf(':')) return null
		String namespace = identifier.substring(0, separator)
		String path = identifier.substring(separator + 1)
		if (!(namespace ==~ /[a-z0-9_.-]+/) || !(path ==~ /[a-z0-9\/._-]+/)) return null

		String tagDirectory = tagDirectoryForSpec(requestedType, prefixedTag, catalog)
		if (prefixedTag || tagDirectory != null) {
			if (tagDirectory == null) return null
			return "tags/${tagDirectory}/${namespace}/${path}.png"
		}
		String iconDirectory = iconDirectoryForSpec(requestedType, catalog)
		return "${iconDirectory}/${namespace}/${path}.png"
	}

	private static String tagDirectoryForSpec(String requestedType, boolean followFixedDelegates, TypeSuggestionCatalog.Snapshot catalog) {
		String type = normalizeType(requestedType)
		String cacheKey = catalog.generation + "|" + type + "|" + followFixedDelegates
		String cached = TAG_DIRECTORY_CACHE.get(cacheKey)
		if (cached != null) return cached.isEmpty() ? null : cached
		String directory = tagDirectoryForSpec(type, followFixedDelegates, new LinkedHashSet<String>(), catalog)
		TAG_DIRECTORY_CACHE.put(cacheKey, directory ?: "")
		return directory
	}

	private static String tagDirectoryForSpec(String requestedType, boolean followFixedDelegates, Set<String> path, TypeSuggestionCatalog.Snapshot catalog) {
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
			Integer argumentIndex = catalog.genericDelegates.get(raw)
			if (argumentIndex != null) {
				List<String> args = typeArguments(type)
				if (argumentIndex >= 0 && argumentIndex < args.size()) {
					String directory = tagDirectoryForSpec(args[argumentIndex], followFixedDelegates, path, catalog)
					if (directory != null) return directory
				}
			}
			if (followFixedDelegates) {
				for (TypeSuggestionCatalog.DelegateRule rule : catalog.delegates) {
					String source = rule.source
					if (type == source || raw == source) {
						String directory = tagDirectoryForSpec(rule.target, true, path, catalog)
						if (directory != null) return directory
					}
				}
			}
			return null
		} catch (Exception error) {
			recover(error)
			return null
		} finally {
			path.remove(type)
		}
	}

	private static String iconDirectoryForSpec(String requestedType, TypeSuggestionCatalog.Snapshot catalog) {
		String type = normalizeType(requestedType)
		String cacheKey = catalog.generation + "|" + type
		String cached = ICON_DIRECTORY_CACHE.get(cacheKey)
		if (cached != null) return cached
		String directory = iconDirectoryForSpec(type, new LinkedHashSet<String>(), catalog) ?: "item"
		ICON_DIRECTORY_CACHE.put(cacheKey, directory)
		return directory
	}

	private static String iconDirectoryForSpec(String requestedType, Set<String> path, TypeSuggestionCatalog.Snapshot catalog) {
		String type = normalizeType(requestedType)
		if (type.isEmpty() || !path.add(type)) return null
		try {
			String raw = rawTypeName(type)
			if (raw == "net.minecraft.world.level.material.Fluid") return "fluid"
			if (raw == "net.minecraft.tags.TagKey") return null
			Integer argumentIndex = catalog.genericDelegates.get(raw)
			if (argumentIndex != null) {
				List<String> args = typeArguments(type)
				if (argumentIndex >= 0 && argumentIndex < args.size()) {
					String directory = iconDirectoryForSpec(args[argumentIndex], path, catalog)
					if (directory != null) return directory
				}
			}
			for (TypeSuggestionCatalog.DelegateRule rule : catalog.delegates) {
				String source = rule.source
				if (type == source || raw == source) {
					String directory = iconDirectoryForSpec(rule.target, path, catalog)
					if (directory != null) return directory
				}
			}
			return "item"
		} catch (Exception error) {
			recover(error)
			return "item"
		} finally {
			path.remove(type)
		}
	}

	static List<File> exportedIconFiles(File projectRoot, String id, Collection<String> requestedTypes,
		TypeSuggestionCatalog.Snapshot catalog = TypeSuggestionCatalog.loadConfig()) {
		if (projectRoot == null || requestedTypes == null) return []
		LinkedHashSet<String> relativePaths = new LinkedHashSet<String>()
		for (String type : requestedTypes) {
			String relative = exportedIconRelativePath(id, type, catalog)
			if (relative != null) relativePaths.add(relative)
		}
		List<File> files = []
		try {
			String projectRootPath = canonicalProjectRootPath(projectRoot)
			for (String folder : ["icons-32", "icon-32"]) {
				for (String relative : relativePaths) {
					File file = exportedIconFile(projectRootPath, folder, relative)
					if (file != null) files.add(file)
				}
			}
		} catch (Exception error) { recover(error) }
		return files
	}

	static List<File> iconFilesForSuggestion(String id, Collection<String> requestedTypes, File projectRoot) {
		return iconCandidateFilesForSuggestion(id, requestedTypes, projectRoot).findAll { it.isFile() }.toList()
	}

	private static List<File> iconCandidateFilesForSuggestion(String id, Collection<String> requestedTypes, File projectRoot,
		TypeSuggestionCatalog.Snapshot catalog = TypeSuggestionCatalog.loadConfig()) {
		LinkedHashMap<String, File> files = new LinkedHashMap<String, File>()
		for (File file : exportedIconFiles(projectRoot, id, requestedTypes, catalog)) files.put(file.getAbsolutePath(), file)
		for (File file : fallbackIconFilesForSuggestion(id)) files.put(file.getAbsolutePath(), file)
		return files.values().toList()
	}

	private static List<File> fallbackIconFilesForSuggestion(String id) {
		LinkedHashMap<String, File> files = new LinkedHashMap<String, File>()
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

	private static String canonicalProjectRootPath(File projectRoot) {
		String key = projectRoot.getAbsolutePath()
		String cached = PROJECT_ROOT_PATHS.get(key)
		if (cached != null) return cached
		String canonical = projectRoot.getCanonicalPath()
		PROJECT_ROOT_PATHS.put(key, canonical)
		return canonical
	}

	private static File exportedIconFile(String projectRootPath, String folder, String relative) {
		String key = exportedIconIndexKey(projectRootPath, folder, relative)
		File file = EXPORTED_ICON_FILES.get(key)
		if (file != null) return file
		String rootKey = projectRootPath + "|" + folder
		File root = EXPORT_ICON_ROOTS.get(rootKey)
		if (root == null) {
			root = new File(projectRootPath, "local/kubegroovy/export/" + folder).getCanonicalFile()
			EXPORT_ICON_ROOTS.put(rootKey, root)
		}
		file = safeIconFile(root, relative)
		if (file != null) EXPORTED_ICON_FILES.put(key, file)
		return file
	}

	private static List<String> exportedIconPathsForSuggestion(String id, Collection<String> requestedTypes, TypeSuggestionCatalog.Snapshot catalog) {
		LinkedHashSet<String> paths = new LinkedHashSet<String>()
		for (String type : requestedTypes ?: []) {
			String relative = exportedIconRelativePath(id, normalizeType(type), catalog)
			if (relative != null) paths.add(relative)
		}
		return new ArrayList<String>(paths)
	}

	private static File safeIconFile(File root, String relative) {
		try {
			File canonicalRoot = root.getCanonicalFile()
			File canonicalFile = new File(canonicalRoot, relative).getCanonicalFile()
			return canonicalFile.toPath().startsWith(canonicalRoot.toPath()) ? canonicalFile : null
		} catch (Exception error) {
			recover(error)
			return null
		}
	}

	/**
	 * Resolves candidates in priority order. Watching missing higher-priority files
	 * lets a new export replace a cached fallback. Entries are published atomically
	 * and keyed by catalog generation, project, identifier and resolved asset paths.
	 */
	static Icon iconFor(String id, Collection<String> requestedTypes, File projectRoot,
		TypeSuggestionCatalog.Snapshot catalog = TypeSuggestionCatalog.loadConfig(),
		Map<String, String> fileStates = new HashMap<String, String>()) {
		if (id == null) return null
		long started = System.nanoTime()
		try {
			activateCatalog(catalog)
			String root = projectRoot == null ? "" : projectRoot.getAbsolutePath()
			List<String> paths = exportedIconPathsForSuggestion(id, requestedTypes, catalog)
			String identifier = id.startsWith("#") ? id.substring(1) : id
			String key = catalog.generation + "|" + root + "|" + identifier + "|" + paths.join(",")
			CachedIcon cached = ICONS.get(key)
			if (cached != null) {
				if (cached.stamp == iconCandidateStamp(cached.watched, fileStates)) return cached.icon
				ICONS.remove(key, cached)
			}
			// Preloading has already canonicalized exported paths and decoded their files.
			// Stop at the first usable candidate before constructing any fallback paths.
			List<File> watched = new ArrayList<File>()
			if (projectRoot != null) {
				String projectRootPath = canonicalProjectRootPath(projectRoot)
				for (String folder : ["icons-32", "icon-32"]) {
					for (String relative : paths) {
						File file = exportedIconFile(projectRootPath, folder, relative)
						if (file == null) continue
						watched.add(file)
						Icon icon = loadIcon(file, fileStates)
						if (icon != null) return rememberIcon(key, icon, watched, fileStates)
					}
				}
			}
			for (File file : fallbackIconFilesForSuggestion(id)) {
				if (watched.contains(file)) continue
				watched.add(file)
				Icon icon = loadIcon(file, fileStates)
				if (icon == null) continue
				return rememberIcon(key, icon, watched, fileStates)
			}
			return rememberIcon(key, null, watched, fileStates)
		} catch (Exception error) {
			recover(error)
		} finally {
			if (System.nanoTime() - started >= 50_000_000L) debugTiming("icon lookup id=" + id + " total=" + elapsed(started))
		}
		return null
	}

	private static Icon rememberIcon(String key, Icon icon, List<File> watched, Map<String, String> fileStates) {
		ICONS.put(key, new CachedIcon(icon, watched, iconCandidateStamp(watched, fileStates)))
		return icon
	}

	private static synchronized void activateCatalog(TypeSuggestionCatalog.Snapshot catalog) {
		if (catalog.generation <= catalogGeneration) return
		clearSuggestionIconCache()
		catalogGeneration = catalog.generation
	}

	/** An icon and the file state that justified choosing it form one cache entry. */
	private static final class CachedIcon {
		final Icon icon
		final List<File> watched
		final String stamp

		CachedIcon(Icon icon, List<File> watched, String stamp) {
			this.icon = icon
			this.watched = Collections.unmodifiableList(new ArrayList<File>(watched))
			this.stamp = stamp
		}
	}

	/** Read each candidate's attributes once per completion, including shared fallbacks. */
	private static String iconFileState(File file, Map<String, String> fileStates) {
		String path = file.getAbsolutePath()
		String cached = fileStates.get(path)
		if (cached != null) return cached
		String state = "missing"
		try {
			BasicFileAttributes attributes = Files.readAttributes(file.toPath(), BasicFileAttributes.class)
			if (attributes.isRegularFile()) state = attributes.lastModifiedTime().toMillis() + "|" + attributes.size()
		} catch (NoSuchFileException ignored) {
			// Missing higher-priority candidates are watched so later exports replace fallbacks.
		} catch (IOException | SecurityException error) { recover(error) }
		fileStates.put(path, state)
		return state
	}

	private static String iconCandidateStamp(Collection<File> files, Map<String, String> fileStates) {
		StringBuilder stamp = new StringBuilder()
		for (File file : files) {
			stamp.append(file.getAbsolutePath()).append('|').append(iconFileState(file, fileStates)).append('\n')
		}
		return stamp.toString()
	}

	private static Icon loadIcon(File file, Map<String, String> fileStates = new HashMap<String, String>()) {
		long loadStarted = System.nanoTime()
		long lockWaitStarted = 0L
		long lockWaitNanos = 0L
		long decodeNanos = 0L
		try {
			if (file == null) return null
			String state = iconFileState(file, fileStates)
			if (state == "missing") return null
			String cacheKey = file.getAbsolutePath() + "|" + state
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
				Icon loaded = loadIconUncached(file)
				decodeNanos = System.nanoTime() - decodeStarted
				if (loaded != null) FILE_ICONS.put(cacheKey, loaded)
				return loaded
			}
		} catch (Exception error) {
			recover(error)
			return null
		} finally {
			long totalNanos = System.nanoTime() - loadStarted
			if (totalNanos >= 10_000_000L) {
				long otherNanos = Math.max(0L, totalNanos - decodeNanos - lockWaitNanos)
				debugTiming("icon file load path=" + file?.getAbsolutePath() + " total=" + elapsed(loadStarted)
					+ " decode=" + String.format(Locale.ROOT, "%.2fms", decodeNanos / 1_000_000.0d)
					+ " lockWait=" + String.format(Locale.ROOT, "%.2fms", lockWaitNanos / 1_000_000.0d)
					+ " file/key=" + String.format(Locale.ROOT, "%.2fms", otherNanos / 1_000_000.0d))
			}
		}
	}

	private static Icon loadIconUncached(File canonical) {
		try {
			if (canonical.getName().toLowerCase(Locale.ROOT).endsWith(".svg")) {
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
		} catch (Exception error) {
			recover(error)
			return null
		}
	}

	/** Registry ids remain useful for the old per-plugin icon directory. */
	static String iconFileName(String id) {
		String name = (id.startsWith("#") ? id.substring(1) : id)
			.replace(":", "__").replace("\"", "'").replace("/", "___")
		return name + ".png"
	}
}

/**
 * Resolves completion types from Groovy use-sites against one catalog snapshot.
 * Unknown types and unsupported/incomplete expressions return null or an empty list;
 * map/list traversal selects the scalar parameter type without modifying declarations.
 */
@CompileStatic
class TypeSuggestionPsi {
	private final TypeSuggestionCatalog.Snapshot catalog
	private Project project
	private final Map<String, List<String>> enumValues = new HashMap<>()
	private static final Pattern AS_CAST_TEXT = Pattern.compile("\"[^\"]*\"\\s+as\\s+([\\w.\$]+(?:\\s*<[^>]+>)?(?:\\[\\])?)")
	private static final Set<String> MAP_TYPES = Set.of("java.util.Map", "java.util.SortedMap", "java.util.NavigableMap")
	private static final Set<String> COLLECTION_TYPES = Set.of("java.util.Collection", "java.lang.Iterable")

	TypeSuggestionPsi(TypeSuggestionCatalog.Snapshot catalog = TypeSuggestionCatalog.loadConfig(), Project project = null) {
		this.catalog = catalog
		this.project = project
	}

	private String classifyType(PsiType type) {
		if (project == null && type instanceof PsiClassType) project = ((PsiClassType) type).resolve()?.getProject()
		return resolveSupportedType(psiTypeName(type))
	}

	private String resolveSupportedType(String name) {
		return catalog.resolveTypeName(name, { String type -> enumSuggestions(type) } as Function<String, List<String>>)
	}

	private List<String> enumSuggestions(String name) {
		if (project == null) return []
		if (enumValues.containsKey(name)) return enumValues.get(name)
		List<String> values = []
		try {
			PsiClass cls = JavaPsiFacade.getInstance(project).findClass(name, GlobalSearchScope.allScope(project))
			if (cls != null && cls.isEnum()) {
				for (def field : cls.getFields()) {
					if (field instanceof PsiEnumConstant) values.add(field.getName().toLowerCase(Locale.ROOT))
				}
			}
		} catch (Exception error) { recover(error); return [] }
		enumValues.put(name, values)
		return values
	}

	List<String> suggestionsForType(String name) {
		return catalog.suggestionsForType(name, { String type -> enumSuggestions(type) } as Function<String, List<String>>)
	}

	/** Null outside the builder-type argument; empty when the event's registry is unknown. */
	List<String> builderSuggestions(PsiElement position, int offset) {
		if (PsiTreeUtil.getParentOfType(position, GrTypeElement.class, false) != null) return null
		GrCall call = PsiTreeUtil.getParentOfType(position, GrCall.class, false)
		if (!(call instanceof GrMethodCallExpression)) return null
		GrExpression invoked = ((GrMethodCallExpression) call).getInvokedExpression()
		if (!(invoked instanceof GrReferenceExpression)) return null
		GrReferenceExpression reference = (GrReferenceExpression) invoked
		if (reference.getReferenceName() != 'create' || argumentIndex(call, call.getExpressionArguments(), offset) != 1) return null
		PsiType receiver = reference.getQualifierExpression()?.getType()
		if (!(receiver instanceof PsiClassType)) return null
		def resolved = ((PsiClassType) receiver).resolveGenerics()
		PsiClass event = resolved.getElement()
		String name = event?.getQualifiedName()
		if (name != 'dev.uncandango.kubegroovy.registry.RegistryKubeEvent' &&
			name != 'dev.uncandango.kubegroovy.registry.ServerRegistryKubeEvent') return null
		if (event.getTypeParameters().length != 1) return []
		PsiType element = resolved.getSubstitutor().substitute(event.getTypeParameters()[0])
		String elementName = psiTypeName(element)
		return elementName == null ? [] : catalog.registryBuilderTypes.get(elementName) ?: []
	}

	/** Finds all supported types at the caret, preferring applicable methods before sibling overloads and casts. */
	List<String> completionTypes(PsiElement position, int offset) {
		project = position.getProject()
		// A cast's type is inside the argument range, but is not a value completion site.
		if (PsiTreeUtil.getParentOfType(position, GrTypeElement.class, false) != null) return []
		List<String> types = new ArrayList<>()
		GrCall call = PsiTreeUtil.getParentOfType(position, GrCall.class, false)
		if (call != null) {
			GrExpression[] arguments = call.getExpressionArguments() ?: new GrExpression[0]
			int index = argumentIndex(call, arguments, offset)
			if (index >= 0) {
				Map<PsiMethod, PsiSubstitutor> resolved = resolvedMethods(call)
				// The applicable registry bridge takes Object so plain strings work.
				// Its typed ResourceKey sibling may be absent from completion variants;
				// recover the registry-name catalog from the event callback signature.
				if (index == 0 && isRegistryEventTarget(resolved.keySet())) {
					String registryType = catalog.resolveTypeName('net.minecraft.core.Registry')
					if (registryType != null) return [registryType]
				}
				PsiElement argument = index < arguments.length ? arguments[index] : null
				for (Map.Entry<PsiMethod, PsiSubstitutor> entry : resolved.entrySet()) {
					String type = specForParam(entry.key, index, entry.value, argument, position, offset)
					if (type != null) types.add(type)
				}
				if (types.isEmpty() && call instanceof GrMethodCallExpression) {
					GrExpression invoked = ((GrMethodCallExpression) call).getInvokedExpression()
					if (invoked instanceof GrReferenceExpression) {
						GrReferenceExpression reference = (GrReferenceExpression) invoked
						String name = reference.getReferenceName()
						if (name != null) {
							Set<PsiMethod> overloads = new LinkedHashSet<>()
							overloads.addAll(namedVariants(reference, name))
							overloads.addAll(classOverloads(reference, resolved.keySet(), name))
							for (PsiMethod method : overloads) {
								String type = specForParam(method, index, null, argument, position, offset)
								if (type != null) types.add(type)
							}
						}
					}
				}
			}
		}
		if (types.isEmpty()) {
			String coerced = getCoercedSpec(position, offset)
			if (coerced != null) types.add(coerced)
		}
		return types
	}

	private static boolean isRegistryEventTarget(Collection<PsiMethod> methods) {
		for (PsiMethod method : methods) {
			if (method.getContainingClass()?.getQualifiedName() != 'dev.uncandango.kubegroovy.typing.EventGroupBinding') continue
			def parameters = method.getParameterList().getParameters()
			if (parameters.length != 2) continue
			String callback = psiTypeName(parameters[1].getType())
			if (callback == null || rawTypeName(callback) != 'java.util.function.Consumer') continue
			List<String> events = typeArguments(callback)
			if (events.size() != 1) continue
			String event = rawTypeName(events[0])
			if (event == 'dev.uncandango.kubegroovy.registry.RegistryKubeEvent' ||
				event == 'dev.uncandango.kubegroovy.registry.ServerRegistryKubeEvent') return true
		}
		return false
	}

	private static int argumentIndex(GrCall call, GrExpression[] arguments, int offset) {
		for (int i = 0; i < arguments.length; i++) {
			if (arguments[i].getTextRange().contains(offset)) return i
		}
		GrArgumentList list = call.getArgumentList()
		return list != null && list.getTextRange().contains(offset) ? arguments.length : -1
	}

	private static Map<PsiMethod, PsiSubstitutor> resolvedMethods(GrCall call) {
		Map<PsiMethod, PsiSubstitutor> methods = new LinkedHashMap<>()
		PsiMethod single = call.resolveMethod()
		if (single != null) methods.put(single, null)
		for (GroovyResolveResult variant : call.multiResolveGroovy(false)) {
			PsiElement element = variant.getElement()
			if (!(element instanceof PsiMethod)) continue
			PsiMethod method = (PsiMethod) element
			PsiSubstitutor substitutor = variant.getSubstitutor()
			if (!methods.containsKey(method) || substitutor != null) methods.put(method, substitutor)
		}
		return methods
	}

	/**
	 * Returns the supported type at a positional argument, after generic substitution,
	 * nested container selection and varargs handling. Negative/out-of-range indexes return null.
	 */
	String specForParam(PsiMethod method, int index, PsiSubstitutor substitutor = null,
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
				} catch (Exception error) { recover(error) }
			}
			if (index >= params.length - 1) {
				try {
					PsiType comp = varargComponentType(method)
					if (comp != null) {
						if (substitutor != null) comp = substitutor.substitute(comp)
						String s = classifyType(comp)
						if (s != null) return s
					}
				} catch (Exception error) { recover(error) }
			}
		} catch (Exception error) { recover(error) }
		return null
	}

	String denotesSpec(GrExpression typeExpr) {
		if (typeExpr == null) return null
		try {
			String resolved = classifyType(typeExpr.getType())
			if (resolved != null) return resolved
		} catch (Exception error) { recover(error) }
		try {
			String text = normalizeType(typeExpr.getText())
			PsiElement resolved = typeExpr instanceof GrReferenceExpression ? ((GrReferenceExpression) typeExpr).resolve() : null
			if (resolved instanceof PsiClass) {
				String qn = ((PsiClass) resolved).getQualifiedName()
				if (qn != null) text = qn + (text.contains("<") ? text.substring(text.indexOf('<')) : "")
			}
			String supported = resolveSupportedType(text)
			if (supported != null) return supported
		} catch (Exception error) { recover(error) }
		return null
	}

	/** Resolve the scalar type selected inside recursively nested list/map literals. */
	String typeAtArgument(PsiType expected, PsiElement expression, PsiElement position, int offset) {
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
				} else {
					PsiType mapType = matchingSupertype(selected, MAP_TYPES, new LinkedHashSet<String>())
					if (mapType != null) {
						int side = mapSide(container, position, offset)
						if (side < 0) return null
						selected = typeFromArgument(mapType, side)
					} else {
						PsiType collectionType = matchingSupertype(selected, COLLECTION_TYPES, new LinkedHashSet<String>())
						selected = typeFromArgument(collectionType, 0)
					}
				}
				if (selected == null) return null
			}
			return classifyType(selected)
		} catch (Exception error) { recover(error); return null }
	}

	private PsiType unwrapSuggestionWrapper(PsiType type) {
		Map<String, Integer> generic = catalog.genericDelegates
		Set<String> path = new LinkedHashSet<String>()
		PsiType selected = type
		while (selected != null) {
			String name = psiTypeName(selected)
			if (name == null || !path.add(normalizeType(name))) return null
			Integer index = generic.get(rawTypeName(name))
			if (index == null) return selected
			selected = typeFromArgument(selected, index)
		}
		return null
	}

	private PsiType typeFromArgument(PsiType parent, int index) {
		try {
			if (parent instanceof PsiClassType) {
				PsiType[] params = ((PsiClassType) parent).getParameters()
				if (index >= 0 && index < params.length) return params[index]
			}
		} catch (Exception error) { recover(error) }
		return null
	}

	private PsiType matchingSupertype(PsiType type, Set<String> names, Set<String> visited) {
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
		} catch (Exception error) { recover(error) }
		return null
	}

	/**
	 * Reads either PSI representation of a map entry. Reflection is restricted to
	 * this adapter because older IDEs expose MapEntry rather than NamedArgument.
	 */
	static PsiElement mapEntryPart(PsiElement entry, boolean key) {
		String simple = entry.getClass().getSimpleName()
		List<String> names
		if (simple.contains("NamedArgument")) names = key ? ["getLabel"] : ["getExpression"]
		else if (simple.contains("MapEntry")) names = key ? ["getKey", "getKeyExpression"] : ["getValue", "getValueExpression"]
		else return null
		for (String name : names) {
			try {
				Object selected = entry.getClass().getMethod(name).invoke(entry)
				if (selected instanceof PsiElement) return (PsiElement) selected
			} catch (NoSuchMethodException ignored) {
				// Try the spelling exposed by another PSI version.
			} catch (ReflectiveOperationException error) {
				Throwable cause = error.getCause()
				if (cause instanceof RuntimeException) throw (RuntimeException) cause
				if (cause instanceof Error) throw (Error) cause
				recover(error)
				return null
			}
		}
		return null
	}

	/** Map generic argument index: 0 for keys, 1 for values, -1 if no position can be determined. */
	static int mapSide(GrListOrMap container, PsiElement position, int offset) {
		PsiElement element = position
		while (element != null && element != container) {
			String simple = element.getClass().getSimpleName()
			if (simple.contains("NamedArgument") || simple.contains("MapEntry")) {
				PsiElement value = mapEntryPart(element, false)
				return value != null && value.getTextRange().contains(offset) ? 1 : 0
			}
			element = element.getParent()
		}
		// Incomplete entries may not have an entry node yet. Locate the current direct entry.
		String text = container.getText()
		int relative = offset - container.getTextRange().getStartOffset()
		if (relative < 0) return -1
		int depth = 0
		char quote = (char) 0
		int entryStart = 0
		for (int i = 0; i < Math.min(relative, text.length()); i++) {
			char c = text.charAt(i)
			if (quote != 0) {
				if (c == quote && (i == 0 || text.charAt(i - 1) != '\\')) quote = (char) 0
				continue
			}
			if (c == '\'' || c == '"') { quote = c; continue }
			if (c == '[' || c == '(' || c == '{') depth++
			else if (c == ']' || c == ')' || c == '}') depth--
			else if (c == ',' && depth == 1) entryStart = i + 1
		}
		int colon = text.indexOf(':', entryStart)
		return colon >= 0 && colon < relative ? 1 : 0
	}

	/**
	 * The component type of a varargs declaration, or null when the
	 * method is not varargs. Handles both PSI shapes: an ellipsis type
	 * ({@code T...}) and a plain array with {@code isVarArgs()}
	 * ({@code T[]}), which is how some Groovy varargs surface.
	 */
	static PsiType varargComponentType(PsiMethod method) {
		try {
			def params = method.getParameterList()?.getParameters()
			if (params == null || params.length == 0) return null
			PsiType last = params[params.length - 1].getType()
			if (last instanceof PsiEllipsisType) {
				try {
					return ((PsiEllipsisType) last).getComponentType()
				} catch (Exception error) { recover(error); return null }
			}
			boolean vargs = false
			try {
				vargs = method.isVarArgs()
			} catch (Exception error) { recover(error) }
			if (vargs) {
				try {
					if (last.getArrayDimensions() > 0) return last.getDeepComponentType()
				} catch (Exception error) { recover(error) }
			}
		} catch (Exception error) { recover(error) }
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
				} catch (Exception error) { recover(error) }
			}
		} catch (Exception error) { recover(error) }
		// (per-variant detail omitted; count below tells enough)
		debug("namedVariants count=" + out.size())
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
					} catch (Exception error) { recover(error) }
				}
			}
		} catch (Exception error) { recover(error) }
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
		} catch (Exception error) { recover(error) }
		for (PsiClass cls : classes) {
			try {
				PsiMethod[] found = cls.findMethodsByName(name, true)
				if (found != null) {
					debug("class overloads=" + found.length)
					out.addAll(Arrays.asList(found))
				}
			} catch (Exception error) { recover(error) }
		}
		return out
	}

	/**
	 * Infers the target of a string cast containing the caret. PSI shapes are tried
	 * before an offset-checked text fallback, so unrelated nearby casts cannot trigger suggestions.
	 */
	String getCoercedSpec(PsiElement position, int offset) {
		project = position.getProject()
		GrLiteral lit = null
		try {
			lit = PsiTreeUtil.getParentOfType(position, GrLiteral.class, false)
			if (lit == null || !lit.getTextRange().contains(offset)) return null
			try {
				if (!lit.isString()) return null
			} catch (Exception error) { recover(error) }
		} catch (Exception error) { recover(error); return null }
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
		} catch (Exception error) { recover(error) }
		try {
			PsiElement e = lit
			for (int i = 0; i < 12 && e != null && !(e instanceof PsiFile); i++) {
				e = e.getParent()
				if (e instanceof GrTypeCastExpression || e instanceof GrSafeCastExpression) {
					GrExpression operand = e instanceof GrSafeCastExpression
						? ((GrSafeCastExpression) e).getOperand() : ((GrTypeCastExpression) e).getOperand()
					if (operand == null || !operand.getTextRange().contains(offset)) return null
					GrTypeElement castType = e instanceof GrSafeCastExpression
						? ((GrSafeCastExpression) e).getCastTypeElement() : ((GrTypeCastExpression) e).getCastTypeElement()
					if (castType == null) return null
					try {
						PsiType t = castType.getType()
						if (t != null) {
							String s = classifyType(t)
							if (s != null) return s
						}
					} catch (Exception error) { recover(error) }
					try {
						String rt = castType.getText()?.trim()
						if (rt != null) {
							String textType = normalizeType(rt)
							String supported = resolveSupportedType(textType)
							if (supported != null) return supported
						}
					} catch (Exception error) { recover(error) }
					return null
				}
			}
		} catch (Exception error) { recover(error) }
		try {
			PsiElement e = lit
			for (int i = 0; i < 12 && e != null && !(e instanceof PsiFile); i++) {
				try {
					String text = e.getText()
					int base = e.getTextRange().getStartOffset()
					Matcher m = AS_CAST_TEXT.matcher(text)
					while (m.find()) {
						if (base + m.start() <= offset && offset <= base + m.end()) {
							String textType = resolveSupportedType(m.group(1))
							if (textType != null) return textType
						}
					}

				} catch (Exception error) { recover(error) }
				e = e.getParent()
			}
		} catch (Exception error) { recover(error) }
		return null
	}
}

/**
 * Text and range helpers used after IntelliJ's default lookup insertion.
 * Ranges are document offsets in committed PSI; null means only the inserted lookup span is known.
 */
@CompileStatic
class TypeSuggestionInsertion {
	private static final Pattern AS_AFTER = Pattern.compile("as\\s+([\\w.\$]+(?:\\s*<[^>]+>)?(?:\\[\\])?)")

	/** Produces a double-quoted Groovy string with interpolation and control characters escaped. */
	static String quoteSuggestion(String id) {
		StringBuilder out = new StringBuilder('"')
		for (int i = 0; i < id.length(); i++) {
			char c = id.charAt(i)
			switch (c) {
				case '\\': out.append("\\\\"); break
				case '"': out.append("\\\""); break
				case '$': out.append('\\').append('$'); break
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

	/** Returns only the selected key or value, leaving its entry and surrounding map intact. */
	static TextRange mapEntryPartRange(PsiElement position, int offset, boolean key) {
		GrListOrMap map = PsiTreeUtil.getParentOfType(position, GrListOrMap.class, false)
		if (map == null || !map.isMap()) return null
		PsiElement element = position
		while (element != null && element != map) {
			PsiElement selected = TypeSuggestionPsi.mapEntryPart(element, key)
			if (selected != null && selected.getTextRange().contains(offset)) return selected.getTextRange()
			element = element.getParent()
		}
		return null
	}

	/** Returns the direct list element containing the offset, rather than the whole list argument. */
	static TextRange listElementPartRange(PsiElement position, int offset) {
		GrListOrMap list = PsiTreeUtil.getParentOfType(position, GrListOrMap.class, false)
		if (list == null || list.isMap()) return null
		PsiElement element = position
		while (element != null && element.getParent() != list) element = element.getParent()
		return element instanceof GrExpression && element.getTextRange().contains(offset) ? element.getTextRange() : null
	}

	/** Prefix inside a string, excluding completion's dummy identifier and interpolation; empty outside literals. */
	static String literalPrefix(PsiElement position, int offset) {
		GrLiteral literal = PsiTreeUtil.getParentOfType(position, GrLiteral.class, false)
		if (literal == null || !literal.getTextRange().contains(offset)) return ""
		String text = literal.getText()
		int opening = text.startsWith('"""') || text.startsWith("'''") ? 3 : 1
		int relative = offset - literal.getTextRange().getStartOffset()
		if (text.length() < opening + 1 || relative <= opening) return ""
		String prefix = text.substring(opening, Math.min(relative, text.length()))
		int dummy = prefix.toLowerCase(Locale.ROOT).indexOf("intellijidearulezzz")
		if (dummy >= 0) prefix = prefix.substring(0, dummy)
		int interpolation = prefix.indexOf('$')
		return interpolation >= 0 ? prefix.substring(0, interpolation) : prefix
	}

	/** Text-based to retain a user's explicit cast regardless of the IDE's cast PSI representation. */
	static boolean hasFollowingCast(String text, int endOffset) {
		int index = endOffset
		while (index < text.length() && Character.isWhitespace(text.charAt(index))) index++
		return AS_AFTER.matcher(text.substring(index, Math.min(index + 80, text.length()))).lookingAt()
	}

	/**
	 * Selects the narrowest replaceable container part, otherwise the enclosing argument
	 * or literal. An incomplete container uses the lookup span so siblings survive.
	 */
	static TextRange replacementRange(PsiElement position, int offset, String text, TextRange inserted) {
		GrLiteral literal = PsiTreeUtil.getParentOfType(position, GrLiteral.class, false)
		if (literal != null && literal.isString() && hasFollowingCast(text, literal.getTextRange().getEndOffset())) {
			return literal.getTextRange()
		}
		TextRange key = mapEntryPartRange(position, offset, true)
		if (key != null) return key
		TextRange value = mapEntryPartRange(position, offset, false)
		if (value != null) return value
		TextRange listElement = listElementPartRange(position, offset)
		if (listElement != null) return listElement
		if (PsiTreeUtil.getParentOfType(position, GrListOrMap.class, false) != null) return inserted
		PsiElement element = position
		while (element != null && !(element instanceof PsiFile)) {
			if (element instanceof GrExpression && element.getParent() instanceof GrArgumentList) return element.getTextRange()
			element = element.getParent()
		}
		return literal != null && literal.isString() ? literal.getTextRange() : inserted
	}
}

/**
 * Replaces IntelliJ's inserted lookup text with one quoted identifier in a write action.
 * The original use-site range follows the default insertion through a smart pointer.
 * If it is unavailable, query committed PSI inside the inserted span. Commit after
 * editing and move both the insertion tail and editor caret to the replacement end.
 */
@CompileStatic
class TypeSuggestionInsertHandler implements InsertHandler<LookupElement> {
	private final SmartPsiFileRange target

	TypeSuggestionInsertHandler(SmartPsiFileRange target = null) {
		this.target = target
	}

	@Override
	void handleInsert(InsertionContext context, LookupElement item) {
		if (sideForFile(context.getFile()) == null) return
		String id = item.getObject() instanceof String ? (String) item.getObject() : item.getLookupString()
		if (id == null || id.isEmpty()) return
		context.commitDocument()
		int start = context.getStartOffset()
		int tail = context.getTailOffset()
		int length = context.getDocument().getTextLength()
		if (start < 0 || tail < start || tail > length) return
		int offset = tail > start ? tail - 1 : Math.min(tail, length - 1)
		PsiElement position = offset < 0 ? null : context.getFile().findElementAt(offset)
		Segment anchored = target?.getRange()
		TextRange inserted = new TextRange(start, tail)
		// Smart ranges may exclude an insertion made exactly at their end (a map key).
		TextRange range = anchored != null && anchored.getStartOffset() <= start && anchored.getEndOffset() >= start
			? new TextRange(anchored.getStartOffset(), Math.max(anchored.getEndOffset(), tail))
			: TypeSuggestionInsertion.replacementRange(position, offset, context.getDocument().getText(), inserted)
		if (range.getStartOffset() > start || range.getEndOffset() < tail) range = inserted
		String replacement = TypeSuggestionInsertion.quoteSuggestion(id)
		context.getDocument().replaceString(range.getStartOffset(), range.getEndOffset(), replacement)
		int end = range.getStartOffset() + replacement.length()
		context.setTailOffset(end)
		context.getEditor().getCaretModel().moveToOffset(end)
		context.commitDocument()
	}
}

/** Coordinates one immutable catalog, PSI inference and lookup rendering for each BASIC completion. */
@CompileStatic
class TypeSuggestionCompletionProvider extends CompletionProvider<CompletionParameters> {
	@Override
	protected void addCompletions(CompletionParameters parameters, ProcessingContext context, CompletionResultSet result) {
		long started = System.nanoTime()
		if (sideForFile(parameters.getOriginalFile()) == null) return
		TypeSuggestionCatalog.Snapshot catalog = TypeSuggestionCatalog.loadConfig(parameters.getOriginalFile())
		PsiElement position = parameters.getPosition()
		int offset = parameters.getOffset()
		long inferenceStarted = System.nanoTime()
		TypeSuggestionPsi psi = new TypeSuggestionPsi(catalog, position.getProject())
		List<String> builders = psi.builderSuggestions(position, offset)
		List<String> types = builders == null ? psi.completionTypes(position, offset) : []
		List<List<String>> suggestionGroups = builders == null ? types.collect { String type -> psi.suggestionsForType(type) } : [builders]
		long inferenceNanos = System.nanoTime() - inferenceStarted
		if (suggestionGroups.isEmpty() || suggestionGroups.every { List<String> group -> group.isEmpty() }) return
		int originalOffset = Math.max(0, offset - 1)
		PsiElement originalPosition = parameters.getOriginalFile().findElementAt(originalOffset)
		TextRange targetRange = TypeSuggestionInsertion.replacementRange(originalPosition, originalOffset,
			parameters.getOriginalFile().getText(), new TextRange(offset, offset))
		SmartPsiFileRange target = targetRange.isEmpty() ? null : SmartPointerManager.getInstance(parameters.getOriginalFile().getProject())
			.createSmartPsiFileRangePointer(parameters.getOriginalFile(), targetRange)
		InsertHandler<LookupElement> insert = new TypeSuggestionInsertHandler(target)
		CompletionResultSet sink = result.withPrefixMatcher(TypeSuggestionInsertion.literalPrefix(position, offset))
		File root = TypeSuggestionIcons.projectRoot(parameters.getOriginalFile())
		Map<String, String> iconFileStates = new HashMap<String, String>()
		int considered = 0
		int offered = 0
		long iconNanos = 0L
		for (List<String> group : suggestionGroups) {
			for (String id : group) {
				ProgressManager.checkCanceled()
				considered++
				// IDEA requires valid line separators in lookup strings. Keep the raw id
				// as the lookup object, while escaped text is safe for its default insertion.
				String quoted = TypeSuggestionInsertion.quoteSuggestion(id)
				String lookup = quoted.substring(1, quoted.length() - 1)
				LookupElementBuilder element = LookupElementBuilder.create(id, lookup).withInsertHandler(insert)
				if (!sink.getPrefixMatcher().prefixMatches(element)) continue
				long iconStarted = System.nanoTime()
				Icon icon = TypeSuggestionIcons.iconFor(id, types, root, catalog, iconFileStates)
				iconNanos += System.nanoTime() - iconStarted
				if (icon != null) element = element.withIcon(icon)
				sink.addElement(element)
				offered++
			}
		}
		debugTiming("completion types=" + types.size() + " considered=" + considered + " suggestions=" + offered
			+ " inference=" + elapsedNanos(inferenceNanos) + " icons=" + elapsedNanos(iconNanos)
			+ " total=" + elapsed(started) + " preload=" + TypeSuggestionIcons.projectIconPreloadStatus(root)
			+ " " + TypeSuggestionIcons.iconCacheSizes())
	}
}

/** Adds BASIC completion items; the provider scopes use-sites without filtering other contributors. */
@CompileStatic
class TypeSuggestionCompletionContributor extends CompletionContributor {
	TypeSuggestionCompletionContributor() {
		extend(CompletionType.BASIC, PlatformPatterns.psiElement(), new TypeSuggestionCompletionProvider())
	}
}

// LivePlugin executes only this body. Disposal unregisters the contributor on reload.
TypeSuggestionIcons.setIconDir(new File(pluginPath?.toString() ?: "", "icons").getAbsolutePath())
TypeSuggestionIcons.preloadOpenProjects()
def descriptor = new DefaultPluginDescriptor(PluginId.getId("LivePlugin"), TypeSuggestionCompletionContributor.classLoader)
ExtensionPointName.create("com.intellij.completion.contributor").point.registerExtension(
	new CompletionContributorEP("Groovy", TypeSuggestionCompletionContributor.name, descriptor),
	LoadingOrder.FIRST, pluginDisposable)
