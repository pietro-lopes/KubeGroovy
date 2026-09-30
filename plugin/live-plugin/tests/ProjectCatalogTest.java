package dev.uncandango.kubegroovy.intellij;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Uses the real JSON readers with separate project roots and no installed-file overrides. */
public final class ProjectCatalogTest extends LivePluginTestCase {
	private record Catalog(String folder, String support, String filename, String loadMethod) {}

	private static final List<Catalog> CATALOGS = List.of(
		new Catalog("method-aliases", "MethodAliasSupport", "aliases.json", "load"),
		new Catalog("object-overload", "ObjectOverloadSupport", "overloads.json", "load"),
		new Catalog("bindings", "BindingsSupport", "bindings.json", "load"),
		new Catalog("events", "EventMethodSupport", "events.json", "load"),
		new Catalog("type-suggestions", "TypeSuggestionCatalog", "suggestions.json", "loadConfig"));

	public void testCatalogsStayWithTheirProjectAndReloadLocalChanges() throws Exception {
		Path root = Files.createTempDirectory("kgy-project-catalogs");
		try {
			PsiElement first = projectPlace(root.resolve("first"));
			PsiElement other = projectPlace(root.resolve("other"));
			PsiElement missing = projectPlace(root.resolve("missing"));
			for (Catalog catalog : CATALOGS) {
				Class<?> support = loadScript(catalog.folder()).loadClass(catalog.support());
				Path firstFile = writeCatalog(root.resolve("first"), catalog, "first");
				writeCatalog(root.resolve("other"), catalog, "other");
				// Both files have identical timestamps and lengths: only the project path distinguishes them.
				Object firstSnapshot = load(support, catalog, first);
				assertEquals(catalog.folder(), List.of("first"), values(catalog, firstSnapshot));
				assertEquals(catalog.folder(), List.of("other"), values(catalog, load(support, catalog, other)));
				assertSame(catalog.folder(), firstSnapshot, load(support, catalog, first));
				assertTrue(catalog.folder(), values(catalog, load(support, catalog, missing)).isEmpty());
				assertTrue(catalog.folder(), values(catalog, load(support, catalog, null)).isEmpty());

				// Detect changed length even if a quick rewrite keeps the timestamp unchanged.
				writeCatalog(root.resolve("first"), catalog, "updated-first");
				Object updated = load(support, catalog, first);
				assertEquals(catalog.folder(), List.of("updated-first"), values(catalog, updated));
				assertEquals(catalog.folder(), List.of("other"), values(catalog, load(support, catalog, other)));

				Files.writeString(firstFile, "{");
				assertSame(catalog.folder() + " malformed update", updated, load(support, catalog, first));
				Files.delete(firstFile);
				assertSame(catalog.folder() + " missing update", updated, load(support, catalog, first));
				writeCatalog(root.resolve("missing"), catalog, "created-later");
				assertEquals(catalog.folder(), List.of("created-later"), values(catalog, load(support, catalog, missing)));
			}
		} finally {
			deleteTree(root);
		}
	}

	public void testOverloadAliasesUseTheCallSitesProject() throws Exception {
		Path root = Files.createTempDirectory("kgy-project-aliases");
		try {
			Catalog catalog = CATALOGS.getFirst();
			writeCatalog(root.resolve("first"), catalog, "first");
			writeCatalog(root.resolve("other"), catalog, "other");
			Class<?> support = loadScript("object-overload").loadClass("ObjectOverloadSupport");
			var aliases = support.getMethod("aliasesFor", String.class, PsiElement.class);
			assertEquals(Map.of("first", "real"), aliases.invoke(null, "example.Widget", projectPlace(root.resolve("first"))));
			assertEquals(Map.of("other", "real"), aliases.invoke(null, "example.Widget", projectPlace(root.resolve("other"))));
			assertEquals(Map.of(), aliases.invoke(null, "example.Widget", projectPlace(root.resolve("missing"))));
			assertEquals(Map.of("first", "real"), aliases.invoke(null, "example.Widget", projectPlace(root.resolve("first"))));
		} finally {
			deleteTree(root);
		}
	}

	public void testSuggestionGenerationsIsolateProjectDelegationRules() throws Exception {
		Path root = Files.createTempDirectory("kgy-project-delegates");
		try {
			ClassLoader loader = loadScript("type-suggestions");
			Class<?> support = loader.loadClass("TypeSuggestionCatalog");
			Catalog catalog = CATALOGS.getLast();
			Path firstFile = writeCatalog(root.resolve("first"), catalog, "first");
			Path otherFile = writeCatalog(root.resolve("other"), catalog, "other");
			Files.writeString(firstFile, """
				{"schemaVersion":1,"values":{},"delegates":[
				 {"source":"example.Wrapper","target":"net.minecraft.world.item.Item","prefix":""}],"genericDelegates":{}}
				""");
			Files.writeString(otherFile, Files.readString(firstFile).replace("world.item.Item", "world.level.material.Fluid"));
			Object first = load(support, catalog, projectPlace(root.resolve("first")));
			Object other = load(support, catalog, projectPlace(root.resolve("other")));
			assertNotSame(first, other);
			assertFalse(first.getClass().getMethod("getGeneration").invoke(first)
				.equals(other.getClass().getMethod("getGeneration").invoke(other)));
			var iconPath = loader.loadClass("TypeSuggestionIcons").getMethod("exportedIconRelativePath",
				String.class, String.class, first.getClass());
			assertEquals("item/minecraft/stone.png", iconPath.invoke(null, "minecraft:stone", "example.Wrapper", first));
			assertEquals("fluid/minecraft/stone.png", iconPath.invoke(null, "minecraft:stone", "example.Wrapper", other));
			assertEquals("item/minecraft/stone.png", iconPath.invoke(null, "minecraft:stone", "example.Wrapper", first));
		} finally {
			deleteTree(root);
		}
	}

	private static Object load(Class<?> support, Catalog catalog, PsiElement place) throws Exception {
		return support.getMethod(catalog.loadMethod(), PsiElement.class).invoke(null, place);
	}

	@SuppressWarnings("unchecked")
	private static List<String> values(Catalog catalog, Object snapshot) throws Exception {
		if (catalog.folder().equals("type-suggestions")) {
			return (List<String>) snapshot.getClass().getMethod("suggestionsForType", String.class).invoke(snapshot, "example.Value");
		}
		if (catalog.folder().equals("object-overload")) {
			return List.copyOf((Collection<String>) snapshot.getClass().getMethod("getTargets").invoke(snapshot));
		}
		List<?> entries = switch (catalog.folder()) {
			case "method-aliases" -> (List<?>) snapshot.getClass().getMethod("forClass", String.class).invoke(snapshot, "example.Widget");
			case "bindings" -> (List<?>) snapshot.getClass().getMethod("forSide", String.class).invoke(snapshot, "server");
			case "events" -> (List<?>) snapshot.getClass().getMethod("forGroup", String.class, String.class).invoke(snapshot, "server", "$ServerEvents");
			default -> throw new AssertionError(catalog.folder());
		};
		var result = new java.util.ArrayList<String>();
		for (Object entry : entries) {
			result.add((String) entry.getClass().getMethod(catalog.folder().equals("method-aliases") ? "getTo" : "getName").invoke(entry));
		}
		return result;
	}

	private static Path writeCatalog(Path project, Catalog catalog, String value) throws Exception {
		Path file = project.resolve("local/kubegroovy/live-plugin").resolve(catalog.folder()).resolve(catalog.filename());
		Files.createDirectories(file.getParent());
		String json = switch (catalog.folder()) {
			case "method-aliases" -> "{\"aliases\":[{\"class\":\"example.Widget\",\"from\":\"real\",\"to\":\"%s\"}]}";
			case "object-overload" -> "{\"targets\":[\"%s\"]}";
			case "bindings" -> "{\"server\":[{\"name\":\"%s\",\"type\":\"example.Value\"}]}";
			case "events" -> "{\"server\":{\"$ServerEvents\":[{\"name\":\"%s\"}]}}";
			case "type-suggestions" -> "{\"schemaVersion\":1,\"values\":{\"example.Value\":[\"%s\"]},\"delegates\":[],\"genericDelegates\":{}}";
			default -> throw new AssertionError(catalog.folder());
		};
		Files.writeString(file, json.formatted(value));
		Files.setLastModifiedTime(file, FileTime.fromMillis(1_900_000_000_000L));
		return file;
	}

	private static PsiElement projectPlace(Path root) {
		Project project = (Project) Proxy.newProxyInstance(Project.class.getClassLoader(), new Class<?>[]{Project.class},
			(proxy, method, args) -> method.getName().equals("getBasePath") ? root.toString() : null);
		return (PsiElement) Proxy.newProxyInstance(PsiElement.class.getClassLoader(), new Class<?>[]{PsiElement.class},
			(proxy, method, args) -> method.getName().equals("getProject") ? project : null);
	}

	private static void deleteTree(Path root) throws Exception {
		try (var paths = Files.walk(root)) {
			for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
		}
	}
}