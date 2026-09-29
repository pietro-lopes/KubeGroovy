package dev.uncandango.kubegroovy.intellij;

import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

public final class LivePluginScopeTest extends BasePlatformTestCase {
	private ClassLoader loadScript(String folder) throws Exception {
		Class<?> loaderType = Class.forName("groovy.lang.GroovyClassLoader");
		ClassLoader loader = (ClassLoader) loaderType.getConstructor(ClassLoader.class).newInstance(getClass().getClassLoader());
		Path script = Path.of("../live-plugin", folder, "plugin.groovy");
		loaderType.getMethod("parseClass", File.class).invoke(loader, script.toFile());
		return loader;
	}

	public void testEveryScriptUsesExactGroovyScope() throws Exception {
		String[][] scripts = {
			{"bindings", "BindingsSupport"}, {"events", "EventMethodSupport"},
			{"method-aliases", "MethodAliasSupport"}, {"object-overload", "ObjectOverloadSupport"},
			{"type-suggestions", "ItemStackSupport"}, {"info-docs", "InfoDocSupport"}
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
