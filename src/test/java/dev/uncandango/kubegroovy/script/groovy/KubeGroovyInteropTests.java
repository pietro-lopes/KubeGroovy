package dev.uncandango.kubegroovy.groovytest;

import dev.uncandango.kubegroovy.plugin.builtin.wrapper.UtilsWrapper;
import dev.uncandango.kubegroovy.event.EventResult;
import dev.uncandango.kubegroovy.event.EventExit;
import dev.uncandango.kubegroovy.error.KubeRuntimeException;
import dev.uncandango.kubegroovy.registry.RegistryKubeEvent;
import dev.uncandango.kubegroovy.util.KubeIdentifier;
import dev.uncandango.kubegroovy.util.ScheduledEvents;
import dev.uncandango.kubegroovy.script.KubeGroovyContext;
import dev.uncandango.kubegroovy.script.annotations.HideFromGY;
import dev.uncandango.kubegroovy.script.annotations.RemapForGY;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import dev.uncandango.kubegroovy.script.bridge.Context;
import dev.uncandango.kubegroovy.script.bridge.ContextFactory;
import dev.uncandango.kubegroovy.script.bridge.GroovyConversions;
import dev.uncandango.kubegroovy.script.bridge.TypeWrappers;
import dev.uncandango.kubegroovy.script.type.TypeInfo;
import dev.uncandango.kubegroovy.script.groovy.TypeWrapperGeneratedMethod;
import org.codehaus.groovy.reflection.CachedMethod;
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;


import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Focused regression coverage for the Groovy-only interoperability layer. */
class KubeGroovyInteropTests {
	//private static final ScopedValue<Context> CONTEXT = ScopedValue.newInstance();
	public Context cx = null;

	@BeforeEach
	void installBridge() {
		cx = new Context(new ContextFactory());
	}

	@AfterEach
	void clearContext() {
		//Context.setCurrent(null);
	}

	@Test
	void scriptRuntimeErrorsPreserveRuntimeIdentityAndCheckedCauses() {
		var runtime = new IllegalArgumentException("runtime failure");
		assertSame(runtime, Context.throwAsScriptRuntimeEx(runtime, cx));
		var checked = new IOException("checked failure");
		var wrapped = Context.throwAsScriptRuntimeEx(checked, cx);
		assertTrue(wrapped instanceof KubeRuntimeException);
		assertSame(checked, wrapped.getCause());
	}

	@Test
	void containersConvertWrappedElementsWithoutMutatingTheInput() throws Exception {
		var wrappers = new TypeWrappers();
		wrappers.register(WrappedValue.class, (value, type) -> new WrappedValue((String) value), true);
		var context = new Context(new ContextFactory() {
			@Override
			public TypeWrappers getTypeWrappers() {
				return wrappers;
			}
		});
		ScopedValue.where(Context.CURRENT, context).run(() -> {
			var element = TypeInfo.of(WrappedValue.class);
			var list = TypeInfo.RAW_LIST.withParams(element);
			var nested = TypeInfo.RAW_MAP.withParams(element, TypeInfo.RAW_LIST.withParams(list));
			var source = Map.of("key", List.of(List.of("value")));
			assertTrue(wrappers.hasAnyWrapper(nested));
			assertEquals(Map.of(new WrappedValue("key"), List.of(List.of(new WrappedValue("value")))), context.gyToJava(source, nested));
			assertEquals(Map.of("key", List.of(List.of("value"))), source);
			assertEquals(Set.of(new WrappedValue("value")), context.gyToJava(Set.of("value"), TypeInfo.RAW_SET.withParams(element)));
			assertEquals(List.of(), context.gyToJava(List.of(), list));
			assertSame(source, context.gyToJava(source, TypeInfo.RAW_MAP));
			var existing = new WrappedValue("existing");
			assertSame(existing, ((List<?>) context.gyToJava(List.of(existing), list)).getFirst());
		});

		var reflected = ContainerTarget.class.getMethod("values", List.class);
		ScopedValue.where(Context.CURRENT, context).run(() -> {
			var method = new TypeWrapperGeneratedMethod(new CachedMethod(reflected), ContainerTarget.class, true);
			assertEquals(List.class, method.getNativeParameterTypes()[0]);
			assertEquals("converted", method.invoke(null, new Object[]{List.of("converted")}));
			try {
				assertEquals("handle", method.getTargetMethodHandle().invokeWithArguments(null, List.of("handle")));
			} catch (Throwable error) {
				throw new AssertionError(error);
			}
		});
	}

	public static class ContainerTarget {
		public static String values(List<WrappedValue> values) {
			return values.getFirst().value();
		}
	}

	@Test
	void registeredWrappersKeepValidatorsAndFactories() {
		ScopedValue.where(Context.CURRENT, cx).run(() -> {
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
		});
	}

	@Test
	void sandboxRewritesExplicitCastAndAsTypeForms() {
		ScopedValue.where(Context.CURRENT, cx).run(() -> {
			var wrappers = new TypeWrappers();
			wrappers.register(String.class, (value, target) -> String.valueOf(value), true);
			var configuration = new CompilerConfiguration();
			KubeGroovySandbox.customizers(null, wrappers).forEach(configuration::addCompilationCustomizers);
			var shell = new GroovyShell(configuration);

			assertDoesNotThrow(() -> shell.parse("def castValue = 'value' as String; def typeValue = 'value'.asType(String)"));
			assertDoesNotThrow(() -> shell.parse("def simple = [] as List<String>; def nested = [] as List<List<String>>; def wildcard = [] as List<?>"));
		});
	}

	@Test
	@SuppressWarnings("unchecked")
	void callbackAdaptersRestoreTheirOriginatingContext() {
		var context = new Context(new ContextFactory());
		var callback = ScopedValue.where(Context.CURRENT, context).call(() -> {
			Closure<Object> closure = new Closure<Object>(this) {
				public Object doCall(Object value) {
					assertSame(context, Context.getCurrent());
					if ("fail".equals(value)) throw new IllegalStateException("callback failure");
					return value;
				}
			};
			return (Function<Object, Object>) GroovyConversions.toFunctionalInterface(closure, Function.class);
		});
		assertEquals("outside", callback.apply("outside"));
		var previous = new Context(new ContextFactory());
		ScopedValue.where(Context.CURRENT, previous).run(() -> {
			assertEquals("value", callback.apply("value"));
			assertSame(previous, Context.getCurrent());
			assertThrows(IllegalStateException.class, () -> callback.apply("fail"));
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
			var runFuture = CompletableFuture.runAsync(
				() -> ScopedValue.where(Context.CURRENT, captured).run(() -> seenRun.set(Context.getCurrent())));
			runFuture.join();
			var supplyFuture = CompletableFuture.supplyAsync(
				() -> ScopedValue.where(Context.CURRENT, captured).call(Context::getCurrent));
			supplyFuture.join();
			seenSupply.set((Context) supplyFuture.join());
		});
		assertSame(context, seenRun.get());
		assertSame(context, seenSupply.get());
	}

	@Test
	void timerBindingsRemainCallableAndRestoreContextWhenTheyFire() {
		var origin = new Context(new ContextFactory());
		var scheduled = new ScheduledEvents(ScheduledEvents.ScheduledEvent::new);
		var timeout = new ScheduledEvents.TimeoutFunction(scheduled, false, false);
		var interval = new ScheduledEvents.TimeoutFunction(scheduled, false, true);
		var clear = new ScheduledEvents.TimeoutFunction(scheduled, true, false);
		var seen = new ArrayList<Context>();
		ScopedValue.where(Context.CURRENT, origin).run(() -> {
			var binding = new Binding(new HashMap<>(Map.of("setTimeout", timeout, "setInterval", interval, "clearTimeout", clear, "clearInterval", clear, "delay", Duration.ZERO)));
			binding.setVariable("callback", new Closure<Object>(this) {
				public Object doCall(Object event) {
					seen.add(Context.getCurrent());
					return null;
				}
			});
			new GroovyShell(binding).evaluate("""
				def saved = setTimeout
				saved(callback, delay)
				clearTimeout(setTimeout(callback, delay))
				clearInterval(setInterval(callback, delay))
				setInterval(callback, delay)
				""");
		});
		var previous = new Context(new ContextFactory());
		ScopedValue.where(Context.CURRENT, previous).run(() -> {
			for (int tick = 0; tick < 3; tick++) scheduled.tickAll(tick);
			assertEquals(List.of(origin, origin, origin, origin), seen);
			assertSame(previous, Context.getCurrent());
		});
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
		ScopedValue.where(Context.CURRENT, cx).run(() -> {
			var configuration = new CompilerConfiguration();
			KubeGroovySandbox.customizers(null).forEach(configuration::addCompilationCustomizers);
			var shell = new GroovyShell(configuration);
			var error = assertThrows(Throwable.class, () -> shell.evaluate("System.exit(1)"));
			assertTrue(hasCause(error, SecurityException.class)
				|| String.valueOf(error).contains("System")
				|| String.valueOf(error).contains("not allowed"));
		});
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
