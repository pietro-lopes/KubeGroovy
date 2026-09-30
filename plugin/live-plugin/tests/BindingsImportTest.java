package dev.uncandango.kubegroovy.intellij;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.completion.CompletionContributorEP;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.openapi.extensions.DefaultPluginDescriptor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.LoadingOrder;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor;
import org.jetbrains.plugins.groovy.lang.resolve.imports.GrImportContributor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public final class BindingsImportTest extends LivePluginTestCase {
	private Path config;
	private int fileNumber;

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		ClassLoader loader = loadScript("bindings");
		config = Files.createTempFile("kgy-binding-imports", ".json");
		Files.writeString(config, """
			{"startup":[{"name":"$TestRuntime","type":"example.TestRuntime","classValue":true},
			{"name":"runtime","type":"example.TestRuntime","classValue":false}],
			"server":[{"name":"$ServerRuntime","type":"example.TestRuntime","classValue":true}],
			"client":[{"name":"$ClientRuntime","type":"example.TestRuntime","classValue":true}]}
			""");
		loader.loadClass("BindingsSupport").getMethod("setConfigFile", java.io.File.class).invoke(null, config.toFile());
		GrImportContributor.EP_NAME.getPoint().registerExtension(
			(GrImportContributor) loader.loadClass("BindingsImportContributor").getConstructor().newInstance(), getTestRootDisposable());
		NonCodeMembersContributor.EP_NAME.getPoint().registerExtension(
			(NonCodeMembersContributor) loader.loadClass("BindingsContributor").getConstructor().newInstance(), getTestRootDisposable());
		var descriptor = new DefaultPluginDescriptor(PluginId.getId("LivePlugin"), loader);
		ExtensionPointName.<CompletionContributorEP>create("com.intellij.completion.contributor").getPoint()
			.registerExtension(new CompletionContributorEP("Groovy", "BindingsCompletionContributor", descriptor),
				LoadingOrder.FIRST, getTestRootDisposable());
		boolean autoComplete = CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION;
		CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = false;
		Disposer.register(getTestRootDisposable(), () -> CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = autoComplete);
		myFixture.addFileToProject("example/TestRuntime.java", """
			package example;
			public class TestRuntime {
				public static void passStartup(String marker) {}
				public void instanceCall() {}
			}
			""");
	}

	@Override
	protected void tearDown() throws Exception {
		try {
			if (config != null) Files.deleteIfExists(config);
		} finally {
			super.tearDown();
		}
	}

	private PsiFile script(String root, String text) {
		return myFixture.addFileToProject(root + "/Bindings" + fileNumber++ + ".groovy", text);
	}

	private GrReferenceExpression reference(PsiFile file, String name) {
		return PsiTreeUtil.findChildrenOfType(file, GrReferenceExpression.class).stream()
			.filter(ref -> name.equals(ref.getReferenceName())).findFirst().orElseThrow();
	}

	public void testClassAliasAndStaticMethodResolveWithoutImport() {
		PsiFile file = script("kubegroovy/startup_scripts", "$TestRuntime.passStartup('marker')");
		assertInstanceOf(reference(file, "$TestRuntime").resolve(), PsiClass.class);
		assertInstanceOf(reference(file, "passStartup").resolve(), PsiMethod.class);
		assertEquals("example.TestRuntime", ((PsiClass) reference(file, "$TestRuntime").resolve()).getQualifiedName());
	}

	public void testImportsAreScopedBySideAndScriptDirectory() {
		for (String[] entry : new String[][]{{"startup", "$TestRuntime"}, {"server", "$ServerRuntime"}, {"client", "$ClientRuntime"}}) {
			assertInstanceOf(reference(script("kubegroovy/" + entry[0] + "_scripts", entry[1]), entry[1]).resolve(), PsiClass.class);
		}
		for (String root : new String[]{"kubegroovy/server_scripts", "kubegroovy/client_scripts", "scripts", "otherkubegroovy/startup_scripts"}) {
			assertNull(reference(script(root, "$TestRuntime"), "$TestRuntime").resolve());
		}
	}

	public void testInstanceBindingsKeepTheirInstanceType() {
		PsiFile file = script("kubegroovy/startup_scripts", "runtime.instanceCall()");
		assertFalse(reference(file, "runtime").resolve() instanceof PsiClass);
		assertInstanceOf(reference(file, "instanceCall").resolve(), PsiMethod.class);
	}

	public void testCompletionInsertsOnlyAlias() {
		PsiFile file = script("kubegroovy/startup_scripts", "$TestR");
		myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
		myFixture.getEditor().getCaretModel().moveToOffset(6);
		var elements = myFixture.complete(CompletionType.BASIC);
		assertNotNull(elements);
		var selected = Arrays.stream(elements).filter(item -> "$TestRuntime".equals(item.getLookupString())).findFirst().orElseThrow();
		myFixture.getLookup().setCurrentItem(selected);
		myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);
		assertEquals("$TestRuntime", myFixture.getEditor().getDocument().getText());
		assertInstanceOf(reference(myFixture.getFile(), "$TestRuntime").resolve(), PsiClass.class);
	}

	public void testStaticMemberCompletionWithoutImport() {
		PsiFile file = script("kubegroovy/startup_scripts", "$TestRuntime.pass");
		myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
		myFixture.getEditor().getCaretModel().moveToOffset(file.getTextLength());
		var elements = myFixture.complete(CompletionType.BASIC);
		assertNotNull(elements);
		assertTrue(Arrays.stream(elements).anyMatch(item -> "passStartup".equals(item.getLookupString())));
	}
}
