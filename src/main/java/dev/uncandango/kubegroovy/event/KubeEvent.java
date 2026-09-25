package dev.uncandango.kubegroovy.event;

import dev.uncandango.kubegroovy.typings.Info;
import dev.uncandango.kubegroovy.script.bridge.Context;
import dev.uncandango.kubegroovy.script.type.TypeInfo;
import dev.uncandango.kubegroovy.script.annotations.HideFromGY;
import org.jspecify.annotations.Nullable;

public interface KubeEvent {
	@Nullable
	@HideFromGY
	default Object defaultExitValue() {
		var cx = Context.getCurrent();
		return null;
	}

	@Nullable
	@HideFromGY
	default Object mapExitValue(@Nullable Object value) {
		var cx = Context.getCurrent();
		var t = getExitValueType();
		return t == null ? value : cx.gyToJava(value, t);
	}

	@Nullable
	@HideFromGY
	default TypeInfo getExitValueType() {
		return null;
	}

	@Info("""
		Cancels the event with default exit value. Execution will be stopped **immediately**.
		
		`cancel` denotes a `false` outcome.
		""")
	default Object cancel() throws EventExit {
		var cx = Context.getCurrent();
		return cancel(defaultExitValue());
	}

	@Info("""
		Stops the event with default exit value. Execution will be stopped **immediately**.
		
		`success` denotes a `true` outcome.
		""")
	default Object success() throws EventExit {
		var cx = Context.getCurrent();
		return success(defaultExitValue());
	}

	@Info("""
		Stops the event with default exit value. Execution will be stopped **immediately**.
		
		`exit` denotes a `default` outcome.
		""")
	default Object exit() throws EventExit {
		var cx = Context.getCurrent();
		return exit(defaultExitValue());
	}

	@Info("""
		Cancels the event with the given exit value. Execution will be stopped **immediately**.
		
		`cancel` denotes a `false` outcome.
		""")
	default Object cancel(@Nullable Object value) throws EventExit {
		var cx = Context.getCurrent();
		throw EventResult.Type.INTERRUPT_FALSE.exit(mapExitValue(value));
	}

	@Info("""
		Stops the event with the given exit value. Execution will be stopped **immediately**.
		
		`success` denotes a `true` outcome.
		""")
	default Object success(@Nullable Object value) throws EventExit {
		var cx = Context.getCurrent();
		throw EventResult.Type.INTERRUPT_TRUE.exit(mapExitValue(value));
	}

	@Info("""
		Stops the event with the given exit value. Execution will be stopped **immediately**.
		
		`exit` denotes a `default` outcome.
		""")
	default Object exit(@Nullable Object value) throws EventExit {
		var cx = Context.getCurrent();
		throw EventResult.Type.INTERRUPT_DEFAULT.exit(mapExitValue(value));
	}

	@HideFromGY
	default void afterPosted(EventResult result) {
	}
}