package dev.uncandango.kubegroovy.testmod.security;

import dev.uncandango.kubegroovy.KubeGroovy;
import dev.uncandango.kubegroovy.KubeGroovyPaths;
import dev.uncandango.kubegroovy.block.BlockBuilder;
import dev.uncandango.kubegroovy.block.entity.BlockEntityBuilder;
import dev.uncandango.kubegroovy.client.LangKubeEvent;
import dev.uncandango.kubegroovy.entity.AttributeBuilder;
import dev.uncandango.kubegroovy.item.FoodBuilder;
import dev.uncandango.kubegroovy.item.ItemBehaviorFunctions;
import dev.uncandango.kubegroovy.item.ItemBuilder;
import dev.uncandango.kubegroovy.registry.BuilderBase;
import dev.uncandango.kubegroovy.script.ScriptManager;
import dev.uncandango.kubegroovy.script.annotations.HideFromGY;
import dev.uncandango.kubegroovy.script.bridge.Context;
import dev.uncandango.kubegroovy.script.groovy.KubeGroovyClassLoader;
import dev.uncandango.kubegroovy.script.groovy.KubeGroovySandbox;
import dev.uncandango.kubegroovy.script.annotations.RemapForGY;
import dev.uncandango.kubegroovy.text.action.TextActionBuilder;
import groovy.lang.Binding;
import groovy.lang.GroovySystem;
import groovy.lang.Script;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.neoforged.testframework.DynamicTest;
import net.neoforged.testframework.annotation.ForEachTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.GameTest;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.CompilationCustomizer;

import javax.swing.JEditorPane;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.AnnotatedElement;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static dev.uncandango.kubegroovy.testmod.GameAsserts.assertj;

@ForEachTest(groups = "kubegroovy.security")
public final class SandboxGameTests {
	public static class LangBase {
		public int calls;
		public String generateLang(Map<String, String> lang) { calls++; return lang.get("name"); }
		public String generateLang(String name) { calls++; return name; }
		public Object erased() { return Runtime.getRuntime(); }
	}

	public static class Probe {
		public static int packageStaticCalls;
		@RemapForGY("packageAlias") static String packageStatic() { packageStaticCalls++; return "unsafe"; }
		protected static String protectedStatic() { return "protected"; }
		public int calls;
		public String denied = "private";
		public Probe() {}
		public Probe(String value) { calls++; }
		public String forbidden() { calls++; return "unsafe"; }
		public String safe() { return "safe"; }
		public Object erased() { return Runtime.getRuntime(); }
		public Object nested() { return List.of(Runtime.getRuntime()); }
		@RemapForGY("alias") public String secret() { calls++; return "unsafe"; }
	}
	public static class DesktopProbe extends JEditorPane {
		public int calls;
		@Override public void setPage(String url) { calls++; }
	}

	private static Object run(ScriptManager manager, String text, Map<String, Object> variables) throws Exception {
		var config = new CompilerConfiguration();
		config.getOptimizationOptions().put("indy", true);
		config.setDisabledGlobalASTTransformations(Set.of("groovy.grape.GrabAnnotationTransformation"));
		config.addCompilationCustomizers(KubeGroovySandbox.customizers(ScriptManager.getClassFilter(), manager.contextFactory.getTypeWrappers()).toArray(CompilationCustomizer[]::new));
		try (var loader = new KubeGroovyClassLoader(SandboxGameTests.class.getClassLoader(), manager, config)) {
			return ScopedValue.where(Context.CURRENT, manager.context).call(() -> {
				var script = (Script) loader.parseClass(text).getConstructor().newInstance();
				script.setBinding(new Binding(variables));
				return script.run();
			});
		}
	}

	@GameTest
	@EmptyTemplate
	@TestHolder(value = "sandbox_super_dispatch", description = "Guarded super calls use nonvirtual bridges and preserve runtime checks")
	static void superDispatch(final DynamicTest test) {
		test.onGameTest(helper -> {
			assertj(helper, () -> {
				var manager = KubeGroovy.getStartupScriptManager();
				try {
					var imports = "import dev.uncandango.kubegroovy.testmod.security.SandboxGameTests.LangBase; ";
					var hierarchy = imports + "class Middle extends LangBase { String generateLang(Map lang) { 'middle:' + super.generateLang(lang) }; String generateLang(String name) { 'middle:' + super.generateLang(name) } }; "
						+ "class Child extends Middle { String generateLang(Map lang) { 'child:' + super.generateLang(lang) }; String generateLang(String name) { 'child:' + super.generateLang(name) } }; ";
					assertThat(run(manager, hierarchy + "def value = new Child(); 100.times { assert value.generateLang([name: 'ok']) == 'child:middle:ok'; assert value.generateLang('ok') == 'child:middle:ok' }; value.calls", Map.of())).isEqualTo(200);
					assertThat(run(manager, "class Parent { String value() { 'parent' } }; class Child extends Parent { String value() { super.value() + ':child' } }; def child = new Child(); 100.times { assert child.value() == 'parent:child' }; child.value()", Map.of())).isEqualTo("parent:child");
					assertThatThrownBy(() -> run(manager, imports + "class Child extends LangBase { Object erased() { super.erased() } }; new Child().erased()", Map.of())).isInstanceOf(Exception.class);
					var probe = (Probe) run(manager, "import dev.uncandango.kubegroovy.testmod.security.SandboxGameTests.Probe; class Child extends Probe { String forbidden() { super.forbidden() } }; new Child()", Map.of());
					assertThatThrownBy(() -> run(manager, "probe.'super$2$forbidden'()", Map.of("probe", probe))).isInstanceOf(Exception.class);
					assertThat(probe.calls).isZero();
				} catch (Exception e) { throw new AssertionError(e); }
			});
			helper.succeed();
		});
	}

	@GameTest
	@EmptyTemplate
	@TestHolder(value = "sandbox_builder_lang", description = "Script item-builder subclasses can call exposed builder lifecycle methods")
	static void builderLang(final DynamicTest test) {
		test.onGameTest(helper -> {
			assertj(helper, () -> {
				var manager = KubeGroovy.getStartupScriptManager();
				var translations = new HashMap<LangKubeEvent.Key, String>();
				var lang = new LangKubeEvent("en_us", translations);
				try {
					var source = "import dev.uncandango.kubegroovy.item.ItemBuilder; import dev.uncandango.kubegroovy.client.LangKubeEvent; import net.minecraft.resources.Identifier; "
						+ "class Derivative extends ItemBuilder { Derivative(Identifier id) { super(id) }; void generateLang(LangKubeEvent lang) { super.generateLang(lang); lang.add('testmod', 'security.derivative', 'Derivative') } }; "
						+ "class Sulfur extends Derivative { Sulfur(Identifier id) { super(id) }; void generateLang(LangKubeEvent lang) { super.generateLang(lang); lang.add('testmod', 'security.sulfur', 'Sulfur') } }; "
						+ "def builder = new Sulfur(Identifier.parse('testmod:security_sulfur')); builder.translationKey('item.testmod.security_sulfur'); 100.times { builder.generateLang(lang) }; builder";
					var builder = run(manager, source, Map.of("lang", lang));
					assertThat(translations).containsExactlyInAnyOrderEntriesOf(Map.of(
						new LangKubeEvent.Key("testmod", "en_us", "item.testmod.security_sulfur"), "Security Sulfur",
						new LangKubeEvent.Key("testmod", "en_us", "security.derivative"), "Derivative",
						new LangKubeEvent.Key("testmod", "en_us", "security.sulfur"), "Sulfur"));
					assertThat(run(manager, "builder.kgy$getDisplayName()", Map.of("builder", builder))).isNull();
					assertThat(run(manager, "import dev.uncandango.kubegroovy.registry.BuilderBase; import net.minecraft.resources.Identifier; class StringBuilder extends BuilderBase<String> { StringBuilder(Identifier id) { super(id) }; String createObject() { 'created' } }; def value = new StringBuilder(Identifier.parse('testmod:security_value')); assert value.createTransformedObject() == 'created'; value.get()", Map.of())).isEqualTo("created");
				} catch (Exception e) { throw new AssertionError(e); }
			});
			helper.succeed();
		});
	}

	@GameTest
	@EmptyTemplate
	@TestHolder(value = "sandbox_builder_visibility", description = "BuilderBase declarations expose their members while unrelated builders retain HideFromGY")
	static void builderVisibility(final DynamicTest test) {
		test.onGameTest(helper -> {
			assertj(helper, () -> {
				for (var type : List.of(
					BuilderBase.class,
					BlockBuilder.class,
					BlockEntityBuilder.class,
					AttributeBuilder.class,
					ItemBuilder.class)) {
					assertThat(BuilderBase.class.isAssignableFrom(type)).as("registry builder %s", type).isTrue();
					var members = new ArrayList<AnnotatedElement>();
					members.add(type);
					Collections.addAll(members, type.getDeclaredMethods());
					Collections.addAll(members, type.getDeclaredFields());
					Collections.addAll(members, type.getDeclaredConstructors());
					for (var member : members) assertThat(member.isAnnotationPresent(HideFromGY.class))
						.as("builder declaration %s", member).isFalse();
				}
				for (var entry : Map.of(
					FoodBuilder.class, List.of("applyTo"),
					ItemBehaviorFunctions.class, List.of("kgy$getOrCreateBehavior", "displayName", "foodEaten"),
					DataComponentMap.Builder.class, List.of("set"),
					DataComponentPatch.Builder.class, List.of("set")).entrySet()) {
					assertThat(BuilderBase.class.isAssignableFrom(entry.getKey())).isFalse();
					for (var name : entry.getValue()) {
						assertThat(entry.getKey().getDeclaredMethods())
							.filteredOn(method -> method.getName().equals(name)
								&& (!(name.equals("displayName") || name.equals("set")) || method.getParameterCount() == 2))
							.isNotEmpty()
							.allSatisfy(method -> assertThat(method.isAnnotationPresent(HideFromGY.class))
								.as("hidden declaration %s", method).isTrue());
					}
				}
				assertThat(TextActionBuilder.class.getDeclaredFields()).filteredOn(field -> field.getName().equals("actions"))
					.isNotEmpty().allSatisfy(field -> assertThat(field.isAnnotationPresent(HideFromGY.class)).isTrue());
			});
			helper.succeed();
		});
	}

	@GameTest
	@EmptyTemplate
	@TestHolder(value = "sandbox_runtime_policy", description = "Global policy blocks capabilities, fields, aliases and I/O while preserving normal indy dispatch")
	static void sandboxPolicy(final DynamicTest test) {
		test.onGameTest(helper -> {
			assertj(helper, () -> {
			var manager = KubeGroovy.getStartupScriptManager();
			var probe = new Probe();
			// Warm the trusted cache before a script observes it.
			GroovySystem.getMetaClassRegistry().getMetaClass(Probe.class).initialize();
			Probe.packageStaticCalls = 0;
			var visibilityImports = "import dev.uncandango.kubegroovy.testmod.security.SandboxGameTests.Probe; ";
			for (var text : List.of("Probe.packageStatic()", "def name = 'packageStatic'; Probe.\"$name\"()",
				"def method = Probe.&packageStatic; method()", "Probe.packageAlias()", "probe.packageStatic()",
				"class Child extends Probe {}; Child.packageStatic()")) {
				assertThatThrownBy(() -> run(manager, visibilityImports + text, Map.of("probe", probe))).as(text).isInstanceOf(Exception.class);
			}
			assertThat(Probe.packageStaticCalls).as("package-private static Java methods never execute").isZero();
			for (var text : List.of("probe.forbidden()", "probe.alias()", "probe.erased()", "probe.nested()", "probe.denied", "probe.@denied", "def n = 'denied'; probe.\"$n\"", "new dev.uncandango.kubegroovy.testmod.security.SandboxGameTests.Probe('blocked')", "file.text", "file.delete()")) {
				assertThatThrownBy(() -> run(manager, text, Map.of("probe", probe, "file", new File(KubeGroovyPaths.GAMEDIR.toFile(), "sandbox-safe.json"))))
					.as(text).isInstanceOf(Exception.class);
			}
			assertThat(probe.calls).as("blocked methods and aliases never execute").isZero();
			for (var address : List.of("http://127.0.0.1:61423/api/mods", "file:///C:/Temp/kgy_secret.txt",
				"file:///C:/game/kubegroovy/link_escape_test/kgy_secret.txt")) {
				assertThatThrownBy(() -> run(manager, "new javax.swing.JEditorPane('" + address + "').getText()", Map.of()))
					.as("desktop string I/O %s", address).isInstanceOf(Exception.class).hasMessageContaining("javax.swing.JEditorPane");
			}
			var desktop = new DesktopProbe();
			for (var text : List.of("pane.setPage('http://127.0.0.1:61423/api/mods')", "def n = 'setPage'; pane.\"$n\"('file:///C:/Temp/kgy_secret.txt')", "pane.text")) {
				assertThatThrownBy(() -> run(manager, text, Map.of("pane", desktop))).as(text).isInstanceOf(Exception.class);
			}
			assertThat(desktop.calls).as("desktop subclass never performs string I/O").isZero();
			var modsTarget = KubeGroovyPaths.GAMEDIR.resolve("mods/sandbox-denied.jar");
			for (var value : List.of(modsTarget, modsTarget.toFile(), "mods/sandbox-denied.jar")) {
				assertThatThrownBy(() -> run(manager, "import dev.uncandango.kubegroovy.util.JsonIO; JsonIO.write(target, [safe: false])", Map.of("target", value)))
					.as("mods writes denied for %s", value.getClass()).isInstanceOf(Exception.class).hasRootCauseInstanceOf(IOException.class);
			}
			assertThat(Files.exists(modsTarget)).as("blocked mods writes never create a payload").isFalse();
			try {
				assertThat(run(manager, "100.times { probe.safe() }; probe.safe()", Map.of("probe", probe))).isEqualTo("safe");
				assertThat(run(manager, visibilityImports + "class Child extends Probe { String value() { super.protectedStatic() } }; new Child().value()", Map.of())).isEqualTo("protected");
				assertThat(run(manager, "class Helper { @groovy.transform.PackageScope static String value() { 'helper' }; private static String hidden() { value() }; static String callHelper() { hidden() } }; Helper.callHelper()", Map.of())).isEqualTo("helper");
				var target = KubeGroovyPaths.GAMEDIR.resolve("sandbox-approved.json");
				try {
					assertThat(run(manager, "import dev.uncandango.kubegroovy.util.JsonIO; JsonIO.write('sandbox-approved.json', [safe: true]); JsonIO.read('sandbox-approved.json').safe.asBoolean", Map.of())).isEqualTo(true);
					assertThat(Files.isRegularFile(target)).isTrue();
				} finally { Files.deleteIfExists(target); }
			} catch (Exception e) { throw new AssertionError(e); }
			});
			helper.succeed();
		});
	}
}
