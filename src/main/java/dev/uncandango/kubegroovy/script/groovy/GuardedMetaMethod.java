package dev.uncandango.kubegroovy.script.groovy;

import dev.uncandango.kubegroovy.error.MissingContextException;
import dev.uncandango.kubegroovy.script.annotations.HideFromGY;
import dev.uncandango.kubegroovy.script.bridge.Context;
import dev.uncandango.kubegroovy.script.type.WithTypeInfo;
import groovy.lang.MetaMethod;
import org.codehaus.groovy.reflection.CachedMethod;
import org.codehaus.groovy.reflection.GeneratedMetaMethod;
import org.codehaus.groovy.reflection.ReflectionCache;
import org.codehaus.groovy.runtime.InvokerInvocationException;
import org.codehaus.groovy.runtime.metaclass.NewMetaMethod;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

/// Preserves the exposed signature while ensuring indy cannot bypass invocation guards.
public final class GuardedMetaMethod extends GeneratedMetaMethod {
	private static final MethodHandle INVOKE;
	static {
		try {
			INVOKE = MethodHandles.lookup().findVirtual(GuardedMetaMethod.class, "invoke", MethodType.methodType(Object.class, Object.class, Object[].class));
		} catch (ReflectiveOperationException e) { throw new ExceptionInInitializerError(e); }
	}
	private final MetaMethod delegate;
	private final MetaMethod policyMethod;
	public GuardedMetaMethod(MetaMethod delegate) {
		super(delegate.getName(), delegate.getDeclaringClass(), delegate.getReturnType(), delegate.getNativeParameterTypes());
		this.delegate = delegate;
		this.policyMethod = bridgeTarget(delegate);
	}
	private static MetaMethod bridgeTarget(MetaMethod method) {
		if (!(method instanceof CachedMethod) || !method.getName().matches("(super|this)\\$[0-9]+\\$.*")) return method;
		var pending = new ArrayDeque<Class<?>>();
		var seen = new HashSet<Class<?>>();
		pending.add(method.getDeclaringClass().getTheClass());
		while (!pending.isEmpty()) {
			var owner = pending.removeFirst();
			if (!seen.add(owner)) continue;
			for (var candidate : ReflectionCache.getCachedClass(owner).getMethods()) {
				if (candidate.getMopName().equals(method.getName()) && Arrays.equals(candidate.getNativeParameterTypes(), method.getNativeParameterTypes())) return candidate;
			}
			if (owner.getSuperclass() != null) pending.add(owner.getSuperclass());
			Collections.addAll(pending, owner.getInterfaces());
		}
		throw new IllegalArgumentException("Cannot resolve Groovy bridge target: " + method);
	}

	public static MetaMethod wrap(MetaMethod method) { return method instanceof GuardedMetaMethod ? method : new GuardedMetaMethod(method); }
	@HideFromGY
	public boolean isGroovyExtension() {
		return delegate instanceof TypeWrapperGeneratedMethod wrapped ? wrapped.isGroovyExtension()
			: delegate instanceof GeneratedMetaMethod || delegate instanceof NewMetaMethod;
	}
	@Override
	public int getModifiers() { return delegate.getModifiers(); }
	@Override
	public Object invoke(Object receiver, Object[] arguments) {
		boolean script = delegate.getDeclaringClass().getTheClass() != SandboxRuntime.class && SandboxRuntime.inScript();
		if (script && !SandboxRuntime.plumbing(delegate)) SandboxRuntime.before(policyMethod, receiver, arguments);
		if (script && delegate instanceof CachedMethod cached) {
			Context context;
			try { context = Context.getCurrent(); }
			catch (MissingContextException ignored) { context = null; }
			if (context != null) {
				var types = WithTypeInfo.generateParamsTypeInfo(cached.getCachedMethod(), cached.getDeclaringClass().getTheClass(), false);
				arguments = arguments.clone();
				for (int i = 0; i < types.length; i++) {
					if (context.factory.getTypeWrappers().hasWrappedContainer(types[i])) arguments[i] = context.gyToJava(arguments[i], types[i]);
				}
			}
		}
		Object result;
		try {
			result = delegate.invoke(receiver, arguments);
		} catch (InvokerInvocationException exception) {
			// Direct indy handles preserve event control-flow exceptions; reflective invocation must too.
			var cause = exception.getCause();
			if (cause != null) return GuardedMetaMethod.<RuntimeException, Object>rethrow(cause);
			throw exception;
		}
		return script && !delegate.getName().equals("getMetaClass") ? SandboxRuntime.checkValue(result) : result;
	}
	@SuppressWarnings("unchecked")
	private static <E extends Throwable, T> T rethrow(Throwable cause) throws E { throw (E) cause; }
	@Override
	public MethodHandle getTargetMethodHandle() {
		var handle = INVOKE.bindTo(this).asCollector(Object[].class, getNativeParameterTypes().length);
		var parameters = new Class<?>[getNativeParameterTypes().length + 1];
		parameters[0] = Object.class;
		System.arraycopy(getNativeParameterTypes(), 0, parameters, 1, getNativeParameterTypes().length);
		return handle.asType(MethodType.methodType(getReturnType(), parameters));
	}
}
