package dev.uncandango.kubegroovy.groovytest;

import dev.latvian.apps.tinyserver.HTTPServer;
import dev.latvian.apps.tinyserver.http.response.HTTPResponse;
import dev.uncandango.kubegroovy.plugin.ClassFilter;
import dev.uncandango.kubegroovy.plugin.builtin.BuiltinKubeGroovyPlugin;
import dev.uncandango.kubegroovy.script.annotations.HideFromGY;
import groovy.ui.GroovyMain;
import groovy.util.FileTreeBuilder;
import org.codehaus.groovy.reflection.CachedMethod;
import org.junit.jupiter.api.Test;

import javax.swing.JEditorPane;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.net.Socket;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.Formatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class ClassFilterTests {
	public static class Parent {
		public String value() { return "safe"; }
		public String overloaded(String value) { return value; }
		public String overloaded(int value) { return "" + value; }
		public ByteArrayInputStream stream() { return new ByteArrayInputStream(new byte[0]); }
		@HideFromGY public String hidden() { return "hidden"; }
		public String field;
	}
	public static class Child extends Parent {}
	public static class TypedValues {
		public Parent value() { return new Parent(); }
		public void accept(Parent[] values) {}
	}
	public static class Visibility {
		static String packageStatic() { return "package"; }
		public static String publicStatic() { return "public"; }
		protected static String protectedStatic() { return "protected"; }
		private static String privateStatic() { return "private"; }
		String packageInstance() { return "instance"; }
	}
	public static class VisibilityChild extends Visibility {}
	public static class Editor extends JEditorPane {
		@Override public void setPage(String url) { throw new AssertionError("Blocked desktop method executed"); }
	}
	public static class DesktopValues {
		public Editor editor() { throw new AssertionError("Blocked return type executed"); }
		public void accept(Editor editor) { throw new AssertionError("Blocked parameter type executed"); }
	}

	private static ClassFilter sandbox() {
		var filter = new ClassFilter();
		BuiltinKubeGroovyPlugin.registerSandboxClasses(filter);
		return filter;
	}

	@Test void newFilterHasNoBaseline() {
		var filter = new ClassFilter();
		assertTrue(filter.isAllowed(Runtime.class));
		assertTrue(filter.isGlobalMethodNameAllowed("execute"));
	}

	@Test void desktopIoTypesAreDenied() throws Exception {
		var filter = sandbox();
		for (var name : List.of("javax.swing.JEditorPane", "javax.swing.text.html.HTMLEditorKit", "java.awt.Toolkit",
			"java.awt.image.BufferedImage", "java.applet.Applet", "javax.sound.sampled.AudioSystem", "javax.sound.midi.MidiSystem",
			"javax.imageio.ImageIO", "javax.imageio.stream.FileImageInputStream", "java.awt.Desktop", "java.awt.Robot")) {
			assertFalse(filter.isAllowed(name), name);
		}
		assertFalse(filter.isAllowed(JEditorPane.class));
		assertFalse(filter.isAllowed(JEditorPane[].class));
		assertFalse(filter.isConstructorAllowed(JEditorPane.class.getConstructor(String.class)));
		assertFalse(filter.isMethodAllowed(JEditorPane.class, JEditorPane.class.getMethod("setPage", String.class)));
		assertFalse(filter.isMethodAllowed(JEditorPane.class, JEditorPane.class.getMethod("getText")));
		// Subtypes outside the desktop packages are caught by the type rule, even when allowed by name.
		filter.allow(Editor.class);
		assertFalse(filter.isAllowed(Editor.class));
		assertFalse(filter.isMethodAllowed(Editor.class, Editor.class.getMethod("setPage", String.class)));
		assertFalse(filter.isMethodAllowed(DesktopValues.class, DesktopValues.class.getMethod("editor")));
		assertFalse(filter.isMethodAllowed(DesktopValues.class, DesktopValues.class.getMethod("accept", Editor.class)));
		assertTrue(filter.isAllowed("javax.swingish.Safe"));
		assertTrue(filter.isAllowed(String.class));
	}

	@Test void fileTreeAndTinyServerAreDenied() throws Exception {
		var filter = sandbox();
		for (var type : List.of(FileTreeBuilder.class, HTTPServer.class, HTTPResponse.class)) {
			assertFalse(filter.isAllowed(type.getName()));
			assertFalse(filter.isAllowed(type));
			assertFalse(filter.isAllowed(type.arrayType()));
		}
		assertFalse(filter.isConstructorAllowed(FileTreeBuilder.class.getConstructor()));
		assertFalse(filter.isMethodAllowed(FileTreeBuilder.class,
			FileTreeBuilder.class.getMethod("file", String.class, CharSequence.class)));
		assertFalse(filter.isMethodAllowed(HTTPServer.class, HTTPServer.class.getMethod("start")));
		assertTrue(filter.isAllowed("dev.latvian.apps.tinyserverish.Safe"));
		assertTrue(filter.isAllowed("groovy.util.FileTreeBuilderish"));
	}

	@Test void sandboxPackagesAreDenied() {
		var filter = sandbox();
		for (var pkg : List.of("ai.djl", "org.jsoup", "de.keksuccino", "org.apache.logging.log4j.core",
			"com.electronwill.nightconfig", "crash_assistant_relocated_libs", "dev.kostromdan.mods.crash_assistant",
			"scala.io", "scala.sys", "com.mojang.realmsclient", "groovy.ui", "dev.langchain4j", "opennlp.tools",
			"me.lucko.spark", "com.typesafe", "com.moandjiezana", "ai.onnxruntime", "com.euphoriapatches.shadow.com.google.gson")) {
			for (var name : List.of(pkg, pkg + ".Blocked", pkg + ".nested.Blocked", pkg + ".nested.Blocked$Inner")) {
				assertFalse(filter.isAllowed(name), name);
			}
			assertTrue(filter.isAllowed(pkg + "ish.Safe"));
		}
		assertTrue(filter.isAllowed("org.apache.logging.log4j.Logger"));
		assertTrue(filter.isAllowed("com.google.gson.Gson"));
		assertTrue(filter.isAllowed("scala.collection.immutable.List"));
		assertTrue(filter.isAllowed("com.mojang.serialization.Codec"));
	}

	@Test void sandboxPackagesAreOrdinaryRules() {
		var filter = sandbox();
		assertFalse(filter.isAllowed("org.jsoup.Jsoup"));
		filter.allow("org.jsoup.Jsoup");
		assertTrue(filter.isAllowed("org.jsoup.Jsoup"));
		filter.allow(Runtime.class);
		assertFalse(filter.isAllowed(Runtime.class), "exact class denies still win");
	}

	@Test void ioAndConcurrencyRules() {
		var filter = sandbox();
		for (var type : List.of(File.class, Serializable.class, Closeable.class, IOException.class, URI.class)) {
			assertTrue(filter.isAllowed(type), type.getName());
		}
		for (var type : List.of(FileInputStream.class, Socket.class, Executors.class, ThreadLocalRandom.class,
			ForkJoinTask.class, Thread.State.class, Thread.Builder.class, ProcessHandle.Info.class, FileChannel.class)) {
			assertFalse(filter.isAllowed(type), type.getName());
		}
		try (var executor = Executors.newSingleThreadExecutor()) {
			assertFalse(filter.isAllowed(executor.getClass()));
		}
		assertTrue(filter.isAllowed(ConcurrentHashMap.class));
	}

	@Test void formatterAndGroovyUiAreDenied() throws Exception {
		var filter = sandbox();
		assertFalse(filter.isAllowed(Formatter.class.getName()));
		assertFalse(filter.isAllowed(Formatter.class));
		assertFalse(filter.isAllowed(Formatter[].class));
		assertFalse(filter.isConstructorAllowed(Formatter.class.getConstructor(String.class)));
		assertFalse(filter.isConstructorAllowed(Formatter.class.getConstructor()));
		assertFalse(filter.isAllowed(GroovyMain.class));
		assertFalse(filter.isMethodAllowed(GroovyMain.class, GroovyMain.class.getMethod("main", String[].class)));
		assertTrue(filter.isAllowed(Locale.class));
	}

	@Test void packagePrivateStaticJavaMethodsCannotBeAllowed() throws Exception {
		var filter = new ClassFilter();
		var method = Visibility.class.getDeclaredMethod("packageStatic");
		filter.allow(Visibility.class);
		filter.allowMethods(Visibility.class, "packageStatic");
		assertFalse(filter.isMethodAllowed(Visibility.class, method));
		assertFalse(filter.isMethodAllowed(VisibilityChild.class, method));
		assertFalse(filter.isMethodAllowed(Visibility.class, new CachedMethod(method)));
		for (var name : List.of("publicStatic", "protectedStatic", "privateStatic", "packageInstance")) {
			assertTrue(filter.isMethodAllowed(Visibility.class, Visibility.class.getDeclaredMethod(name)), name);
		}
	}

	@Test void packageBoundariesAndExactPrecedence() {
		var filter = new ClassFilter();
		assertTrue(filter.isAllowed("example.api.Value"));
		filter.denyPackage("example.api");
		assertFalse(filter.isAllowed("example.api.Value"));
		assertTrue(filter.isAllowed("example.apiculture.Value"));
		filter.allow("example.api.Value");
		assertTrue(filter.isAllowed("example.api.Value"));
		filter.deny("example.api.Value");
		assertFalse(filter.isAllowed("example.api.Value"));
	}

	@Test void sandboxAndHiddenRestrictionsWin() throws Exception {
		var filter = sandbox();
		filter.allow(Runtime.class);
		filter.allowMethodName("execute");
		assertFalse(filter.isAllowed(Runtime.class));
		assertFalse(filter.isMemberNameAllowed(String.class, "execute"));
		assertFalse(filter.isMethodAllowed(Parent.class, Parent.class.getMethod("hidden")));
		assertFalse(filter.isMethodAllowed(Parent.class, Parent.class.getMethod("stream")));
		assertTrue(filter.isAllowed(Path.class));
		assertTrue(filter.isAllowed(Path.of("safe").getClass()));
	}

	@Test void overloadsInheritanceAndDeniesWin() throws Exception {
		var filter = new ClassFilter();
		filter.denyMethod(Parent.class, "overloaded", int.class);
		filter.allowMethods(Child.class, "overloaded");
		assertFalse(filter.isMethodAllowed(Child.class, Parent.class.getMethod("overloaded", int.class)));
		assertTrue(filter.isMethodAllowed(Child.class, Parent.class.getMethod("overloaded", String.class)));
		filter.denyMethods(Child.class, "value");
		assertFalse(filter.isMethodAllowed(Child.class, Parent.class.getMethod("value")));
		assertTrue(filter.isMethodAllowed(Parent.class, Parent.class.getMethod("value")));
	}

	@Test void fieldConstructorAndTypeRules() throws Exception {
		var filter = new ClassFilter();
		filter.denyField(Parent.class, "field");
		filter.denyConstructor(Parent.class);
		assertFalse(filter.isFieldAllowed(Child.class, Parent.class.getField("field")));
		assertFalse(filter.isConstructorAllowed(Parent.class.getConstructor()));
		filter.denyParameterType(CharSequence.class);
		assertFalse(filter.isMethodAllowed(Parent.class, Parent.class.getMethod("overloaded", String.class)));
		filter.denyReturnType(CharSequence.class);
		assertFalse(filter.isMethodAllowed(Parent.class, Parent.class.getMethod("value")));
	}

	@Test void frozenPolicySupportsConcurrentLookups() throws Exception {
		var filter = sandbox();
		filter.freeze();
		assertThrows(IllegalStateException.class, () -> filter.deny(String.class));
		try (var executor = Executors.newFixedThreadPool(4)) {
			var tasks = IntStream.range(0, 64).mapToObj(i -> executor.submit(() -> {
				assertTrue(filter.isAllowed(String.class));
				assertFalse(filter.isAllowed(Socket.class));
			})).toList();
			for (var task : tasks) task.get();
		}
	}

	@Test void typeRulesInvalidateCachesAndRejectLateRegistration() throws Exception {
		var filter = new ClassFilter();
		var value = Parent.class.getMethod("value");
		assertTrue(filter.isAllowed(Child.class));
		assertTrue(filter.isMethodAllowed(Child.class, value));
		filter.denyTypes(Parent.class::isAssignableFrom);
		filter.allow(Child.class);
		filter.allowMethods(Child.class, "value");
		assertFalse(filter.isAllowed(Child.class));
		assertFalse(filter.isAllowed(Child[].class));
		assertFalse(filter.isMethodAllowed(Child.class, value));
		filter.freeze();
		assertThrows(IllegalStateException.class, () -> filter.denyTypes(type -> false));
		assertThrows(IllegalStateException.class, () -> filter.denyMethodNames(name -> false));
		assertThrows(IllegalStateException.class, () -> filter.denyMemberNames((type, name) -> false));
	}

	@Test void nameRulesGuardDynamicAndSelectedMembers() throws Exception {
		var filter = new ClassFilter();
		assertTrue(filter.isMethodAllowed(Parent.class, Parent.class.getMethod("value")));
		filter.denyMethodNames("value"::equals);
		filter.allowMethodName("value");
		assertFalse(filter.isGlobalMethodNameAllowed("value"));
		assertFalse(filter.isMemberNameAllowed(Parent.class, "value"));
		assertFalse(filter.isMethodAllowed(Parent.class, Parent.class.getMethod("value")));
		filter.denyMemberNames((type, name) -> Child.class.isAssignableFrom(type) && name.equals("overloaded"));
		filter.allowMethods(Child.class, "overloaded");
		assertFalse(filter.isMemberNameAllowed(Child.class, "overloaded"));
		assertFalse(filter.isMethodAllowed(Child.class, Parent.class.getMethod("overloaded", String.class)));
		assertTrue(filter.isMethodAllowed(Parent.class, Parent.class.getMethod("overloaded", String.class)));
	}

	@Test void typeRulesProtectRuntimeSignatures() throws Exception {
		var filter = new ClassFilter();
		var value = TypedValues.class.getMethod("value");
		var accept = TypedValues.class.getMethod("accept", Parent[].class);
		assertTrue(filter.isMethodAllowed(TypedValues.class, value));
		assertTrue(filter.isMethodAllowed(TypedValues.class, accept));
		filter.denyTypes(Parent.class::equals);
		filter.allow(Parent.class);
		filter.allowReturnType(Parent.class);
		filter.allowParameterType(Parent.class);
		assertFalse(filter.isAllowed(Parent.class));
		assertFalse(filter.isMethodAllowed(TypedValues.class, value));
		assertFalse(filter.isMethodAllowed(TypedValues.class, accept));
	}
}
