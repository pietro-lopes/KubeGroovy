package dev.uncandango.kubegroovy.intellij;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.completion.CompletionContributorEP;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.extensions.DefaultPluginDescriptor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.LoadingOrder;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Exercises the actual contributor and insert handler, rather than assembling replacements from helper ranges. */
public final class TypeSuggestionCompletionTest extends LivePluginTestCase {
	private ClassLoader loader;
	private Path config;
	private int fileNumber;

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		loader = loadScript("type-suggestions");
		config = Files.createTempFile("kgy-completion-fixture", ".json");
		writeCatalog(Map.of("example.Item", List.of("addon:apple", "addon:stone"),
			"java.lang.String", List.of("keyChoice", "otherKey")));
		var descriptor = new DefaultPluginDescriptor(PluginId.getId("LivePlugin"), loader);
		ExtensionPointName.<CompletionContributorEP>create("com.intellij.completion.contributor").getPoint()
			.registerExtension(new CompletionContributorEP("Groovy", "TypeSuggestionCompletionContributor", descriptor),
				LoadingOrder.FIRST, getTestRootDisposable());
		boolean autoComplete = CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION;
		CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = false;
		Disposer.register(getTestRootDisposable(), () ->
			CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = autoComplete);
		myFixture.addFileToProject("example/Item.java", "package example; public class Item {}");
		myFixture.addFileToProject("example/Target.java", """
			package example;
			public class Target {
				public void accept(Item item) {}
				public void overloaded(Object item) {}
				public void overloaded(Item item) {}
				public void list(java.util.List<Item> items) {}
				public void map(java.util.Map<String, Item> items) {}
				public void nested(java.util.Map<String, java.util.List<Item>> items) {}
				public void array(Item[][] items) {}
				public void varargs(Item... items) {}
			}
			""");
		myFixture.addFileToProject("example/Child.java", "package example; public class Child extends Target {}");
	}

	@Override
	protected void tearDown() throws Exception {
		try {
			if (config != null) Files.deleteIfExists(config);
		} finally {
			super.tearDown();
		}
	}

	private void writeCatalog(Map<String, List<String>> values) throws Exception {
		String json = (String) Class.forName("groovy.json.JsonOutput").getMethod("toJson", Object.class)
			.invoke(null, Map.of("schemaVersion", 1, "values", values, "delegates", List.of(),
				"genericDelegates", Map.of("java.util.Optional", 0)));
		Files.writeString(config, json);
		setSuggestionConfig(loader, config.toFile());
	}

	private LookupElement[] complete(String path, String text) {
		int offset = text.indexOf("<caret>");
		assertTrue("Caret marker required", offset >= 0);
		PsiFile file = myFixture.addFileToProject(path, text.replace("<caret>", ""));
		myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
		myFixture.getEditor().getCaretModel().moveToOffset(offset);
		return myFixture.complete(CompletionType.BASIC);
	}

	private void choose(String source, String id, String expected) {
		LookupElement[] elements = complete("kubegroovy/server_scripts/Completion" + fileNumber++ + ".groovy", source);
		assertNotNull("Expected a completion popup", elements);
		LookupElement selected = Arrays.stream(elements).filter(item -> id.equals(item.getObject()))
			.findFirst().orElseThrow(() -> new AssertionError("Missing " + id + " in " +
				source + ": " + Arrays.stream(elements).limit(20).map(LookupElement::getLookupString).toList()));
		myFixture.getLookup().setCurrentItem(selected);
		myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);
		assertEquals(expected.replace("<caret>", ""), myFixture.getEditor().getDocument().getText());
		assertEquals(expected.indexOf("<caret>"), myFixture.getEditor().getCaretModel().getOffset());
	}

	public void testArgumentsAndEmptySlotsInsertExactlyOneQuotedId() {
		choose("new example.Target().accept('<caret>')", "addon:apple",
			"new example.Target().accept(\"addon:apple\"<caret>)");
		choose("new example.Target().accept(\"<caret>\")", "addon:apple",
			"new example.Target().accept(\"addon:apple\"<caret>)");
		choose("new example.Target().accept(<caret>)", "addon:apple",
			"new example.Target().accept(\"addon:apple\"<caret>)");
		choose("new example.Target().varargs('first', <caret>)", "addon:apple",
			"new example.Target().varargs('first', \"addon:apple\"<caret>)");
	}

	public void testEnumConstantsComeFromPsiAndWorkInsideWrappersAndCasts() {
		myFixture.addFileToProject("example/Direction.java", "package example; public enum Direction { NORTH, SOUTH_EAST }");
		myFixture.addFileToProject("example/EnumTarget.java", """
			package example;
			public class EnumTarget {
				public void accept(Direction value) {}
				public void wrapped(java.util.Optional<Direction> value) {}
				public void list(java.util.List<Direction> values) {}
			}
			""");
		choose("new example.EnumTarget().accept('<caret>')", "north",
			"new example.EnumTarget().accept(\"north\"<caret>)");
		choose("new example.EnumTarget().wrapped(<caret>)", "south_east",
			"new example.EnumTarget().wrapped(\"south_east\"<caret>)");
		choose("new example.EnumTarget().list(['<caret>'])", "north",
			"new example.EnumTarget().list([\"north\"<caret>])");
		choose("import example.Direction\n'<caret>' as Direction", "north",
			"import example.Direction\n\"north\"<caret> as Direction");
	}

	public void testExportedEnumNamesOverridePsiConstants() throws Exception {
		myFixture.addFileToProject("example/Custom.java", "package example; public enum Custom { FIRST, SECOND }");
		myFixture.addFileToProject("example/EnumTarget.java", "package example; public class EnumTarget { public void accept(Custom value) {} }");
		writeCatalog(Map.of("example.Custom", List.of("renamed", "second")));
		LookupElement[] elements = complete("kubegroovy/server_scripts/EnumOverride.groovy", "new example.EnumTarget().accept('<caret>')");
		assertNotNull(elements);
		assertTrue(Arrays.stream(elements).anyMatch(item -> "renamed".equals(item.getObject())));
		assertFalse(Arrays.stream(elements).anyMatch(item -> "first".equals(item.getObject())));
	}

	public void testSiblingOverloadSuggestionsAlsoWorkForInheritedMethods() {
		choose("new example.Target().overloaded('<caret>')", "addon:apple",
			"new example.Target().overloaded(\"addon:apple\"<caret>)");
		choose("new example.Child().overloaded('<caret>')", "addon:apple",
			"new example.Child().overloaded(\"addon:apple\"<caret>)");
	}

	public void testNestedContainerInsertionPreservesOtherEntries() {
		choose("new example.Target().list(['<caret>', 'keep'])", "addon:apple",
			"new example.Target().list([\"addon:apple\"<caret>, 'keep'])");
		choose("new example.Target().map([key: '<caret>', keep: 'keep'])", "addon:apple",
			"new example.Target().map([key: \"addon:apple\"<caret>, keep: 'keep'])");
		choose("new example.Target().map([ke<caret>: 'keep', other: 'keep'])", "keyChoice",
			"new example.Target().map([\"keyChoice\"<caret>: 'keep', other: 'keep'])");
		choose("new example.Target().nested([key: ['<caret>', 'keep'], other: ['keep']])", "addon:apple",
			"new example.Target().nested([key: [\"addon:apple\"<caret>, 'keep'], other: ['keep']])");
		choose("new example.Target().array([['<caret>', 'keep']])", "addon:apple",
			"new example.Target().array([[\"addon:apple\"<caret>, 'keep']])");
	}

	public void testEmptyContainersPreserveTheirDelimiters() {
		choose("new example.Target().list([<caret>])", "addon:apple",
			"new example.Target().list([\"addon:apple\"<caret>])");
		choose("new example.Target().map([key: <caret>])", "addon:apple",
			"new example.Target().map([key: \"addon:apple\"<caret>])");
	}

	public void testExplicitCastsAndLiteralPrefixesAreRetained() {
		choose("new example.Target().accept('<caret>' as example.Item)", "addon:apple",
			"new example.Target().accept(\"addon:apple\"<caret> as example.Item)");
		choose("def value = '<caret>' as example.Item", "addon:apple",
			"def value = \"addon:apple\"<caret> as example.Item");
		choose("def value = '<caret>' as Optional<example.Item>", "addon:apple",
			"def value = \"addon:apple\"<caret> as Optional<example.Item>");
		choose("new example.Target().accept('addon:a<caret>')", "addon:apple",
			"new example.Target().accept(\"addon:apple\"<caret>)");
	}

	public void testCastTypesDoNotOfferArgumentIds() {
		for (String argument : List.of("'addon:stone' as It<caret>",
			"'addon:stone' as example.It<caret>", "'addon:stone' as Optional<example.It<caret>>",
			"(example.It<caret>) 'addon:stone'")) {
			LookupElement[] elements = complete("kubegroovy/server_scripts/CastType" + fileNumber++ + ".groovy",
				"new example.Target().accept(" + argument + ")");
			assertTrue("Argument IDs offered in cast type: " + argument,
				elements == null || Arrays.stream(elements).noneMatch(item -> item.getLookupString().startsWith("addon:")));
		}
	}

	public void testQuotesBackslashesDollarsAndControlsStayInsideOneLiteral() throws Exception {
		String id = "a\"\\$\n\r\t\u0001";
		writeCatalog(Map.of("example.Item", List.of(id)));
		choose("new example.Target().accept('<caret>')", id,
			"new example.Target().accept(\"a\\\"\\\\\\$\\n\\r\\t\\u0001\"<caret>)");
	}

	public void testLiteralPrefixFiltersBeforeResolvingIcons() throws Exception {
		List<String> ids = new ArrayList<>(List.of("addon:apple", "addon:apricot"));
		for (int i = 0; i < 1000; i++) ids.add("addon:other" + i);
		writeCatalog(Map.of("example.Item", ids));
		Path directory = Files.createTempDirectory("kgy-completion-icons");
		try {
			Path icons = Files.createDirectories(directory.resolve("type-suggestions/icons"));
			var image = new java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB);
			for (String name : List.of("apple", "apricot", "other0")) {
				javax.imageio.ImageIO.write(image, "png", icons.resolve("addon__" + name + ".png").toFile());
			}
			Class<?> support = loader.loadClass("TypeSuggestionIcons");
			support.getMethod("setIconDir", String.class).invoke(null, icons.toString());
			LookupElement[] elements = complete("kubegroovy/server_scripts/Prefix.groovy",
				"new example.Target().accept('addon:ap<caret>')");
			assertNotNull(elements);
			assertEquals(List.of("addon:apple", "addon:apricot"), Arrays.stream(elements)
				.map(LookupElement::getLookupString).filter(id -> id.startsWith("addon:")).sorted().toList());
			String cacheSizes = (String) support.getMethod("iconCacheSizes").invoke(null);
			assertTrue("Only matching IDs should resolve icons: " + cacheSizes, cacheSizes.contains("cachedSuggestions=2 "));
			assertTrue("The excluded icon should never be decoded: " + cacheSizes, cacheSizes.contains("cachedFiles=2 "));
		} finally {
			try (var paths = Files.walk(directory)) {
				for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
			}
		}
	}

	public void testContributorDoesNotOfferIdsOutsideScriptRoots() {
		LookupElement[] elements = complete("src/Unrelated.groovy", "new example.Target().accept('<caret>')");
		assertTrue(elements == null || Arrays.stream(elements).noneMatch(item -> item.getLookupString().startsWith("addon:")));
	}
}
