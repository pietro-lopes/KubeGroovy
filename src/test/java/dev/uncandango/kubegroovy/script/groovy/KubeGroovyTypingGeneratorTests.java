package dev.uncandango.kubegroovy.typingtest;

import dev.uncandango.kubegroovy.core.AdvancementNodeKGY;
import dev.uncandango.kubegroovy.recipe.filter.RecipeMatchContext;
import dev.uncandango.kubegroovy.script.annotations.HideFromGY;
import dev.uncandango.kubegroovy.script.annotations.RemapForGY;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import dev.uncandango.kubegroovy.script.bridge.Context;
import dev.uncandango.kubegroovy.script.bridge.TypeWrappers;
import dev.uncandango.kubegroovy.script.type.TypeInfo;
import dev.uncandango.kubegroovy.typing.KubeGroovyTypingGenerator;
import dev.uncandango.kubegroovy.typing.ModGdslGenerator;
import dev.uncandango.kubegroovy.typing.TypingBinding;
import dev.uncandango.kubegroovy.testmod.assertion.KubeEntityEventAssert;
import groovy.lang.GroovyShell;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.world.item.ItemStack;
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
	void generationWritesOutputsAndRefusesManualGdsl() throws Exception {
		var game = temp.resolve("game");
		var bindings = Map.of(dev.uncandango.kubegroovy.script.ScriptType.SERVER, List.<TypingBinding>of());
		var first = KubeGroovyTypingGenerator.generate(game, bindings);
		assertTrue(Files.isRegularFile(first.module()));
		assertTrue(Files.isRegularFile(first.modules()));
		assertTrue(Files.isRegularFile(game.resolve(".idea/misc.xml")));
		assertTrue(Files.isRegularFile(first.gdsl()));
		assertEquals(game.resolve("kubegroovy/typings/kubegroovy.gdsl").normalize(), first.gdsl());
		assertEquals(game.resolve(".live-plugins/kubegroovy/plugin.kts").normalize(), first.livePlugin());
		assertTrue(Files.isRegularFile(first.livePlugin()));
		assertTrue(Files.readString(first.livePlugin()).contains(KubeGroovyTypingGenerator.GENERATED_MARKER));
		var events = game.resolve("kubegroovy/typings/events.gdsl");
		var catalog = first.livePlugin().resolveSibling("catalog.json");
		assertTrue(Files.isRegularFile(catalog));
		assertTrue(Files.readString(catalog).contains(KubeGroovyTypingGenerator.GENERATED_MARKER));
		assertTrue(Files.isRegularFile(events));
		assertTrue(Files.readString(game.resolve(".idea/misc.xml")).contains("project-jdk-name=\"25\""));
		var before = Files.readString(first.gdsl());
		var eventsBefore = Files.readString(events);
		var catalogBefore = Files.readString(catalog);
		var second = KubeGroovyTypingGenerator.generate(game, bindings);
		assertFalse(second.gdslChanged());
		assertFalse(second.livePluginChanged());
		assertEquals(before, Files.readString(first.gdsl()));
		assertEquals(eventsBefore, Files.readString(events));
		assertEquals(catalogBefore, Files.readString(catalog));
		Files.writeString(events, "manual events file");
		assertThrows(Exception.class, () -> KubeGroovyTypingGenerator.generate(game, bindings));
		assertEquals("manual events file", Files.readString(events));
		Files.writeString(first.gdsl(), "manual file");
		assertThrows(Exception.class, () -> KubeGroovyTypingGenerator.generate(game, bindings));
		assertEquals("manual file", Files.readString(first.gdsl()));
		var manualPluginGame = temp.resolve("manual-live-plugin-game");
		var manualPluginResult = KubeGroovyTypingGenerator.generate(manualPluginGame, bindings);
		Files.writeString(manualPluginResult.livePlugin(), "manual LivePlugin script");
		assertThrows(Exception.class, () -> KubeGroovyTypingGenerator.generate(manualPluginGame, bindings));
		assertEquals("manual LivePlugin script", Files.readString(manualPluginResult.livePlugin()));
	}

	@Test
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

		Files.writeString(result.gdsl(), "manual GDSL");
		assertThrows(Exception.class, () -> KubeGroovyTypingGenerator.generate(game, bindings, true));
		assertEquals("manual GDSL", Files.readString(result.gdsl()));
	}

	@Test
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
	void generatedLivePluginContainsScriptFacingModMembers() throws Exception {
		var wrappers = wrappersFor(WrapperFixture.class);
		var mod = new ModGdslGenerator.ModData("fixture_mod", List.of(GeneratorFixture.class, UnboundGeneratorFixture.class), List.of());
		var startup = dev.uncandango.kubegroovy.script.ScriptType.STARTUP;
		var binding = new TypingBinding("fixture", GeneratorFixture.class.getName(), false, false, startup);
		var result = KubeGroovyTypingGenerator.generate(temp.resolve("plugin-mod-members-game"),
			Map.of(startup, List.of(binding)), List.of(mod), List.of(wrappers));
		var generated = Files.readString(result.livePlugin());
		var fixtureType = GeneratorFixture.class.getCanonicalName();
		var wrapperType = WrapperFixture.class.getCanonicalName();

		assertTrue(generated.contains("private fun kgyMembersChunk0(): List<KubeGroovyScriptMember> = listOf("));
		assertTrue(generated.contains("KubeGroovyScriptData.members = listOf(kgyMembersChunk0()).flatten()"));
		assertTrue(generated.contains("KubeGroovyScriptMember(\"" + fixtureType
			+ "\", \"getId\", \"java.lang.String\", emptyList(), listOf(emptyList()), emptyList(), false, false)"));
		assertTrue(generated.contains("KubeGroovyScriptMember(\"" + fixtureType
			+ "\", \"withContext\", \"java.lang.String\", listOf(\"java.lang.String\"), listOf(listOf(\"java.lang.String\")), emptyList(), false, false)"));
		assertTrue(generated.contains("KubeGroovyScriptMember(\"" + fixtureType
			+ "\", \"contextOnly\", \"void\", emptyList(), listOf(emptyList()), emptyList(), false, false)"));
		assertTrue(generated.contains("KubeGroovyScriptMember(\"" + fixtureType
			+ "\", \"takesWrapper\", \"void\", listOf(\"java.lang.Object\"), listOf(listOf(\"" + wrapperType + "\")), emptyList(), false, false)"));
		assertTrue(generated.contains("KubeGroovyScriptMember(\"" + fixtureType
			+ "\", \"GeneratorFixture\", \"" + fixtureType + "\", listOf(\"java.lang.Object\"), listOf(listOf(\"" + wrapperType + "\")), emptyList(), false, true)"));
		assertTrue(generated.contains("KubeGroovySuppressedMethod(\"" + fixtureType
			+ "\", \"withContext\", listOf(\"dev.uncandango.kubegroovy.script.bridge.Context\", \"java.lang.String\"))"));
		assertTrue(generated.contains("KubeGroovySuppressedMethod(\"" + fixtureType
			+ "\", \"kgy\\u0024getId\", emptyList())"));
		assertTrue(generated.contains("KubeGroovySuppressedMethod(\"" + fixtureType
			+ "\", \"hidden\", emptyList())"));
		assertTrue(generated.contains("KubeGroovyScriptProperty(\"" + fixtureType
			+ "\", \"id\", \"java.lang.String\", false)"));
		assertFalse(generated.contains("KubeGroovyScriptMember(\"" + UnboundGeneratorFixture.class.getCanonicalName() + "\""));
		assertFalse(generated.contains("KubeGroovyScriptMember(\"" + fixtureType + "\", \"hidden\""));
		assertFalse(generated.contains("KGY_MEMBER_DATA"));
	}

	@Test
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
	void livePluginKeepsOriginalTypesWhenWrapperOverloadsShareOneVisibleSignature() throws Exception {
		var fixture = OverloadedSemanticFixture.class;
		var wrappers = wrappersFor(WrapperFixture.class, ItemStack.class);
		var mod = new ModGdslGenerator.ModData("semantic_fixture", List.of(fixture), List.of());
		var binding = new TypingBinding("semantic", fixture.getName(), false, false,
			dev.uncandango.kubegroovy.script.ScriptType.STARTUP);
		var result = KubeGroovyTypingGenerator.generate(temp.resolve("semantic-overload-game"),
			Map.of(dev.uncandango.kubegroovy.script.ScriptType.STARTUP, List.of(binding)), List.of(mod), List.of(wrappers));
		var generated = Files.readString(result.livePlugin());
		var visibleMethods = generated.lines().filter(line -> line.contains("KubeGroovyScriptMember(\"" + fixture.getCanonicalName()
			+ "\", \"convert\"")).toList();
		assertEquals(1, visibleMethods.size());
		var visibleMethod = visibleMethods.getFirst();
		assertTrue(visibleMethod.contains("listOf(\"java.lang.Object\")"));
		assertTrue(visibleMethod.contains("net.minecraft.world.item.ItemStack"));
		assertTrue(visibleMethod.contains(WrapperFixture.class.getCanonicalName()));
	}

	@Test
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
	void modGdslRemapsNamesDropsContextAddsWrapperOverloadsAndFiltersHiddenMembers() {
		var wrappers = wrappersFor(WrapperFixture.class);
		var text = ModGdslGenerator.render(new ModGdslGenerator.ModData("testmod", List.of(GeneratorFixture.class), List.of()), List.of(wrappers));

		assertTrue(text.contains("method(name: 'getId', type: 'java.lang.String')"));
		assertFalse(text.contains("kgy$getId"));
		assertFalse(text.contains("method(name: 'originalName'"));
		assertTrue(text.contains("method(name: 'friendlyName', type: 'java.lang.String')"));
		assertTrue(text.contains("method(name: 'withContext', type: 'java.lang.String', params: [arg0: 'java.lang.String'])"));
		assertTrue(text.contains("method(name: 'contextOnly', type: 'void')"));
		assertFalse(text.contains("dev.uncandango.kubegroovy.script.bridge.Context'"));
		assertTrue(text.contains("method(name: 'matches', type: 'boolean', params: [arg0: 'net.minecraft.world.item.ItemStack', arg1: 'boolean'])"));
		assertTrue(text.contains("method(name: 'recipeContextOnly', type: 'void')"));
		assertFalse(text.contains("RecipeMatchContext"));
		assertTrue(text.contains("method(name: 'takesWrapper', type: 'void', params: [arg0: 'java.lang.Object'])"));
		assertTrue(text.contains("method(name: 'wrappedElements', type: 'void', params: [arg0: 'java.util.List<java.lang.Object>'])"));
		assertTrue(text.contains("method(name: 'finalArray', type: 'void', params: [arg0: 'java.lang.Object[]'])"));
		assertTrue(text.contains("method(name: 'nonFinalArray', type: 'void', params: [arg0: 'java.util.List<java.lang.String>', arg1: 'int'])"));
		assertTrue(text.contains("method(name: 'staticFriendly', type: 'void', params: [arg0: 'java.lang.Object'], isStatic: true)"));
		assertTrue(text.contains("property(name: 'id', type: 'java.lang.String')"));
		assertFalse(text.contains("hidden"));
		assertFalse(text.contains("privateMethod"));
		new GroovyShell().parse(text);
	}

	@Test
	void wrapperConstructorsAreGeneratedAndDuplicateSignaturesCollapse() {
		var wrappers = wrappersFor(WrapperFixture.class);
		var bridge = new ModGdslGenerator.ReceiverBridge(GeneratorFixture.class,
			List.of(DuplicateBridgeA.class, DuplicateBridgeB.class), List.of());
		var text = ModGdslGenerator.render(new ModGdslGenerator.ModData("testmod", List.of(GeneratorFixture.class), List.of(bridge)), List.of(wrappers));

		assertTrue(text.contains("method(name: 'GeneratorFixture', type: '" + GeneratorFixture.class.getName()
			+ "', params: [arg0: 'java.lang.Object'], constructor: true)"));
		assertFalse(text.contains("method(name: 'GeneratorFixture', type: '" + GeneratorFixture.class.getName()
			+ "', params: [arg0: 'java.util.List<java.lang.Object>'], constructor: true)"));
		assertEquals(1, text.split("constructor: true", -1).length - 1);
		assertEquals(1, text.split("method\\(name: 'shared', type: 'java.lang.String'", -1).length - 1);
		assertTrue(text.contains("method(name: 'shared', type: 'java.lang.String')"));
		new GroovyShell().parse(text);
	}

	@Test
	void injectedInterfaceMembersUseTheirActualReceiverAndRemappedNames() {
		var bridge = new ModGdslGenerator.ReceiverBridge(AdvancementNode.class, List.of(AdvancementNodeKGY.class), List.of());
		var mod = new ModGdslGenerator.ModData("kubegroovy", List.of(), List.of(bridge));
		var text = ModGdslGenerator.render(mod, List.of());

		assertTrue(text.contains("contributor(context(ctype: 'net.minecraft.advancements.AdvancementNode'))"));
		assertTrue(text.contains("method(name: 'getId', type: 'net.minecraft.resources.Identifier')"));
		assertFalse(text.contains("kgy$getId"));
		var livePluginApi = ModGdslGenerator.describeForLivePlugin(List.of(mod), List.of(), List.of(AdvancementNode.class.getName()));
		assertTrue(livePluginApi.members().stream().anyMatch(member -> member.receiverType().equals(AdvancementNode.class.getCanonicalName())
			&& member.name().equals("getId")));
		new GroovyShell().parse(text);
	}

	@Test
	void unchangedInheritedObjectArrayMethodsAreNotGenerated() {
		var text = ModGdslGenerator.render(new ModGdslGenerator.ModData("testmod",
			List.of(KubeEntityEventAssert.class), List.of()), List.of());

		for (var name : List.of("as", "describedAs", "doesNotHaveToString", "hasToString", "isIn", "isNotIn", "overridingErrorMessage")) {
			assertFalse(text.contains("method(name: '" + name + "'"), name);
		}
		assertTrue(text.contains("method(name: 'isInstanceOfAny'"));
	}

	@Test
	void modIdsProduceSeparateFilesAndRuntimeMissingClassesAreSkipped() throws Exception {
		var data = new ModGdslGenerator.ModData("shared_fixture", List.of(GeneratorFixture.class), List.of());
		var generated = ModGdslGenerator.renderAll(List.of(data,
			new ModGdslGenerator.ModData("second_fixture", List.of(GeneratorFixture.class), List.of())), List.of());
		assertEquals(List.of("second_fixture", "shared_fixture"), List.copyOf(generated.keySet()));
		assertEquals(generated.get("shared_fixture").replace("// Mod: shared_fixture", "// Mod: <modid>"),
			generated.get("second_fixture").replace("// Mod: second_fixture", "// Mod: <modid>"));
		assertEquals(List.of(String.class), ModGdslGenerator.loadClasses(List.of(String.class.getName(), "missing.runtime.ClientOnlyType"), getClass().getClassLoader()));

		var game = temp.resolve("per-mod-game");
		var result = KubeGroovyTypingGenerator.generate(game, Map.of(), List.of(data,
			new ModGdslGenerator.ModData("second_fixture", List.of(GeneratorFixture.class), List.of())), List.of());
		var mods = game.resolve("kubegroovy/typings/mods");
		assertTrue(Files.isRegularFile(mods.resolve("shared_fixture.gdsl")));
		assertTrue(Files.isRegularFile(mods.resolve("second_fixture.gdsl")));
		var firstText = Files.readString(mods.resolve("shared_fixture.gdsl"));
		var second = KubeGroovyTypingGenerator.generate(game, Map.of(), List.of(data,
			new ModGdslGenerator.ModData("second_fixture", List.of(GeneratorFixture.class), List.of())), List.of());
		assertFalse(second.gdslChanged());
		assertEquals(firstText, Files.readString(mods.resolve("shared_fixture.gdsl")));
		assertTrue(result.gdsl().endsWith("kubegroovy.gdsl"));
	}

	@Test
	void largeModGdslValidatesWithoutCompilingOneOversizedGroovyMethod() throws Exception {
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
			var mod = new ModGdslGenerator.ModData("largefixture", List.of(fixture), List.of());
			var game = temp.resolve("large-gdsl-game");
			var startup = dev.uncandango.kubegroovy.script.ScriptType.STARTUP;
			var binding = new TypingBinding("largeFixture", fixture.getName(), false, false, startup);
			KubeGroovyTypingGenerator.generate(game, Map.of(startup, List.of(binding)), List.of(mod), List.of());
			var generated = Files.readString(game.resolve("kubegroovy/typings/mods/largefixture.gdsl"));
			assertTrue(generated.length() > 65_535);
			assertTrue(generated.contains("method(name: 'member9999'"));
			assertFalse(generated.contains("kgy$member"));
			var generatedPlugin = Files.readString(game.resolve(".live-plugins/kubegroovy/plugin.kts"));
			assertTrue(generatedPlugin.contains("private fun kgyMembersChunk624(): List<KubeGroovyScriptMember> = listOf("));
			assertTrue(generatedPlugin.contains("private fun kgySuppressedMethodsChunk624(): List<KubeGroovySuppressedMethod> = listOf("));
			assertTrue(generatedPlugin.contains("KubeGroovyScriptData.members = listOf(kgyMembersChunk0(), kgyMembersChunk1()"));
			assertFalse(generatedPlugin.contains("// KGY_MEMBER_DATA"));
		}
	}

	@Test
	void modGdslCleanupAndManualTargetsRespectGeneratorMarker() throws Exception {
		var game = temp.resolve("mod-cleanup-game");
		var mods = game.resolve("kubegroovy/typings/mods");
		Files.createDirectories(mods);
		var staleGenerated = mods.resolve("removed_mod.gdsl");
		Files.writeString(staleGenerated, "// " + KubeGroovyTypingGenerator.GENERATED_MARKER + "\nold");
		var manual = mods.resolve("notes.gdsl");
		Files.writeString(manual, "manual typing notes");

		KubeGroovyTypingGenerator.generate(game, Map.of());
		assertFalse(Files.exists(staleGenerated));
		assertEquals("manual typing notes", Files.readString(manual));

		var conflict = mods.resolve("manualmod.gdsl");
		Files.writeString(conflict, "manual mod typings");
		var mod = new ModGdslGenerator.ModData("manualmod", List.of(GeneratorFixture.class), List.of());
		assertThrows(Exception.class, () -> KubeGroovyTypingGenerator.generate(game, Map.of(), List.of(mod), List.of()));
		assertEquals("manual mod typings", Files.readString(conflict));
	}

	private static TypeWrappers wrappersFor(Class<?>... types) {
		var wrappers = new TypeWrappers();
		for (var type : types) wrappers.wrappers.put(type,
			new TypeWrappers.Entry(dev.uncandango.kubegroovy.script.bridge.TypeWrapperValidator.ALWAYS_VALID, null, List.of()));
		return wrappers;
	}

	private static final class WrapperFixture {
	}

	public static class UnboundGeneratorFixture {
		public void unrelatedMethod() {
		}
	}

	public static class OverloadedSemanticFixture {
		public void convert(ItemStack item) {
		}

		public void convert(WrapperFixture wrapper) {
		}
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

	public interface DuplicateBridgeA {
		String shared();
	}

	public interface DuplicateBridgeB {
		String shared();
	}
}
