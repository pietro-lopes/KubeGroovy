package dev.uncandango.kubegroovy.script.groovy;


import dev.uncandango.kubegroovy.KubeGroovy;
import dev.uncandango.kubegroovy.error.MissingContextException;
import dev.uncandango.kubegroovy.script.KubeGroovyContext;
import dev.uncandango.kubegroovy.script.bridge.Context;
import dev.uncandango.kubegroovy.script.type.ArrayTypeInfo;
import dev.uncandango.kubegroovy.script.type.TypeInfo;
import dev.uncandango.kubegroovy.script.type.WithTypeInfo;
import groovy.lang.MetaMethod;
import net.neoforged.neoforge.common.util.Lazy;
import org.codehaus.groovy.reflection.CachedClass;
import org.codehaus.groovy.reflection.CachedMethod;
import org.codehaus.groovy.reflection.GeneratedMetaMethod;
import org.codehaus.groovy.runtime.InvokerInvocationException;
import org.codehaus.groovy.runtime.MetaClassHelper;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

// TODO: try to implement class generation like DMG does
public class TypeWrapperGeneratedMethod extends GeneratedMetaMethod implements MhPostProcessor {
	private final MetaMethod delegate;
	public static final MethodHandle GY_TO_JAVA;
	public final TypeInfo[] paramsTypeInfo;

	static {
		try {
			var lookup = MethodHandles.lookup();
			GY_TO_JAVA = lookup.findVirtual(Context.class, "gyToJava", MethodType.methodType(Object.class, Object.class, TypeInfo.class));
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	//private final Context cx;
	// TODO: NOT OPTIMAL, NEED TO BE AT STATIC FINAL, CONSIDERING CLASS GEN
	private final Lazy<MethodHandle> mhSupplier;

	public TypeWrapperGeneratedMethod(MetaMethod delegate) {
		super(delegate.getName(), delegate.getDeclaringClass(), delegate.getReturnType(), convertParamsType(delegate));
		Objects.requireNonNull(Context.getCurrent());
		this.delegate = delegate;
		this.paramsTypeInfo = delegate instanceof WithTypeInfo wti ? wti.getParamsTypeInfo() : WithTypeInfo.generateParamsTypeInfo(((CachedMethod)delegate).getCachedMethod(), delegate.getDeclaringClass().getTheClass(), false);
		this.mhSupplier = Lazy.of(() -> {
			MethodHandle mh = null;
			if (delegate instanceof CachedMethod cm) {
				mh = getMethodHandle(getDeclaringClass().getTheClass(), cm.getCachedMethod());
			}
			if (delegate instanceof GeneratedMetaMethod gmm) {
				mh = gmm.getTargetMethodHandle();
			}
			if (mh == null) {
				throw new RuntimeException("Failed to get method handle for " + delegate);
			}
			// fix the receiver: (Object, TypeInfo) -> Object
			MethodHandle bound = GY_TO_JAVA.bindTo(Context.getCurrent());

			MethodType mt = mh.type();
			MethodHandle[] filters = new MethodHandle[paramsTypeInfo.length];

			for (int i = 0; i < paramsTypeInfo.length; i++) {
				Class<?> declaredParamType = mt.parameterType(i + 1); // +1 skips receiver

				MethodHandle f = MethodHandles.insertArguments(bound, 1, paramsTypeInfo[i]);

				// Key change: if the real target parameter is an array type,
				// keep the *outer visible* filter input type as an array type too,
				// so Groovy's own varargs/collector linking (asCollector) still
				// recognizes this slot as array-like and can pre-collect loose
				// call-site arguments into an Object[] before your filter runs.
				Class<?> filterInputType = declaredParamType.isArray() ? Object[].class : Object.class;

				f = f.asType(MethodType.methodType(declaredParamType, filterInputType));

				filters[i] = f;
			}

//			for (int i = 0; i < paramsTypeInfo.length; i++) {
//				Class<?> declaredParamType = mt.parameterType(i + 1); // +1 skips receiver
//
//				// fix the TypeInfo for this specific parameter: (Object) -> Object
//				MethodHandle f = MethodHandles.insertArguments(bound, 1, paramsTypeInfo[i]);
//
//				// cast the Object result down to whatever the target method actually declares
//				f = f.asType(MethodType.methodType(declaredParamType, Object.class));
//
//				filters[i] = f;
//			}

			// splice the filters in starting at position 1 (after the receiver)
			return MethodHandles.filterArguments(mh, 1, filters);



















			//			var params = mh.type().parameterArray();
//			var offset = (delegate.isPublic() ? -1 : 0);
//			var count = mh.type().parameterCount() + offset;
//			var typeCount = paramsTypeInfo.length;
//			if (count != typeCount) { throw new IllegalArgumentException("Type arguments count mismatch: " + count + " != " + typeCount); }
//			KubeGroovy.LOGGER.info("mh.type()=" + mh.type() + " offset=" + offset + " count=" + count);
//			for (var p : params) KubeGroovy.LOGGER.info("  param: " + p);
//			for (int i = 0; i < count; i++) {
//				var paramFromMh = params[i - offset];
//				var paramFromType = paramsTypeInfo[i];
//				if (!paramFromType.shouldConvert()) continue;
//
//				var lastArg = i == count - 1;
//				var isArray = paramFromMh.isArray();
//
//				if (lastArg && isArray) {
//					mh = mh.asCollector(paramFromMh, 1);
//				}
//
//				// Object(!), Object, TypeInfo -> Object, TypeInfo
//				var transformer = GY_TO_JAVA.bindTo(Context.getCurrent());
//
//				// Object, TypeInfo(!) -> Object
//				transformer = MethodHandles.insertArguments(transformer, 1, paramFromType);
//
//
//				transformer = MethodHandles.explicitCastArguments(transformer, MethodType.methodType(paramFromMh, Object.class));
//
//				mh = MethodHandles.filterArguments(mh, i - offset, transformer);
//				//mh = mh.withVarargs(lastArg && isArray);
//				KubeGroovy.LOGGER.info("MethodHandle:" + mh);
//			}

			//return mh;
		});
	}

	public static Class<?>[] convertParamsType(MetaMethod metaMethod) {
		TypeInfo[] params = null;
		if (metaMethod instanceof WithTypeInfo wti) {
			params = wti.getParamsTypeInfo().clone();
		}
		if (metaMethod instanceof CachedMethod cm) {
			params = WithTypeInfo.generateParamsTypeInfo(cm.getCachedMethod(), metaMethod.getDeclaringClass().getTheClass(), false);
		}
		if (params == null) throw new IllegalArgumentException("Couldn't resolve return type info");
		for (int i = 0; i < params.length; i++) {
			var origType = params[i];
			var isArray = origType.asClass().isArray();
			var isLastArg = i == params.length - 1;
			if (isArray) {
				var componentType = origType.componentType();
				params[i] = isLastArg ? TypeInfo.OBJECT_ARRAY : TypeInfo.RAW_LIST.withParams(componentType);
				// TODO: remove me, was just testing stuff
				//params[i] = Context.getCurrent().factory.getTypeWrappers().hasAnyWrapper(componentType) ? TypeInfo.RAW_LIST.withParams(componentType) : origType;
				continue;
			}
			params[i] = Context.getCurrent().factory.getTypeWrappers().hasAnyWrapper(origType) ? TypeInfo.OBJECT : origType;
		}
		return Arrays.stream(params).map(TypeInfo::asClass).toArray(Class[]::new);
	}

	@Override
	public MethodHandle getTargetMethodHandle() {
		return this.mhSupplier.get();
	}

	private MethodHandle getMethodHandle(Class<?> clazz, Method method) {
		try {
			var lookup = MethodHandles.privateLookupIn(clazz, MethodHandles.lookup());
			if (!lookup.hasFullPrivilegeAccess()) {
				return lookup.unreflect(method);
			}
			return lookup.unreflectSpecial(method, clazz);
		}
		catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public Object invoke(Object object, Object[] arguments) {
		try {
			var cx = Context.getCurrent();
			Objects.requireNonNull(cx);
			for (int i = 0; i < paramsTypeInfo.length; i++) {
				arguments[i] = cx.gyToJava(arguments[i], paramsTypeInfo[i]);
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

	private static Class<?>[] removeContext(Class<?>[] arguments){
		if (arguments.length == 0) {
			return arguments;
		}
		var containsContext = Context.class.isAssignableFrom(arguments[0]);
		if (containsContext) {
			var newArray = new Class<?>[arguments.length - 1];
			System.arraycopy(arguments, 1, newArray, 0, newArray.length);
			return newArray;
		}
		return arguments;
	}

	private Object[] injectContext(Object[] arguments) {
		var newArray = new Object[arguments.length + 1];
		newArray[0] = Context.getCurrent();
		System.arraycopy(arguments, 0, newArray, 1, arguments.length);
		return newArray;
	}

	private Object normalizeBoxedReturn(final Object value) {
		return MetaClassHelper.normalizeBoxedReturn(value, delegate.getReturnType());
	}

	@Override
	public MethodHandle process(MethodHandle mh) {
		var params = mh.type().parameterArray();
		var offset = (delegate.isPublic() ? -1 : 0);
		var count = mh.type().parameterCount() + offset;
		var typeCount = paramsTypeInfo.length;
		if (count != typeCount) { throw new IllegalArgumentException("Type arguments count mismatch: " + count + " != " + typeCount); }
		KubeGroovy.LOGGER.info("mh.type()=" + mh.type() + " offset=" + offset + " count=" + count);
		for (var p : params) KubeGroovy.LOGGER.info("  param: " + p);
		for (int i = 0; i < count; i++) {
			var paramFromMh = params[i - offset];
			var paramFromType = paramsTypeInfo[i];
			if (!paramFromType.shouldConvert()) continue;

//				var lastArg = i == count - 1;
//				var isArray = paramFromMh.isArray();

			// Object(!), Object, TypeInfo -> Object, TypeInfo
			var transformer = GY_TO_JAVA.bindTo(Context.getCurrent());

			// Object, TypeInfo(!) -> Object
			transformer = MethodHandles.insertArguments(transformer, 1, paramFromType);

			transformer = MethodHandles.explicitCastArguments(transformer, MethodType.methodType(paramFromMh, Object.class));

			mh = MethodHandles.filterArguments(mh, i - offset, transformer);
			//mh = mh.withVarargs(lastArg && isArray);
			KubeGroovy.LOGGER.info("MethodHandle:" + mh);
		}
		return mh;
	}
}
