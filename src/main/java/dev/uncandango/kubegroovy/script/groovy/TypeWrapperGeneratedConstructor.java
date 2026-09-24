package dev.uncandango.kubegroovy.script.groovy;

import dev.uncandango.kubegroovy.error.MissingContextException;
import dev.uncandango.kubegroovy.script.bridge.Context;
import dev.uncandango.kubegroovy.script.type.TypeConsolidator;
import dev.uncandango.kubegroovy.script.type.TypeInfo;
import dev.uncandango.kubegroovy.script.type.WithTypeInfo;
import groovy.lang.MetaMethod;
import net.neoforged.neoforge.common.util.Lazy;
import org.codehaus.groovy.reflection.CachedConstructor;
import org.codehaus.groovy.reflection.CachedMethod;
import org.codehaus.groovy.reflection.GeneratedMetaMethod;
import org.codehaus.groovy.runtime.InvokerInvocationException;
import org.codehaus.groovy.runtime.MetaClassHelper;
import org.jspecify.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

public final class TypeWrapperGeneratedConstructor extends GeneratedMetaMethod {
	private final CachedConstructor delegate;
	private final TypeInfo[] paramsTypeInfo;
	private final Lazy<MethodHandle> mhSupplier;

	public TypeWrapperGeneratedConstructor(CachedConstructor delegate) {
		super(delegate.getName(), delegate.getCachedClass(), delegate.getCachedClass().getTheClass(), convertParamsType(delegate, delegate.getCachedClass().getTheClass()));
		this.delegate = delegate;
		this.paramsTypeInfo = resolveParamsTypeInfo(delegate, delegate.getCachedClass().getTheClass());
		this.mhSupplier = Lazy.of(() -> {
			MethodHandle methodHandle = null;
			if (delegate instanceof CachedConstructor cachedMethod) {
				methodHandle = getMethodHandle(this.getDeclaringClass().getTheClass(), cachedMethod.getCachedConstructor());
			}
			if (methodHandle == null) {
				throw new RuntimeException("Failed to get method handle for " + delegate);
			}
			return applyFilters(methodHandle);
		});
	}

	private MethodHandle applyFilters(MethodHandle methodHandle) {
		if (methodHandle.type().parameterCount() != paramsTypeInfo.length + 1) {
			throw new IllegalArgumentException("Type arguments count mismatch: " + methodHandle.type().parameterCount() + " != " + (paramsTypeInfo.length));
		}

		var filters = new MethodHandle[paramsTypeInfo.length];
		for (int i = 0; i < paramsTypeInfo.length; i++) {
			var declaredParamType = methodHandle.type().parameterType(i + 1);
			var filter = MethodHandles.insertArguments(TypeWrapperGeneratedMethod.GY_TO_JAVA, 1, paramsTypeInfo[i]);
			var filterInputType = declaredParamType.isArray() ? Object[].class : Object.class;
			filters[i] = filter.asType(MethodType.methodType(declaredParamType, filterInputType));
		}
		return MethodHandles.filterArguments(methodHandle, 1, filters);
	}

	@Override
	public MethodHandle getTargetMethodHandle() {
		return mhSupplier.get();
	}

	private MethodHandle getMethodHandle(Class<?> clazz, Constructor<?> method) {
		try {
			var lookup = MethodHandles.privateLookupIn(clazz, MethodHandles.lookup());
			if (method instanceof Constructor<?>) {
				return MethodHandles.dropArguments(lookup.unreflectConstructor(method), 0, Class.class);
			}
			if (!lookup.hasFullPrivilegeAccess()) {
				return lookup.unreflectConstructor(method);
			}
			return lookup.unreflectConstructor(method);
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}

	public static Class<?>[] convertParamsType(CachedConstructor metaMethod, Class<?> concreteClass) {
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

	private static TypeInfo[] resolveParamsTypeInfo(CachedConstructor delegate, Class<?> concreteClass) {
		if (delegate instanceof WithTypeInfo withTypeInfo) {
			return withTypeInfo.getParamsTypeInfo();
		}
		if (delegate instanceof CachedConstructor cachedConstructor) {
			return WithTypeInfo.generateParamsTypeInfo(cachedConstructor.getCachedConstructor(), concreteClass, false);
		}
		throw new IllegalArgumentException("Couldn't resolve parameter type info");
	}
//
//	@Nullable
//	public static TypeWrapperGeneratedConstructor create(CachedConstructor delegate, Context context) {
//		var constructor = delegate.getCachedConstructor();
//		var paramsTypeInfo = TypeConsolidator.consolidateAll(
//			TypeInfo.safeOfArray(constructor::getGenericParameterTypes),
//			TypeConsolidator.getMapping(constructor.getDeclaringClass())
//		);
//		var exposedParameterTypes = Arrays.stream(paramsTypeInfo).map(TypeInfo::asClass).toArray(Class<?>[]::new);
//		var typeWrappers = context.factory.getTypeWrappers();
//		var shouldWrap = false;
//
//		for (int i = 0; i < paramsTypeInfo.length; i++) {
//			var parameter = paramsTypeInfo[i];
//			if (parameter.asClass().isArray()) {
//				var isLastArgument = i == paramsTypeInfo.length - 1;
//				exposedParameterTypes[i] = isLastArgument ? Object[].class : java.util.List.class;
//				shouldWrap = true;
//			} else if (typeWrappers.hasAnyWrapper(parameter)) {
//				exposedParameterTypes[i] = Object.class;
//				shouldWrap = true;
//			}
//		}
//
//		return shouldWrap ? new TypeWrapperGeneratedConstructor(delegate, paramsTypeInfo, exposedParameterTypes) : null;
//	}

	@Override
	public int getModifiers() {
		return delegate.getModifiers();
	}

	@Override
	public Object invoke(Object obj, Object[] arguments) {
		try {
			for (int i = 0; i < paramsTypeInfo.length; i++) {
				arguments[i] = gyToJava(arguments[i], paramsTypeInfo[i]);
			}
			return normalizeBoxedReturn(delegate.doConstructorInvoke(arguments));
		} catch (IllegalArgumentException e) {
			throw new InvokerInvocationException(e);
		}
	}

	private Object normalizeBoxedReturn(Object value) {
		return MetaClassHelper.normalizeBoxedReturn(value, delegate.getCachedClass().getTheClass());
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
