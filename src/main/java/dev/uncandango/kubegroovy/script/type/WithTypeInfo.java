package dev.uncandango.kubegroovy.script.type;

import groovy.lang.MetaMethod;
import org.codehaus.groovy.reflection.CachedMethod;

import java.lang.reflect.Method;

public interface WithTypeInfo {
	TypeInfo[] getParamsTypeInfo();
	TypeInfo getReturnTypeInfo();

	static TypeInfo generateReturnTypeInfo(MetaMethod delegate, Class<?> declaringClass) {
		if (delegate instanceof CachedMethod cm) {
			var type = TypeInfo.safeOf(() -> cm.getCachedMethod().getGenericReturnType());
			return type.consolidate(TypeConsolidator.getMapping(declaringClass));
		}
		throw new IllegalArgumentException("Couldn't resolve type info");
	}

	static TypeInfo generateReturnTypeInfo(Method method, Class<?> declaringClass) {
		var type = TypeInfo.safeOf(method::getGenericReturnType);
		return type.consolidate(TypeConsolidator.getMapping(declaringClass));
	}

	static TypeInfo[] generateParamsTypeInfo(MetaMethod delegate, Class<?> declaringClass) {
		if (delegate instanceof CachedMethod cm) {
			var params = TypeInfo.safeOfArray(() -> cm.getCachedMethod().getGenericParameterTypes());
			var noContextArray = new TypeInfo[params.length - 1];
			System.arraycopy(params, 1, noContextArray, 0, noContextArray.length);
			return TypeConsolidator.consolidateAll(noContextArray, TypeConsolidator.getMapping(declaringClass));
		}
		throw new IllegalArgumentException("Couldn't resolve type info");
	}

	static TypeInfo[] generateParamsTypeInfo(Method method, Class<?> declaringClass, boolean removeContext) {
		var params = TypeInfo.safeOfArray(method::getGenericParameterTypes);
		if (removeContext) {
			var noContextArray = new TypeInfo[params.length - 1];
			System.arraycopy(params, 1, noContextArray, 0, noContextArray.length);
			return TypeConsolidator.consolidateAll(noContextArray, TypeConsolidator.getMapping(declaringClass));
		} else {
			return TypeConsolidator.consolidateAll(params, TypeConsolidator.getMapping(declaringClass));
		}

	}
}
