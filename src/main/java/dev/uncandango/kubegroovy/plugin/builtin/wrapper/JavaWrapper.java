package dev.uncandango.kubegroovy.plugin.builtin.wrapper;

import dev.uncandango.kubegroovy.script.ConsoleGY;
import dev.uncandango.kubegroovy.script.KubeGroovyContext;
import dev.uncandango.kubegroovy.typings.Info;
import dev.uncandango.kubegroovy.util.Cast;
import dev.uncandango.kubegroovy.script.bridge.BaseFunction;
import dev.uncandango.kubegroovy.script.bridge.Context;
import dev.uncandango.kubegroovy.script.bridge.RecordTypeInfo;
import dev.uncandango.kubegroovy.script.bridge.TypeInfo;
import dev.uncandango.kubegroovy.script.annotations.HideFromGY;
import org.jspecify.annotations.Nullable;
import org.slf4j.LoggerFactory;

import java.util.Map;

@Info("Methods for working with Java classes. Reflection my beloved ♥")
public interface JavaWrapper {
	@Info("""
		Loads the specified class, and throws error if class it not found or allowed.
		The returned object can have public static methods and fields accessed directly from it.
		Constructors can be used with the new keyword.
		""")
	static Object loadClass(KubeGroovyContext cx, String className) {
		return cx.loadJavaClass(className, true);
	}

	@Info("""
		Loads the specified class, and returns null if class is not found or allowed.
		The returned object can have public static methods and fields accessed directly from it.
		Constructors can be used with the new keyword.
		""")
	@Nullable
	static Object tryLoadClass(KubeGroovyContext cx, String className) {
		return cx.loadJavaClass(className, false);
	}

	@Info("Creates a custom ConsoleGY instance for you to use to, well, log stuff")
	static ConsoleGY createConsole(KubeGroovyContext cx, String name) {
		return new ConsoleGY(cx.getType(), LoggerFactory.getLogger(name));
	}

	static <T> T makeFunctionProxy(Context cx, TypeInfo targetClass, BaseFunction function) {
		return Cast.to(cx.createInterfaceAdapter(targetClass, function));
	}

	@Info("Cast the object to a target type, use if Rhino can't determine the parameter type due to type erasure.")
	static <T> T cast(Context cx, Class<T> targetClass, Object object) {
		return Cast.to(cx.gyToJava(object, TypeInfo.of(targetClass)));
	}

	@Nullable
	@HideFromGY
	static Class<?> tryLoadClass(String className) {
		try {
			return Class.forName(className);
		} catch (Exception ignored) {
			return null;
		}
	}

	static <R extends Record> R mergeRecord(Context cx, R original, Map<String, ?> merge) {
		var typeInfo = (RecordTypeInfo) TypeInfo.of(original.getClass());

		return original;
	}
}
