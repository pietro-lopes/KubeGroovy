package dev.uncandango.kubegroovy.intellij;

import com.intellij.openapi.util.Disposer;
import com.intellij.codeInsight.completion.CompletionType;
import java.util.Arrays;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor;
import org.jetbrains.plugins.groovy.lang.typing.GrTypeCalculator;

import java.nio.file.Files;
import java.nio.file.Path;

public final class RegistryBuilderInferenceTest extends LivePluginTestCase {
	private Path config;
	private int fileNumber;

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		myFixture.addFileToProject("example/Block.java", "package example; public class Block {}");
		myFixture.addFileToProject("example/Item.java", "package example; public class Item {}");
		myFixture.addFileToProject("example/Registry.java", "package example; public class Registry<T> {}");
		myFixture.addFileToProject("example/ResourceKey.java", "package example; public class ResourceKey<T> {}");
		myFixture.addFileToProject("example/BuilderBase.java", "package example; public class BuilderBase<T> {}");
		myFixture.addFileToProject("example/BlockBuilder.java", "package example; public class BlockBuilder extends BuilderBase<Block> { public BlockBuilder blockEntity(java.util.function.Consumer<Object> callback) { return this; } }");
		myFixture.addFileToProject("example/DetectorBlock.java", "package example; public class DetectorBlock { public static class Builder extends BlockBuilder { public Builder detectorId(String id) { return this; } } }");
		myFixture.addFileToProject("example/ItemBuilder.java", "package example; public class ItemBuilder extends BuilderBase<Item> {}");
		myFixture.addFileToProject("dev/uncandango/kubegroovy/registry/RegistryKubeEvent.java", """
			package dev.uncandango.kubegroovy.registry;
			public class RegistryKubeEvent<T> {
				public example.BuilderBase<? extends T> create(String id) { return null; }
				public example.BuilderBase<? extends T> create(String id, String type) { return null; }
			}
			""");
		myFixture.addFileToProject("dev/uncandango/kubegroovy/registry/ServerRegistryKubeEvent.java", "package dev.uncandango.kubegroovy.registry; public class ServerRegistryKubeEvent<T> extends RegistryKubeEvent<T> {}");
		myFixture.addFileToProject("dev/uncandango/kubegroovy/typing/EventGroupBinding.java", "package dev.uncandango.kubegroovy.typing; public class EventGroupBinding {}");
		ClassLoader loader = loadScript("events");
		config = Files.createTempFile("kgy-builder-inference", ".json");
		Files.writeString(config, """
			{"_registryTargets":[{"key":"minecraft:block","elementType":"example.Block"},{"key":"minecraft:item","elementType":"example.Item"}],
			"_builderTypes":[
			{"registryKey":"minecraft:block","typeId":"kubegroovy:default","builderType":"example.BlockBuilder","defaultType":true},
			{"registryKey":"minecraft:block","typeId":"kubegroovy:detector","builderType":"example.DetectorBlock.Builder","defaultType":false},
			{"registryKey":"minecraft:item","typeId":"addon:detector","builderType":"example.ItemBuilder","defaultType":false}],
			"startup":{"$StartupEvents":[{"name":"registry","returns":"void","typeParams":["T"],
			"params":[{"name":"key","type":"example.ResourceKey<example.Registry<T>>"},
			{"name":"callback","type":"java.util.function.Consumer<dev.uncandango.kubegroovy.registry.RegistryKubeEvent<T>>"}]}]}}
			""");
		loader.loadClass("EventMethodSupport").getMethod("setConfigFile", java.io.File.class).invoke(null, config.toFile());
		var contributor = (NonCodeMembersContributor) loader.loadClass("EventMethodsContributor").getConstructor().newInstance();
		NonCodeMembersContributor.EP_NAME.getPoint().registerExtension(contributor, getTestRootDisposable());
		var calculator = (GrTypeCalculator<?>) loader.loadClass("RegistryBuilderTypeCalculator").getConstructor().newInstance();
		GrTypeCalculator.EP.addExplicitExtension(GrMethodCallExpression.class, calculator);
		Disposer.register(getTestRootDisposable(), () -> GrTypeCalculator.EP.removeExplicitExtension(GrMethodCallExpression.class, calculator));
	}

	@Override
	protected void tearDown() throws Exception {
		try {
			if (config != null) Files.deleteIfExists(config);
		} finally {
			super.tearDown();
		}
	}

	private PsiFile script(String root, String element, String expression) {
		return myFixture.addFileToProject(root + "/Builder" + fileNumber++ + ".groovy", """
			import example.*
			import dev.uncandango.kubegroovy.typing.EventGroupBinding
			def $StartupEvents = new EventGroupBinding()
			$StartupEvents.registry('block' as ResourceKey<Registry<%s>>) { event ->
				%s
			}
			""".formatted(element, expression));
	}

	private void assertCreateType(String element, String arguments, String expected) {
		PsiFile file = script("kubegroovy/startup_scripts", element, "event.create(" + arguments + ")");
		GrMethodCallExpression create = PsiTreeUtil.findChildrenOfType(file, GrMethodCallExpression.class).stream()
			.filter(call -> call.getInvokedExpression().getText().endsWith(".create")).findFirst().orElseThrow();
		assertNotNull(create.getType());
		assertEquals(expected, create.getType().getCanonicalText());
	}

	public void testDetectorAndDefaultBuilders() {
		assertCreateType("Block", "'test', 'detector'", "example.DetectorBlock.Builder");
		assertCreateType("Block", "'test', 'kubegroovy:detector'", "example.DetectorBlock.Builder");
		assertCreateType("Block", "'test', 'other:detector'", "example.DetectorBlock.Builder");
		assertCreateType("Block", "'test'", "example.BlockBuilder");
		assertCreateType("Item", "'test', 'detector'", "example.ItemBuilder");
	}

	public void testDetectorMethodsResolveOnChainAndLocalVariable() {
		PsiFile file = script("kubegroovy/startup_scripts", "Block", """
			event.create('test', 'detector').detectorId('test')
			def builder = event.create('test', 'detector')
			builder.detectorId('test')
			event.create('test').blockEntity { be -> }
			""");
		for (GrReferenceExpression ref : PsiTreeUtil.findChildrenOfType(file, GrReferenceExpression.class)) {
			if ("detectorId".equals(ref.getReferenceName()) || "blockEntity".equals(ref.getReferenceName())) {
				assertInstanceOf(ref.resolve(), PsiMethod.class);
			}
		}
	}

	public void testDetectorCompletionOffersItsOwnMethod() {
		PsiFile file = script("kubegroovy/startup_scripts", "Block", "event.create('test', 'detector').det");
		myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
		myFixture.getEditor().getCaretModel().moveToOffset(file.getText().lastIndexOf(".det") + 4);
		var lookups = myFixture.complete(CompletionType.BASIC);
		// A single result may be inserted automatically by the fixture.
		if (lookups == null) assertTrue(myFixture.getEditor().getDocument().getText().contains(".detectorId("));
		else assertTrue(Arrays.stream(lookups).anyMatch(it -> "detectorId".equals(it.getLookupString())));
	}

	public void testUnknownAndDynamicTypesKeepDeclaredReturnType() {
		assertCreateType("Block", "'test', 'missing'", "example.BuilderBase<? extends example.Block>");
		assertCreateType("Block", "'test', System.getProperty('type')", "example.BuilderBase<? extends example.Block>");
	}

	public void testUnrelatedCreateAndOutsideScriptAreUnaffected() {
		myFixture.addFileToProject("example/Other.java", "package example; public class Other { public String create(String id, String type) { return null; } }");
		for (String root : new String[]{"src", "kubegroovy/startup_scripts"}) {
			PsiFile file = myFixture.addFileToProject(root + "/Other" + fileNumber++ + ".groovy", "new example.Other().create('id', 'detector')");
			var call = PsiTreeUtil.findChildrenOfType(file, GrMethodCallExpression.class).iterator().next();
			assertEquals("java.lang.String", call.getType().getCanonicalText());
		}
		PsiFile file = myFixture.addFileToProject("src/Outside.groovy", """
			def event = new dev.uncandango.kubegroovy.registry.RegistryKubeEvent<example.Block>()
			event.create('test', 'detector')
			""");
		var call = PsiTreeUtil.findChildrenOfType(file, GrMethodCallExpression.class).iterator().next();
		assertEquals("example.BuilderBase<? extends example.Block>", call.getType().getCanonicalText());
	}

	public void testAmbiguousAliasAndMissingBuilderClassStayUnspecialized() throws Exception {
		String original = Files.readString(config);
		Files.writeString(config, original.replace("\"_builderTypes\":[", """
			"_builderTypes":[
			{"registryKey":"minecraft:block","typeId":"addon:detector","builderType":"example.BlockBuilder","defaultType":false},
			{"registryKey":"minecraft:block","typeId":"addon:missingclass","builderType":"example.MissingBuilder","defaultType":false},
			"""));
		assertCreateType("Block", "'test', 'other:detector'", "example.BuilderBase<? extends example.Block>");
		assertCreateType("Block", "'test', 'detector'", "example.DetectorBlock.Builder");
		assertCreateType("Block", "'test', 'kubegroovy:detector'", "example.DetectorBlock.Builder");
		assertCreateType("Block", "'test', 'missingclass'", "example.BuilderBase<? extends example.Block>");
	}

	public void testServerRegistryBuilderType() {
		PsiFile file = myFixture.addFileToProject("kubegroovy/server_scripts/Server.groovy", """
			def event = new dev.uncandango.kubegroovy.registry.ServerRegistryKubeEvent<example.Block>()
			event.create('test', 'detector')
			""");
		var call = PsiTreeUtil.findChildrenOfType(file, GrMethodCallExpression.class).iterator().next();
		assertEquals("example.DetectorBlock.Builder", call.getType().getCanonicalText());
	}

	public void testAmbiguousRegistryElementAndOldCatalogKeepDeclaredType() throws Exception {
		String original = Files.readString(config);
		Files.writeString(config, original.replace("\"_registryTargets\":[", "\"_registryTargets\":[{\"key\":\"addon:block\",\"elementType\":\"example.Block\"},"));
		assertCreateType("Block", "'test', 'detector'", "example.BuilderBase<? extends example.Block>");
		Files.writeString(config, original.substring(0, original.indexOf("\"_registryTargets\"")) + original.substring(original.indexOf("\"startup\"")));
		assertCreateType("Block", "'test', 'detector'", "example.BuilderBase<? extends example.Block>");
	}
}