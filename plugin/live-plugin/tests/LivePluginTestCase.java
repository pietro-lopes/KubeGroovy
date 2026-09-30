package dev.uncandango.kubegroovy.intellij;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Loads the real scripts into the IDE test runtime without executing LivePlugin registration. */
abstract class LivePluginTestCase extends BasePlatformTestCase {
	@Override
	protected void setUp() throws Exception {
		super.setUp();
		// Container inference needs java.util types. Use the fixture JVM's SDK, rather than source-tree mock JDK data.
		Sdk sdk = JavaSdk.getInstance().createJdk("LivePlugin fixture JDK", System.getProperty("java.home"), false);
		ApplicationManager.getApplication().runWriteAction(() ->
			ProjectJdkTable.getInstance().addJdk(sdk, getTestRootDisposable()));
		ModuleRootModificationUtil.setModuleSdk(myFixture.getModule(), sdk);
	}

	protected ClassLoader loadScript(String folder) throws Exception {
		Class<?> loaderType = Class.forName("groovy.lang.GroovyClassLoader");
		ClassLoader loader = (ClassLoader) loaderType.getConstructor(ClassLoader.class).newInstance(getClass().getClassLoader());
		Path script = Path.of(System.getProperty("kubegroovy.livePluginRoot"), folder, "plugin.groovy");
		loaderType.getMethod("parseClass", File.class).invoke(loader, script.toFile());
		if (loader instanceof Closeable closeable) {
			Disposer.register(getTestRootDisposable(), () -> {
				try {
					closeable.close();
				} catch (IOException error) {
					throw new RuntimeException(error);
				}
			});
		}
		return loader;
	}

	protected void setSuggestionConfig(ClassLoader loader, File config) throws Exception {
		loader.loadClass("TypeSuggestionCatalog").getMethod("setConfigFile", File.class).invoke(null, config);
	}

	protected Object suggestionCatalog(ClassLoader loader) throws Exception {
		return loader.loadClass("TypeSuggestionCatalog").getMethod("loadConfig").invoke(null);
	}

	@SuppressWarnings("unchecked")
	protected List<String> suggestions(ClassLoader loader, String type) throws Exception {
		Object catalog = suggestionCatalog(loader);
		return (List<String>) catalog.getClass().getMethod("suggestionsForType", String.class).invoke(catalog, type);
	}

	protected String coercedSpec(ClassLoader loader, PsiElement position, int offset) throws Exception {
		Class<?> psi = loader.loadClass("TypeSuggestionPsi");
		return (String) psi.getMethod("getCoercedSpec", PsiElement.class, int.class)
			.invoke(psi.getConstructor().newInstance(), position, offset);
	}
}