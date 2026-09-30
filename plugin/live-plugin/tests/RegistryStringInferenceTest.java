package dev.uncandango.kubegroovy.intellij;

import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor;
import org.jetbrains.plugins.groovy.lang.typing.GrTypeCalculator;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Exercises coercion and call applicability with both real contributors enabled. */
public final class RegistryStringInferenceTest extends LivePluginTestCase {
	private int fileNumber;
	private Path events;
	private Path bindings;
	private Path overloads;

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		myFixture.addFileToProject("example/Block.java", "package example; public class Block {}");
		myFixture.addFileToProject("example/Item.java", "package example; public class Item {}");
		myFixture.addFileToProject("net/minecraft/core/Registry.java", "package net.minecraft.core; public class Registry<T> {}");
		myFixture.addFileToProject("net/minecraft/resources/ResourceKey.java", "package net.minecraft.resources; public class ResourceKey<T> {}");
		myFixture.addFileToProject("example/BuilderBase.java", "package example; public class BuilderBase<T> {}");
		myFixture.addFileToProject("example/BlockBuilder.java", "package example; public class BlockBuilder extends BuilderBase<Block> { public BlockBuilder blockEntity(java.util.function.Consumer<Object> callback) { return this; } }");
		myFixture.addFileToProject("example/DetectorBlock.java", "package example; public class DetectorBlock { public static class Builder extends BlockBuilder { public Builder detectorId(String id) { return this; } } }");
		myFixture.addFileToProject("dev/uncandango/kubegroovy/util/KubeIdentifier.java", "package dev.uncandango.kubegroovy.util; public class KubeIdentifier {}");
		myFixture.addFileToProject("dev/uncandango/kubegroovy/registry/RegistryKubeEvent.java", """
			package dev.uncandango.kubegroovy.registry;
			import dev.uncandango.kubegroovy.util.KubeIdentifier;
			public class RegistryKubeEvent<T> {
				public example.BuilderBase<? extends T> create(KubeIdentifier id) { return null; }
				public example.BuilderBase<? extends T> create(KubeIdentifier id, KubeIdentifier type) { return null; }
			}
			""");
		myFixture.addFileToProject("example/Registries.java", "package example; public class Registries { public static net.minecraft.resources.ResourceKey<net.minecraft.core.Registry<Block>> BLOCK; }");
		myFixture.addFileToProject("dev/uncandango/kubegroovy/typing/EventGroupBinding.java", "package dev.uncandango.kubegroovy.typing; public class EventGroupBinding {}");
		events = Files.createTempFile("kgy-string-events", ".json");
		bindings = Files.createTempFile("kgy-string-bindings", ".json");
		overloads = Files.createTempFile("kgy-string-overloads", ".json");
		Files.writeString(events, """
			{"_registryTargets":[{"key":"minecraft:block","elementType":"example.Block"},{"key":"addon:item","elementType":"example.Item"}],
			"_builderTypes":[
			{"registryKey":"minecraft:block","typeId":"kubegroovy:default","builderType":"example.BlockBuilder","defaultType":true},
			{"registryKey":"minecraft:block","typeId":"kubegroovy:detector","builderType":"example.DetectorBlock.Builder","defaultType":false}],
			"startup":{"$StartupEvents":[{"name":"registry","returns":"void","typeParams":["T"],
			"params":[{"name":"key","type":"net.minecraft.resources.ResourceKey<net.minecraft.core.Registry<T>>"},
			{"name":"callback","type":"java.util.function.Consumer<dev.uncandango.kubegroovy.registry.RegistryKubeEvent<T>>"}]}]}}
			""");
		Files.writeString(bindings, "{\"startup\":[{\"name\":\"$StartupEvents\",\"type\":\"example.Events\",\"eventGroup\":true}]}");
		Files.writeString(overloads, "{\"targets\":[\"net.minecraft.resources.ResourceKey\",\"dev.uncandango.kubegroovy.util.KubeIdentifier\"]}");
		ClassLoader eventLoader = register("events", "EventMethodSupport", "EventMethodsContributor", events);
		var calculator = (GrTypeCalculator<?>) eventLoader.loadClass("RegistryBuilderTypeCalculator").getConstructor().newInstance();
		GrTypeCalculator.EP.addExplicitExtension(GrMethodCallExpression.class, calculator);
		Disposer.register(getTestRootDisposable(), () -> GrTypeCalculator.EP.removeExplicitExtension(GrMethodCallExpression.class, calculator));
		register("bindings", "BindingsSupport", "BindingsContributor", bindings);
		register("object-overload", "ObjectOverloadSupport", "ObjectOverloadContributor", overloads);
		myFixture.enableInspections(new GroovyAssignabilityCheckInspection());
	}

	private ClassLoader register(String folder, String support, String contributor, Path config) throws Exception {
		ClassLoader loader = loadScript(folder);
		loader.loadClass(support).getMethod("setConfigFile", java.io.File.class).invoke(null, config.toFile());
		var instance = (NonCodeMembersContributor) loader.loadClass(contributor).getConstructor().newInstance();
		NonCodeMembersContributor.EP_NAME.getPoint().registerExtension(instance, getTestRootDisposable());
		return loader;
	}

	@Override
	protected void tearDown() throws Exception {
		try {
			for (Path config : new Path[]{events, bindings, overloads}) if (config != null) Files.deleteIfExists(config);
		} finally {
			super.tearDown();
		}
	}

	private void assertCall(String target, String body, String usageName, String expected) {
		PsiFile file = myFixture.addFileToProject("kubegroovy/startup_scripts/Registry" + fileNumber++ + ".groovy", """
			import example.*
			import net.minecraft.resources.ResourceKey
			import net.minecraft.core.Registry
			$StartupEvents.registry(%s) { %s }
			""".formatted(target, body));
		var references = PsiTreeUtil.findChildrenOfType(file, GrReferenceExpression.class);
		var registry = references.stream().filter(ref -> "registry".equals(ref.getReferenceName())).findFirst().orElseThrow();
		assertTrue("Registry call must have an applicable overload", Arrays.stream(registry.multiResolve(false)).anyMatch(result -> result.isValidResult()));
		var usage = references.stream().filter(ref -> usageName.equals(ref.getText())).findFirst().orElseThrow();
		assertNotNull(usage.getType());
		assertEquals(expected, usage.getType().getCanonicalText());
		assertNoApplicabilityWarning(file);
	}

	private void assertNoApplicabilityWarning(PsiFile file) {
		myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
		var diagnostics = myFixture.doHighlighting();
		assertFalse(diagnostics.toString(), diagnostics.stream().anyMatch(info ->
			info.getDescription() != null && info.getDescription().contains("cannot be applied")));
	}

	public void testBareAndNamespacedRegistryStrings() {
		assertCall("'block'", "event -> event", "event", "dev.uncandango.kubegroovy.registry.RegistryKubeEvent<example.Block>");
		assertCall("'minecraft:block'", "event -> event", "event", "dev.uncandango.kubegroovy.registry.RegistryKubeEvent<example.Block>");
		assertCall("'addon:item'", "event -> event", "event", "dev.uncandango.kubegroovy.registry.RegistryKubeEvent<example.Item>");
	}

	public void testImplicitClosureParameterWithRegistryString() {
		assertCall("'block'", "it", "it", "dev.uncandango.kubegroovy.registry.RegistryKubeEvent<example.Block>");
	}

	public void testTypedKeysRetainGenericInferenceWithCoercionOverload() {
		assertCall("Registries.BLOCK", "event -> event", "event", "dev.uncandango.kubegroovy.registry.RegistryKubeEvent<example.Block>");
		assertCall("'block' as ResourceKey<Registry<Block>>", "event -> event", "event", "dev.uncandango.kubegroovy.registry.RegistryKubeEvent<example.Block>");
	}

	public void testUnknownAndDynamicTargetsAreApplicableWithoutInventingElementType() {
		assertCall("'unknown'", "event -> event", "event", "dev.uncandango.kubegroovy.registry.RegistryKubeEvent<?>");
		assertCall("System.getProperty('registry')", "event -> event", "event", "dev.uncandango.kubegroovy.registry.RegistryKubeEvent<?>");
	}

	public void testDetectorChainWithPlainRegistryNameAndIdentifierCoercion() {
		PsiFile file = myFixture.addFileToProject("kubegroovy/startup_scripts/Detector.groovy", """
			$StartupEvents.registry('block') { event ->
				event.create('test', 'detector').detectorId('test')
				event.create('ticker').blockEntity { be -> }
			}
			""");
		for (GrReferenceExpression ref : PsiTreeUtil.findChildrenOfType(file, GrReferenceExpression.class)) {
			if ("detectorId".equals(ref.getReferenceName()) || "blockEntity".equals(ref.getReferenceName())) {
				assertInstanceOf(ref.resolve(), PsiMethod.class);
			}
		}
		var create = PsiTreeUtil.findChildrenOfType(file, GrMethodCallExpression.class).stream()
			.filter(call -> call.getText().equals("event.create('test', 'detector')")).findFirst().orElseThrow();
		assertEquals("example.DetectorBlock.Builder", create.getType().getCanonicalText());
		assertNoApplicabilityWarning(file);
	}

	public void testInvalidCallbackStillReportsApplicabilityWarning() {
		PsiFile file = myFixture.addFileToProject("kubegroovy/startup_scripts/Invalid.groovy", "$StartupEvents.registry('block', 42)");
		myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
		assertTrue(myFixture.doHighlighting().stream().anyMatch(info ->
			info.getDescription() != null && info.getDescription().contains("cannot be applied")));
	}
}