package dev.uncandango.kubegroovy.intellij;

import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class LivePluginScopeTest extends LivePluginTestCase {
	public void testEveryScriptUsesExactGroovyScope() throws Exception {
		String[][] scripts = {
			{"bindings", "BindingsSupport"}, {"events", "EventMethodSupport"},
			{"method-aliases", "MethodAliasSupport"}, {"object-overload", "ObjectOverloadSupport"},
			{"type-suggestions", "TypeSuggestionSupport"}, {"info-docs", "InfoDocSupport"}
		};
		for (String[] script : scripts) {
			var scope = loadScript(script[0]).loadClass(script[1]).getMethod("sideForFile", PsiFile.class);
			for (String side : new String[]{"startup", "client", "server"}) {
				PsiFile file = myFixture.addFileToProject("scope/" + script[0] + "/kubegroovy/" + side + "_scripts/nested/check.groovy", "def x = 1");
				assertEquals(script[0], side, scope.invoke(null, file));
				assertEquals(script[0] + " completion copy", side, scope.invoke(null, file.copy()));
			}
			for (String path : new String[]{"src/Main.java", "kubegroovy/startup_scripts/Main.java",
				"src/check.groovy", "startup_scripts/check.groovy", "otherkubegroovy/startup_scripts/check.groovy",
				"kubegroovy/startup_scripts_backup/check.groovy", "kubegroovy/other/startup_scripts/check.groovy",
				"kubegroovy/startup_scripts/check.gradle", "kubegroovy/server_scripts.groovy"}) {
				PsiFile file = myFixture.addFileToProject("scope/" + script[0] + "/" + path, "");
				assertNull(script[0] + ": " + path, scope.invoke(null, file));
			}
			assertNull(scope.invoke(null, new Object[]{null}));
		}
	}

	public void testSuggestionCatalogRecursesWithoutDeduplicatingAndKeepsLastValidReload() throws Exception {
		ClassLoader loader = loadScript("type-suggestions");
		Class<?> insertion = loader.loadClass("TypeSuggestionInsertion");
		Path config = Files.createTempFile("kgy-type-suggestions", ".json");
		try {
			Files.writeString(config, """
				{"schemaVersion":1,
				 "values":{"example.Item":["addon:first","addon:first"],"example.Tag":["addon:tag"],
				           "example.TagKey<example.Item>":["addon:item-tag"],
				           "example.Exact<example.Item>":["exact-only"],"example.Raw":["raw-type"],
				           "example.CycleB":["addon:cycle"]},
				 "delegates":[
				   {"source":"example.ItemStack","target":"example.Item","prefix":""},
				   {"source":"example.Ingredient","target":"example.Tag","prefix":"#"},
				   {"source":"example.CycleA","target":"example.CycleB","prefix":""},
				   {"source":"example.CycleB","target":"example.CycleA","prefix":""}],
				 "genericDelegates":{"java.util.Optional":0,"example.Holder":0}}
				""");
			setSuggestionConfig(loader, config.toFile());
			var nested = suggestions(loader, "java.util.Optional < example.Holder < example.Item > >");
			assertEquals(List.of("addon:first", "addon:first"), nested);
			assertEquals(List.of(), suggestions(loader, "example.Exact<example.Other>"));
			assertEquals(List.of("raw-type"), suggestions(loader, "example.Raw<example.Other>"));
			assertEquals(List.of(), suggestions(loader, "java.util.Optional"));
			assertEquals(List.of("#addon:tag"), suggestions(loader, "example.Ingredient"));
			assertEquals(List.of("addon:cycle"), suggestions(loader, "example.CycleA"));
			PsiFile castFieldFile = myFixture.addFileToProject("kubegroovy/server_scripts/ItemStackFieldCast.groovy",
				"def itemStackField = \"\" as ItemStack");
			int castOffset = castFieldFile.getText().indexOf("\"\"") + 1;
			String castSpec = coercedSpec(loader, castFieldFile.findElementAt(castOffset), castOffset);
			assertEquals("example.ItemStack", castSpec);
			assertEquals(List.of("addon:first", "addon:first"), suggestions(loader, castSpec));
			assertEquals(true, insertion.getMethod("hasFollowingCast", String.class, int.class)
				.invoke(null, castFieldFile.getText(), castFieldFile.getText().indexOf("\"\"") + 2));
			PsiFile optionalItemFile = myFixture.addFileToProject("kubegroovy/server_scripts/OptionalItemCast.groovy",
				"def item = \"\" as Optional<Item>");
			int optionalItemOffset = optionalItemFile.getText().indexOf("\"\"") + 1;
			String optionalItemSpec = coercedSpec(loader, optionalItemFile.findElementAt(optionalItemOffset), optionalItemOffset);
			assertEquals("java.util.Optional<example.Item>", optionalItemSpec);
			assertEquals(List.of("addon:first", "addon:first"), suggestions(loader, optionalItemSpec));
			PsiFile itemTagFile = myFixture.addFileToProject("kubegroovy/server_scripts/ItemTagCast.groovy",
				"def itemTag = \"\" as TagKey<Item>");
			int itemTagOffset = itemTagFile.getText().indexOf("\"\"") + 1;
			String itemTagSpec = coercedSpec(loader, itemTagFile.findElementAt(itemTagOffset), itemTagOffset);
			assertEquals("example.TagKey<example.Item>", itemTagSpec);
			assertEquals(List.of("addon:item-tag"), suggestions(loader, itemTagSpec));
			Files.writeString(config, "{ malformed");
			Files.setLastModifiedTime(config, FileTime.fromMillis(System.currentTimeMillis() + 2000));
			assertEquals(List.of("addon:first", "addon:first"), suggestions(loader, "example.Item"));
			assertEquals("\"a\\\"\\\\\\$\\n\"", insertion.getMethod("quoteSuggestion", String.class)
				.invoke(null, "a\"\\$\n"));
		} finally {
			Files.deleteIfExists(config);
		}
	}

	public void testNestedMapAndListPositionsUseTheMatchingGenericType() throws Exception {
		ClassLoader loader = loadScript("type-suggestions");
		Class<?> support = loader.loadClass("TypeSuggestionPsi");
		Class<?> insertion = loader.loadClass("TypeSuggestionInsertion");
		Path config = Files.createTempFile("kgy-type-suggestions-map", ".json");
		try {
			Files.writeString(config, """
				{"schemaVersion":1,
				 "values":{"java.lang.String":["key-choice"],"example.Item":["item-choice"]},
				 "delegates":[],"genericDelegates":{"example.MapTypes.Box":0,"example.MapTypes.Outer":0}}
				""");
			setSuggestionConfig(loader, config.toFile());
			Object resolver = support.getConstructor().newInstance();
			myFixture.addFileToProject("example/Item.java", "package example; public class Item {}");
			var types = (PsiJavaFile) myFixture.addFileToProject("example/MapTypes.java",
				"package example; public class MapTypes { "
					+ "public static class Box<T> {} public static class Outer<T> {} "
					+ "public void accept(java.util.Map<String, java.util.List<Item>> x) {} "
					+ "public void acceptWrapped(Box<Outer<java.util.Map<String, java.util.List<Item>>>> x) {} "
					+ "public void acceptArray(Item[][] x) {} "
					+ "public void acceptDirect(java.util.Map<String, Item> x) {} "
					+ "public <T> void generic(T x) {} "
					+ "public void overloaded(Object x) {} public void overloaded(Item x) {} "
					+ "public void varargs(Item... x) {} }");
			PsiType expected = types.getClasses()[0].findMethodsByName("accept", false)[0]
				.getParameterList().getParameters()[0].getType();
			PsiFile file = myFixture.addFileToProject(
				"kubegroovy/server_scripts/MapSuggestions.groovy",
				"new example.MapTypes().accept([key: ['leaf']])");
			var containers = PsiTreeUtil.findChildrenOfType(file, GrListOrMap.class).stream()
				.sorted(java.util.Comparator.comparingInt(e -> e.getTextRange().getStartOffset())).toList();
			assertEquals(2, containers.size());
			String text = file.getText();
			int keyOffset = text.indexOf("key") + 1;
			int leafOffset = text.indexOf("leaf") + 1;
			var keyPosition = file.findElementAt(keyOffset);
			var leafPosition = file.findElementAt(leafOffset);
			var typeAtArgument = support.getMethod("typeAtArgument", PsiType.class, PsiElement.class, PsiElement.class, int.class);
			assertEquals("java.lang.String", typeAtArgument.invoke(resolver, expected, containers.get(0), keyPosition, keyOffset));
			assertEquals("example.Item", typeAtArgument.invoke(resolver, expected, containers.get(0), leafPosition, leafOffset));
			var mapPartRange = insertion.getMethod("mapEntryPartRange", PsiElement.class, int.class, boolean.class);
			var keyRange = (com.intellij.openapi.util.TextRange) mapPartRange.invoke(null, keyPosition, keyOffset, true);
			assertEquals("key", text.substring(keyRange.getStartOffset(), keyRange.getEndOffset()));
			assertNull(mapPartRange.invoke(null, leafPosition, leafOffset, true));
			assertNull(mapPartRange.invoke(null, keyPosition, keyOffset, false));
			var listPartRange = insertion.getMethod("listElementPartRange", PsiElement.class, int.class);
			var leafRange = (com.intellij.openapi.util.TextRange) listPartRange.invoke(null, leafPosition, leafOffset);
			assertEquals("'leaf'", text.substring(leafRange.getStartOffset(), leafRange.getEndOffset()));
			assertEquals("new example.MapTypes().accept([\"key-choice\": ['leaf']])",
				text.substring(0, keyRange.getStartOffset()) + "\"key-choice\"" + text.substring(keyRange.getEndOffset()));
			assertEquals("new example.MapTypes().accept([key: [\"item-choice\"]])",
				text.substring(0, leafRange.getStartOffset()) + "\"item-choice\"" + text.substring(leafRange.getEndOffset()));

			PsiType directMapType = types.getClasses()[0].findMethodsByName("acceptDirect", false)[0]
				.getParameterList().getParameters()[0].getType();
			PsiFile directMap = myFixture.addFileToProject("kubegroovy/server_scripts/DirectMap.groovy",
				"new example.MapTypes().acceptDirect([key: 'leaf'])");
			int directValueOffset = directMap.getText().indexOf("leaf") + 1;
			PsiElement directValuePosition = directMap.findElementAt(directValueOffset);
			assertEquals("example.Item", typeAtArgument.invoke(resolver, directMapType,
				PsiTreeUtil.findChildrenOfType(directMap, GrListOrMap.class).iterator().next(), directValuePosition, directValueOffset));
			var directValueRange = (com.intellij.openapi.util.TextRange) mapPartRange.invoke(null, directValuePosition, directValueOffset, false);
			assertEquals("'leaf'", directMap.getText().substring(directValueRange.getStartOffset(), directValueRange.getEndOffset()));

			PsiType wrappedType = types.getClasses()[0].findMethodsByName("acceptWrapped", false)[0]
				.getParameterList().getParameters()[0].getType();
			PsiFile wrappedFile = myFixture.addFileToProject("kubegroovy/server_scripts/WrappedMap.groovy",
				"new example.MapTypes().acceptWrapped([key: ['leaf']])");
			var wrappedContainers = PsiTreeUtil.findChildrenOfType(wrappedFile, GrListOrMap.class).stream()
				.sorted(java.util.Comparator.comparingInt(e -> e.getTextRange().getStartOffset())).toList();
			int wrappedLeafOffset = wrappedFile.getText().indexOf("leaf") + 1;
			assertEquals("example.Item", typeAtArgument.invoke(resolver, wrappedType, wrappedContainers.get(0),
				wrappedFile.findElementAt(wrappedLeafOffset), wrappedLeafOffset));

			PsiType arrayType = types.getClasses()[0].findMethodsByName("acceptArray", false)[0]
				.getParameterList().getParameters()[0].getType();
			PsiFile arrayFile = myFixture.addFileToProject("kubegroovy/server_scripts/ArraySuggestions.groovy",
				"new example.MapTypes().acceptArray([['leaf']])");
			var arrayContainers = PsiTreeUtil.findChildrenOfType(arrayFile, GrListOrMap.class).stream()
				.sorted(java.util.Comparator.comparingInt(e -> e.getTextRange().getStartOffset())).toList();
			int arrayLeafOffset = arrayFile.getText().indexOf("leaf") + 1;
			assertEquals("example.Item", typeAtArgument.invoke(resolver, arrayType, arrayContainers.get(0),
				arrayFile.findElementAt(arrayLeafOffset), arrayLeafOffset));

			PsiMethod genericMethod = types.getClasses()[0].findMethodsByName("generic", false)[0];
			PsiClass itemClass = JavaPsiFacade.getInstance(getProject())
				.findClass("example.Item", GlobalSearchScope.allScope(getProject()));
			PsiSubstitutor substitutor = PsiSubstitutor.EMPTY.put(genericMethod.getTypeParameters()[0],
				JavaPsiFacade.getElementFactory(getProject()).createType(itemClass));
			var specForParam = support.getMethod("specForParam", PsiMethod.class, int.class, PsiSubstitutor.class,
				PsiElement.class, PsiElement.class, int.class);
			assertEquals("example.Item", specForParam.invoke(resolver, genericMethod, 0, substitutor, null, null, -1));

			PsiFile castFile = myFixture.addFileToProject("kubegroovy/server_scripts/CastSuggestions.groovy",
				"def value = 'leaf' as example.Item");
			int castOffset = castFile.getText().indexOf("leaf") + 1;
			assertEquals("example.Item", coercedSpec(loader, castFile.findElementAt(castOffset), castOffset));

			PsiFile overloadFile = myFixture.addFileToProject("kubegroovy/server_scripts/OverloadSuggestions.groovy",
				"new example.MapTypes().overloaded('leaf')");
			GrReferenceExpression overloadRef = PsiTreeUtil.findChildrenOfType(overloadFile, GrReferenceExpression.class).stream()
				.filter(ref -> "overloaded".equals(ref.getReferenceName())).findFirst().orElseThrow();
			var overloads = (List<PsiMethod>) support.getMethod("classOverloads", GrReferenceExpression.class,
				java.util.Collection.class, String.class).invoke(null, overloadRef, List.of(), "overloaded");
			assertEquals(true, overloads.stream().anyMatch(method -> method.getParameterList().getParameters()[0]
				.getType().equalsToText("example.Item")));
			PsiMethod varargs = types.getClasses()[0].findMethodsByName("varargs", false)[0];
			assertEquals("example.Item", specForParam.invoke(resolver, varargs, 0, null, null, null, -1));
		} finally {
			Files.deleteIfExists(config);
		}
	}

	public void testSuggestionIconsUseProjectExportsTagFoldersAndBindingsLogoFallback() throws Exception {
		ClassLoader loader = loadScript("type-suggestions");
		Class<?> support = loader.loadClass("TypeSuggestionIcons");
		Path config = Files.createTempFile("kgy-type-suggestion-icons", ".json");
		Path projectRoot = Files.createTempDirectory("kgy-type-suggestion-project");
		Path livePlugins = Files.createTempDirectory("kgy-type-suggestion-live-plugins");
		try {
			Files.writeString(config, """
				{"schemaVersion":1,"values":{},
				 "delegates":[
				   {"source":"net.minecraft.world.item.crafting.Ingredient","target":"net.minecraft.world.item.Item","prefix":""},
				   {"source":"net.minecraft.world.item.crafting.Ingredient","target":"net.minecraft.tags.TagKey<net.minecraft.world.item.Item>","prefix":"#"}],
				 "genericDelegates":{"java.util.Optional":0,"net.minecraft.resources.ResourceKey":0}}
				""");
			setSuggestionConfig(loader, config.toFile());
			var relativePath = support.getMethod("exportedIconRelativePath", String.class, String.class);
			assertEquals("item/minecraft/stone.png", relativePath.invoke(null,
				"minecraft:stone", "net.minecraft.world.item.Item"));
			assertEquals("fluid/minecraft/water.png", relativePath.invoke(null,
				"minecraft:water", "net.minecraft.world.level.material.Fluid"));
			assertEquals("tags/item/minecraft/planks.png", relativePath.invoke(null,
				"minecraft:planks", "net.minecraft.tags.TagKey<net.minecraft.world.item.Item>"));
			assertEquals("tags/block/minecraft/mineable.png", relativePath.invoke(null,
				"minecraft:mineable", "net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block>"));
			assertEquals("tags/fluid/minecraft/water.png", relativePath.invoke(null,
				"minecraft:water", "java.util.Optional<net.minecraft.tags.TagKey<net.minecraft.world.level.material.Fluid>>"));
			assertEquals("tags/item/minecraft/planks.png", relativePath.invoke(null,
				"#minecraft:planks", "net.minecraft.world.item.crafting.Ingredient"));

			Path itemIcon = projectRoot.resolve("local/kubegroovy/export/icons-32/item/minecraft/stone.png");
			Files.createDirectories(itemIcon.getParent());
			javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(32, 32,
				java.awt.image.BufferedImage.TYPE_INT_ARGB), "png", itemIcon.toFile());
			Path suggestionPlugin = livePlugins.resolve("type-suggestions");
			Path bindingsPlugin = livePlugins.resolve("bindings");
			Files.createDirectories(suggestionPlugin.resolve("icons"));
			Files.createDirectories(bindingsPlugin);
			Path logo = bindingsPlugin.resolve("logo.png");
			javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(32, 32,
				java.awt.image.BufferedImage.TYPE_INT_ARGB), "png", logo.toFile());
			Path svgLogo = bindingsPlugin.resolve("logo.svg");
			Files.writeString(svgLogo, "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"16\" height=\"16\" viewBox=\"0 0 16 16\"><path fill=\"#4298b8\" d=\"M1 1h14v14H1z\"/></svg>");
			support.getMethod("setIconDir", String.class).invoke(null,
				suggestionPlugin.resolve("icons").toString());

			var iconFiles = support.getMethod("iconFilesForSuggestion", String.class,
				java.util.Collection.class, File.class);
			var itemFiles = (List<File>) iconFiles.invoke(null, "minecraft:stone",
				List.of("net.minecraft.world.item.Item"), projectRoot.toFile());
			assertEquals(itemIcon.toFile().getCanonicalFile(), itemFiles.getFirst());
			var fallbackFiles = (List<File>) iconFiles.invoke(null, "testmod:missing",
				List.of("example.Unmatched"), projectRoot.toFile());
			assertEquals(svgLogo.toFile().getCanonicalFile(), fallbackFiles.getFirst());
			var iconFor = support.getMethod("iconFor", String.class, java.util.Collection.class, File.class);
			Object resolvedItemIcon = iconFor.invoke(null, "minecraft:stone",
				List.of("net.minecraft.world.item.Item"), projectRoot.toFile());
			assertNotNull(resolvedItemIcon);
			assertSame(resolvedItemIcon, iconFor.invoke(null, "minecraft:stone",
				List.of("net.minecraft.world.item.Item"), projectRoot.toFile()));
			assertSame(resolvedItemIcon, iconFor.invoke(null, "minecraft:stone",
				List.of("net.minecraft.resources.ResourceKey<net.minecraft.world.item.Item>"), projectRoot.toFile()));
			Object fallbackIcon = iconFor.invoke(null, "testmod:missing",
				List.of("example.Unmatched"), projectRoot.toFile());
			assertNotNull(fallbackIcon);
			assertEquals(16, ((javax.swing.Icon) fallbackIcon).getIconWidth());
			assertSame(fallbackIcon, iconFor.invoke(null, "testmod:missing",
				List.of("example.Unmatched"), projectRoot.toFile()));
			Path newlyExportedIcon = projectRoot.resolve("local/kubegroovy/export/icons-32/item/testmod/missing.png");
			Files.createDirectories(newlyExportedIcon.getParent());
			javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(32, 32,
				java.awt.image.BufferedImage.TYPE_INT_ARGB), "png", newlyExportedIcon.toFile());
			Object refreshedIcon = iconFor.invoke(null, "testmod:missing",
				List.of("example.Unmatched"), projectRoot.toFile());
			assertNotSame(fallbackIcon, refreshedIcon);

			Object previousCatalog = suggestionCatalog(loader);
			List<String> ingredient = List.of("net.minecraft.world.item.crafting.Ingredient");
			assertSame(resolvedItemIcon, iconFor.invoke(null, "minecraft:stone", ingredient, projectRoot.toFile()));
			Path fluidIcon = projectRoot.resolve("local/kubegroovy/export/icons-32/fluid/minecraft/stone.png");
			Files.createDirectories(fluidIcon.getParent());
			javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(32, 32,
				java.awt.image.BufferedImage.TYPE_INT_ARGB), "png", fluidIcon.toFile());
			Files.writeString(config, """
				{"schemaVersion":1,"values":{},
				 "delegates":[{"source":"net.minecraft.world.item.crafting.Ingredient",
				               "target":"net.minecraft.world.level.material.Fluid","prefix":""}],"genericDelegates":{}}
				""");
			Files.setLastModifiedTime(config, FileTime.fromMillis(System.currentTimeMillis() + 2000));
			Object reloadedIcon = iconFor.invoke(null, "minecraft:stone", ingredient, projectRoot.toFile());
			assertNotSame(resolvedItemIcon, reloadedIcon);
			assertSame(reloadedIcon, iconFor.invoke(null, "minecraft:stone",
				List.of("net.minecraft.world.level.material.Fluid"), projectRoot.toFile()));
			var iconForSnapshot = support.getMethod("iconFor", String.class, java.util.Collection.class,
				File.class, previousCatalog.getClass());
			assertSame(resolvedItemIcon, iconForSnapshot.invoke(null, "minecraft:stone", ingredient,
				projectRoot.toFile(), previousCatalog));
			assertSame(reloadedIcon, iconFor.invoke(null, "minecraft:stone", ingredient, projectRoot.toFile()));
		} finally {
			Files.deleteIfExists(config);
			for (Path directory : List.of(projectRoot, livePlugins)) {
				try (var paths = Files.walk(directory)) {
					paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
						try { Files.deleteIfExists(path); } catch (Exception error) { throw new RuntimeException(error); }
					});
				}
			}
		}
	}

	public void testPublishedCatalogsRemainImmutableAcrossReloads() throws Exception {
		ClassLoader loader = loadScript("type-suggestions");
		Path config = Files.createTempFile("kgy-snapshot-fixture", ".json");
		try {
			Files.writeString(config, """
				{"schemaVersion":1,"values":{"example.Item":["old","old"]},"delegates":[],"genericDelegates":{}}
				""");
			setSuggestionConfig(loader, config.toFile());
			Object first = suggestionCatalog(loader);
			Map<?, ?> values = (Map<?, ?>) first.getClass().getMethod("getValues").invoke(first);
			try {
				values.clear();
				fail("Published values must be immutable");
			} catch (UnsupportedOperationException expected) {
				// A consumer cannot mutate a snapshot used by other completion callbacks.
			}
			try {
				((List<?>) values.get("example.Item")).clear();
				fail("Published value lists must be immutable");
			} catch (UnsupportedOperationException expected) {
				// The collection boundary is immutable too.
			}
			Files.writeString(config, """
				{"schemaVersion":1,"values":{"example.Item":["new"]},"delegates":[],"genericDelegates":{}}
				""");
			Files.setLastModifiedTime(config, FileTime.fromMillis(System.currentTimeMillis() + 2000));
			Object second = suggestionCatalog(loader);
			assertNotSame(first, second);
			assertSame(second, suggestionCatalog(loader));
			assertEquals(List.of("new"), suggestions(loader, "example.Item"));
			assertEquals(List.of("old", "old"), first.getClass().getMethod("suggestionsForType", String.class)
				.invoke(first, "example.Item"));
		} finally {
			Files.deleteIfExists(config);
		}
	}

	public void testSuggestionIconPreloaderLoadsItemFluidBlockAndTagFolders() throws Exception {
		ClassLoader loader = loadScript("type-suggestions");
		Class<?> support = loader.loadClass("TypeSuggestionIcons");
		Path projectRoot = Files.createTempDirectory("kgy-type-suggestion-preload");
		List<Path> icons = List.of(
			projectRoot.resolve("local/kubegroovy/export/icons-32/item/minecraft/stone.png"),
			projectRoot.resolve("local/kubegroovy/export/icons-32/fluid/minecraft/water.png"),
			projectRoot.resolve("local/kubegroovy/export/icons-32/block/testmod/custom.png"),
			projectRoot.resolve("local/kubegroovy/export/icons-32/tags/item/minecraft/planks.png"),
			projectRoot.resolve("local/kubegroovy/export/icons-32/tags/block/minecraft/mineable.png"),
			projectRoot.resolve("local/kubegroovy/export/icons-32/tags/fluid/minecraft/water.png"));
		try {
			for (Path icon : icons) {
				Files.createDirectories(icon.getParent());
				javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(32, 32,
					java.awt.image.BufferedImage.TYPE_INT_ARGB), "png", icon.toFile());
			}
			CompletableFuture<?> preload = (CompletableFuture<?>) support
				.getMethod("preloadProjectIcons", File.class).invoke(null, projectRoot.toFile());
			preload.get(10, TimeUnit.SECONDS);
			var cacheField = support.getDeclaredField("FILE_ICONS");
			cacheField.setAccessible(true);
			Map<?, ?> fileCache = (Map<?, ?>) cacheField.get(null);
			var exportIndexField = support.getDeclaredField("EXPORTED_ICON_FILES");
			exportIndexField.setAccessible(true);
			Map<String, File> exportIndex = (Map<String, File>) exportIndexField.get(null);
			for (Path icon : icons) {
				String prefix = icon.toFile().getCanonicalPath() + "|";
				assertTrue("Expected preloaded icon " + icon, fileCache.keySet().stream()
					.map(Object::toString).anyMatch(key -> key.startsWith(prefix)));
				String relative = projectRoot.resolve("local/kubegroovy/export/icons-32").relativize(icon)
					.toString().replace('\\', '/');
				String indexKey = projectRoot.toFile().getCanonicalPath() + "|icons-32/" + relative;
				assertEquals("Expected indexed export " + icon, icon.toFile().getCanonicalFile(), exportIndex.get(indexKey));
			}
			var iconFor = support.getMethod("iconFor", String.class, java.util.Collection.class, File.class);
			assertNotNull(iconFor.invoke(null, "minecraft:stone", List.of("net.minecraft.world.item.Item"), projectRoot.toFile()));
			assertNotNull(iconFor.invoke(null, "minecraft:planks",
				List.of("net.minecraft.tags.TagKey<net.minecraft.world.item.Item>"), projectRoot.toFile()));
		} finally {
			try (var paths = Files.walk(projectRoot)) {
				paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
					try { Files.deleteIfExists(path); } catch (Exception error) { throw new RuntimeException(error); }
				});
			}
		}
	}

	public void testAliasResolvesOnlyInsideScriptsWithoutChangingJavaClass() throws Exception {
		ClassLoader loader = loadScript("method-aliases");
		Path config = Files.createTempFile("kgy-alias-scope", ".json");
		try {
			Files.writeString(config, "{\"aliases\":[{\"class\":\"example.Widget\",\"from\":\"original\",\"to\":\"alias\"}]}");
			loader.loadClass("MethodAliasSupport").getMethod("setConfigFile", File.class).invoke(null, config.toFile());
			var contributor = (NonCodeMembersContributor) loader.loadClass("MethodAliasContributor").getConstructor().newInstance();
			NonCodeMembersContributor.EP_NAME.getPoint().registerExtension(contributor, getTestRootDisposable());
			var widget = ((PsiJavaFile) myFixture.addFileToProject("example/Widget.java", "package example; public class Widget { public String original() { return null; } }")).getClasses()[0];
			myFixture.addFileToProject("example/Child.java", "package example; public class Child extends Widget {}");
			for (String type : new String[]{"Widget", "Child"}) {
				for (String root : new String[]{"kubegroovy/startup_scripts/", "kubegroovy/client_scripts/", "kubegroovy/server_scripts/", "src/"}) {
					PsiFile file = myFixture.addFileToProject(root + type + ".groovy", "new example." + type + "().alias()");
					var ref = PsiTreeUtil.findChildrenOfType(file, GrReferenceExpression.class).stream()
						.filter(it -> "alias".equals(it.getReferenceName())).findFirst().orElseThrow();
					if (root.startsWith("kubegroovy/")) assertInstanceOf(ref.resolve(), PsiMethod.class);
					else assertNull(ref.resolve());
				}
			}
			assertEquals(0, widget.findMethodsByName("alias", false).length);
			assertEquals(1, widget.findMethodsByName("original", false).length);
		} finally {
			Files.deleteIfExists(config);
		}
	}

	public void testObjectOverloadsStayLocalToScriptCalls() throws Exception {
		ClassLoader loader = loadScript("object-overload");
		Path config = Files.createTempFile("kgy-overload-scope", ".json");
		try {
			Files.writeString(config, "{\"targets\":[\"example.Target\"]}");
			loader.loadClass("ObjectOverloadSupport").getMethod("setConfigFile", File.class).invoke(null, config.toFile());
			var contributor = (NonCodeMembersContributor) loader.loadClass("ObjectOverloadContributor").getConstructor().newInstance();
			NonCodeMembersContributor.EP_NAME.getPoint().registerExtension(contributor, getTestRootDisposable());
			myFixture.addFileToProject("example/Target.java", "package example; public class Target {}");
			myFixture.addFileToProject("java/lang/Object.java", "package java.lang; public class Object {}");
			myFixture.addFileToProject("java/lang/String.java", "package java.lang; public final class String {}");
			myFixture.addFileToProject("java/lang/Class.java", "package java.lang; public final class Class<T> {}");
			var widget = ((PsiJavaFile) myFixture.addFileToProject("example/Widget.java", "package example; public class Widget { public static void take(Target target) {} }")).getClasses()[0];
			myFixture.addFileToProject("example/Child.java", "package example; public class Child extends Widget {}");
			for (String type : new String[]{"Widget", "Child"}) {
				for (String root : new String[]{"kubegroovy/server_scripts/", "src/"}) {
					PsiFile file = myFixture.addFileToProject(root + type + ".groovy", "example." + type + ".take('id')");
					var ref = PsiTreeUtil.findChildrenOfType(file, GrReferenceExpression.class).stream()
						.filter(it -> "take".equals(it.getReferenceName())).findFirst().orElseThrow();
					var contributed = new java.util.ArrayList<com.intellij.psi.PsiElement>();
					contributor.processDynamicElements(com.intellij.psi.JavaPsiFacade.getElementFactory(getProject()).createType(com.intellij.psi.JavaPsiFacade.getInstance(getProject()).findClass("example." + type, file.getResolveScope())),
						new com.intellij.psi.scope.BaseScopeProcessor() {
							@Override
							public boolean execute(com.intellij.psi.PsiElement element, com.intellij.psi.ResolveState state) {
								contributed.add(element);
								return true;
							}
						}, ref, com.intellij.psi.ResolveState.initial());
					assertEquals("Direct contributions: " + root + type, root.startsWith("kubegroovy/") ? 1 : 0, contributed.size());
					if (!contributed.isEmpty()) {
						PsiMethod method = (PsiMethod) contributed.getFirst();
						assertEquals("java.lang.Object", method.getParameterList().getParameters()[0].getType().getCanonicalText());
						assertTrue(method.hasModifierProperty("static"));
					}
				}
			}
			assertEquals(1, widget.findMethodsByName("take", false).length);
			assertEquals("example.Target", widget.findMethodsByName("take", false)[0].getParameterList().getParameters()[0].getType().getCanonicalText());
			var constructed = ((PsiJavaFile) myFixture.addFileToProject("example/Constructed.java", "package example; public class Constructed { public Constructed(Target target) {} }")).getClasses()[0];
			for (String root : new String[]{"kubegroovy/server_scripts/", "src/"}) {
				PsiFile file = myFixture.addFileToProject(root + "constructor.groovy", "new example.Constructed('id')");
				var candidates = org.jetbrains.plugins.groovy.lang.resolve.impl.ConstructorsKt.getAllConstructors(constructed, file);
				boolean hasObject = candidates.stream().anyMatch(method -> "java.lang.Object".equals(method.getParameterList().getParameters()[0].getType().getCanonicalText()));
				assertEquals(root, root.startsWith("kubegroovy/"), hasObject);
			}
		} finally {
			Files.deleteIfExists(config);
		}
	}
}
