package dev.uncandango.kubegroovy.groovytest;

import dev.uncandango.kubegroovy.plugin.builtin.wrapper.UtilsWrapper;
import dev.uncandango.kubegroovy.item.ItemBuilder;
import dev.uncandango.kubegroovy.event.EventResult;
import dev.uncandango.kubegroovy.event.EventExit;
import dev.uncandango.kubegroovy.registry.RegistryKubeEvent;
import dev.uncandango.kubegroovy.util.KubeIdentifier;
import dev.uncandango.kubegroovy.script.KubeGroovyContext;
import dev.uncandango.kubegroovy.script.annotations.HideFromGY;
import dev.uncandango.kubegroovy.script.annotations.RemapForGY;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import dev.uncandango.kubegroovy.script.bridge.BaseFunction;
import dev.uncandango.kubegroovy.script.bridge.Context;
import dev.uncandango.kubegroovy.script.bridge.ContextFactory;
import dev.uncandango.kubegroovy.script.bridge.GroovyConversions;
import dev.uncandango.kubegroovy.script.bridge.TypeWrappers;
import dev.uncandango.kubegroovy.script.groovy.KubeGroovyMetaClassCreationHandle;
import dev.uncandango.kubegroovy.script.groovy.KubeGroovySandbox;
import groovy.lang.Binding;
import groovy.lang.Closure;
import groovy.lang.GroovyShell;
import groovy.lang.MissingMethodException;
import groovy.lang.MissingPropertyException;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.codehaus.groovy.runtime.typehandling.GroovyCastException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import net.minecraft.resources.Identifier;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Focused regression coverage for the Groovy-only interoperability layer. */
class KubeGroovyInteropTests {
	//private static final ScopedValue<Context> CONTEXT = ScopedValue.newInstance();

	@BeforeEach
	void installBridge() {
		//Context.setCurrent(null);
		KubeGroovyMetaClassCreationHandle.install();
	}

	@AfterEach
	void clearContext() {
		//Context.setCurrent(null);
	}

	@Test
	void registeredWrappersKeepValidatorsAndFactories() {
		var wrappers = new TypeWrappers();
		var validated = new AtomicBoolean();
		wrappers.register(WrappedValue.class,
			(from, target) -> {
				validated.set(true);
				return from instanceof String;
			},
			(from, target) -> new WrappedValue((String) from), true);

		var value = (WrappedValue) wrappers.coerce("wrapped", WrappedValue.class);
		assertEquals("wrapped", value.value());
		assertTrue(validated.get());
		assertThrows(GroovyCastException.class, () -> wrappers.coerce(42, WrappedValue.class));

		wrappers.registerDirect(DirectValue.class, value1 -> new DirectValue(String.valueOf(value1)), false);
		assertEquals("direct", ((DirectValue) wrappers.coerce("direct", DirectValue.class)).value());
	}

	@Test
	void sandboxRewritesExplicitCastAndAsTypeForms() {
		var wrappers = new TypeWrappers();
		wrappers.register(String.class, (value, target) -> String.valueOf(value), true);
		var configuration = new CompilerConfiguration();
		KubeGroovySandbox.customizers(null, wrappers).forEach(configuration::addCompilationCustomizers);
		var shell = new GroovyShell(configuration);

		assertDoesNotThrow(() -> shell.parse("def castValue = 'value' as String; def typeValue = 'value'.asType(String)"));
		assertDoesNotThrow(() -> shell.parse("def simple = [] as List<String>; def nested = [] as List<List<String>>; def wildcard = [] as List<?>"));
	}

	@Test
	void callableFunctionsRestoreTheirOriginatingContext() {
		var context = new Context(new ContextFactory());
		ScopedValue.where(KubeGroovyContext.CURRENT, context).run(() -> {
			var function = new BaseFunction((Function<Object[], Object>) args -> {
				assertSame(context, Context.getCurrent());
				return args[0];
			});
			assertEquals("value", function.call("value"));
		});
		var previous = new Context(new ContextFactory());
		ScopedValue.where(KubeGroovyContext.CURRENT, previous).run(() -> {
			assertSame(previous, Context.getCurrent());
		});
	}

	@Test
	@SuppressWarnings("unchecked")
	void callbackAdaptersRestoreTheirOriginatingContext() {
		var context = new Context(new ContextFactory());
		ScopedValue.where(KubeGroovyContext.CURRENT, context).run( () -> {
			Closure<Object> closure = new Closure<Object>(this) {
				public Object doCall(Object value) {
					assertSame(context, Context.getCurrent());
					return value;
				}
			};
			var callback = GroovyConversions.toFunctionalInterface(closure, Function.class);
			assertEquals("value", ((Function<Object, Object>) callback).apply("value"));
		});

		var previous = new Context(new ContextFactory());
		ScopedValue.where(KubeGroovyContext.CURRENT, previous).run(() -> {
			assertSame(previous, Context.getCurrent());
		});
	}

	@Test
	void asyncWrappersPropagateCapturedContext() {
		var context = new Context(new ContextFactory());
		var seenRun = new AtomicReference<Context>();
		var seenSupply = new AtomicReference<Context>();
		ScopedValue.where(KubeGroovyContext.CURRENT, context).run(() -> {
			var captured = Context.getCurrent();
			var runFuture = java.util.concurrent.CompletableFuture.runAsync(
				() -> ScopedValue.where(Context.CURRENT, captured).run(() -> seenRun.set(Context.getCurrent())));
			runFuture.join();
			var supplyFuture = java.util.concurrent.CompletableFuture.supplyAsync(
				() -> ScopedValue.where(Context.CURRENT, captured).call(Context::getCurrent));
			supplyFuture.join();
			seenSupply.set((Context) supplyFuture.join());
		});
		assertSame(context, seenRun.get());
		assertSame(context, seenSupply.get());
	}

	@Test
	void publicScriptMethodsUseTheCurrentContextWithoutAContextArgument() throws Exception {
		var context = new Context(new ContextFactory());
		var result = (EventExit) ScopedValue.where(KubeGroovyContext.CURRENT, context)
			.call(() -> EventResult.Type.INTERRUPT_DEFAULT.exit("value"));

		assertSame(context, result.result.cx());
		assertDoesNotThrow(() -> RegistryKubeEvent.class.getMethod("create", KubeIdentifier.class, KubeIdentifier.class));
	}

//	@Test
//	void utilsRunAsyncPropagatesContext() {
//		var context = new Context(new ContextFactory());
//		var seenRun = new AtomicReference<Context>();
//		var seenSupply = new AtomicReference<Context>();
//		ScopedValue.where(KubeGroovyContext.CURRENT, context).run(() -> {
//			UtilsWrapper.runAsync(() -> seenRun.set(Context.getCurrent())).join();
//			var supplied = UtilsWrapper.supplyAsync(() -> Context.getCurrent()).join();
//			seenSupply.set((Context) supplied);
//		});
//		assertSame(context, seenRun.get());
//		assertSame(context, seenSupply.get());
//	}

//	@Test
//	void remapPrecedenceContextInjectionAndHiddenMembersAreStable() {
//		KubeGroovyMetaClassCreationHandle.refresh(BridgeTarget.class);
//		var target = new BridgeTarget();
//
//		// A regular Java getter wins over an annotated fallback alias.
//		assertEquals("normal", InvokerHelper.getProperty(target, "alias"));
//		assertEquals("renamed", InvokerHelper.getProperty(target, "renamed"));
//
//		var context = new Context(new ContextFactory());
//		ScopedValue.where(KubeGroovyContext.CURRENT, context).run(() -> {
//			assertEquals("context:context", InvokerHelper.invokeMethod(target, "contextValue", new Object[]{"context"}));
//		});
//
//		var configuration = new CompilerConfiguration();
//		configuration.getOptimizationOptions().put("indy", true);
//		var shell = new GroovyShell(getClass().getClassLoader(), new Binding(Map.of("target", target)), configuration);
//		var values = ScopedValue.where(KubeGroovyContext.CURRENT, context).call(() ->
//			shell.evaluate("target.mutable = 'changed'; [target.alias, target.value, target.ready, target.renamed, target.time, target.prefixedRenamed, target.contextProperty, target.mutable]"));
//		assertEquals(List.of("normal", "value", true, "renamed", "time", "prefixed", "context", "changed"), values);
//		assertThrows(MissingPropertyException.class, () -> shell.evaluate("target.hidden"));
//
//		assertThrows(MissingPropertyException.class, () -> InvokerHelper.getProperty(target, "hidden"));
//		assertThrows(MissingMethodException.class, () -> InvokerHelper.invokeMethod(target, "doesNotExist", new Object[0]));
//	}

	@Test
	void activeSandboxBlocksDangerousCalls() {
		var configuration = new CompilerConfiguration();
		KubeGroovySandbox.customizers(null).forEach(configuration::addCompilationCustomizers);
		var shell = new GroovyShell(configuration);
		var error = assertThrows(Throwable.class, () -> shell.evaluate("System.exit(1)"));
		assertTrue(hasCause(error, SecurityException.class)
			|| String.valueOf(error).contains("System")
			|| String.valueOf(error).contains("not allowed"));
	}

	@Test
	void builderStateUsesFluentMethodsInsteadOfDirectFieldAssignment() {
		var builder = new ItemBuilder(Identifier.parse("kubegroovy:interop_item"));
		var configuration = new CompilerConfiguration();
		KubeGroovySandbox.customizers(null).forEach(configuration::addCompilationCustomizers);
		var shell = new GroovyShell(getClass().getClassLoader(), new Binding(Map.of("builder", builder)), configuration);

		assertThrows(Throwable.class, () -> shell.evaluate("builder.maxStackSize = 7"));
		assertSame(builder, shell.evaluate("builder.maxStackSize(7)"));
	}

//	@Test
//	void constructorsUseRegisteredTypeWrappers() {
//		var wrappers = new TypeWrappers();
//		wrappers.register(WrappedValue.class, (cx, value, target) -> new WrappedValue(String.valueOf(value)), true);
//		var context = new Context(new ContextFactory() {
//			@Override
//			public TypeWrappers getTypeWrappers() {
//				return wrappers;
//			}
//		});
//
//		var constructed = (ConstructorTarget) ScopedValue.where(Context.CURRENT, context).call(() ->
//			InvokerHelper.invokeConstructorOf(ConstructorTarget.class, new Object[]{"wrapped"}));
//
//		assertEquals("wrapped", constructed.value.value());
//	}

	private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
		for (var current = error; current != null; current = current.getCause()) {
			if (type.isInstance(current)) {
				return true;
			}
		}
		return false;
	}

	record WrappedValue(String value) {
	}

	record DirectValue(String value) {
	}

	static class ConstructorTarget {
		final WrappedValue value;

		ConstructorTarget(WrappedValue value) {
			this.value = value;
		}
	}

	@RemapPrefixForGY("bridge$")
	public static class BridgeTarget {
		private String mutable = "initial";

		public String getAlias() {
			return "normal";
		}

		public String bridge$getAlias() {
			return "bridge";
		}

		@RemapForGY("renamed")
		public String getRenamedValue() {
			return "renamed";
		}

		@RemapForGY("getTime")
		public String getTimeValue() {
			return "time";
		}

		@RemapForGY("prefixedRenamed")
		public String bridge$getPrefixedValue() {
			return "prefixed";
		}

		public String bridge$getValue() {
			return "value";
		}

		public boolean bridge$isReady() {
			return true;
		}

		public String bridge$getContextProperty(Context context) {
			return context == Context.getCurrent() ? "context" : "wrong";
		}

		public String bridge$getMutable() {
			return mutable;
		}

		public void bridge$setMutable(String value) {
			mutable = value;
		}

		public String bridge$contextValue(Context context, String value) {
			return value + ":" + (context == Context.getCurrent() ? "context" : "wrong");
		}

		@HideFromGY
		public String bridge$getHidden() {
			return "hidden";
		}
	}
}
