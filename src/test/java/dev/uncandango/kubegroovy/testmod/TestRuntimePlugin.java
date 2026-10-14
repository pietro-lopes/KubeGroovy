package dev.uncandango.kubegroovy.testmod;

import dev.uncandango.kubegroovy.plugin.ClassFilter;
import dev.uncandango.kubegroovy.plugin.KubeGroovyPlugin;
import dev.uncandango.kubegroovy.plugin.TypeSuggestionRegistry;
import dev.uncandango.kubegroovy.script.BindingRegistry;
import dev.uncandango.kubegroovy.testmod.security.SandboxGameTests;
import dev.uncandango.kubegroovy.testmod.web.TestControlWeb;
import dev.uncandango.kubegroovy.web.LocalWebServerAPIRegistry;
import dev.uncandango.kubegroovy.web.LocalWebServerRegistry;

import java.util.Objects;
import java.util.stream.Stream;

public class TestRuntimePlugin implements KubeGroovyPlugin {
	@Override
	public void registerBindings(BindingRegistry bindings) {
		bindings.add("TestRuntime", TestRuntime.class);
	}

	@Override
	public void registerTypeSuggestions(TypeSuggestionRegistry registry) {
		registry.register(TestSuggestionValue.class, (cx, typeInfo) -> {
			Objects.requireNonNull(cx.getRegistries(), "The addon provider should receive the active script context");
			return Stream.of("testmod:addon_suggestion");
		});
	}

	public static final class TestSuggestionValue {
	}

	/// Test-only class-filter allowances so script fixtures can reference
	/// wrapper targets the production deny-list otherwise blocks. The
	/// production plugin policy is untouched; exact allows
	/// take precedence over package denies, scoped to test runs.
	/// `java.lang.Throwable` is needed because any try/catch in script
	/// bytecode makes the JVM verifier load it via the script classloader,
	/// and `java.lang.AssertionError` because native `assert` compiles to a
	/// direct throw of it.
	@Override
	public void registerClasses(ClassFilter filter) {
		var probe = SandboxGameTests.Probe.class;
		filter.denyMethods(probe, "forbidden");
		filter.denyMethods(probe, "secret");
		filter.denyField(probe, "denied");
		filter.denyConstructor(probe, String.class);
		filter.allow("java.io.File");
		filter.allow("java.nio.file.Path");
		filter.allow("java.lang.Throwable");
		filter.allow("java.lang.AssertionError");
		filter.allow("java.lang.IllegalStateException");
		filter.allow("dev.uncandango.kubegroovy.error.KubeRuntimeException");
		filter.allow("dev.uncandango.kubegroovy.core.PlayerSelector");
		filter.allow("dev.uncandango.kubegroovy.script.type.TypeInfo");
		filter.allow("dev.uncandango.kubegroovy.script.bridge.Context");
		filter.allow("dev.uncandango.kubegroovy.script.WithScriptContext");
	}

	@Override
	public void registerLocalWebServerAPIs(LocalWebServerAPIRegistry registry) {
		TestControlWeb.registerAPIs(registry);
	}

	@Override
	public void registerLocalWebServer(LocalWebServerRegistry registry) {
		TestControlWeb.register(registry);
	}

	@Override
	public void registerLocalWebServerWithAuth(LocalWebServerRegistry registry) {
		TestControlWeb.registerWithAuth(registry);
	}
}
