package dev.uncandango.kubegroovy.typingtest;

import dev.uncandango.kubegroovy.core.AdvancementNodeKGY;
import dev.uncandango.kubegroovy.event.EventGroup;
import dev.uncandango.kubegroovy.event.EventTargetType;
import dev.uncandango.kubegroovy.event.KubeEvent;
import dev.uncandango.kubegroovy.script.ScriptType;
import dev.uncandango.kubegroovy.typing.TypeTree;
import dev.uncandango.kubegroovy.recipe.filter.RecipeMatchContext;
import dev.uncandango.kubegroovy.script.annotations.HideFromGY;
import dev.uncandango.kubegroovy.script.annotations.RemapForGY;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import dev.uncandango.kubegroovy.script.bridge.Context;
import dev.uncandango.kubegroovy.script.type.TypeInfo;
import dev.uncandango.kubegroovy.typing.KubeGroovyTypingGenerator;
import dev.uncandango.kubegroovy.typing.MethodAliasGenerator;
import dev.uncandango.kubegroovy.typing.TypingBinding;
import dev.uncandango.kubegroovy.plugin.TypeSuggestionCatalog;
import groovy.lang.GroovyShell;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KubeGroovyTypingGeneratorTests {
	@TempDir
	Path temp;

	@Test
	void typeSuggestionJsonPreservesValueAndDelegateOrderAndDuplicates() {
		var catalog = new TypeSuggestionCatalog(
			Map.of("example.Type", List.of("one", "one", "two")),
			List.of(new TypeSuggestionCatalog.Delegate("example.Wrapper", "example.Type", "#")),
			Map.of("example.Optional", 0));
		var text = KubeGroovyTypingGenerator.renderTypeSuggestionsJson(catalog);
		var parsed = com.google.gson.JsonParser.parseString(text).getAsJsonObject();
		assertEquals(1, parsed.get("schemaVersion").getAsInt());
		var values = parsed.getAsJsonObject("values").getAsJsonArray("example.Type");
		assertEquals(3, values.size());
		assertEquals("one", values.get(0).getAsString());
		assertEquals("one", values.get(1).getAsString());
		assertEquals("two", values.get(2).getAsString());
		assertEquals("#", parsed.getAsJsonArray("delegates").get(0).getAsJsonObject().get("prefix").getAsString());
		assertEquals(0, parsed.getAsJsonObject("genericDelegates").get("example.Optional").getAsInt());
		assertTrue(text.endsWith("\n"));
	}

	@Test
	void generatedModuleContainsJava25SourcesAndEscapedClasspathRoots() throws Exception {
		var jar = temp.resolve("library & addon.jar");
		Files.write(jar, new byte[]{0});
		var text = KubeGroovyTypingGenerator.renderIml(List.of(jar));
		assertTrue(text.contains("LANGUAGE_LEVEL=\"JDK_25\""));
		assertTrue(text.contains("<content url=\"file://$MODULE_DIR$/kubegroovy\">"));
		assertFalse(text.contains("file://$MODULE_DIR$/../kubegroovy"));
		assertTrue(text.contains("<sourceFolder url=\"file://$MODULE_DIR$/kubegroovy/startup_scripts\" isTestSource=\"false\" />"));
		assertTrue(text.contains("<sourceFolder url=\"file://$MODULE_DIR$/kubegroovy/server_scripts\" isTestSource=\"false\" />"));
		assertTrue(text.contains("<sourceFolder url=\"file://$MODULE_DIR$/kubegroovy/client_scripts\" isTestSource=\"false\" />"));
		assertTrue(text.contains("<sourceFolder url=\"file://$MODULE_DIR$/kubegroovy/typings\" isTestSource=\"false\" />"));
		assertFalse(text.contains("<sourceFolder url=\"file://$MODULE_DIR$/kubegroovy\" isTestSource=\"false\" />"));
		assertTrue(text.contains("<excludeFolder url=\"file://$MODULE_DIR$/kubegroovy/config\" />"));
		assertTrue(text.contains("<sourceFolder url=\"file://$MODULE_DIR$/kubegroovy/assets\" type=\"java-resource\" />"));
		assertTrue(text.contains("<sourceFolder url=\"file://$MODULE_DIR$/kubegroovy/data\" type=\"java-resource\" />"));
		assertTrue(text.contains("<orderEntry type=\"jdk\" jdkName=\"25\" jdkType=\"JavaSDK\" />"));
		assertFalse(text.contains("<orderEntry type=\"inheritedJdk\" />"));
		assertTrue(text.contains("module-library"));
		assertTrue(text.contains("jar://"));
		assertTrue(text.contains("library &amp; addon.jar"));
		assertFalse(text.contains("%20"));
	}

	@Test
	void miscUsesJava25ProjectSdkAndPreservesOtherComponents() throws Exception {
		var existing = "<?xml version=\"1.0\"?>\n<project version=\"4\">\n"
			+ "  <component name=\"Other\" enabled=\"true\" />\n"
			+ "  <component name=\"ProjectRootManager\" version=\"2\" languageLevel=\"JDK_21\" default=\"true\" project-jdk-name=\"jbr-21\" project-jdk-type=\"JavaSDK\" />\n"
			+ "</project>\n";
		var merged = KubeGroovyTypingGenerator.mergeMisc(existing);
		assertTrue(merged.contains("name=\"Other\""));
		assertTrue(merged.contains("languageLevel=\"JDK_25\""));
		assertTrue(merged.contains("project-jdk-name=\"25\""));
		assertEquals(KubeGroovyTypingGenerator.renderMisc(), KubeGroovyTypingGenerator.mergeMisc(null));
	}

	@Test
	void codeInsightSettingsExcludeGroovyGdkAndMergeCleanly() throws Exception {
		var fresh = KubeGroovyTypingGenerator.mergeCodeInsightSettings(null);
		assertTrue(fresh.contains("<component name=\"JavaProjectCodeInsightSettings\">"));
		assertTrue(fresh.contains("<name>org.codehaus.groovy.runtime.*</name>"));
		assertEquals(fresh, KubeGroovyTypingGenerator.mergeCodeInsightSettings(fresh));
		var existing = "<?xml version=\"1.0\"?>\n<project version=\"4\">\n"
			+ "  <component name=\"Other\" enabled=\"true\" />\n"
			+ "  <component name=\"JavaProjectCodeInsightSettings\">\n"
			+ "    <excluded-names>\n"
			+ "      <name>com.example.*</name>\n"
			+ "    </excluded-names>\n"
			+ "  </component>\n"
			+ "</project>\n";
		var merged = KubeGroovyTypingGenerator.mergeCodeInsightSettings(existing);
		assertTrue(merged.contains("name=\"Other\""));
		assertTrue(merged.contains("<name>com.example.*</name>"));
		assertTrue(merged.contains("<name>org.codehaus.groovy.runtime.*</name>"));
		assertEquals(merged, KubeGroovyTypingGenerator.mergeCodeInsightSettings(merged));
		assertThrows(Exception.class, () -> KubeGroovyTypingGenerator.mergeCodeInsightSettings("<project><oops>"));
	}

	@Test
	void eventMethodsDescribeOverloadMatrixAndSideFiltering() {
		var group = EventGroup.of("TestEvents");
		group.startup("plain", () -> FixtureEvent.class);
		group.startup("optional", () -> FixtureEvent.class).supportsTarget(EventTargetType.STRING);
		group.startup("targeted", () -> FixtureEvent.class).requiredTarget(EventTargetType.ID);
		group.server("serverOnly", () -> FixtureEvent.class);
		var startup = KubeGroovyTypingGenerator.describeEventMethods(group.getHandlers().values(), ScriptType.STARTUP);
		assertEquals(List.of("optional", "optional", "plain", "targeted", "targeted"),
			startup.stream().map(KubeGroovyTypingGenerator.EventMethod::name).toList());
		for (var method : startup) {
			assertEquals("void", method.returns());
			assertTrue(method.typeParams().isEmpty());
		}
		var optional = startup.stream()
			.filter(method -> method.name().equals("optional")).toList();
		assertEquals(1, optional.get(0).params().size());
		assertEquals("handler", optional.get(0).params().get(0).name());
		assertTrue(optional.get(0).params().get(0).type().contains("Consumer"));
		assertTrue(optional.get(0).params().get(0).type().contains("FixtureEvent"));
		assertEquals(2, optional.get(1).params().size());
		assertEquals("target", optional.get(1).params().get(0).name());
		assertEquals("java.lang.String", optional.get(1).params().get(0).type());
		var targeted = startup.stream()
			.filter(method -> method.name().equals("targeted")).toList();
		assertEquals("java.lang.String", targeted.get(0).params().get(0).type());
		assertTrue(targeted.get(1).params().get(0).type().endsWith(".Identifier"));
		var server = KubeGroovyTypingGenerator.describeEventMethods(group.getHandlers().values(), ScriptType.SERVER);
		assertEquals(List.of("serverOnly"), server.stream().map(KubeGroovyTypingGenerator.EventMethod::name).toList());
	}

	@Test
	void eventMethodsCollapseRegistryShapeToGenericMethod() {
		var group = EventGroup.of("RegistryTestEvents");
		group.startup("registry", () -> GenericFixtureEvent.class).requiredTarget(EventTargetType.REGISTRY);
		group.startup("rawRegistry", () -> FixtureEvent.class).requiredTarget(EventTargetType.REGISTRY);
		var methods = KubeGroovyTypingGenerator.describeEventMethods(group.getHandlers().values(), ScriptType.STARTUP);
		assertEquals(3, methods.size());
		var generic = methods.stream().filter(method -> method.name().equals("registry")).toList();
		assertEquals(1, generic.size());
		assertEquals(List.of("T"), generic.get(0).typeParams());
		assertEquals("registry", generic.get(0).params().get(0).name());
		assertEquals("net.minecraft.resources.ResourceKey<net.minecraft.core.Registry<T>>",
			generic.get(0).params().get(0).type());
		assertEquals("consumer", generic.get(0).params().get(1).name());
		assertTrue(generic.get(0).params().get(1).type().startsWith("java.util.function.Consumer<"));
		assertTrue(generic.get(0).params().get(1).type().contains("GenericFixtureEvent<T>"));
		var raw = methods.stream().filter(method -> method.name().equals("rawRegistry")).toList();
		assertEquals(2, raw.size());
		assertTrue(raw.stream().allMatch(method -> method.typeParams().isEmpty()));
		assertEquals("net.minecraft.resources.ResourceKey", raw.get(1).params().get(0).type());
	}

	@Test
	void eventMethodsRenderJsonShape() {
		var methods = Map.of(ScriptType.STARTUP, Map.of("$StartupEvents", List.of(
			new KubeGroovyTypingGenerator.EventMethod("registry", "void", List.of("T"), List.of(
				new KubeGroovyTypingGenerator.EventMethodParam("registry",
					"net.minecraft.resources.ResourceKey<net.minecraft.core.Registry<T>>"),
				new KubeGroovyTypingGenerator.EventMethodParam("consumer",
					"java.util.function.Consumer<com.example.RegistryEvent<T>>"))))));
		var json = KubeGroovyTypingGenerator.renderEventMethodsJson(methods);
		assertTrue(json.contains("\"$StartupEvents\""));
		assertTrue(json.contains("\"name\": \"registry\""));
		assertTrue(json.contains("\"returns\": \"void\""));
		assertTrue(json.contains("\"typeParams\": [\n          \"T\"\n        ]"));
		assertTrue(json.contains("ResourceKey<net.minecraft.core.Registry<T>>"));
		assertTrue(json.contains("\"startup\""));
		assertTrue(KubeGroovyTypingGenerator.renderEventMethodsJson(null).contains("\"startup\""));
	}

	static class FixtureEvent implements KubeEvent {
	}

	static class GenericFixtureEvent<T> implements KubeEvent {
	}

	@Test
	void typeTreeParsesNestedGenericsAndArrays() {
		var simple = TypeTree.parse("java.lang.String");
		assertEquals("java.lang.String", simple.name());
		assertTrue(simple.args().isEmpty());
		assertEquals(0, simple.arrays());
		var nested = TypeTree.parse("java.util.Map<java.lang.String, java.util.List<T>>");
		assertEquals("java.util.Map", nested.name());
		assertEquals(2, nested.args().size());
		assertEquals("java.lang.String", nested.args().get(0).name());
		assertEquals("java.util.List", nested.args().get(1).name());
		assertEquals("T", nested.args().get(1).args().get(0).name());
		var registry = TypeTree.parse("net.minecraft.resources.ResourceKey<net.minecraft.core.Registry<T>>");
		assertEquals("net.minecraft.resources.ResourceKey", registry.name());
		assertEquals("net.minecraft.core.Registry", registry.args().get(0).name());
		assertEquals("T", registry.args().get(0).args().get(0).name());
		assertEquals(1, TypeTree.parse("java.lang.String[]").arrays());
		assertEquals(2, TypeTree.parse("int[][]").arrays());
		assertEquals("Map", TypeTree.parse("  Map < String , List < T > > ").name());
	}

	@Test
	void typeTreeRejectsNonGrammar() {
		assertEquals(null, TypeTree.parse(null));
		assertEquals(null, TypeTree.parse(""));
		assertEquals(null, TypeTree.parse("   "));
		assertEquals(null, TypeTree.parse("java.util.Map<>"));
		assertEquals(null, TypeTree.parse("java.util.Map<java.lang.String"));
		assertEquals(null, TypeTree.parse("java.lang.String>"));
		assertEquals(null, TypeTree.parse("java.util.Map<java.lang.String,,Integer>"));
		assertEquals(null, TypeTree.parse("?"));
		assertEquals(null, TypeTree.parse("? extends Foo"));
		assertEquals(null, TypeTree.parse("java..lang.String"));
		assertEquals(null, TypeTree.parse(".String"));
		assertEquals(null, TypeTree.parse("java.lang.String extra"));
	}

	@Test
	void gdslEscapesBindingDataAndUsesScriptContexts() {
		var hostile = new TypingBinding("addon'\\name", "addon.Type", false, false,
			dev.uncandango.kubegroovy.script.ScriptType.SERVER);
		var text = KubeGroovyTypingGenerator.renderGdsl(temp, Map.of(
			dev.uncandango.kubegroovy.script.ScriptType.SERVER, List.of(hostile)));
		assertTrue(text.contains("addon\\'\\\\name"));
		assertTrue(text.contains("def startupRegexp = [pathRegexp: \".*/startup_scripts/.*\"]"));
		assertTrue(text.contains("def serverRegexp = [pathRegexp: \".*/server_scripts/.*\"]"));
		assertTrue(text.contains("def clientRegexp = [pathRegexp: \".*/client_scripts/.*\"]"));
		assertTrue(text.contains("def startupCtx = context(base + startupRegexp)"));
		assertTrue(text.contains("def serverCtx = context(base + serverRegexp)"));
		assertTrue(text.contains("def clientCtx = context(base + clientRegexp)"));
		assertFalse(text.contains("__kgyFile"));
		assertFalse(text.contains("__kgyUnder"));
		assertFalse(text.contains("params: []"));
		new GroovyShell().parse(text);
	}

	@Test
	@Disabled("Retired: GDSL generation is disabled")
	void eventGdslUsesRegisteredHandlersAndStaysSeparateFromBindings() {
		var bindingsText = KubeGroovyTypingGenerator.renderGdsl(temp, Map.of());
		var eventsText = KubeGroovyTypingGenerator.renderEventsGdsl(temp);

		assertFalse(bindingsText.contains("variable(name: '$BlockEvents'"));
		assertFalse(bindingsText.contains("closureInMethod"));
		assertTrue(eventsText.contains("variable(name: '$BlockEvents', type: 'dev.uncandango.kubegroovy.event.EventGroupWrapper')"));
		assertFalse(eventsText.contains("property(name: '$BlockEvents'"));
		assertTrue(eventsText.contains("def eventType = [ctype: 'dev.uncandango.kubegroovy.event.EventGroupWrapper']"));
		assertTrue(eventsText.contains("def startupEventCtx = context(base + startupRegexp + eventType)"));
		assertTrue(eventsText.contains("def serverEventCtx = context(base + serverRegexp + eventType)"));
		assertTrue(eventsText.contains("def clientEventCtx = context(base + clientRegexp + eventType)"));
		assertTrue(eventsText.contains("rightClicked"));
		assertTrue(eventsText.contains("method(name: 'rightClicked', type: 'void', params: [handler: 'java.util.function.Consumer<dev.uncandango.kubegroovy.block.BlockRightClickedKubeEvent>'])"));
		assertTrue(eventsText.contains("method(name: 'rightClicked', type: 'void', params: [target: 'java.lang.String', handler: 'java.util.function.Consumer<dev.uncandango.kubegroovy.block.BlockRightClickedKubeEvent>'])"));
		assertTrue(eventsText.contains("method(name: 'rightClicked', type: 'void', params: [target: 'net.minecraft.resources.ResourceKey', handler: 'java.util.function.Consumer<dev.uncandango.kubegroovy.block.BlockRightClickedKubeEvent>'])"));
		assertTrue(eventsText.contains("method(name: 'randomTick', type: 'void', params: [target: 'java.lang.String', handler: 'java.util.function.Consumer<dev.uncandango.kubegroovy.block.RandomTickKubeEvent>'])"));
		assertTrue(eventsText.contains("method(name: 'randomTick', type: 'void', params: [target: 'net.minecraft.resources.ResourceKey', handler: 'java.util.function.Consumer<dev.uncandango.kubegroovy.block.RandomTickKubeEvent>'])"));
		assertTrue(eventsText.contains("method(name: 'modifyCreativeTab', type: 'void', params: [target: 'java.lang.String', handler: 'java.util.function.Consumer<dev.uncandango.kubegroovy.item.creativetab.CreativeTabKubeEvent>'])"));
		assertTrue(eventsText.contains("method(name: 'modifyCreativeTab', type: 'void', params: [target: 'net.minecraft.resources.Identifier', handler: 'java.util.function.Consumer<dev.uncandango.kubegroovy.item.creativetab.CreativeTabKubeEvent>'])"));
		assertTrue(eventsText.contains("method(name: 'generateData', type: 'void', params: [target: 'java.lang.String', handler: 'java.util.function.Consumer<dev.uncandango.kubegroovy.generator.KubeDataGenerator>'])"));
		assertTrue(eventsText.contains("method(name: 'generateData', type: 'void', params: [target: 'dev.uncandango.kubegroovy.script.data.GeneratedDataStage', handler: 'java.util.function.Consumer<dev.uncandango.kubegroovy.generator.KubeDataGenerator>'])"));
		assertTrue(eventsText.contains("method(name: 'loaded', type: 'void', params: [handler: 'java.util.function.Consumer<dev.uncandango.kubegroovy.server.ServerKubeEvent>'])"));
		assertEquals(1, eventsText.split("method\\(name: 'basicCommand'", -1).length - 1);
		assertTrue(eventsText.contains("startup_scripts"));
		assertTrue(eventsText.contains("server_scripts"));
		assertTrue(eventsText.contains("client_scripts"));
		assertTrue(eventsText.contains("filetypes: ['groovy', 'gvy', 'gy']"));
		assertFalse(eventsText.contains("__kgyFile"));
		assertFalse(eventsText.contains("__kgyUnder"));
		assertFalse(eventsText.contains("groovy.lang.Closure"));
		assertFalse(eventsText.contains("closureInMethod"));
		assertTrue(eventsText.contains("place?.qualifierExpression?.text"));
		assertFalse(eventsText.contains("params: []"));
		new GroovyShell().parse(eventsText);
	}

	@Test
	void bindingDescriptorsDistinguishClassValues() {
		var descriptor = TypingBinding.describe("StringType", String.class,
			dev.uncandango.kubegroovy.script.ScriptType.STARTUP);
		assertTrue(descriptor.classValue());
		assertEquals(String.class.getName(), descriptor.typeName());
	}

	@Test
	void modulesMergeIsMinimalAndIdempotent() throws Exception {
		var original = "<?xml version=\"1.0\"?>\n<project version=\"4\"><component name=\"ProjectModuleManager\"><modules>\n  <module fileurl=\"file://$PROJECT_DIR$/other.iml\" filepath=\"$PROJECT_DIR$/other.iml\" />\n</modules></component></project>\n";
		var merged = KubeGroovyTypingGenerator.mergeModules(original);
		assertTrue(merged.contains("other.iml"));
		assertTrue(merged.contains("kubegroovy.iml"));
		assertEquals(merged, KubeGroovyTypingGenerator.mergeModules(merged));
	}

	@Test
	void malformedAndConflictingModulesAreRefused() {
		assertThrows(Exception.class, () -> KubeGroovyTypingGenerator.mergeModules("<project><modules>"));
		var conflict = "<project version=\"4\"><component name=\"ProjectModuleManager\"><modules><module fileurl=\"file://$PROJECT_DIR$/other/kubegroovy.iml\" filepath=\"$PROJECT_DIR$/other/kubegroovy.iml\" /></modules></component></project>";
		assertThrows(Exception.class, () -> KubeGroovyTypingGenerator.mergeModules(conflict));
	}

	@Test
	void fallbackJarIsDeterministicAndCleanupIsGeneratorScoped() throws Exception {
		var cache = temp.resolve("local/kubegroovy/typing-cache");
		var entries = Map.of("b/Test.class", new byte[]{2}, "a/Test.class", new byte[]{1});
		var first = KubeGroovyTypingGenerator.exportFallbackJar(cache, "addon unsafe/name", entries);
		var second = KubeGroovyTypingGenerator.exportFallbackJar(cache, "addon unsafe/name", entries);
		assertEquals(first, second);
		assertTrue(Files.exists(first));
		var unrelated = cache.resolve("unrelated.jar");
		Files.write(unrelated, "not generated".getBytes(StandardCharsets.UTF_8));
		KubeGroovyTypingGenerator.pruneFallbackCache(cache, List.of());
		assertFalse(Files.exists(first));
		assertTrue(Files.exists(unrelated));
	}

	@Test
	@Disabled("Retired: LivePlugin plugin.kts generation (template absent)")
	void generationWritesOutputsAndLeavesGdslUntouched() throws Exception {
		var game = temp.resolve("game");
		var bindings = Map.of(dev.uncandango.kubegroovy.script.ScriptType.SERVER, List.<TypingBinding>of());
		var first = KubeGroovyTypingGenerator.generate(game, bindings);
		assertTrue(Files.isRegularFile(first.module()));
		assertTrue(Files.isRegularFile(first.modules()));
		assertTrue(Files.isRegularFile(game.resolve(".idea/misc.xml")));
		// GDSL generation is disabled: the compatibility path is reported but no file is written.
		assertEquals(game.resolve("kubegroovy/typings/kubegroovy.gdsl").normalize(), first.gdsl());
		assertFalse(Files.exists(first.gdsl()));
		assertEquals(game.resolve(".live-plugins/kubegroovy/plugin.kts").normalize(), first.livePlugin());
		assertTrue(Files.isRegularFile(first.livePlugin()));
		assertTrue(Files.readString(first.livePlugin()).contains(KubeGroovyTypingGenerator.GENERATED_MARKER));
		var events = game.resolve("kubegroovy/typings/events.gdsl");
		var catalog = first.livePlugin().resolveSibling("catalog.json");
		assertTrue(Files.isRegularFile(catalog));
		assertTrue(Files.readString(catalog).contains(KubeGroovyTypingGenerator.GENERATED_MARKER));
		assertFalse(Files.exists(events));
		assertTrue(Files.readString(game.resolve(".idea/misc.xml")).contains("project-jdk-name=\"25\""));
		var catalogBefore = Files.readString(catalog);
		var second = KubeGroovyTypingGenerator.generate(game, bindings);
		assertFalse(second.gdslChanged());
		assertFalse(second.livePluginChanged());
		assertFalse(Files.exists(first.gdsl()));
		assertFalse(Files.exists(events));
		assertEquals(catalogBefore, Files.readString(catalog));
		Files.createDirectories(events.getParent());
		Files.writeString(events, "manual events file");
		KubeGroovyTypingGenerator.generate(game, bindings);
		assertEquals("manual events file", Files.readString(events));
		Files.writeString(first.gdsl(), "manual file");
		KubeGroovyTypingGenerator.generate(game, bindings);
		assertEquals("manual file", Files.readString(first.gdsl()));
		var manualPluginGame = temp.resolve("manual-live-plugin-game");
		var manualPluginResult = KubeGroovyTypingGenerator.generate(manualPluginGame, bindings);
		Files.writeString(manualPluginResult.livePlugin(), "manual LivePlugin script");
		assertThrows(Exception.class, () -> KubeGroovyTypingGenerator.generate(manualPluginGame, bindings));
		assertEquals("manual LivePlugin script", Files.readString(manualPluginResult.livePlugin()));
	}

	@Test
	@Disabled("Retired: LivePlugin plugin.kts generation (template absent)")
	void forceGenerationReplacesOnlyManualKubegroovyIml() throws Exception {
		var game = temp.resolve("force-module-game");
		var module = game.resolve(".idea/kubegroovy.iml");
		Files.createDirectories(module.getParent());
		Files.writeString(module, "manual IntelliJ module");
		var bindings = Map.of(dev.uncandango.kubegroovy.script.ScriptType.SERVER, List.<TypingBinding>of());

		assertThrows(Exception.class, () -> KubeGroovyTypingGenerator.generate(game, bindings));
		assertEquals("manual IntelliJ module", Files.readString(module));

		var result = KubeGroovyTypingGenerator.generate(game, bindings, true);
		assertTrue(result.moduleChanged());
		assertTrue(Files.readString(module).contains(KubeGroovyTypingGenerator.GENERATED_MARKER));

		// GDSL generation is disabled: manual GDSL files are left untouched even in force mode.
		Files.createDirectories(result.gdsl().getParent());
		Files.writeString(result.gdsl(), "manual GDSL");
		KubeGroovyTypingGenerator.generate(game, bindings, true);
		assertEquals("manual GDSL", Files.readString(result.gdsl()));
	}

	@Test
	@Disabled("Retired: LivePlugin plugin.kts generation (template absent)")
	void generatedLivePluginContainsEscapedRuntimeBindingsPerScriptType() throws Exception {
		var startup = dev.uncandango.kubegroovy.script.ScriptType.STARTUP;
		var server = dev.uncandango.kubegroovy.script.ScriptType.SERVER;
		var bindings = Map.of(
			startup, List.of(
				new TypingBinding("$StartupEvents", "dev.uncandango.kubegroovy.event.EventGroupWrapper", false, true, startup),
				new TypingBinding("addon\"\\Name", "example.AddonType", false, false, startup)
			),
			server, List.of(new TypingBinding("serverOnly", "java.lang.String", false, false, server))
		);
		var result = KubeGroovyTypingGenerator.generate(temp.resolve("binding-plugin-game"), bindings);
		var generated = Files.readString(result.livePlugin());

		assertTrue(generated.contains("\"startup\" to listOf("));
		assertTrue(generated.contains("KubeGroovyBinding(\"\\u0024StartupEvents\", \"dev.uncandango.kubegroovy.event.EventGroupWrapper\", false, true)"));
		assertTrue(generated.contains("KubeGroovyBinding(\"addon\\\"\\\\Name\", \"example.AddonType\", false, false)"));
		assertTrue(generated.contains("\"server\" to listOf(\n\t\tKubeGroovyBinding(\"serverOnly\", \"java.lang.String\", false, false)"));
		assertTrue(generated.contains("KubeGroovyEventMethod(\"StartupEvents\", \"init\", \"dev.uncandango.kubegroovy.event.KubeStartupEvent\", null, false)"));
		assertTrue(generated.contains("KubeGroovyEventMethod(\"StartupEvents\", \"postInit\", \"dev.uncandango.kubegroovy.event.KubeStartupEvent\", null, false)"));
		assertTrue(generated.contains("val dummyIdentifier = referenceText.lowercase(Locale.ROOT).indexOf(\"intellijidearulezzz\")"));
		assertTrue(generated.contains("fun supportHasBindingPrefix(file: PsiFile, prefix: String): Boolean"));
		assertTrue(generated.contains("receiverClass.allMethods.asSequence()"));
		assertTrue(generated.contains(".withIcon(AllIcons.Nodes.Method)"));
		assertTrue(generated.contains("supportTargetForType(type, context) != null"));
		assertTrue(generated.contains("receiverClass.qualifiedName == \"java.lang.Class\""));
		assertTrue(generated.contains("supportScriptEventMethods(file).map { it.groupName }.distinct()"));
		assertTrue(generated.contains("withTailText(\"  KubeGroovy event\", true)"));
		assertTrue(generated.contains("private fun LookupElement.withKubeGroovyPriority(): LookupElement"));
		assertTrue(generated.contains("PrioritizedLookupElement.withPriority(this, 1000.0)"));
		assertTrue(generated.contains(".withKubeGroovyPriority()"));
		assertTrue(generated.contains("KubeGroovyNames.STARTUP_EVENTS_ALIAS to it"));
		assertFalse(generated.contains("KGY_BINDING_DATA"));
		assertFalse(generated.contains("KGY_EVENT_DATA"));
	}

	@Test
	@Disabled("Retired: LivePlugin plugin.kts generation (template absent)")
	void livePluginCatalogIsSortedRepeatableAndProtectedFromManualOverwrite() throws Exception {
		var catalog = new KubeGroovyTypingGenerator.LivePluginValueCatalog(List.of(
			new KubeGroovyTypingGenerator.LivePluginRegistryValues("zmod:later", "example.Later",
				List.of("zmod:z", "minecraft:b", "minecraft:a"), List.of("zmod:z_tag", "minecraft:a_tag")),
			new KubeGroovyTypingGenerator.LivePluginRegistryValues("minecraft:items", "net.minecraft.world.item.Item",
				List.of("minecraft:zombie_spawn_egg", "minecraft:air"), List.of("minecraft:planks", "minecraft:logs"))
		), List.of("z_action", "a_action"), List.of("z_tool", "a_tool"), List.of("z_sound", "a_sound"),
			List.of("z_color", "a_color"), List.of("z_sound_action", "a_sound_action"),
			List.of("net.minecraft.world.item.ItemStack", "net.minecraft.world.level.ItemLike"));
		var game = temp.resolve("catalog-game");
		var livePluginConfig = temp.resolve("catalog-live-plugins");
		var result = KubeGroovyTypingGenerator.generate(game, Map.of(), List.of(), List.of(), livePluginConfig,
			List.of(), List.of(), catalog);
		var catalogFile = result.livePlugin().resolveSibling("catalog.json");
		var text = Files.readString(catalogFile);
		assertTrue(text.contains("\"generatedBy\": \"" + KubeGroovyTypingGenerator.GENERATED_MARKER + "\""));
		assertTrue(text.indexOf("\"key\": \"minecraft:items\"") < text.indexOf("\"key\": \"zmod:later\""));
		assertTrue(text.indexOf("\"minecraft:a\"") < text.indexOf("\"minecraft:b\""));
		assertTrue(text.indexOf("\"a_action\"") < text.indexOf("\"z_action\""));
		assertTrue(text.contains("\"itemTintFunctions\""));
		assertTrue(text.contains("\"blockTintFunctions\""));
		assertTrue(text.indexOf("\"net.minecraft.world.item.ItemStack\"") < text.indexOf("\"net.minecraft.world.level.ItemLike\""));
		KubeGroovyTypingGenerator.generate(game, Map.of(), List.of(), List.of(), livePluginConfig, List.of(), List.of(), catalog);
		assertEquals(text, Files.readString(catalogFile));

		var manualConfig = temp.resolve("manual-catalog-live-plugins");
		var manualCatalog = manualConfig.resolve("kubegroovy/catalog.json");
		Files.createDirectories(manualCatalog.getParent());
		Files.writeString(manualCatalog, "manual catalog");
		assertThrows(Exception.class, () -> KubeGroovyTypingGenerator.generate(temp.resolve("manual-catalog-game"),
			Map.of(), List.of(), List.of(), manualConfig, List.of(), List.of(), catalog));
		assertEquals("manual catalog", Files.readString(manualCatalog));
	}

	@Test
	@Disabled("Retired: LivePlugin plugin.kts generation (template absent)")
	void livePluginRegistrySuggestionsUseRuntimeKeysAndFullyQualifiedGenericTypes() throws Exception {
		var listOfItems = TypeInfo.of(List.class).withParams(TypeInfo.of(net.minecraft.world.item.Item.class));
		var nestedType = TypeInfo.of(Map.class).withParams(TypeInfo.of(String.class), listOfItems);
		assertEquals("java.util.Map<java.lang.String, java.util.List<net.minecraft.world.item.Item>>", nestedType.toStringFQN());

		var targets = new java.util.ArrayList<>(List.of(
			new KubeGroovyTypingGenerator.LivePluginRegistryTarget("minecraft:item",
				TypeInfo.of(net.minecraft.world.item.Item.class).toStringFQN()),
			new KubeGroovyTypingGenerator.LivePluginRegistryTarget("minecraft:block",
				TypeInfo.of(net.minecraft.world.level.block.Block.class).toStringFQN()),
			new KubeGroovyTypingGenerator.LivePluginRegistryTarget("example:combined", nestedType.toStringFQN())
		));
		for (int index = 0; index < 15; index++) {
			targets.add(new KubeGroovyTypingGenerator.LivePluginRegistryTarget("test:registry_" + index, nestedType.toStringFQN()));
		}
		var builderTypes = List.of(
			new KubeGroovyTypingGenerator.LivePluginBuilderType("minecraft:block", "kubegroovy:default",
				"dev.uncandango.kubegroovy.block.custom.BasicKubeBlock.Builder", true),
			new KubeGroovyTypingGenerator.LivePluginBuilderType("minecraft:block", "kubegroovy:crop",
				"dev.uncandango.kubegroovy.block.custom.CropBlockBuilder", false),
			new KubeGroovyTypingGenerator.LivePluginBuilderType("minecraft:item", "kubegroovy:default",
				"dev.uncandango.kubegroovy.item.custom.ItemBuilder", true)
		);
		var result = KubeGroovyTypingGenerator.generate(temp.resolve("registry-target-game"), Map.of(), List.of(), List.of(), targets, builderTypes);
		var generated = Files.readString(result.livePlugin());

		assertTrue(generated.contains("KubeGroovyScriptData.registryTargets = listOf(kgyRegistryTargetsChunk0(), kgyRegistryTargetsChunk1()).flatten()"));
		assertTrue(generated.contains("private fun kgyRegistryTargetsChunk1(): List<KubeGroovyRegistryTarget> = listOf("));
		assertTrue(generated.contains("KubeGroovyRegistryTarget(\"minecraft:item\", \"net.minecraft.world.item.Item\")"));
		assertTrue(generated.contains("KubeGroovyRegistryTarget(\"minecraft:block\", \"net.minecraft.world.level.block.Block\")"));
		assertTrue(generated.contains("KubeGroovyRegistryTarget(\"example:combined\", \"java.util.Map<java.lang.String, java.util.List<net.minecraft.world.item.Item>>\")"));
		assertTrue(generated.contains("KubeGroovyScriptData.registryTargets.forEach { target ->"));
		assertTrue(generated.contains("withTailText(\" (RegistryKubeEvent<${target.elementTypeName}>)\", true)"));
		assertTrue(generated.contains("target?.elementTypeName ?: \"?\""));
		assertTrue(generated.contains("val elementTypeName = valueType.parameters.singleOrNull()?.canonicalText"));
		assertTrue(generated.contains("private fun registryTargetForKey(key: String): KubeGroovyRegistryTarget?"));
		assertTrue(generated.contains("KubeGroovyScriptData.builderTypes = listOf(kgyBuilderTypesChunk0()).flatten()"));
		assertTrue(generated.contains("KubeGroovyBuilderType(\"minecraft:block\", \"kubegroovy:default\", \"dev.uncandango.kubegroovy.block.custom.BasicKubeBlock.Builder\", true)"));
		assertTrue(generated.contains("KubeGroovyBuilderType(\"minecraft:block\", \"kubegroovy:crop\", \"dev.uncandango.kubegroovy.block.custom.CropBlockBuilder\", false)"));
		assertTrue(generated.contains("class RegistryBuilderCallTypeCalculator : GrCallTypeCalculator"));
		assertTrue(generated.contains("method: PsiMethod,\n\t\targuments: List<Argument>?,"));
		assertTrue(generated.contains("else requestedBuilderTypeId(typeExpression) ?: return null"));
		assertTrue(generated.contains("callTypeCalculatorPoint.point.registerExtension(callTypeCalculator, LoadingOrder.FIRST, pluginDisposable)"));
		assertTrue(generated.contains("supportBuilderTypeTarget(parameters.position)?.let { target ->"));
		assertTrue(generated.contains("current = current?.parent"));
		assertTrue(generated.contains("registryBuilderTypes.firstOrNull { it.defaultType }?.let { options.add(\"default\" to it) }"));
		assertTrue(generated.contains("type.typeId.startsWith(\"kubegroovy:\")"));
		assertTrue(generated.contains("type.typeId.removePrefix(\"kubegroovy:\")"));
		assertTrue(generated.contains("supportInsertBuilderType(insertion, suggestedId)"));
		assertTrue(generated.contains("kubeGroovyCompletionSupport.supportIsBuilderTypeArgument(position)"));
		assertTrue(generated.contains("supportTargetForType(type, position)"));
		assertTrue(generated.contains("supportMapLiteralIsKey(container, literal)"));
		assertTrue(generated.contains("supportExpectedTypes(position)"));
		assertTrue(generated.contains("UUID.randomUUID().toString()"));
		assertTrue(generated.contains("supportIsValidIdentifier(value)"));
		assertTrue(generated.contains("RegistryPredicate"));
		assertTrue(generated.contains("catalog.json"));
		assertFalse(generated.contains("RegistryKind.entries"));
	}

	@Test
	@Disabled("Retired: LivePlugin plugin.kts generation (template absent)")
	void livePluginIsInstalledInDetectedIntelliJConfigDirectory() throws Exception {
		var jetBrains = temp.resolve("JetBrains");
		Files.createDirectories(jetBrains.resolve("IntelliJIdea2025.3"));
		Files.createDirectories(jetBrains.resolve("IntelliJIdea2025.10"));
		Files.createDirectories(jetBrains.resolve("IdeaIC2026.1"));
		var newestLivePluginDirectory = KubeGroovyTypingGenerator.resolveLivePluginDirectory(jetBrains);
		assertEquals(jetBrains.resolve("IdeaIC2026.1/live-plugins"), newestLivePluginDirectory);
		Files.createDirectories(jetBrains.resolve("IntelliJIdea2025.3/plugins/LivePlugin"));
		var livePluginDirectory = KubeGroovyTypingGenerator.resolveLivePluginDirectory(jetBrains);
		assertEquals(jetBrains.resolve("IntelliJIdea2025.3/live-plugins"), livePluginDirectory);

		var game = temp.resolve("config-target-game");
		var generated = KubeGroovyTypingGenerator.generate(game, Map.of(), List.of(), List.of(), livePluginDirectory);
		assertEquals(livePluginDirectory.resolve("kubegroovy/plugin.kts"), generated.livePlugin());
		assertTrue(Files.isRegularFile(generated.livePlugin()));
		assertTrue(Files.readString(generated.livePlugin()).contains(KubeGroovyTypingGenerator.GENERATED_MARKER));
		Files.writeString(generated.livePlugin(), "manual LivePlugin script");
		assertThrows(Exception.class, () -> KubeGroovyTypingGenerator.generate(game, Map.of(), List.of(), List.of(), livePluginDirectory));
		assertEquals("manual LivePlugin script", Files.readString(generated.livePlugin()));
	}

	@Test
	void methodAliasesAreCollectedFromRemapAnnotations() {
		var mod = new MethodAliasGenerator.ModData("testmod", List.of(GeneratorFixture.class), List.of());
		var aliases = MethodAliasGenerator.methodAliases(List.of(mod));
		var type = GeneratorFixture.class.getCanonicalName();
		assertTrue(aliases.contains(new MethodAliasGenerator.MethodAlias(type, "kgy$getId", "getId")));
		assertTrue(aliases.contains(new MethodAliasGenerator.MethodAlias(type, "originalName", "friendlyName")));
		assertTrue(aliases.contains(new MethodAliasGenerator.MethodAlias(type, "staticOriginal", "staticFriendly")));
		assertFalse(aliases.stream().anyMatch(alias -> alias.from().equals("hidden") || alias.from().equals("privateMethod")));
		// Deterministic, sorted and deduplicated across mods.
		var doubled = MethodAliasGenerator.methodAliases(List.of(mod, mod,
			new MethodAliasGenerator.ModData("amod", List.of(GeneratorFixture.class), List.of())));
		assertEquals(aliases, doubled);
		var keys = aliases.stream().map(alias -> alias.type() + "\u0000" + alias.from() + "\u0000" + alias.to()).toList();
		assertEquals(keys.stream().sorted().toList(), keys);
	}

	@Test
	void methodAliasesUseReceiverTypeForBridges() {
		var bridge = new MethodAliasGenerator.ReceiverBridge(AdvancementNode.class, List.of(AdvancementNodeKGY.class), List.of());
		var aliases = MethodAliasGenerator.methodAliases(List.of(new MethodAliasGenerator.ModData("kubegroovy", List.of(), List.of(bridge))));
		var receiver = AdvancementNode.class.getCanonicalName();
		assertTrue(aliases.contains(new MethodAliasGenerator.MethodAlias(receiver, "kgy$getId", "getId")));
		assertFalse(aliases.stream().anyMatch(alias -> alias.type().equals(AdvancementNodeKGY.class.getCanonicalName())));
		var signatures = MethodAliasGenerator.methodAliasSignatures(List.of(new MethodAliasGenerator.ModData("kubegroovy", List.of(), List.of(bridge))));
		var alias = new MethodAliasGenerator.MethodAlias(receiver, "kgy$getId", "getId");
		assertTrue(signatures.get(alias).stream().anyMatch(signature -> signature.contains(" kgy$getId(")));
		var json = com.google.gson.JsonParser.parseString(KubeGroovyTypingGenerator.renderMethodAliasesJson(List.of(alias), signatures)).getAsJsonObject();
		assertEquals(signatures.get(alias).getFirst(), json.getAsJsonArray("aliases").get(0).getAsJsonObject()
			.getAsJsonArray("signatures").get(0).getAsString());
	}

	@Test
	void methodAliasesJsonFollowsLivePluginTemplate() {
		var type = GeneratorFixture.class.getCanonicalName();
		var text = KubeGroovyTypingGenerator.renderMethodAliasesJson(List.of(
			new MethodAliasGenerator.MethodAlias(type, "originalName", "friendlyName"),
			new MethodAliasGenerator.MethodAlias("a.Class", "kgy$thing", "thing")));
		var parsed = com.google.gson.JsonParser.parseString(text).getAsJsonObject();
		assertFalse(parsed.get("hideOriginal").getAsBoolean());
		var entries = parsed.getAsJsonArray("aliases");
		assertEquals(2, entries.size());
		assertEquals("a.Class", entries.get(0).getAsJsonObject().get("class").getAsString());
		assertEquals("kgy$thing", entries.get(0).getAsJsonObject().get("from").getAsString());
		assertEquals("thing", entries.get(0).getAsJsonObject().get("to").getAsString());
		assertEquals(type, entries.get(1).getAsJsonObject().get("class").getAsString());
		assertTrue(parsed.has("_notes"));
		assertTrue(text.endsWith("\n"));
	}

	@Test
	void methodAliasSignaturesPreserveOverloadsAndGenericDeclarations() {
		var mod = new MethodAliasGenerator.ModData("signatures", List.of(AliasSignatureFixture.class), List.of());
		var signatures = MethodAliasGenerator.methodAliasSignatures(List.of(mod, mod));
		var type = AliasSignatureFixture.class.getCanonicalName();
		var overloads = signatures.get(new MethodAliasGenerator.MethodAlias(type, "kgy$value", "value"));
		assertEquals(2, overloads.size());
		assertTrue(overloads.contains("public java.lang.String kgy$value();"));
		assertTrue(overloads.stream().anyMatch(value -> value.startsWith("public int kgy$value(int ")));
		var generic = signatures.get(new MethodAliasGenerator.MethodAlias(type, "kgy$identity", "identity")).getFirst();
		assertTrue(generic.startsWith("public static <T extends java.lang.Number & java.lang.Comparable<T>> T kgy$identity(T "));
		var varargs = signatures.get(new MethodAliasGenerator.MethodAlias(type, "kgy$many", "many")).getFirst();
		assertTrue(varargs.startsWith("public void kgy$many(java.lang.String... "));
		assertTrue(varargs.endsWith(") throws java.io.IOException;"));
	}

	@RemapPrefixForGY("kgy$")
	public static class AliasSignatureFixture {
		public String kgy$value() { return "value"; }
		public int kgy$value(int count) { return count; }
		public static <T extends Number & Comparable<T>> T kgy$identity(T input) { return input; }
		public void kgy$many(String... values) throws java.io.IOException {}
	}

	@Test
	void objectOverloadsJsonFollowsLivePluginTemplate() {
		var text = KubeGroovyTypingGenerator.renderObjectOverloadsJson(
			List.of("net.minecraft.world.item.ItemStack", "a.Class"));
		var parsed = com.google.gson.JsonParser.parseString(text).getAsJsonObject();
		var targets = parsed.getAsJsonArray("targets");
		assertEquals(2, targets.size());
		assertEquals("a.Class", targets.get(0).getAsString());
		assertEquals("net.minecraft.world.item.ItemStack", targets.get(1).getAsString());
		assertTrue(parsed.get("enumsToObject").getAsBoolean());
		var wrappers = parsed.getAsJsonArray("wrappers");
		assertEquals("java.util.Optional", wrappers.get(0).getAsString());
		assertTrue(wrappers.size() > 0);
		var rules = parsed.getAsJsonArray("typeRules");
		assertEquals(3, rules.size());
		assertEquals("java.util.Map", rules.get(0).getAsJsonObject().get("type").getAsString());
		assertEquals("keep", rules.get(0).getAsJsonObject().get("action").getAsString());
		assertEquals("net.minecraft.core.HolderSet", rules.get(2).getAsJsonObject().get("type").getAsString());
		assertEquals("replace", rules.get(2).getAsJsonObject().get("action").getAsString());
		assertEquals("java.util.List", rules.get(2).getAsJsonObject().get("replaceWith").getAsString());
		assertEquals(0, rules.get(2).getAsJsonObject().get("requireEligibleArg").getAsInt());
		var rewrite = parsed.getAsJsonObject("rewrite");
		assertEquals("java.lang.Object", rewrite.get("scalar").getAsString());
		assertEquals("java.lang.Object[]", rewrite.get("array").getAsString());
		assertEquals("java.util.List<java.lang.Object>", rewrite.get("collection").getAsString());
		assertTrue(parsed.has("_notes"));
		assertTrue(text.endsWith("\n"));
	}

	@Test
	void bindingsJsonGroupsVariablesByScriptSide() {
		var bindings = Map.of(
			dev.uncandango.kubegroovy.script.ScriptType.STARTUP, List.of(
				new TypingBinding("$Item", "example.ItemWrapper", true, false,
					dev.uncandango.kubegroovy.script.ScriptType.STARTUP),
				new TypingBinding("SECOND", "java.lang.Long", false, false,
					dev.uncandango.kubegroovy.script.ScriptType.STARTUP)),
			dev.uncandango.kubegroovy.script.ScriptType.SERVER, List.of(
				new TypingBinding("$ServerEvents", "dev.uncandango.kubegroovy.event.EventGroupWrapper", false, true,
					dev.uncandango.kubegroovy.script.ScriptType.SERVER)));
		var text = KubeGroovyTypingGenerator.renderBindingsJson(bindings);
		var parsed = com.google.gson.JsonParser.parseString(text).getAsJsonObject();
		var startup = parsed.getAsJsonArray("startup");
		assertEquals(2, startup.size());
		assertEquals("$Item", startup.get(0).getAsJsonObject().get("name").getAsString());
		assertEquals("example.ItemWrapper", startup.get(0).getAsJsonObject().get("type").getAsString());
		assertTrue(startup.get(0).getAsJsonObject().get("classValue").getAsBoolean());
		assertFalse(startup.get(0).getAsJsonObject().get("eventGroup").getAsBoolean());
		assertEquals("SECOND", startup.get(1).getAsJsonObject().get("name").getAsString());
		var server = parsed.getAsJsonArray("server");
		assertEquals(1, server.size());
		assertTrue(server.get(0).getAsJsonObject().get("eventGroup").getAsBoolean());
		assertTrue(parsed.getAsJsonArray("client").isEmpty());
		assertTrue(parsed.has("_notes"));
		assertTrue(text.endsWith("\n"));
	}

	@Test
	void bundledLivePluginScriptsArePackaged() throws Exception {
		var scripts = KubeGroovyTypingGenerator.readBundledLivePluginScripts();
		assertEquals(List.of("bindings", "events", "info-docs", "method-aliases", "object-overload", "type-suggestions"),
			scripts.keySet().stream().sorted().toList());
		for (var text : scripts.values()) {
			assertFalse(text.isBlank());
			assertTrue(text.contains("registerExtension"));
		}
	}

	@Test
	void bundledBindingsLogosArePackaged() throws Exception {
		assertNotNull(KubeGroovyTypingGenerator.readBundledBindingsLogoSvg());
		assertNotNull(KubeGroovyTypingGenerator.readBundledBindingsLogoDarkSvg());

		// The PNG masters are the deterministic lookup fallback: they must decode, and ship at
		// 32px so the LivePlugin script can bicubic-downscale them to the 16px lookup size.
		for (var png : new byte[][] {
			KubeGroovyTypingGenerator.readBundledBindingsLogo(),
			KubeGroovyTypingGenerator.readBundledBindingsLogoDark() }) {
			assertNotNull(png);
			try (var in = new java.io.ByteArrayInputStream(png)) {
				var image = javax.imageio.ImageIO.read(in);
				assertNotNull(image);
				assertEquals(32, image.getWidth());
				assertEquals(32, image.getHeight());
			}
		}
	}

	@Test
	void livePluginInstallRootsAreDiscoveredFromJetBrainsConfig() throws Exception {
		var jetBrains = temp.resolve("install-roots-JetBrains");
		Files.createDirectories(jetBrains.resolve("IntelliJIdea2025.3"));
		Files.createDirectories(jetBrains.resolve("IdeaIC2026.1"));
		var empty = KubeGroovyTypingGenerator.findLivePluginInstallRoots(jetBrains);
		assertTrue(empty.jetBrainsFound());
		assertTrue(empty.roots().isEmpty());

		Files.createDirectories(jetBrains.resolve("IntelliJIdea2025.3/live-plugins"));
		Files.createDirectories(jetBrains.resolve("IntelliJIdea2025.3/plugins"));
		var found = KubeGroovyTypingGenerator.findLivePluginInstallRoots(jetBrains);
		assertTrue(found.jetBrainsFound());
		assertEquals(List.of(jetBrains.resolve("IntelliJIdea2025.3/live-plugins").normalize()), found.roots());

		var missing = KubeGroovyTypingGenerator.findLivePluginInstallRoots(temp.resolve("no-such-JetBrains"));
		assertFalse(missing.jetBrainsFound());
		assertTrue(missing.roots().isEmpty());
	}

	@Test
	@Disabled("Retired: LivePlugin plugin.kts generation (template absent)")
	void modDataSkipsMissingClassesAndGeneratesNoGdsl() throws Exception {
		var data = new MethodAliasGenerator.ModData("shared_fixture", List.of(GeneratorFixture.class), List.of());
		assertEquals(List.of(String.class), MethodAliasGenerator.loadClasses(List.of(String.class.getName(), "missing.runtime.ClientOnlyType"), getClass().getClassLoader()));

		// GDSL generation is disabled: per-mod GDSL files are not written by the typing generator.
		var game = temp.resolve("per-mod-game");
		var result = KubeGroovyTypingGenerator.generate(game, Map.of(), List.of(data,
			new MethodAliasGenerator.ModData("second_fixture", List.of(GeneratorFixture.class), List.of())), List.of());
		var mods = game.resolve("kubegroovy/typings/mods");
		assertFalse(Files.exists(mods.resolve("shared_fixture.gdsl")));
		assertFalse(Files.exists(mods.resolve("second_fixture.gdsl")));
		var second = KubeGroovyTypingGenerator.generate(game, Map.of(), List.of(data,
			new MethodAliasGenerator.ModData("second_fixture", List.of(GeneratorFixture.class), List.of())), List.of());
		assertFalse(second.gdslChanged());
		assertFalse(Files.exists(mods.resolve("shared_fixture.gdsl")));
		assertTrue(result.gdsl().endsWith("kubegroovy.gdsl"));
	}

	@Test
	void largeModAliasCollectionStaysComplete() throws Exception {
		var sourceFile = temp.resolve("large-fixture-src/generated/LargeTypingFixture.java");
		var classes = temp.resolve("large-fixture-classes");
		Files.createDirectories(sourceFile.getParent());
		Files.createDirectories(classes);
		var source = new StringBuilder("package generated; @dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY(\"kgy$\") public class LargeTypingFixture {\n");
		for (int i = 0; i < 10_000; i++) source.append("public void kgy$member").append(i).append("() {}\n");
		source.append('}');
		Files.writeString(sourceFile, source, StandardCharsets.UTF_8);

		var compiler = ToolProvider.getSystemJavaCompiler();
		assertNotNull(compiler);
		assertEquals(0, compiler.run(null, null, null, "-classpath", System.getProperty("java.class.path"),
			"-d", classes.toString(), sourceFile.toString()));
		try (var classLoader = new URLClassLoader(new java.net.URL[]{classes.toUri().toURL()}, getClass().getClassLoader())) {
			var fixture = Class.forName("generated.LargeTypingFixture", false, classLoader);
			var mod = new MethodAliasGenerator.ModData("largefixture", List.of(fixture), List.of());
			var aliases = MethodAliasGenerator.methodAliases(List.of(mod));
			// Every kgy$-prefixed member yields exactly one stripped alias.
			assertEquals(10_000, aliases.size());
			assertTrue(aliases.contains(new MethodAliasGenerator.MethodAlias(fixture.getCanonicalName(), "kgy$member9999", "member9999")));
			assertFalse(aliases.stream().anyMatch(alias -> alias.to().contains("kgy$")));
		}
	}

	@Test
	@Disabled("Retired: LivePlugin plugin.kts generation (template absent)")
	void modGdslFilesAreLeftUntouched() throws Exception {
		var game = temp.resolve("mod-cleanup-game");
		var mods = game.resolve("kubegroovy/typings/mods");
		Files.createDirectories(mods);
		var staleGenerated = mods.resolve("removed_mod.gdsl");
		Files.writeString(staleGenerated, "// " + KubeGroovyTypingGenerator.GENERATED_MARKER + "\nold");
		var manual = mods.resolve("notes.gdsl");
		Files.writeString(manual, "manual typing notes");

		// GDSL generation is disabled: existing GDSL files are neither pruned nor overwritten.
		KubeGroovyTypingGenerator.generate(game, Map.of());
		assertEquals("// " + KubeGroovyTypingGenerator.GENERATED_MARKER + "\nold", Files.readString(staleGenerated));
		assertEquals("manual typing notes", Files.readString(manual));

		var conflict = mods.resolve("manualmod.gdsl");
		Files.writeString(conflict, "manual mod typings");
		var mod = new MethodAliasGenerator.ModData("manualmod", List.of(GeneratorFixture.class), List.of());
		KubeGroovyTypingGenerator.generate(game, Map.of(), List.of(mod), List.of());
		assertEquals("manual mod typings", Files.readString(conflict));
	}

	private static final class WrapperFixture {
	}

	@RemapPrefixForGY("kgy$")
	public static class GeneratorFixture {
		public GeneratorFixture(WrapperFixture wrapper) {
		}

		@HideFromGY
		public GeneratorFixture(WrapperFixture[] wrappers) {
		}

		public String kgy$getId() { return "id"; }

		@RemapForGY("friendlyName")
		public String originalName() { return "name"; }

		public String withContext(Context context, String value) { return value; }

		public void contextOnly(Context context) {
		}

		public boolean matches(RecipeMatchContext context, ItemStack stack, boolean strict) { return strict; }

		public void recipeContextOnly(RecipeMatchContext context) {
		}

		public void takesWrapper(WrapperFixture wrapper) {
		}

		public void wrappedElements(WrapperFixture[] wrappers) {
		}

		public void finalArray(String[] values) {
		}

		public void nonFinalArray(String[] values, int count) {
		}

		public static void staticWrapped(WrapperFixture wrapper) {
		}

		@RemapForGY("staticFriendly")
		public static void staticOriginal(WrapperFixture wrapper) {
		}

		@HideFromGY
		public void hidden() {
		}

		private void privateMethod() {
		}
	}
}
