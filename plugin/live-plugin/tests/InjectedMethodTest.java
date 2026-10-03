package dev.uncandango.kubegroovy.intellij;

import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

public final class InjectedMethodTest extends LivePluginTestCase {
	public void testSameNameInjectedMethodsResolveAndCompleteOnlyInScripts() throws Exception {
		var catalog = Files.createTempFile("kgy-injected-methods", ".json");
		try {
			Files.writeString(catalog, """
				{"aliases":[
				 {"class":"example.Ingredient","from":"toJson","to":"toJson",
				  "signatures":["public java.lang.String toJson();"]},
				 {"class":"example.Ingredient","from":"toNBT","to":"toNBT",
				  "signatures":["public java.util.List<java.lang.String> toNBT();"]},
				 {"class":"example.Ingredient","from":"identity","to":"identity",
				  "signatures":["public <T extends java.lang.Number> T identity(T value);"]},
				 {"class":"example.Ingredient","from":"existing","to":"existing",
				  "signatures":["public int existing();","public int existing(int value);"]}]}
				""");
			ClassLoader loader = loadScript("method-aliases");
			loader.loadClass("MethodAliasSupport").getMethod("setConfigFile", File.class).invoke(null, catalog.toFile());
			var contributor = (NonCodeMembersContributor) loader.loadClass("MethodAliasContributor").getConstructor().newInstance();
			NonCodeMembersContributor.EP_NAME.getPoint().registerExtension(contributor, getTestRootDisposable());
			myFixture.addFileToProject("example/Parent.java", "package example; public class Parent { public String existing() { return null; } }");
			var ingredient = ((PsiJavaFile) myFixture.addFileToProject("example/Ingredient.java",
				"package example; public class Ingredient extends Parent {}" )).getClasses()[0];
			myFixture.addFileToProject("example/Child.java", "package example; public class Child extends Ingredient {}");
			for (String type : List.of("Ingredient", "Child")) {
				for (String call : List.of("toJson()", "toNBT()", "identity(1)", "existing()", "existing(1)")) {
					PsiFile file = myFixture.addFileToProject("kubegroovy/server_scripts/" + type + call.hashCode() + ".groovy",
						"new example." + type + "()." + call);
					String name = call.substring(0, call.indexOf('('));
					var ref = PsiTreeUtil.findChildrenOfType(file, GrReferenceExpression.class).stream()
						.filter(it -> name.equals(it.getReferenceName())).findFirst().orElseThrow();
					assertInstanceOf(ref.resolve(), PsiMethod.class);
					var method = (PsiMethod) ref.resolve();
					if (call.equals("existing()")) assertEquals("java.lang.String", method.getReturnType().getCanonicalText());
					if (call.equals("existing(1)")) assertEquals("int", method.getReturnType().getCanonicalText());
					if (call.equals("toNBT()")) assertEquals("java.util.List<java.lang.String>", method.getReturnType().getCanonicalText());
					if (call.equals("identity(1)")) assertEquals(1, method.getTypeParameters().length);
				}
			}
			PsiFile completion = myFixture.addFileToProject("kubegroovy/server_scripts/Completion.groovy", "new example.Ingredient().to");
			myFixture.configureFromExistingVirtualFile(completion.getVirtualFile());
			myFixture.getEditor().getCaretModel().moveToOffset(completion.getTextLength());
			var suggestions = Arrays.stream(myFixture.completeBasic()).map(it -> it.getLookupString()).toList();
			assertTrue(suggestions.contains("toJson"));
			assertTrue(suggestions.contains("toNBT"));
			PsiFile outside = myFixture.addFileToProject("src/OutsideInjected.groovy", "new example.Ingredient().toJson()");
			var ref = PsiTreeUtil.findChildrenOfType(outside, GrReferenceExpression.class).stream()
				.filter(it -> "toJson".equals(it.getReferenceName())).findFirst().orElseThrow();
			assertNull(ref.resolve());
			assertEquals(0, ingredient.findMethodsByName("toJson", false).length);
		} finally {
			Files.deleteIfExists(catalog);
		}
	}
}
