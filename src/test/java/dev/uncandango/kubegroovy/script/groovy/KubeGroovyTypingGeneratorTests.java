package dev.uncandango.kubegroovy.typingtest;

import dev.uncandango.kubegroovy.typing.KubeGroovyTypingGenerator;
import dev.uncandango.kubegroovy.typing.TypingBinding;
import groovy.lang.GroovyShell;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
		assertTrue(text.contains("def base = [scope: scriptScope(null), filetypes: ['groovy', 'gvy', 'gy']]"));
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

		assertFalse(bindingsText.contains("variable(name: 'BlockEvents'"));
		assertFalse(bindingsText.contains("closureInMethod"));
		assertTrue(eventsText.contains("BlockEvents"));
		assertTrue(eventsText.contains("variable(name: 'BlockEvents', type: 'dev.uncandango.kubegroovy.event.EventGroupWrapper')"));
		assertFalse(eventsText.contains("property(name: 'BlockEvents'"));
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
		var events = game.resolve("kubegroovy/typings/events.gdsl");
		assertTrue(Files.isRegularFile(events));
		assertTrue(Files.readString(game.resolve(".idea/misc.xml")).contains("project-jdk-name=\"25\""));
		var before = Files.readString(first.gdsl());
		var eventsBefore = Files.readString(events);
		var second = KubeGroovyTypingGenerator.generate(game, bindings);
		assertFalse(second.gdslChanged());
		assertEquals(before, Files.readString(first.gdsl()));
		assertEquals(eventsBefore, Files.readString(events));
		Files.writeString(events, "manual events file");
		assertThrows(Exception.class, () -> KubeGroovyTypingGenerator.generate(game, bindings));
		assertEquals("manual events file", Files.readString(events));
		Files.writeString(first.gdsl(), "manual file");
		assertThrows(Exception.class, () -> KubeGroovyTypingGenerator.generate(game, bindings));
		assertEquals("manual file", Files.readString(first.gdsl()));
	}
}
