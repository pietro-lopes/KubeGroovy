package dev.uncandango.kubegroovy.intellij;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

public final class ObjectOverloadAliasTest extends LivePluginTestCase {
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
