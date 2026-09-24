package dev.uncandango.kubegroovy.script.groovy;


import dev.uncandango.kubegroovy.error.MissingContextException;
import dev.uncandango.kubegroovy.script.bridge.Context;
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
import java.lang.reflect.Modifier;
import java.util.Arrays;

// TODO: try to implement class generation like DMG does
public class ContextGeneratedMethod extends GeneratedMetaMethod implements WithTypeInfo {
	private final MetaMethod delegate;
	private final TypeInfo[] paramsTypeInfo;
	private final TypeInfo returnTypeInfo;
	private static final MethodHandle CURRENT_CONTEXT;
	// TODO: NOT OPTIMAL, NEED TO BE AT STATIC FINAL, CONSIDERING CLASS GEN
	private final Lazy<MethodHandle> mhSupplier;

	static {
		try {
			CURRENT_CONTEXT = MethodHandles.lookup().findStatic(ContextGeneratedMethod.class, "requireContext", MethodType.methodType(Context.class));
		} catch (ReflectiveOperationException e) {
			throw new ExceptionInInitializerError(e);
		}
	}

	public ContextGeneratedMethod(MetaMethod delegate, Class<?> concreteClass) {
		super(delegate.getName(), delegate.getDeclaringClass(), delegate.getReturnType(), removeContext(Arrays.stream(delegate.getParameterTypes()).map(CachedClass::getTheClass).toArray(Class[]::new)));
		this.paramsTypeInfo = delegate instanceof WithTypeInfo wti ? removeContext(wti.getParamsTypeInfo()) : WithTypeInfo.generateParamsTypeInfo(delegate, concreteClass);
		this.returnTypeInfo = delegate instanceof WithTypeInfo wti ? wti.getReturnTypeInfo() : WithTypeInfo.generateReturnTypeInfo(delegate, concreteClass);
		this.delegate = delegate;
		this.mhSupplier = Lazy.of(() -> {
			MethodHandle mh = null;
			if (delegate instanceof CachedMethod cm) {
				mh = getMethodHandle(concreteClass, cm.getCachedMethod());
			}
			if (delegate instanceof GeneratedMetaMethod gmm) {
				mh = gmm.getTargetMethodHandle();
			}
			if (mh == null) {
				throw new RuntimeException("Failed to get method handle for " + delegate);
			}
			var params = mh.type().parameterArray();
			var count = mh.type().parameterCount();
			for (int i = 0; i < count; i++) {
				if (Context.class.isAssignableFrom(params[i])) {
					var contextSupplier = CURRENT_CONTEXT.asType(MethodType.methodType(params[i]));
					mh = MethodHandles.collectArguments(mh, i, contextSupplier);
					break;
				}
			}
			return mh;
		});
	}

	@Override
	public int getModifiers() {
		return delegate.getModifiers();
	}

	@Override
	public MethodHandle getTargetMethodHandle() {
		return this.mhSupplier.get();
	}

	private MethodHandle getMethodHandle(Class<?> clazz, Method method) {
		try {
			var lookup = MethodHandles.privateLookupIn(clazz, MethodHandles.lookup());
			if (Modifier.isStatic(method.getModifiers())) {
				return MethodHandles.dropArguments(lookup.unreflect(method), 0, Object.class);
			}
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
			return normalizeBoxedReturn(delegate.invoke(object, injectContext(arguments)));
		} catch (IllegalArgumentException e) {
			throw new InvokerInvocationException(e);
		}
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
		newArray[0] = requireContext();
		System.arraycopy(arguments, 0, newArray, 1, arguments.length);
		return newArray;
	}

	private static TypeInfo[] removeContext(TypeInfo[] arguments) {
		if (arguments.length == 0 || !Context.class.isAssignableFrom(arguments[0].asClass())) {
			return arguments;
		}
		return Arrays.copyOfRange(arguments, 1, arguments.length);
	}

	private static Context requireContext() {
		var context = Context.getCurrent();
		if (context == null) {
			throw new MissingContextException();
		}
		return context;
	}

	private Object normalizeBoxedReturn(final Object value) {
		return MetaClassHelper.normalizeBoxedReturn(value, delegate.getReturnType());
	}

	@Override
	public TypeInfo[] getParamsTypeInfo() {
		return paramsTypeInfo;
	}

	@Override
	public TypeInfo getReturnTypeInfo() {
		return returnTypeInfo;
	}
}
