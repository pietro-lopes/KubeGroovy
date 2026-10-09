package dev.uncandango.kubegroovy.script.type;

import dev.uncandango.kubegroovy.script.KubeGroovyContext;
import dev.uncandango.kubegroovy.script.bridge.Context;
import dev.uncandango.kubegroovy.script.annotations.RemapForGY;
import dev.uncandango.kubegroovy.script.bridge.TypeWrapperFactory;
import org.apache.commons.lang3.NotImplementedException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class RecordTypeInfo extends ClassTypeInfo implements TypeWrapperFactory<Object> {
	private static final Map<Class<?>, Object> GLOBAL_DEFAULT_VALUES = new IdentityHashMap<>();
	private static final TypeInfo CONSUMER_TYPE_INFO = TypeInfo.RAW_CONSUMER.withParams(TypeInfo.RAW_MAP.withParams(TypeInfo.STRING, TypeInfo.NONE));

	public static <T> void setGlobalDefaultValue(Class<T> type, T value) {
		GLOBAL_DEFAULT_VALUES.put(type, value);
	}

	static {
		setGlobalDefaultValue(Optional.class, Optional.empty());
		setGlobalDefaultValue(List.class, List.of());
		setGlobalDefaultValue(Set.class, Set.of());
		setGlobalDefaultValue(Map.class, Map.of());
	}

	public record Component(int index, String name, TypeInfo type) {
	}

	public record Data(Component[] components, Map<String, Component> componentMap, Object[] defaultArguments) {
		private Object[] createMHArgs() {
			var args = new Object[components.length + 1];
			System.arraycopy(components, 0, args, 1, components.length);
			return args;
		}
	}

	static final Map<Class<?>, RecordTypeInfo> CACHE = new IdentityHashMap<>();

	private Data data;
	private GYObjectTypeInfo objectTypeInfo;
	private JSFixedArrayTypeInfo arrayTypeInfo;

	RecordTypeInfo(Class<?> type) {
		super(type);
	}

	public synchronized Data getData() {
		if (data == null) {
			var rc = asClass().getRecordComponents();
			var components = new Component[rc.length];
			var componentMap = new HashMap<String, Component>();
			var defaultArguments = new Object[rc.length];

			for (int i = 0; i < rc.length; i++) {
				var gt = rc[i].getGenericType();

				var rename = rc[i].getAccessor().getDeclaredAnnotation(RemapForGY.class);
				var c = new Component(i, rename != null ? rename.value() : rc[i].getName(), TypeInfo.of(gt));
				components[i] = c;
				componentMap.put(c.name, c);
				defaultArguments[i] = c.type.createDefaultValue();

				if (defaultArguments[i] == null) {
					defaultArguments[i] = GLOBAL_DEFAULT_VALUES.getOrDefault(rc[i].getType(), null);
				}
			}

			data = new Data(components, Map.copyOf(componentMap), defaultArguments);
		}

		return data;
	}

	public GYObjectTypeInfo getObjectTypeInfo() {
		if (objectTypeInfo == null) {
			var data = getData();
			var list = new ArrayList<GYOptionalParam>(data.components.length);

			for (var c : data.components) {
				list.add(new GYOptionalParam(c.name, c.type, true));
			}

			objectTypeInfo = new GYObjectTypeInfo(List.copyOf(list));
		}

		return objectTypeInfo;
	}

	public JSFixedArrayTypeInfo getArrayTypeInfo() {
		if (arrayTypeInfo == null) {
			var data = getData();
			var list = new ArrayList<GYOptionalParam>(data.components.length);

			for (var c : data.components) {
				list.add(new GYOptionalParam(c.name, c.type, true));
			}

			arrayTypeInfo = new JSFixedArrayTypeInfo(List.copyOf(list));
		}

		return arrayTypeInfo;
	}

	public TypeInfo createCombinedType(TypeInfo... preference) {
		var types = new ArrayList<TypeInfo>(2 + preference.length);
		types.addAll(Arrays.asList(preference));
		types.add(getObjectTypeInfo());
		types.add(getArrayTypeInfo());
		return new JSOrTypeInfo(types);
	}

	@Override
	public Map<String, Component> recordComponents() {
		return getData().componentMap;
	}

	private Object createInstance0(Context cx, Object original, Object[] args) {
		throw new NotImplementedException();
//		var constructor = cx.factory.getRecordConstructor(asClass());
//
//		if (constructor == null) {
//			throw Context.reportRuntimeError("Unable to find record '" + asClass().getName() + "' constructor", cx);
//		}
//
//		try {
//			return constructor.invokeWithArguments(args);
//		} catch (Throwable ex) {
//			throw Context.throwAsScriptRuntimeEx(ex, cx);
//		}
	}

	public Object createInstance(Map<?, ?> map) {
		var cx = Context.getCurrent();
		var data = getData();
		var defaultRecordProperties = ((KubeGroovyContext)cx).kgyFactory.getDefaultRecordProperties(asClass());
		var args0 = defaultRecordProperties == null ? data.defaultArguments : defaultRecordProperties;
		var args = args0.clone();

		for (var entry : map.entrySet()) {
			var c = data.componentMap.get(String.valueOf(entry.getKey()));

			if (c != null) {
				if (args[c.index] instanceof Optional) {
					args[c.index] = Optional.ofNullable(cx.gyToJava(entry.getValue(), c.type.param(0)));
				} else {
					args[c.index] = cx.gyToJava(entry.getValue(), c.type);
				}
			}
		}

		return createInstance0(cx, map, args);
	}

	public Object createInstance(Object... objects) {
		var cx = Context.getCurrent();
		var data = getData();
		var defaultRecordProperties = ((KubeGroovyContext)cx).kgyFactory.getDefaultRecordProperties(asClass());
		var args0 = defaultRecordProperties == null ? data.defaultArguments : defaultRecordProperties;
		var args = args0.clone();

		int alen = Math.min(args0.length, objects.length);

		for (int i = 0; i < alen; i++) {
			if (args[i] instanceof Optional) {
				args[i] = Optional.ofNullable(cx.gyToJava(objects[i], data.components[i].type.param(0)));
			} else {
				args[i] = cx.gyToJava(objects[i], data.components[i].type);
			}
		}

		return createInstance0(cx, args, args);
	}

	@Override
	@SuppressWarnings({"rawtypes", "unchecked"})
	public Object wrap(Object from, TypeInfo target) {
		var cx = Context.getCurrent();
		if (asClass().isInstance(from)) {
			return from;
		}
//		else if (from instanceof NativeJavaList) {
//			var arr = (Object[]) cx.arrayOf(from, TypeInfo.NONE);
//			return createInstance(cx, arr);
//		}
		else if (from instanceof Map<?, ?>) {
			var map = (Map) cx.mapOf(from, TypeInfo.STRING, TypeInfo.NONE);
			return createInstance(map);
		}
//		else if (from instanceof Callable) {
//			var map = new HashMap<String, Object>(2);
//			var consumer = (Consumer<Map<String, ?>>) cx.gyToJava(from, CONSUMER_TYPE_INFO);
//			consumer.accept(map);
//			return createInstance(cx, map);
//		}
		else {
			return cx.reportConversionError(from, target);
		}
	}
}
