package dev.uncandango.kubegroovy.groovytest;

import dev.uncandango.kubegroovy.script.ScriptManager;
import dev.uncandango.kubegroovy.script.ScriptType;
import dev.uncandango.kubegroovy.script.groovy.KubeGroovyClassLoader;
import dev.uncandango.kubegroovy.script.groovy.KubeGroovySandbox;
import dev.uncandango.kubegroovy.script.groovy.SandboxRuntime;
import groovy.lang.Binding;
import groovy.lang.Closure;
import groovy.lang.Script;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.CompilationCustomizer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SandboxSecurityTests {
	public enum Mode { STRICT }
	@Retention(RetentionPolicy.RUNTIME)
	public @interface Metadata {
		Class<?> type();
		Mode mode();
	}
	public static class Values {
		public static Object DANGER = Runtime.getRuntime();
		public Object danger() { return Runtime.getRuntime(); }
		public Object nested() { return Map.of("danger", Runtime.getRuntime()); }
		public Object callback(Closure<?> callback) { return callback.call(Runtime.getRuntime()); }
		public Object field = Runtime.getRuntime();
		public String safe() { return "safe"; }
	}
	public static class Visibility {
		public static int calls;
		static String packageStatic() { calls++; return "package"; }
		public static String publicStatic() { return "public"; }
		protected static String protectedStatic() { return "protected"; }
	}

	@BeforeAll static void policy() {
		if (ScriptManager.getClassFilterIfInitialized() == null) ScriptManager.initializeClassFilter();
	}

	private Object run(String text, Map<String, Object> variables) throws Exception {
		var config = new CompilerConfiguration();
		config.getOptimizationOptions().put("indy", true);
		config.setDisabledGlobalASTTransformations(Set.of("groovy.grape.GrabAnnotationTransformation"));
		config.addCompilationCustomizers(KubeGroovySandbox.customizers(ScriptManager.getClassFilter()).toArray(CompilationCustomizer[]::new));
		try (var loader = new KubeGroovyClassLoader(getClass().getClassLoader(), new ScriptManager(ScriptType.SERVER), config)) {
			var script = (Script) loader.parseClass(text).getConstructor().newInstance();
			script.setBinding(new Binding(variables));
			return script.run();
		}
	}

	private void denied(String text, Map<String, Object> variables) {
		var error = assertThrows(Exception.class, () -> run(text, variables), text);
		assertTrue(error.toString().contains("Security") || error.toString().contains("Script access denied")
			|| error.toString().contains("Class not allowed") || error.toString().contains("MissingMethod"), error.toString());
	}

	@Test void desktopStringIoRoutesAreRejected() {
		for (var address : List.of("http://127.0.0.1:61423/api/mods", "file:///C:/Windows/win.ini",
			"file:///C:/Temp/kgy_secret.txt", "file:///C:/game/kubegroovy/link_escape_test/kgy_secret.txt")) {
			denied("new javax.swing.JEditorPane('" + address + "').getText()", Map.of());
			denied("import javax.swing.JEditorPane as Editor; def pane = new Editor(); pane.setPage('" + address + "'); pane.text", Map.of());
		}
		for (var text : List.of(
			"import javax.swing.*; new JEditorPane()",
			"java.awt.Toolkit.defaultToolkit.getImage('file:///C:/Temp/kgy_secret.png')",
			"javax.imageio.ImageIO.scanForPlugins()",
			"javax.sound.sampled.AudioSystem.mixerInfo",
			"javax.sound.midi.MidiSystem.sequencer")) denied(text, Map.of());
		var error = assertThrows(NoClassDefFoundError.class, () -> run("class Escape extends javax.swing.JEditorPane {}; new Escape()", Map.of()));
		assertInstanceOf(ClassNotFoundException.class, error.getCause());
		assertTrue(error.getCause().getMessage().contains("Class not allowed by class filter: javax.swing.JEditorPane"));
		var editor = new ClassFilterTests.Editor();
		denied("pane.setPage('http://127.0.0.1:61423/api/mods')", Map.of("pane", editor));
		denied("def method = 'setPage'; pane.\"$method\"('file:///C:/Temp/kgy_secret.txt')", Map.of("pane", editor));
		denied("pane.text", Map.of("pane", editor));
	}

	@Test void packagePrivateStaticJavaCallsAreRejected() throws Exception {
		Visibility.calls = 0;
		var prefix = "import dev.uncandango.kubegroovy.groovytest.SandboxSecurityTests.Visibility; ";
		for (var text : List.of("Visibility.packageStatic()", "def name = 'packageStatic'; Visibility.\"$name\"()",
			"def method = Visibility.&packageStatic; method()", "new Visibility().packageStatic()",
			"class Child extends Visibility {}; Child.packageStatic()")) denied(prefix + text, Map.of());
		assertEquals(0, Visibility.calls);
		assertEquals("public", run(prefix + "100.times { assert Visibility.publicStatic() == 'public' }; Visibility.publicStatic()", Map.of()));
		assertEquals("protected", run(prefix + "class Child extends Visibility { String value() { super.protectedStatic() } }; new Child().value()", Map.of()));
		assertEquals("helper", run("class Helper { @groovy.transform.PackageScope static String value() { 'helper' }; private static String hidden() { value() }; static String callHelper() { hidden() } }; Helper.callHelper()", Map.of()));
	}

	@Test void compileTimeEscapeRoutesAreRejected() {
		for (var text : List.of(
			"import java.net.Socket; new Socket('127.0.0.1', 9)",
			"new java.lang.ProcessBuilder('never-run')",
			"org.codehaus.groovy.runtime.InvokerHelper.invokeMethod('never-run', 'execute', null)",
			"new groovy.util.ConfigSlurper().parse('dangerous code')",
			"new groovy.util.FileTreeBuilder()",
			"import groovy.util.FileTreeBuilder as Tree; new Tree()",
			"import groovy.util.*; new FileTreeBuilder()",
			"new dev.latvian.apps.tinyserver.HTTPServer({ -> null })",
			"import dev.latvian.apps.tinyserver.*; new HTTPServer({ -> null })",
			"dev.latvian.apps.tinyserver.http.response.HTTPStatus.OK",
			"com.mojang.realmsclient.client.RealmsClient.class",
			"import com.mojang.realmsclient.client.RealmsClient as Client; Client.class",
			"import com.mojang.realmsclient.client.*; RealmsClient.class",
			"new java.util.Formatter()",
			"import java.util.Formatter as Format; new Format()",
			"import java.util.*; new Formatter()",
			"groovy.ui.GroovyMain.main(['-e', '1 + 1'] as String[])",
			"import groovy.ui.GroovyMain as Main; Main.main(['-e', '1 + 1'] as String[])",
			"import groovy.ui.*; new GroovyMain()",
			"javax.tools.ToolProvider.systemJavaCompiler",
			"new File('../outside')",
			"@groovy.transform.CompileStatic class Escape { static Object run() { null } }",
			"@groovy.transform.ASTTest(value={ assert false }) def value = 1",
			"@Grab('never:download:1') def value = 1",
			"import groovy.transform.*; @ASTTest(value={ assert false }) class Escape {}",
			"import groovy.transform.ASTTest as Check; @Check(value={ assert false }) class Escape {}",
			"@groovy.transform.TypeChecked(extensions='never-load.groovy') class Escape {}",
			"@groovy.transform.AnnotationCollector class Escape {}",
			"@groovy.transform.BaseScript groovy.lang.Script base",
			"@GrabResolver(name='never', root='https://example.invalid') class Escape {}",
			"@groovy.transform.TupleConstructor(pre={ 'never-run'.execute() }) class Escape { String value }; new Escape('x')",
			"import dev.uncandango.kubegroovy.groovytest.SandboxSecurityTests.Metadata; import dev.uncandango.kubegroovy.groovytest.SandboxSecurityTests.Mode; @Metadata(type=Runtime, mode=Mode.STRICT) class Escape {}",
			"'never-run'.execute()",
			"def f = 'never-run'.&execute; f()"
		)) denied(text, Map.of());
	}

	@Test void dynamicReceiversNamesAndNestedReturnsAreChecked() {
		for (var text : List.of("danger.getRuntime()", "values.danger()", "values.nested()", "values.field", "values.@field")) {
			denied(text, Map.of("danger", Runtime.getRuntime(), "values", new Values()));
		}
		denied("def name = 'execute'; 'never-run'.\"$name\"()", Map.of());
		denied("values.metaClass = null", Map.of("values", new Values()));
		denied("values['metaClass'] = null", Map.of("values", new Values()));
		denied("values.putAt('metaClass', null)", Map.of("values", new Values()));
		denied("values.callback { value -> 'unused dangerous argument' }", Map.of("values", new Values()));
		denied("values.callback { 'unused implicit argument' }", Map.of("values", new Values()));
		denied("String.class.classLoader", Map.of());
		denied("java.util.concurrent.CompletableFuture.runAsync({ -> assert false })", Map.of());
		assertDoesNotThrow(() -> run("String[].class", Map.of()));
		assertDoesNotThrow(() -> run("java.net.URI.create('https://example.invalid/path')", Map.of()));
		denied("java.net.URI.create('https://example.invalid/path').toURL()", Map.of());
		denied("import static dev.uncandango.kubegroovy.groovytest.SandboxSecurityTests.Values.DANGER; DANGER", Map.of());
	}

	@Test void warmedIndyAndClosureCallsKeepChecks() throws Exception {
		assertEquals("safe", run("100.times { values.safe() }; values.safe()", Map.of("values", new Values())));
		denied("def f = { values.danger() }; f()", Map.of("values", new Values()));
		assertEquals(Arrays.asList("A", null, "B"), run("['a', null, 'b']*.toUpperCase()", Map.of()));
		assertNull(run("def value = null; value?.toString()", Map.of()));
	}

	@Test void ordinaryAnnotationsAndBenignTransformsAreAllowed() throws Exception {
		var handler = (Class<?>) run("import net.neoforged.bus.api.*; class Handler { @SubscribeEvent(priority=EventPriority.HIGH) void onEvent(Event event) {} }; Handler", Map.of());
		var event = handler.getDeclaredMethod("onEvent", Event.class).getAnnotation(SubscribeEvent.class);
		assertNotNull(event);
		assertEquals(EventPriority.HIGH, event.priority());
		var annotated = (Class<?>) run("import dev.uncandango.kubegroovy.groovytest.SandboxSecurityTests.Metadata as Info; import dev.uncandango.kubegroovy.groovytest.SandboxSecurityTests.Mode; @Info(type=String, mode=Mode.STRICT) class Data {}; Data", Map.of());
		assertEquals(String.class, annotated.getAnnotation(Metadata.class).type());
		assertEquals(Mode.STRICT, annotated.getAnnotation(Metadata.class).mode());
		assertEquals("ready", run("class Data { @Lazy String value = 'ready' }; new Data().value", Map.of()));
		assertEquals("Data(x)", run("import groovy.transform.*; @Canonical class Data { String value }; new Data('x').toString()", Map.of()));
		assertEquals("safe", run("@groovy.transform.TupleConstructor(pre={ assert true }) class Data { String value }; new Data('safe').value", Map.of()));
	}

	@Test void ordinaryGroovyAssignmentsPointersAndTransformsRemainUsable() throws Exception {
		assertEquals(3, run("def values = [a: 1]; values.a = 2; values.a += 1; values.a", Map.of()));
		assertEquals(3, run("def values = [a: 1]; values.a++; ++values.a; values.a", Map.of()));
		assertEquals(1, run("def value; value = 1; value", Map.of()));
		assertEquals(2, run("def n = 'a'; def values = [a: 1]; values.\"$n\" = 2; values.\"$n\"", Map.of()));
		assertEquals("ABC", run("def upper = 'abc'.&toUpperCase; upper()", Map.of()));
		assertEquals("Data(x)", run("@groovy.transform.Canonical class Data { String value }; new Data('x').toString()", Map.of()));
	}
}
