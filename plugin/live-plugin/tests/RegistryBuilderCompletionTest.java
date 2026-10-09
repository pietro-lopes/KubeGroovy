package dev.uncandango.kubegroovy.intellij;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.completion.CompletionContributorEP;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.extensions.DefaultPluginDescriptor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.LoadingOrder;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiFile;
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public final class RegistryBuilderCompletionTest extends LivePluginTestCase {
	private Path config;
	private int fileNumber;

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		ClassLoader loader = loadScript("type-suggestions");
		config = Files.createTempFile("kgy-builder-completion", ".json");
		Files.writeString(config, """
			{"schemaVersion":1,"values":{},"delegates":[],"genericDelegates":{},
			"registryBuilderTypes":{"example.Block":["detector","kubegroovy:detector","addon:special"],"example.Item":["addon:item"]}}
			""");
		setSuggestionConfig(loader, config.toFile());
		var descriptor = new DefaultPluginDescriptor(PluginId.getId("LivePlugin"), loader);
		ExtensionPointName.<CompletionContributorEP>create("com.intellij.completion.contributor").getPoint()
			.registerExtension(new CompletionContributorEP("Groovy", "TypeSuggestionCompletionContributor", descriptor),
				LoadingOrder.FIRST, getTestRootDisposable());
		boolean autoComplete = CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION;
		CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = false;
		Disposer.register(getTestRootDisposable(), () -> CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = autoComplete);
		myFixture.addFileToProject("example/Block.java", "package example; public class Block {}");
		myFixture.addFileToProject("example/Item.java", "package example; public class Item {}");
		myFixture.addFileToProject("dev/uncandango/kubegroovy/util/KubeIdentifier.java", "package dev.uncandango.kubegroovy.util; public class KubeIdentifier {}");
		myFixture.addFileToProject("dev/uncandango/kubegroovy/registry/RegistryKubeEvent.java", """
			package dev.uncandango.kubegroovy.registry;
			public class RegistryKubeEvent<T> {
				public Object create(dev.uncandango.kubegroovy.util.KubeIdentifier id) { return null; }
				public Object create(dev.uncandango.kubegroovy.util.KubeIdentifier id, dev.uncandango.kubegroovy.util.KubeIdentifier type) { return null; }
			}
			""");
		myFixture.addFileToProject("dev/uncandango/kubegroovy/registry/ServerRegistryKubeEvent.java", "package dev.uncandango.kubegroovy.registry; public class ServerRegistryKubeEvent<T> extends RegistryKubeEvent<T> {}");
	}

	@Override
	protected void tearDown() throws Exception {
		try {
			if (config != null) Files.deleteIfExists(config);
		} finally {
			super.tearDown();
		}
	}

	private LookupElement[] complete(String root, String receiver, String expression) {
		String source = "def event = new " + receiver + "()\n" + expression;
		return completeSource(root, source);
	}

	private LookupElement[] completeSource(String root, String source) {
		int offset = source.indexOf("<caret>");
		PsiFile file = myFixture.addFileToProject(root + "/BuilderCompletion" + fileNumber++ + ".groovy", source.replace("<caret>", ""));
		myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
		myFixture.getEditor().getCaretModel().moveToOffset(offset);
		return myFixture.complete(CompletionType.BASIC);
	}

	private String receiver(String element) {
		return "dev.uncandango.kubegroovy.registry.RegistryKubeEvent<example." + element + ">";
	}

	private void choose(String expression, String id, String expected) {
		LookupElement[] elements = complete("kubegroovy/startup_scripts", receiver("Block"), expression);
		assertNotNull(elements);
		LookupElement selected = Arrays.stream(elements).filter(it -> id.equals(it.getObject())).findFirst().orElseThrow();
		myFixture.getLookup().setCurrentItem(selected);
		myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);
		assertEquals("def event = new " + receiver("Block") + "()\n" + expected, myFixture.getEditor().getDocument().getText());
	}

	public void testQuotedAndEmptySecondArgumentInsertBuilderString() {
		choose("event.create('id', '<caret>')", "detector", "event.create('id', \"detector\")");
		choose("event.create('id', <caret>)", "addon:special", "event.create('id', \"addon:special\")");
		choose("event.create('id', 'kubegroovy:d<caret>')", "kubegroovy:detector", "event.create('id', \"kubegroovy:detector\")");
	}

	public void testSuggestionsAreSpecificToReceiverRegistry() {
		var elements = complete("kubegroovy/startup_scripts", receiver("Item"), "event.create('id', '<caret>')");
		assertNotNull(elements);
		assertTrue(Arrays.stream(elements).anyMatch(it -> "addon:item".equals(it.getObject())));
		assertFalse(Arrays.stream(elements).anyMatch(it -> "detector".equals(it.getObject())));
	}

	public void testBuilderVariablesDoNotOfferLiteralSuggestions() {
		for (String argument : List.of("det<caret>", "<caret>detector", "\"${det<caret>}\"", "\"${<caret>}\"")) {
			var elements = complete("kubegroovy/startup_scripts", receiver("Block"), "event.create('id', " + argument + ")");
			assertTrue("Builder names offered in code: " + argument, elements == null || Arrays.stream(elements)
				.noneMatch(it -> {
					var builder = it.as(LookupElementBuilder.class);
					return builder != null && builder.getInsertHandler() != null &&
						"TypeSuggestionInsertHandler".equals(builder.getInsertHandler().getClass().getSimpleName());
				}));
		}
	}

	public void testServerRegistryAndExplicitCastPreserveInsertion() {
		var elements = complete("kubegroovy/server_scripts", "dev.uncandango.kubegroovy.registry.ServerRegistryKubeEvent<example.Block>", "event.create('id', '<caret>')");
		assertNotNull(elements);
		assertTrue(Arrays.stream(elements).anyMatch(it -> "detector".equals(it.getObject())));
		choose("event.create('id', '<caret>' as dev.uncandango.kubegroovy.util.KubeIdentifier)", "detector",
			"event.create('id', \"detector\" as dev.uncandango.kubegroovy.util.KubeIdentifier)");
	}

	public void testFirstArgumentAndUnrelatedFilesDoNotOfferBuilderNames() {
		for (String expression : List.of("event.create('<caret>')", "event.create('<caret>', 'detector')")) {
			var elements = complete("kubegroovy/startup_scripts", receiver("Block"), expression);
			if (elements != null) assertFalse(Arrays.stream(elements).anyMatch(it -> "detector".equals(it.getObject())));
		}
		var elements = complete("src", receiver("Block"), "event.create('id', '<caret>')");
		if (elements != null) assertFalse(Arrays.stream(elements).anyMatch(it -> "detector".equals(it.getObject())));
	}

	public void testPlainRegistryStringOffersTargetAndBuilderNames() throws Exception {
		myFixture.addFileToProject("net/minecraft/core/Registry.java", "package net.minecraft.core; public class Registry<T> {}");
		myFixture.addFileToProject("net/minecraft/resources/ResourceKey.java", "package net.minecraft.resources; public class ResourceKey<T> {}");
		myFixture.addFileToProject("dev/uncandango/kubegroovy/typing/EventGroupBinding.java", "package dev.uncandango.kubegroovy.typing; public class EventGroupBinding {}");
		Path events = Files.createTempFile("kgy-builder-completion-events", ".json");
		Path bindings = Files.createTempFile("kgy-builder-completion-bindings", ".json");
		try {
			Files.writeString(config, Files.readString(config).replace("\"values\":{}",
				"\"values\":{\"net.minecraft.core.Registry\":[\"minecraft:block\",\"addon:item\"]}"));
			Files.writeString(events, """
				{"_registryTargets":[{"key":"minecraft:block","elementType":"example.Block"}],
				"startup":{"$StartupEvents":[{"name":"registry","returns":"void","typeParams":["T"],
				"params":[{"name":"key","type":"net.minecraft.resources.ResourceKey<net.minecraft.core.Registry<T>>"},
				{"name":"callback","type":"java.util.function.Consumer<dev.uncandango.kubegroovy.registry.RegistryKubeEvent<T>>"}]}]}}
				""");
			Files.writeString(bindings, "{\"startup\":[{\"name\":\"$StartupEvents\",\"type\":\"example.Events\",\"eventGroup\":true}]}");
			for (String[] script : new String[][]{{"events", "EventMethodSupport", "EventMethodsContributor"}, {"bindings", "BindingsSupport", "BindingsContributor"}}) {
				ClassLoader loader = loadScript(script[0]);
				loader.loadClass(script[1]).getMethod("setConfigFile", java.io.File.class).invoke(null, (script[0].equals("events") ? events : bindings).toFile());
				NonCodeMembersContributor.EP_NAME.getPoint().registerExtension(
					(NonCodeMembersContributor) loader.loadClass(script[2]).getConstructor().newInstance(), getTestRootDisposable());
			}
			for (String source : List.of("$StartupEvents.registry('<caret>')", "$StartupEvents.registry('<caret>') { event -> }",
				"$StartupEvents.registry('minecraft:b<caret>') { event -> }", "$StartupEvents.registry(<caret>) { event -> }")) {
				var targets = completeSource("kubegroovy/startup_scripts", source);
				assertNotNull(source, targets);
				assertTrue(source, Arrays.stream(targets).anyMatch(it -> "minecraft:block".equals(it.getObject())));
				assertFalse(source, Arrays.stream(targets).anyMatch(it -> "detector".equals(it.getObject())));
			}
			var elements = completeSource("kubegroovy/startup_scripts", "$StartupEvents.registry('block') { event -> event.create('id', '<caret>') }");
			assertNotNull(elements);
			assertTrue(Arrays.stream(elements).anyMatch(it -> "detector".equals(it.getObject())));
		} finally {
			Files.deleteIfExists(events);
			Files.deleteIfExists(bindings);
		}
	}

	public void testUnknownRegistryAndLegacyCatalogOfferNoBuilderNames() throws Exception {
		var elements = complete("kubegroovy/startup_scripts", "dev.uncandango.kubegroovy.registry.RegistryKubeEvent", "event.create('id', '<caret>')");
		if (elements != null) assertFalse(Arrays.stream(elements).anyMatch(it -> "detector".equals(it.getObject())));
		Files.writeString(config, "{\"schemaVersion\":1,\"values\":{},\"delegates\":[],\"genericDelegates\":{}}");
		elements = complete("kubegroovy/startup_scripts", receiver("Block"), "event.create('id', '<caret>')");
		if (elements != null) assertFalse(Arrays.stream(elements).anyMatch(it -> "detector".equals(it.getObject())));
	}
}