package dev.uncandango.kubegroovy.intellij;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

public final class ObjectOverloadAliasTest extends LivePluginTestCase {
	public void testInformationEventRewritesListElements() throws Exception {
		myFixture.addFileToProject("net/minecraft/network/chat/Component.java",
			"package net.minecraft.network.chat; public interface Component {}");
		PsiClass event = ((PsiJavaFile) myFixture.addFileToProject("example/AddInformationKubeEvent.java", """
			package example;
			public interface AddInformationKubeEvent {
			 void add(Object filter, java.util.List<net.minecraft.network.chat.Component> info);
			 void nested(java.util.List<java.util.List<net.minecraft.network.chat.Component>> info);
			 void map(java.util.Map<net.minecraft.network.chat.Component, net.minecraft.network.chat.Component> info);
			 void plain(java.util.List<String> info);
			}
			""")).getClasses()[0];
		PsiClass implementation = ((PsiJavaFile) myFixture.addFileToProject("example/InformationEvent.java", """
			package example;
			public abstract class InformationEvent implements AddInformationKubeEvent {
			 public void add(Object filter, java.util.List<net.minecraft.network.chat.Component> info) {}
			}
			""")).getClasses()[0];
		PsiElement place = myFixture.addFileToProject("kubegroovy/server_scripts/info.groovy", "");
		var config = Files.createTempFile("kgy-container-overloads", ".json");
		try {
			Files.writeString(config, """
				{"targets":["net.minecraft.network.chat.Component"],
				 "typeRules":[{"type":"java.util.List","action":"keep","args":[0]},
				              {"type":"java.util.Map","action":"keep","args":[0,1]}]}
				""");
			ClassLoader loader = loadScript("object-overload");
			loader.loadClass("ObjectOverloadSupport").getMethod("setConfigFile", File.class).invoke(null, config.toFile());
			var methodsFor = loader.loadClass("ObjectOverloadContributor").getDeclaredMethod("methodsFor",
				PsiClass.class, String.class, PsiElement.class);
			methodsFor.setAccessible(true);
			List<?> methods = (List<?>) methodsFor.invoke(null, event, "add", place);
			assertEquals(1, methods.size());
			var parameters = ((PsiMethod) methods.getFirst()).getParameterList().getParameters();
			assertEquals("java.lang.Object", parameters[0].getType().getCanonicalText());
			assertEquals("java.util.List<java.lang.Object>", parameters[1].getType().getCanonicalText());
			assertEquals(1, ((List<?>) methodsFor.invoke(null, implementation, "add", place)).size());
			for (var expected : java.util.Map.of(
				"nested", "java.util.List<java.util.List<java.lang.Object>>",
				"map", "java.util.Map<java.lang.Object,java.lang.Object>").entrySet()) {
				var rewritten = (List<?>) methodsFor.invoke(null, event, expected.getKey(), place);
				assertEquals(1, rewritten.size());
				assertEquals(expected.getValue(), ((PsiMethod) rewritten.getFirst()).getParameterList().getParameters()[0].getType().getCanonicalText());
			}
			assertTrue(((List<?>) methodsFor.invoke(null, event, "plain", place)).isEmpty());
			var contributor = (NonCodeMembersContributor) loader.loadClass("ObjectOverloadContributor").getConstructor().newInstance();
			NonCodeMembersContributor.EP_NAME.getPoint().registerExtension(contributor, getTestRootDisposable());
			var callFile = myFixture.addFileToProject("kubegroovy/server_scripts/callInfo.groovy",
				"example.AddInformationKubeEvent event; event.add('item', ['text'])");
			var call = PsiTreeUtil.findChildrenOfType(callFile, GrMethodCallExpression.class).iterator().next();
			assertTrue("String-list call must resolve to an applicable overload", java.util.Arrays.stream(call.multiResolveGroovy(false)).anyMatch(result -> result.isValidResult()));
			myFixture.configureFromExistingVirtualFile(callFile.getVirtualFile());
			myFixture.enableInspections(new org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection());
			assertTrue("Reproduce the native List<Component> warning before installing the conversion hook", myFixture.doHighlighting().stream()
				.anyMatch(info -> info.getDescription() != null && info.getDescription().contains("cannot be applied")));
			var converter = (org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrTypeConverter) loader.loadClass("ObjectOverloadCollectionConverter").getConstructor().newInstance();
			org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrTypeConverter.EP_NAME.getPoint().registerExtension(converter, getTestRootDisposable());
			myFixture.configureFromExistingVirtualFile(callFile.getVirtualFile());
			myFixture.getEditor().getCaretModel().moveToOffset(callFile.getText().indexOf("text") + 1);
			var items = new java.util.concurrent.atomic.AtomicReference<Object[]>();
			var context = (com.intellij.lang.parameterInfo.CreateParameterInfoContext) java.lang.reflect.Proxy.newProxyInstance(
				getClass().getClassLoader(), new Class<?>[]{com.intellij.lang.parameterInfo.CreateParameterInfoContext.class}, (proxy, method, arguments) -> switch (method.getName()) {
					case "getEditor" -> myFixture.getEditor();
					case "getFile" -> callFile;
					case "getProject" -> getProject();
					case "getOffset" -> myFixture.getEditor().getCaretModel().getOffset();
					case "getItemsToShow" -> items.get();
					case "setItemsToShow" -> { items.set((Object[]) arguments[0]); yield null; }
					default -> null;
				});
			var handler = (com.intellij.lang.parameterInfo.ParameterInfoHandler<?, ?>) loader.loadClass("ObjectOverloadParameterInfo").getConstructor().newInstance();
			var popup = com.intellij.openapi.application.ReadAction.nonBlocking(() -> handler.findElementForParameterInfo(context))
				.submit(com.intellij.util.concurrency.AppExecutorUtil.getAppExecutorService());
			assertNotNull(com.intellij.testFramework.PlatformTestUtil.waitForPromise(popup));
			assertTrue("Parameter popup must include List<Object>", java.util.Arrays.stream(items.get())
				.anyMatch(item -> item instanceof org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult result
					&& result.getElement() instanceof PsiMethod method && method.getParameterList().getParameters()[1].getType().equalsToText("java.util.List<java.lang.Object>")));
			assertTrue("String-list call must not produce an applicability warning", myFixture.doHighlighting().stream()
				.noneMatch(info -> info.getDescription() != null && info.getDescription().contains("cannot be applied")));
			var sourceType = call.getExpressionArguments()[1].getType();
			var position = org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrTypeConverter.Position.METHOD_PARAMETER;
			var plainType = event.findMethodsByName("plain", false)[0].getParameterList().getParameters()[0].getType();
			assertNull("Unregistered list elements retain native checking", converter.isConvertible(plainType, sourceType, position, call));
			var outside = myFixture.addFileToProject("src/outside.groovy", "example.AddInformationKubeEvent event; event.add('item', ['text'])");
			var outsideCall = PsiTreeUtil.findChildrenOfType(outside, GrMethodCallExpression.class).iterator().next();
			var componentList = event.findMethodsByName("add", false)[0].getParameterList().getParameters()[1].getType();
			assertNull("Conversions stay inside script folders", converter.isConvertible(componentList, sourceType, position, outsideCall));
		} finally {
			Files.deleteIfExists(config);
		}
	}

	public void testTellObjectFromInjectedSignatureAndRealCollision() throws Exception {
		myFixture.addFileToProject("example/Component.java", "package example; public interface Component {}");
		PsiClass server = ((PsiJavaFile) myFixture.addFileToProject("example/Server.java",
			"package example; public class Server {}" )).getClasses()[0];
		PsiClass collision = ((PsiJavaFile) myFixture.addFileToProject("example/Collision.java",
			"package example; public class Collision { public void tell(Object message) {} }" )).getClasses()[0];
		PsiElement place = myFixture.addFileToProject("kubegroovy/server_scripts/tell.groovy", "new example.Server().tell('hello')");
		var overloads = Files.createTempFile("kgy-overloads", ".json");
		var aliases = Files.createTempFile("kgy-aliases", ".json");
		try {
			Files.writeString(overloads, "{\"targets\":[\"example.Component\"]}");
			Files.writeString(aliases, """
				{"aliases":[
				 {"class":"example.Server","from":"kgy$tell","to":"tell",
				  "signatures":["public void kgy$tell(example.Component message);","invalid declaration"]},
				 {"class":"example.Collision","from":"kgy$tell","to":"tell",
				  "signatures":["public void kgy$tell(example.Component message);"]},
				 {"class":"example.Server","from":"accept","to":"accept",
				  "signatures":["public void accept(example.Component message);"]}]}
				""");
			ClassLoader loader = loadScript("object-overload");
			Class<?> support = loader.loadClass("ObjectOverloadSupport");
			support.getMethod("setConfigFile", File.class).invoke(null, overloads.toFile());
			support.getMethod("setAliasFile", File.class).invoke(null, aliases.toFile());
			var methodsFor = loader.loadClass("ObjectOverloadContributor").getDeclaredMethod("methodsFor",
				PsiClass.class, String.class, PsiElement.class);
			methodsFor.setAccessible(true);
			List<?> methods = (List<?>) methodsFor.invoke(null, server, "tell", place);
			assertEquals(1, methods.size());
			PsiMethod tell = (PsiMethod) methods.getFirst();
			assertEquals("tell", tell.getName());
			assertEquals("java.lang.Object", tell.getParameterList().getParameters()[0].getType().getCanonicalText());
			assertTrue(((List<?>) methodsFor.invoke(null, server, "kgy$tell", place)).isEmpty());
			assertTrue(((List<?>) methodsFor.invoke(null, collision, "tell", place)).isEmpty());
			assertEquals(1, ((List<?>) methodsFor.invoke(null, server, "accept", place)).size());
			assertEquals(2, ((List<?>) methodsFor.invoke(null, server, null, place)).size());
		} finally {
			Files.deleteIfExists(overloads);
			Files.deleteIfExists(aliases);
		}
	}
}
