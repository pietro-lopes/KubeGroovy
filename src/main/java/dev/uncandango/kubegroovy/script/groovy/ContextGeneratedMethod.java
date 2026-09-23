package dev.uncandango.kubegroovy.script.groovy;


import dev.uncandango.kubegroovy.KubeGroovy;
import dev.uncandango.kubegroovy.error.MissingContextException;
import dev.uncandango.kubegroovy.script.KubeGroovyContext;
import dev.uncandango.kubegroovy.script.bridge.Context;
import dev.uncandango.kubegroovy.script.type.TypeConsolidator;
import dev.uncandango.kubegroovy.script.type.TypeInfo;
import dev.uncandango.kubegroovy.script.type.WithTypeInfo;
import groovy.lang.MetaMethod;
import groovy.lang.MissingMethodException;
import net.neoforged.neoforge.common.util.Lazy;
import org.codehaus.groovy.reflection.CachedClass;
import org.codehaus.groovy.reflection.CachedMethod;
import org.codehaus.groovy.reflection.GeneratedMetaMethod;
import org.codehaus.groovy.reflection.ReflectionUtils;
import org.codehaus.groovy.runtime.InvokerInvocationException;
import org.codehaus.groovy.runtime.MetaClassHelper;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.function.Predicate;

// TODO: try to implement class generation like DMG does
public class ContextGeneratedMethod extends GeneratedMetaMethod implements WithTypeInfo {
	private final MetaMethod delegate;
	private final TypeInfo[] paramsTypeInfo;
	private final TypeInfo returnTypeInfo;
	private final Context cx;
	// TODO: NOT OPTIMAL, NEED TO BE AT STATIC FINAL, CONSIDERING CLASS GEN
	private final Lazy<MethodHandle> mhSupplier;

	public ContextGeneratedMethod(MetaMethod delegate) {
		super(delegate.getName(), delegate.getDeclaringClass(), delegate.getReturnType(), removeContext(Arrays.stream(delegate.getParameterTypes()).map(CachedClass::getTheClass).toArray(Class[]::new)));
		this.cx = Context.getCurrent();
		if (cx == null) throw new MissingContextException();
		this.paramsTypeInfo = delegate instanceof WithTypeInfo wti ? wti.getParamsTypeInfo() : WithTypeInfo.generateParamsTypeInfo(delegate, delegate.getDeclaringClass().getTheClass());
		this.returnTypeInfo = delegate instanceof WithTypeInfo wti ? wti.getReturnTypeInfo() : WithTypeInfo.generateReturnTypeInfo(delegate, delegate.getDeclaringClass().getTheClass());
		this.delegate = delegate;
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
			var params = mh.type().parameterArray();
			var count = mh.type().parameterCount();
			for (int i = 0; i < count; i++) {
				if (Context.class.isAssignableFrom(params[i])) {
					mh = MethodHandles.insertArguments(mh, i, (KubeGroovyContext) this.cx);
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
		newArray[0] = cx;
		System.arraycopy(arguments, 0, newArray, 1, arguments.length);
		return newArray;
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
