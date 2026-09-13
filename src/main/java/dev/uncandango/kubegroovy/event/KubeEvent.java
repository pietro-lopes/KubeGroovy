package dev.uncandango.kubegroovy.event;

import dev.uncandango.kubegroovy.typings.Info;
import dev.uncandango.kubegroovy.script.bridge.Context;
import dev.uncandango.kubegroovy.script.bridge.TypeInfo;
import dev.uncandango.kubegroovy.script.annotations.HideFromGY;
import org.jspecify.annotations.Nullable;

public interface KubeEvent {
	@Nullable
	@HideFromGY
	default Object defaultExitValue(Context cx) {
		return null;
	}

	@Nullable
	@HideFromGY
	default Object mapExitValue(Context cx, @Nullable Object value) {
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
	default Object cancel(Context cx) throws EventExit {
		return cancel(cx, defaultExitValue(cx));
	}

	@Info("""
		Stops the event with default exit value. Execution will be stopped **immediately**.
		
		`success` denotes a `true` outcome.
		""")
	default Object success(Context cx) throws EventExit {
		return success(cx, defaultExitValue(cx));
	}

	@Info("""
		Stops the event with default exit value. Execution will be stopped **immediately**.
		
		`exit` denotes a `default` outcome.
		""")
	default Object exit(Context cx) throws EventExit {
		return exit(cx, defaultExitValue(cx));
	}

	@Info("""
		Cancels the event with the given exit value. Execution will be stopped **immediately**.
		
		`cancel` denotes a `false` outcome.
		""")
	default Object cancel(Context cx, @Nullable Object value) throws EventExit {
		throw EventResult.Type.INTERRUPT_FALSE.exit(cx, mapExitValue(cx, value));
	}

	@Info("""
		Stops the event with the given exit value. Execution will be stopped **immediately**.
		
		`success` denotes a `true` outcome.
		""")
	default Object success(Context cx, @Nullable Object value) throws EventExit {
		throw EventResult.Type.INTERRUPT_TRUE.exit(cx, mapExitValue(cx, value));
	}

	@Info("""
		Stops the event with the given exit value. Execution will be stopped **immediately**.
		
		`exit` denotes a `default` outcome.
		""")
	default Object exit(Context cx, @Nullable Object value) throws EventExit {
		throw EventResult.Type.INTERRUPT_DEFAULT.exit(cx, mapExitValue(cx, value));
	}

	@HideFromGY
	default void afterPosted(EventResult result) {
	}
}