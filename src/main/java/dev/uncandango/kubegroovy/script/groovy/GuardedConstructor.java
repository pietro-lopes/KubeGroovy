package dev.uncandango.kubegroovy.script.groovy;

import dev.uncandango.kubegroovy.script.ScriptManager;
import org.codehaus.groovy.reflection.CachedConstructor;
import org.codehaus.groovy.reflection.GeneratedMetaMethod;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public final class GuardedConstructor extends GeneratedMetaMethod {
	private static final MethodHandle INVOKE;
	static {
		try {
			INVOKE = MethodHandles.lookup().findVirtual(GuardedConstructor.class, "invoke", MethodType.methodType(Object.class, Object.class, Object[].class));
		} catch (ReflectiveOperationException e) { throw new ExceptionInInitializerError(e); }
	}
	private final CachedConstructor delegate;
	public GuardedConstructor(CachedConstructor delegate) {
		super(delegate.getName(), delegate.getCachedClass(), delegate.getCachedClass().getTheClass(), delegate.getNativeParameterTypes());
		this.delegate = delegate;
	}
	@Override
	public int getModifiers() { return delegate.getCachedConstructor().getModifiers(); }
	@Override
	public Object invoke(Object receiver, Object[] arguments) {
		boolean script = SandboxRuntime.inScript();
		if (script) {
			if (!ScriptManager.getClassFilter().isConstructorAllowed(delegate.getCachedConstructor())) throw SandboxRuntime.blocked("constructor " + getDeclaringClass().getName());
			SandboxRuntime.checkValue(arguments);
		}
		var value = delegate.doConstructorInvoke(arguments);
		return script ? SandboxRuntime.checkValue(value) : value;
	}
	@Override
	public MethodHandle getTargetMethodHandle() {
		var parameters = new Class<?>[getNativeParameterTypes().length + 1];
		parameters[0] = Object.class;
		System.arraycopy(getNativeParameterTypes(), 0, parameters, 1, getNativeParameterTypes().length);
		return INVOKE.bindTo(this).asCollector(Object[].class, getNativeParameterTypes().length).asType(MethodType.methodType(getReturnType(), parameters));
	}
}
