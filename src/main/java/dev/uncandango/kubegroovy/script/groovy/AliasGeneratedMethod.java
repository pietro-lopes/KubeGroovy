package dev.uncandango.kubegroovy.script.groovy;


import dev.uncandango.kubegroovy.KubeGroovy;
import dev.uncandango.kubegroovy.script.type.TypeConsolidator;
import dev.uncandango.kubegroovy.script.type.TypeInfo;
import dev.uncandango.kubegroovy.script.type.WithTypeInfo;
import groovy.lang.MissingMethodException;
import net.neoforged.neoforge.common.util.Lazy;
import org.codehaus.groovy.reflection.CachedClass;
import org.codehaus.groovy.reflection.GeneratedMetaMethod;
import org.codehaus.groovy.reflection.ReflectionUtils;
import org.codehaus.groovy.runtime.InvokerInvocationException;
import org.codehaus.groovy.runtime.MetaClassHelper;
import org.codehaus.groovy.vmplugin.VMPluginFactory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

// TODO: try to implement class generation like DMG does
public class AliasGeneratedMethod extends GeneratedMetaMethod implements WithTypeInfo {
	private boolean makeAccessibleDone;
	private final TypeInfo[] paramsTypeInfo;
	private final TypeInfo returnTypeInfo;
	private final Method cachedMethod;
	// TODO: NOT OPTIMAL, NEED TO BE AT STATIC FINAL, CONSIDERING CLASS GEN
	private final Lazy<MethodHandle> mhSupplier;

	public AliasGeneratedMethod(String alias, Method cachedMethod, CachedClass cachedClass) {
		super(alias, cachedClass, cachedMethod.getReturnType(), cachedMethod.getParameterTypes());
		this.cachedMethod = cachedMethod;
		this.paramsTypeInfo = WithTypeInfo.generateParamsTypeInfo(cachedMethod, cachedClass.getTheClass(), false);
		this.returnTypeInfo = WithTypeInfo.generateReturnTypeInfo(cachedMethod, cachedClass.getTheClass());
		this.mhSupplier = Lazy.of(() -> {
			try {
				var lookup = MethodHandles.privateLookupIn(cachedClass.getTheClass(), KubeGroovy.LOOKUP);
				if (Modifier.isStatic(cachedMethod.getModifiers())) {
					return MethodHandles.dropArguments(lookup.unreflect(cachedMethod), 0, Object.class);
				}
				if (!lookup.hasFullPrivilegeAccess()) {
					return lookup.unreflect(cachedMethod);
				}
				return lookup.unreflectSpecial(cachedMethod, cachedClass.getTheClass());
			}
			catch (IllegalAccessException e) {
				throw new RuntimeException(e);
			}
		});
	}

	@Override
	public int getModifiers() {
		return cachedMethod.getModifiers();
	}

	@Override
	public MethodHandle getTargetMethodHandle() {
		return this.mhSupplier.get();
	}

	@Override
	public Object invoke(Object object, Object[] arguments) {
		makeAccessibleIfNecessary();

		try {
			return normalizeBoxedReturn(cachedMethod.invoke(object, arguments));
		} catch (IllegalArgumentException | IllegalAccessException e) {
			throw new InvokerInvocationException(e);
		} catch (InvocationTargetException e) {
			Throwable cause = e.getCause();
			throw (cause instanceof RuntimeException && !(cause instanceof MissingMethodException)) ? (RuntimeException) cause : new InvokerInvocationException(e);
		}
	}

	private Object normalizeBoxedReturn(final Object value) {
		return MetaClassHelper.normalizeBoxedReturn(value, cachedMethod.getReturnType());
	}

	private void makeAccessibleIfNecessary() {
		if (!makeAccessibleDone) {
			ReflectionUtils.makeAccessibleInPrivilegedAction(cachedMethod);
			makeAccessibleDone = true;
		}
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
