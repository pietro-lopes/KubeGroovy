package dev.uncandango.kubegroovy.intellij;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public final class InfoDocumentationProviderTest extends BasePlatformTestCase {
	@Override
	protected void setUp() throws Exception {
		super.setUp();
		myFixture.addFileToProject("dev/uncandango/kubegroovy/typings/Param.java", """
			package dev.uncandango.kubegroovy.typings;
			public @interface Param {
				String name() default "";
				String value() default "";
			}
			""");
		myFixture.addFileToProject("dev/uncandango/kubegroovy/typings/Info.java", """
			package dev.uncandango.kubegroovy.typings;
			public @interface Info {
				String value() default "";
				Param[] params() default {};
			}
			""");
		myFixture.addFileToProject("example/Widget.java", """
			package example;
			import dev.uncandango.kubegroovy.typings.Info;
			import dev.uncandango.kubegroovy.typings.Param;
			@Info("A buildable widget.")
			public class Widget {
				@Info("The current size.")
				public int size;
				public int undocumented;
				@Info(value = "Adds a part.\\nSecond line.", params = {
					@Param(name = "part", value = "the part to add"),
					@Param(value = "how many")
				})
				public String add(String part, int count) { return part; }
				public void plain() {}
			}
			""");
	}

	public void testMethodDocUsesInfoValueAndParams() {
		PsiMethod method = findMethod("add");
		String doc = new InfoDocumentationProvider().generateDoc(method, null);
		assertNotNull(doc);
		assertTrue(doc, doc.contains("Adds a part."));
		assertTrue(doc, doc.contains("Second line."));
		assertTrue(doc, doc.contains("add"));
		assertTrue(doc, doc.contains("String"));
		assertTrue(doc, doc.contains("the part to add"));
		assertTrue(doc, doc.contains("how many"));
		// First entry overrides the display name, the second falls back to the real parameter name.
		assertTrue(doc, doc.contains("part"));
		assertTrue(doc, doc.contains("count"));

		String hover = new InfoDocumentationProvider().generateHoverDoc(method, null);
		assertEquals(doc, hover);
	}

	public void testFieldAndClassDocsUseInfoValue() {
		PsiClass widget = findClass();
		String classDoc = new InfoDocumentationProvider().generateDoc(widget, null);
		assertNotNull(classDoc);
		assertTrue(classDoc, classDoc.contains("A buildable widget."));

		var size = widget.findFieldByName("size", false);
		assertNotNull(size);
		String fieldDoc = new InfoDocumentationProvider().generateDoc(size, null);
		assertNotNull(fieldDoc);
		assertTrue(fieldDoc, fieldDoc.contains("The current size."));
	}

	public void testUndocumentedMembersFallThrough() {
		PsiMethod plain = findMethod("plain");
		assertNull(new InfoDocumentationProvider().generateDoc(plain, null));

		PsiClass widget = findClass();
		var undocumented = widget.findFieldByName("undocumented", false);
		assertNotNull(undocumented);
		assertNull(new InfoDocumentationProvider().generateDoc(undocumented, null));

		assertNull(new InfoDocumentationProvider().generateDoc(widget.getContainingFile(), null));
	}

	private PsiClass findClass() {
		PsiClass widget = JavaPsiFacade.getInstance(getProject())
			.findClass("example.Widget", GlobalSearchScope.allScope(getProject()));
		assertNotNull(widget);
		return widget;
	}

	private PsiMethod findMethod(String name) {
		PsiMethod[] methods = findClass().findMethodsByName(name, false);
		assertEquals(1, methods.length);
		return methods[0];
	}
}
