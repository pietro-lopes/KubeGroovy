package dev.uncandango.kubegroovy.script.groovy;

import dev.uncandango.kubegroovy.error.KubeRuntimeException;
import dev.uncandango.kubegroovy.error.MissingContextException;
import dev.uncandango.kubegroovy.script.bridge.Context;
import dev.uncandango.kubegroovy.script.type.TypeInfo;
import dev.uncandango.kubegroovy.script.type.WithTypeInfo;
import groovy.lang.MetaMethod;
import net.neoforged.neoforge.common.util.Lazy;
import org.codehaus.groovy.reflection.CachedMethod;
import org.codehaus.groovy.reflection.GeneratedMetaMethod;
import org.codehaus.groovy.runtime.InvokerInvocationException;
import org.codehaus.groovy.runtime.MetaClassHelper;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

// TODO: try to implement class generation like DMG does
public class TypeWrapperGeneratedMethod extends GeneratedMetaMethod implements MhPostProcessor {
	public static final MethodHandle GY_TO_JAVA;

	static {
		try {
			GY_TO_JAVA = MethodHandles.lookup().findStatic(TypeWrapperGeneratedMethod.class, "gyToJava", MethodType.methodType(Object.class, Object.class, TypeInfo.class));
		} catch (ReflectiveOperationException e) {
			throw new ExceptionInInitializerError(e);
		}
	}

	private final MetaMethod delegate;
	private final Lazy<MethodHandle> mhSupplier;
	public final TypeInfo[] paramsTypeInfo;

	public TypeWrapperGeneratedMethod(MetaMethod delegate, Class<?> concreteClass) {
		super(delegate.getName(), delegate.getDeclaringClass(), delegate.getReturnType(), convertParamsType(delegate, concreteClass));
		this.delegate = delegate;
		this.paramsTypeInfo = resolveParamsTypeInfo(delegate, concreteClass);
		this.mhSupplier = Lazy.of(() -> {
			MethodHandle methodHandle = null;
			if (delegate instanceof CachedMethod cachedMethod) {
				methodHandle = getMethodHandle(concreteClass, cachedMethod.getCachedMethod());
			} else if (delegate instanceof GeneratedMetaMethod generatedMethod) {
				methodHandle = generatedMethod.getTargetMethodHandle();
			}
			if (methodHandle == null) {
				throw new RuntimeException("Failed to get method handle for " + delegate);
			}
			return applyFilters(methodHandle);
		});
	}

	public static Class<?>[] convertParamsType(MetaMethod metaMethod, Class<?> concreteClass) {
		var params = resolveParamsTypeInfo(metaMethod, concreteClass).clone();
		var context = Context.getCurrent();
		for (int i = 0; i < params.length; i++) {
			var originalType = params[i];
			var isArray = originalType.asClass().isArray();
			var isLastArgument = i == params.length - 1;
			if (isArray) {
				var componentType = originalType.componentType();
				params[i] = isLastArgument ? TypeInfo.OBJECT_ARRAY : TypeInfo.RAW_LIST.withParams(componentType);
				continue;
			}
			var shouldWrap = context != null && context.factory.getTypeWrappers().hasAnyWrapper(originalType);
			params[i] = shouldWrap ? TypeInfo.OBJECT : originalType;
		}
		return Arrays.stream(params).map(TypeInfo::asClass).toArray(Class[]::new);
	}

	private static TypeInfo[] resolveParamsTypeInfo(MetaMethod delegate, Class<?> concreteClass) {
		if (delegate instanceof WithTypeInfo withTypeInfo) {
			return withTypeInfo.getParamsTypeInfo();
		}
		if (delegate instanceof CachedMethod cachedMethod) {
			return WithTypeInfo.generateParamsTypeInfo(cachedMethod.getCachedMethod(), concreteClass, false);
		}
		throw new IllegalArgumentException("Couldn't resolve parameter type info");
	}

	@Override
	public MethodHandle getTargetMethodHandle() {
		return mhSupplier.get();
	}

	private MethodHandle getMethodHandle(Class<?> clazz, Method method) {
		try {
			MethodHandles.Lookup lookup;
			try {
				lookup = MethodHandles.privateLookupIn(clazz, MethodHandles.lookup());
			} catch (IllegalAccessException e) {
				lookup = MethodHandles.lookup();
			}
			if (Modifier.isStatic(method.getModifiers())) {
				return MethodHandles.dropArguments(lookup.unreflect(method), 0, Object.class);
			}
			if (!lookup.hasFullPrivilegeAccess()) {
				return lookup.unreflect(method);
			}
			return lookup.unreflectSpecial(method, clazz);
		} catch (IllegalAccessException e) {
			throw new KubeRuntimeException(e);
		}
	}

	@Override
	public Object invoke(Object object, Object[] arguments) {
		try {
			for (int i = 0; i < paramsTypeInfo.length; i++) {
				arguments[i] = gyToJava(arguments[i], paramsTypeInfo[i]);
			}
			return normalizeBoxedReturn(delegate.invoke(object, arguments));
		} catch (IllegalArgumentException e) {
			throw new InvokerInvocationException(e);
		}
	}

	@Override
	public int getModifiers() {
		return delegate.getModifiers();
	}

	private Object normalizeBoxedReturn(Object value) {
		return MetaClassHelper.normalizeBoxedReturn(value, delegate.getReturnType());
	}

	@Override
	public MethodHandle process(MethodHandle methodHandle) {
		return applyFilters(methodHandle);
	}

	private MethodHandle applyFilters(MethodHandle methodHandle) {
		var offset = 1;
		if (methodHandle.type().parameterCount() != paramsTypeInfo.length + offset) {
			throw new IllegalArgumentException("Type arguments count mismatch: " + methodHandle.type().parameterCount() + " != " + (paramsTypeInfo.length + offset));
		}

		var filters = new MethodHandle[paramsTypeInfo.length];
		for (int i = 0; i < paramsTypeInfo.length; i++) {
			var declaredParamType = methodHandle.type().parameterType(i + offset);
			var filter = MethodHandles.insertArguments(GY_TO_JAVA, 1, paramsTypeInfo[i]);
			var filterInputType = declaredParamType.isArray() ? Object[].class : Object.class;
			filters[i] = filter.asType(MethodType.methodType(declaredParamType, filterInputType));
		}
		return MethodHandles.filterArguments(methodHandle, offset, filters);
	}

	static Object gyToJava(Object value, TypeInfo type) {
		var context = Context.getCurrent();
		if (context == null) {
			context = Context.from(value);
		}
		if (context == null) {
			throw new MissingContextException();
		}
		return context.gyToJava(value, type);
	}
}
