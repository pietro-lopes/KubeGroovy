package dev.uncandango.kubegroovy.intellij;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.ThreeState;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrVariableEnhancer;


public final class StartupEventTypeInferenceTest extends BasePlatformTestCase {
	public void testRegistryTargetCompletionInsertsStringTarget() {
		addClass("package dev.uncandango.kubegroovy.event; public class EventGroupWrapper {}");
		addClass("package net.minecraft.resources; public class ResourceKey<T> {}");
		addClass("package net.minecraft.core; public interface Registry<T> {}");
		addClass("package net.minecraft.world.item; public class Item {}");
		addClass("package net.minecraft.world.level.block; public class Block {}");
		addClass("package net.minecraft.world.level.material; public class Fluid {}");

		var file = myFixture.configureByText("startup.groovy", """
			$StartupEvents.registry("ite<caret>") { event -> }
			""");

		var completionPosition = file.findElementAt(myFixture.getEditor().getCaretModel().getOffset());
		assertNotNull(completionPosition);
		assertTrue(completionPosition.getText() + " / " + completionPosition.getParent(), RegistryTargetCompletionContributor.isStartupRegistryTarget(completionPosition));
		myFixture.completeBasic();
		assertTrue(file.getText(), file.getText().contains("$StartupEvents.registry(\"item\")"));
		assertFalse(file.getText(), file.getText().contains(" as ResourceKey"));
	}

	public void testStartupEventBindingUsesClassNameHighlighting() {
		addClass("package dev.uncandango.kubegroovy.event; public class EventGroupWrapper {}");
		var file = myFixture.configureByText("startup.groovy", "$StartupEvents");

		HighlightInfo bindingHighlight = myFixture.doHighlighting().stream()
			.filter(info -> ScriptBindingAnnotator.BINDING_ATTRIBUTES.equals(info.forcedTextAttributesKey))
			.findFirst()
			.orElse(null);
		assertNotNull(bindingHighlight);
	}

	public void testRegistryTargetAllowsCompletionAutopopup() {
		addRegistryClasses();
		var file = myFixture.configureByText("startup.groovy", "$StartupEvents.registry(\"<caret>\") { event -> }");
		var offset = myFixture.getEditor().getCaretModel().getOffset();
		var position = file.findElementAt(offset);
		assertNotNull(position);

		assertEquals(ThreeState.NO, new RegistryTargetCompletionConfidence()
			.shouldSkipAutopopup(myFixture.getEditor(), position, file, offset));
	}

	public void testRegistryHandlerInfersItemEventType() {
		addRegistryClasses();

		var file = myFixture.configureByText("startup.groovy", """
			import net.minecraft.resources.ResourceKey
			import net.minecraft.core.Registry
			import net.minecraft.world.item.Item

			$StartupEvents.registry("item" as ResourceKey<Registry<Item>>) { event ->
				event.value
			}
			""");
		var closure = PsiTreeUtil.findChildOfType(file, GrClosableBlock.class);
		assertNotNull(closure);
		GrParameter[] parameters = closure.getAllParameters();
		assertEquals(1, parameters.length);

		PsiType enhancedType = GrVariableEnhancer.getEnhancedType(parameters[0]);
		assertNotNull(enhancedType);
		assertEquals("dev.uncandango.kubegroovy.registry.RegistryKubeEvent<net.minecraft.world.item.Item>", enhancedType.getCanonicalText());

		var valueReference = PsiTreeUtil.findChildrenOfType(file, GrReferenceExpression.class).stream()
			.filter(reference -> "event.value".equals(reference.getText()))
			.findFirst()
			.orElse(null);
		assertNotNull(valueReference);
		assertNotNull(valueReference.resolve());
		var errors = myFixture.doHighlighting().stream()
			.filter(info -> HighlightSeverity.ERROR.equals(info.getSeverity()))
			.toList();
		assertTrue(errors.toString(), errors.isEmpty());
	}

	public void testBlockRegistryHandlerInfersBlockType() {
		addRegistryClasses();
		var file = myFixture.configureByText("startup.groovy", """
			import net.minecraft.resources.ResourceKey
			import net.minecraft.core.Registry
			import net.minecraft.world.level.block.Block

			$StartupEvents.registry("block" as ResourceKey<Registry<Block>>) { event ->
				event.value
			}
			""");

		var closure = PsiTreeUtil.findChildOfType(file, GrClosableBlock.class);
		assertNotNull(closure);
		PsiType enhancedType = GrVariableEnhancer.getEnhancedType(closure.getAllParameters()[0]);
		assertNotNull(enhancedType);
		assertEquals("dev.uncandango.kubegroovy.registry.RegistryKubeEvent<net.minecraft.world.level.block.Block>", enhancedType.getCanonicalText());
		var valueReference = PsiTreeUtil.findChildrenOfType(file, GrReferenceExpression.class).stream()
			.filter(reference -> "event.value".equals(reference.getText()))
			.findFirst()
			.orElse(null);
		assertNotNull(valueReference);
		assertEquals("net.minecraft.world.level.block.Block", valueReference.getType().getCanonicalText());
		var errors = myFixture.doHighlighting().stream()
			.filter(info -> HighlightSeverity.ERROR.equals(info.getSeverity()))
			.toList();
		assertTrue(errors.toString(), errors.isEmpty());
	}

	public void testBareRegistryStringInfersBlockEventType() {
		addRegistryClasses();
		var file = myFixture.configureByText("startup.groovy", """
			$StartupEvents.registry("block") { event ->
				event.value
			}
			""");

		var closure = PsiTreeUtil.findChildOfType(file, GrClosableBlock.class);
		assertNotNull(closure);
		PsiType enhancedType = GrVariableEnhancer.getEnhancedType(closure.getAllParameters()[0]);
		assertNotNull(enhancedType);
		assertEquals("dev.uncandango.kubegroovy.registry.RegistryKubeEvent<net.minecraft.world.level.block.Block>", enhancedType.getCanonicalText());
		var valueReference = PsiTreeUtil.findChildrenOfType(file, GrReferenceExpression.class).stream()
			.filter(reference -> "event.value".equals(reference.getText()))
			.findFirst()
			.orElse(null);
		assertNotNull(valueReference);
		assertEquals("net.minecraft.world.level.block.Block", valueReference.getType().getCanonicalText());
		var errors = myFixture.doHighlighting().stream()
			.filter(info -> HighlightSeverity.ERROR.equals(info.getSeverity()))
			.toList();
		assertTrue(errors.toString(), errors.isEmpty());
	}

	private void addRegistryClasses() {
		addClass("package dev.uncandango.kubegroovy.event; public class EventGroupWrapper {}");
		addClass("package dev.uncandango.kubegroovy.registry; public class RegistryKubeEvent<T> { public T value; }");
		addClass("package dev.uncandango.kubegroovy.plugin.builtin.event; public interface StartupEvents { Object REGISTRY = null; }");
		addClass("package net.minecraft.resources; public class ResourceKey<T> {}");
		addClass("package net.minecraft.core; public interface Registry<T> {}");
		addClass("package net.minecraft.world.item; public class Item {}");
		addClass("package net.minecraft.world.level.block; public class Block {}");
		addClass("package net.minecraft.world.level.material; public class Fluid {}");
	}

	private PsiClass addClass(String source) {
		var packageName = source.replaceFirst("(?s).*?package\\s+([\\w.]+)\\s*;.*", "$1");
		var className = source.replaceFirst("(?s).*?\\b(?:class|interface)\\s+(\\w+).*", "$1");
		var file = (PsiJavaFile) myFixture.addFileToProject(packageName.replace('.', '/') + "/" + className + ".java", source);
		return file.getClasses()[0];
	}
}
