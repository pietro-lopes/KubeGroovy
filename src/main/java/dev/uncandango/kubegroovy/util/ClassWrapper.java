//package dev.uncandango.kubegroovy.util;
//
//import dev.uncandango.kubegroovy.script.bridge.Context;
//
//import dev.uncandango.kubegroovy.script.type.TypeInfo;
//
//public record ClassWrapper<T>(Class<T> wrappedClass){ //  implements CustomJavaToGyWrapper
////	@Override
////	public Scriptable convertJavaToGy(Context cx, Scriptable scope, TypeInfo staticType) {
////		return new NativeJavaClass(cx, scope, wrappedClass);
////	}
//
//	@Override
//	public String toString() {
//		return "ClassWrapper[" + wrappedClass.getName() + "]";
//	}
//}