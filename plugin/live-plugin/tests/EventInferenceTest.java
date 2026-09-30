package dev.uncandango.kubegroovy.intellij;

import com.intellij.psi.PsiFile;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/** Compares ordinary indexed signatures with the actual LivePlugin event methods. */
public final class EventInferenceTest extends LivePluginTestCase {
	private int fileNumber;
	@Override
	protected void setUp() throws Exception {
		super.setUp();
		myFixture.addFileToProject("example/Block.java", "package example; public class Block {}");
		myFixture.addFileToProject("example/Registry.java", "package example; public class Registry<T> {}");
		myFixture.addFileToProject("example/ResourceKey.java", "package example; public class ResourceKey<T> {}");
		myFixture.addFileToProject("example/RegistryEvent.java", "package example; public class RegistryEvent<T> {}");
		myFixture.addFileToProject("example/Registries.java", "package example; public class Registries { public static ResourceKey<Registry<Block>> BLOCK; }");
		myFixture.addFileToProject("example/Events.java", "package example; public class Events { public static <T> void registry(ResourceKey<Registry<T>> key, java.util.function.Consumer<RegistryEvent<T>> callback) {} }");
		myFixture.addFileToProject("dev/uncandango/kubegroovy/typing/EventGroupBinding.java", "package dev.uncandango.kubegroovy.typing; public class EventGroupBinding {}");
	}

	private void assertEventType(String qualifier, String target) {
		assertEventType(qualifier, target, "def $StartupEvents = new EventGroupBinding()", "event -> event", "event");
	}

	private void assertEventType(String qualifier, String target, String binding, String body, String usageName) {
		PsiFile file = myFixture.addFileToProject("kubegroovy/startup_scripts/Inference" + fileNumber++ + ".groovy", """
			import example.*
			import dev.uncandango.kubegroovy.typing.EventGroupBinding
			%s
			%s.registry(%s) { %s }
			""".formatted(binding, qualifier, target, body));
		GrReferenceExpression usage = PsiTreeUtil.findChildrenOfType(file, GrReferenceExpression.class).stream()
			.filter(ref -> usageName.equals(ref.getText())).findFirst().orElseThrow();
		assertNotNull(usage.getType());
		assertEquals("example.RegistryEvent<example.Block>", usage.getType().getCanonicalText());
	}

	public void testRealMethodWithTypedKey() {
		assertEventType("Events", "Registries.BLOCK");
	}

	public void testRealMethodWithCastKey() {
		assertEventType("Events", "'block' as ResourceKey<Registry<Block>>");
	}

	public void testJarMethodWithTypedKey() throws Exception {
		attachFacadeJar();
		assertEventType("example.JarEvents", "Registries.BLOCK");
	}

	public void testJarMethodWithCastKey() throws Exception {
		attachFacadeJar();
		assertEventType("example.JarEvents", "'block' as ResourceKey<Registry<Block>>");
	}

	private void attachFacadeJar() throws Exception {
		Path directory = Files.createTempDirectory("kgy-event-facade");
		Disposer.register(getTestRootDisposable(), () -> {
			try (var paths = Files.walk(directory)) {
				for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
			} catch (Exception error) {
				throw new RuntimeException(error);
			}
		});
		// IDEA's PathClassLoader does not expose the compiler to ToolProvider.
		String executable = System.getProperty("os.name").startsWith("Windows") ? "javac.exe" : "javac";
		List<String> arguments = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", executable).toString(),
			"--release", Integer.toString(Runtime.version().feature()), "-d", directory.toString()));
		for (String name : List.of("Block", "Registry", "ResourceKey", "RegistryEvent", "Events")) {
			String text = PsiManager.getInstance(getProject()).findFile(myFixture.findFileInTempDir("example/" + name + ".java")).getText();
			if (name.equals("Events")) text = text.replace("class Events", "class JarEvents");
			Path source = directory.resolve(name.equals("Events") ? "JarEvents.java" : name + ".java");
			Files.writeString(source, text);
			arguments.add(source.toString());
		}
		Process compiler = new ProcessBuilder(arguments).redirectErrorStream(true).start();
		String diagnostics = new String(compiler.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
		assertEquals(diagnostics, 0, compiler.waitFor());
		Path jar = directory.resolve("events.jar");
		try (var output = new JarOutputStream(Files.newOutputStream(jar))) {
			output.putNextEntry(new JarEntry("example/JarEvents.class"));
			output.write(Files.readAllBytes(directory.resolve("example/JarEvents.class")));
			output.closeEntry();
		}
		ModuleRootModificationUtil.addModuleLibrary(myFixture.getModule(), "Event facade experiment",
			List.of("jar://" + jar.toString().replace('\\', '/') + "!/"), List.of());
	}

	public void testSyntheticMethodWithTypedKey() throws Exception {
		assertSynthetic("Registries.BLOCK");
	}

	public void testSyntheticMethodWithCastKey() throws Exception {
		assertSynthetic("'block' as ResourceKey<Registry<Block>>");
	}

	public void testSyntheticWithImplicitBindingAndClosureParameter() throws Exception {
		assertSynthetic("'block' as ResourceKey<Registry<Block>>", true);
	}

	private void assertSynthetic(String target) throws Exception {
		assertSynthetic(target, false);
	}

	private void assertSynthetic(String target, boolean implicit) throws Exception {
		ClassLoader loader = loadScript("events");
		Path config = Files.createTempFile("kgy-event-inference", ".json");
		Path bindings = Files.createTempFile("kgy-event-bindings", ".json");
		try {
			Files.writeString(config, """
				{"startup":{"$StartupEvents":[{"name":"registry","returns":"void","typeParams":["T"],
				"params":[{"name":"key","type":"example.ResourceKey<example.Registry<T>>"},
				{"name":"callback","type":"java.util.function.Consumer<example.RegistryEvent<T>>"}]}]}}
				""");
			loader.loadClass("EventMethodSupport").getMethod("setConfigFile", java.io.File.class).invoke(null, config.toFile());
			var contributor = (NonCodeMembersContributor) loader.loadClass("EventMethodsContributor").getConstructor().newInstance();
			NonCodeMembersContributor.EP_NAME.getPoint().registerExtension(contributor, getTestRootDisposable());
			if (implicit) {
				ClassLoader bindingLoader = loadScript("bindings");
				Files.writeString(bindings, "{\"startup\":[{\"name\":\"$StartupEvents\",\"type\":\"example.Events\",\"eventGroup\":true}]}");
				bindingLoader.loadClass("BindingsSupport").getMethod("setConfigFile", java.io.File.class).invoke(null, bindings.toFile());
				var bindingContributor = (NonCodeMembersContributor) bindingLoader.loadClass("BindingsContributor").getConstructor().newInstance();
				NonCodeMembersContributor.EP_NAME.getPoint().registerExtension(bindingContributor, getTestRootDisposable());
				assertEventType("$StartupEvents", target, "", "event -> event", "event");
				assertEventType("$StartupEvents", target, "", "it", "it");
			} else {
				assertEventType("$StartupEvents", target);
			}
		} finally {
			Files.deleteIfExists(config);
			Files.deleteIfExists(bindings);
		}
	}
}