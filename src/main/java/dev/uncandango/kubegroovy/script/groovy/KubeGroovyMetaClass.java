package dev.uncandango.kubegroovy.script.groovy;

import dev.uncandango.kubegroovy.KubeGroovy;
import dev.uncandango.kubegroovy.registry.BuilderBase;
import dev.uncandango.kubegroovy.script.KubeGroovyContext;
import dev.uncandango.kubegroovy.script.annotations.HideFromGY;
import dev.uncandango.kubegroovy.script.annotations.RemapForGY;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import dev.uncandango.kubegroovy.script.bridge.Context;
import dev.uncandango.kubegroovy.script.bridge.BaseFunction;
import dev.uncandango.kubegroovy.script.bridge.GroovyConversions;
import dev.uncandango.kubegroovy.script.type.TypeConsolidator;
import dev.uncandango.kubegroovy.script.type.TypeInfo;
import groovy.lang.Closure;
import groovy.lang.MetaBeanProperty;
import groovy.lang.MetaClassImpl;
import groovy.lang.MetaClassRegistry;
import groovy.lang.MetaMethod;
import groovy.lang.MissingMethodException;
import groovy.lang.MissingPropertyException;
import net.neoforged.fml.loading.FMLLoader;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.groovy.util.BeanUtils;
import org.codehaus.groovy.reflection.CachedMethod;
import org.codehaus.groovy.runtime.metaclass.MissingMethodExceptionNoStack;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Normal Groovy dispatch first, followed by a deliberately small compatibility
 * fallback for KubeGroovy's annotated addon interfaces and context-leading APIs.
 */
final class KubeGroovyMetaClass extends MetaClassImpl {
	private static final String CALL_METHOD = "call";
	private static final String DO_CALL_METHOD = "doCall";

	KubeGroovyMetaClass(MetaClassRegistry registry, Class<?> type) {
		super(registry, type);
	}

	record ContextMethodKey (String name, int paramCount, boolean isStatic){}
	private final Map<ContextMethodKey, MetaMethod> methodsIndex = new HashMap<>();

	@Override
	public synchronized void initialize() {
		if (!isInitialized()) {
			TypeConsolidator.getMapping(theClass);
			addRemappedBeanProperties();
//			contextMethods = new HashMap<>();
//			var publicMethodsWithContext = Arrays.stream(theClass.getMethods())
//				.filter(KubeGroovyMetaClass::isVisibleBridgeMethod)
//				.filter(KubeGroovyMetaClass::hasContextPrefix)
//				.toList();
//
//			for (Method method : publicMethodsWithContext) {
//				var alias = remappedAliases(method);
//	    		var key = new ContextMethodKey(!alias.isEmpty() ? alias.getFirst().name() : method.getName(), logicalParameterCount(method), Modifier.isStatic(method.getModifiers()) );
//				var metaMethod = metaMethod(method);
//				contextMethods.put(key, metaMethod);
//			}
		}
		super.initialize();
	}

	private void addRemappedBeanProperties() {
		var properties = new LinkedHashMap<String, BeanPropertyMethods>();
		for (Method method : methodsOf(theClass)) {
			if (!isVisibleBridgeMethod(method)) {
				continue;
			}
			var aliases = new ArrayList<RemappedAlias>();
			for (RemappedAlias alias : remappedAliases(method)) {
				aliases.add(alias);
				var accessor = remappedAccessor(method, alias);
				if (accessor != null) {
					properties.computeIfAbsent(accessor.property(), ignored -> new BeanPropertyMethods()).add(accessor);
				}
			}
			var module = method.getDeclaringClass().getModule();
			if (module != null) {
				if (FMLLoader.getCurrent().getLoadingModList().getModFileById(module.getName()) != null) {
					if (aliases.isEmpty()) {
						var key = new ContextMethodKey(method.getName(), logicalParameterCount(method), Modifier.isStatic(method.getModifiers()));
						var metaMethod = metaMethod(method);
						var replaced = methodsIndex.put(key, metaMethod);
						if (replaced != null) {
							//KubeGroovy.LOGGER.warn("Duplicate method: {} replaced {} with key {}", replaced, metaMethod, key);
						}
					} else {
						for (var alias : aliases) {
							var key = new ContextMethodKey(alias.name(), logicalParameterCount(method), Modifier.isStatic(method.getModifiers()));
							var metaMethod = metaMethod(method);
							var replaced = methodsIndex.put(key, metaMethod);
							if (replaced != null) {
								//KubeGroovy.LOGGER.warn("Duplicate method: {} replaced {} with key {}",replaced, metaMethod, key);
							}
						}
					}
				}
			}
		}
		for (var entry : properties.entrySet()) {
			entry.getValue().install(this, entry.getKey());
		}
	}

	private static boolean isVisibleBridgeMethod(Method method) {
		return Modifier.isPublic(method.getModifiers()) && !method.isSynthetic() && !method.isBridge() && !hidden(method);
	}

	private static BeanAccessor remappedAccessor(Method method, RemappedAlias alias) {
		if (isAccessorName(alias.name())) {
			return accessorForName(method, alias.name(), alias.name());
		}
		if (alias.explicit()) {
			// An explicit remap may name a property directly, e.g. a getValue()
			// method remapped to "value".
			var accessor = accessorForName(method, method.getName(), alias.name());
			if (accessor != null) {
				return accessor;
			}
			var prefix = prefixFor(method.getDeclaringClass());
			if (prefix != null && !prefix.value().isEmpty() && method.getName().startsWith(prefix.value())) {
				return accessorForName(method, method.getName().substring(prefix.value().length()), alias.name());
			}
		}
		return null;
	}

	private static boolean isAccessorName(String name) {
		return name.startsWith("get") && name.length() > 3
			|| name.startsWith("is") && name.length() > 2
			|| name.startsWith("set") && name.length() > 3;
	}

	private static BeanAccessor accessorForName(Method method, String accessorName, String propertyName) {
		if (accessorName.startsWith("get") && accessorName.length() > 3) {
			if (logicalParameterCount(method) == 0 && method.getReturnType() != void.class && method.getReturnType() != Void.class) {
				return new BeanAccessor(propertyName.equals(accessorName) ? BeanUtils.decapitalize(accessorName.substring(3)) : propertyName, method, true, false);
			}
		} else if (accessorName.startsWith("is") && accessorName.length() > 2) {
			if (logicalParameterCount(method) == 0 && method.getReturnType() == boolean.class) {
				return new BeanAccessor(propertyName.equals(accessorName) ? BeanUtils.decapitalize(accessorName.substring(2)) : propertyName, method, true, true);
			}
		} else if (accessorName.startsWith("set") && accessorName.length() > 3) {
			if (logicalParameterCount(method) == 1) {
				return new BeanAccessor(propertyName.equals(accessorName) ? BeanUtils.decapitalize(accessorName.substring(3)) : propertyName, method, false, false);
			}
		}
		return null;
	}

	private static int logicalParameterCount(Method method) {
		return hasContextPrefix(method) ? method.getParameterCount() - 1 : method.getParameterCount();
	}

	private static boolean hasContextPrefix(Method method) {
		return method.getParameterCount() > 0 && Context.class.isAssignableFrom(method.getParameterTypes()[0]);
	}

	private static Class<?>[] logicalParameterTypes(Method method) {
		var parameters = method.getParameterTypes();
		return hasContextPrefix(method) ? Arrays.copyOfRange(parameters, 1, parameters.length) : parameters;
	}

	private static MetaMethod metaMethod(Method method) {
		var cached = CachedMethod.find(method);
		if (cached == null) {
			cached = new CachedMethod(method);
		}
		return new ContextMetaMethod(cached, logicalParameterTypes(method));
	}

	private static List<RemappedAlias> remappedAliases(Method method) {
		var aliases = new ArrayList<RemappedAlias>(2);
		var remap = method.getAnnotation(RemapForGY.class);
		if (remap != null) {
			addRemappedAlias(aliases, remap.value(), true);
		}
		var prefix = prefixFor(method.getDeclaringClass());
		if (prefix != null && !prefix.value().isEmpty() && method.getName().startsWith(prefix.value())) {
			addRemappedAlias(aliases, method.getName().substring(prefix.value().length()), false);
		}
		return aliases;
	}

	private static void addRemappedAlias(List<RemappedAlias> aliases, String name, boolean explicit) {
		if (name.isEmpty()) {
			return;
		}
		for (var alias : aliases) {
			if (alias.name().equals(name)) {
				return;
			}
		}
		aliases.add(new RemappedAlias(name, explicit));
	}

	private record RemappedAlias(String name, boolean explicit) {
	}

	private record BeanAccessor(String property, Method method, boolean getter, boolean booleanGetter) {
	}

	private static final class BeanPropertyMethods {
		private Method getter;
		private boolean booleanGetter;
		private Method setter;

		private void add(BeanAccessor accessor) {
			if (accessor.getter()) {
				if (getter == null || accessor.booleanGetter() && !booleanGetter
					|| accessor.booleanGetter() == booleanGetter && moreSpecific(accessor.method(), getter)) {
					getter = accessor.method();
					booleanGetter = accessor.booleanGetter();
				}
			} else if (setter == null || moreSpecific(accessor.method(), setter)) {
				setter = accessor.method();
			}
		}

		private void install(KubeGroovyMetaClass metaClass, String property) {
			var getterMeta = getter == null ? null : metaMethod(getter);
			var setterMeta = setter == null ? null : metaMethod(setter);
			Class<?> type = getterMeta != null ? getterMeta.getReturnType() : logicalParameterTypes(setter)[0];
			metaClass.addMetaBeanProperty(new MetaBeanProperty(property, type, getterMeta, setterMeta));
		}

		private static boolean moreSpecific(Method candidate, Method current) {
			var candidateType = candidate.getDeclaringClass();
			var currentType = current.getDeclaringClass();
			return !candidateType.equals(currentType) && currentType.isAssignableFrom(candidateType);
		}
	}

	private static final class ContextMetaMethod extends MetaMethod {
		private final MetaMethod delegate;

		private ContextMetaMethod(MetaMethod delegate, Class<?>[] logicalParameterTypes) {
			super(logicalParameterTypes);
			this.delegate = delegate;
		}

		@Override
		public int getModifiers() {
			return delegate.getModifiers();
		}

		@Override
		public String getName() {
			return delegate.getName();
		}

		@Override
		public Class getReturnType() {
			return delegate.getReturnType();
		}

		@Override
		public org.codehaus.groovy.reflection.CachedClass getDeclaringClass() {
			return delegate.getDeclaringClass();
		}

		@Override
		public Object invoke(Object object, Object[] arguments) {
			arguments = arguments == null ? new Object[0] : arguments;
			var context = GroovyConversions.getActiveContext();
			if (context == null) {
				throw new MissingMethodExceptionNoStack(getName(), getDeclaringClass().getTheClass(), arguments);
			}
			Object[] actual;
			boolean hasContext = hasContextPrefix(((CachedMethod)this.delegate).getCachedMethod());
			if (hasContext) {
				actual = new Object[arguments.length + 1];
				actual[0] = context;
			} else {
				actual = new Object[arguments.length];
			}
//			var isVarArgs = this.isVargsMethod();
//			if (isVarArgs) {
//				KubeGroovy.LOGGER.info("VarArg breakpoint!");
//			}
			var methodParams = TypeInfo.ofArray(((CachedMethod)this.delegate).getCachedMethod().getGenericParameterTypes());
			if (methodParams.length - (hasContext ? 1 : 0) != arguments.length) {
				throw new IllegalArgumentException("Method " + getName() + " expected " + methodParams.length + " arguments, but got " + arguments.length);
			}
			for (var i = 0; i < arguments.length; i++) {
				arguments[i] = context.gyToJava(arguments[i], methodParams[i + (hasContext ? 1 : 0)]);
			}
			System.arraycopy(arguments, 0, actual, (hasContext ? 1 : 0), arguments.length);
			return delegate.invoke(object, actual);
		}
	}

	private static Context getContextFromArgs(Object receiver, Object[] args) {
		Context cx = Context.getCurrent();
		if (cx == null) {
			cx = Context.from(receiver);
			if (cx == null) {
				if (args != null) {
					for (var arg : args) {
						cx = Context.from(arg);
						if (cx != null) {
							break;
						}
					}
				}
			}
		}
		return cx;
	}

	@Override
	public Object invokeMethod(Class sender, Object object, String methodName, Object[] originalArguments, boolean isCallToSuper, boolean fromInsideClass) {
		Context cx = getContextFromArgs(object, originalArguments);
		return switch (methodName) {
			case DO_CALL_METHOD, CALL_METHOD -> ScopedValue.where(KubeGroovyContext.CURRENT, cx).call(() -> super.invokeMethod(sender, object, methodName, originalArguments, isCallToSuper, fromInsideClass));
			default -> {
				var indexedMethod = methodsIndex.get(new ContextMethodKey(methodName, originalArguments == null ? 0 : originalArguments.length, false));
				if (indexedMethod != null) {
					yield ScopedValue.where(KubeGroovyContext.CURRENT, cx).call(() -> indexedMethod.invoke(object, originalArguments));
				} else {
					yield ScopedValue.where(KubeGroovyContext.CURRENT, cx).call(() -> super.invokeMethod(sender, object, methodName, originalArguments, isCallToSuper, fromInsideClass));
				}
			}
		};
//		if (methodName.equals(DO_CALL_METHOD) || methodName.equals(CALL_METHOD)) {
//
//		}
//
//
//		var contextMethod = contextMethods.get(new ContextMethodKey(methodName, originalArguments == null ? 0 : originalArguments.length, false));
//		if (contextMethod != null) {
//			return ScopedValue.where(KubeGroovyContext.CURRENT, cx).call(() -> contextMethod.invoke(object, originalArguments));
//		}
//		return ScopedValue.where(KubeGroovyContext.CURRENT, cx).call(() -> super.invokeMethod(sender, object, methodName, originalArguments, isCallToSuper, fromInsideClass));
	}

//	@Override
//	public Object invokeMethod(Object object, String name, Object[] arguments) {
//		Context cx = getContextFromArgs(object, arguments);
//
//		var contextMethod = contextMethods.get(new ContextMethodKey(name, arguments == null ? 0 : arguments.length, false));
//		if (contextMethod != null) {
//			return ScopedValue.where(KubeGroovyContext.CURRENT, cx).call(() -> contextMethod.invoke(object, arguments));
//		}
//		return ScopedValue.where(KubeGroovyContext.CURRENT, cx).call(() -> super.invokeMethod(object, name, arguments));
//	}

	@Override
	public Object invokeStaticMethod(Object object, String name, Object[] arguments) {
		Context cx = getContextFromArgs(object, arguments);
		var indexedMethod = methodsIndex.get(new ContextMethodKey(name, arguments == null ? 0 : arguments.length, true));
		if (indexedMethod != null) {
			return ScopedValue.where(KubeGroovyContext.CURRENT, cx).call(() -> indexedMethod.invoke(object, arguments));
		}
		return ScopedValue.where(KubeGroovyContext.CURRENT, cx).call(() -> super.invokeStaticMethod(object, name, arguments));
	}

//	@Override
//	public Object invokeMethod(Class sender, Object object, String name, Object[] arguments, boolean isCallToSuper, boolean fromInsideClass) {
//		Object[] actualArguments = arguments == null ? new Object[0] : arguments;
//		Objects.requireNonNull(Context.getCurrent());
//		try {
//			var contextMethod = contextMethods.get(new ContextMethodKey(name, actualArguments.length, false));
//			if (contextMethod != null) {
//				return contextMethod.invoke(object, actualArguments);
//			}
//
//			return super.invokeMethod(sender, object, name, actualArguments, isCallToSuper, fromInsideClass);
//		} catch (RuntimeException missing) {
//			MissingMethodException dispatchFailure = missingMethodCause(missing);
//			if (dispatchFailure == null) {
//				throw missing;
//			}
//			Object result = invokeFallback(object, name, arguments == null ? new Object[0] : arguments);
//			if (result != Fallback.MISSING) {
//				return result;
//			}
//			throw dispatchFailure;
//		}
//	}



	private static MissingMethodException missingMethodCause(RuntimeException error) {
		Throwable current = error;
		while (current != null) {
			if (current instanceof MissingMethodException missing) {
				return missing;
			}
			if (current == current.getCause()) {
				break;
			}
			current = current.getCause();
		}
		return null;
	}

	@Override
	public Object getProperty(Class sender, Object object, String property, boolean useSuper, boolean fromInsideClass) {
		try {
			Object result = super.getProperty(sender, object, property, useSuper, fromInsideClass);
			KubeGroovyMetaClassCreationHandle.refreshIfNeeded(result);
			return result;
		} catch (MissingPropertyException missing) {
			Object result = invokeFallback(object, property, new Object[0]);
			if (result != Fallback.MISSING) {
				KubeGroovyMetaClassCreationHandle.refreshIfNeeded(result);
				return result;
			}
			throw missing;
		}
	}

	@Override
	public Object getProperty(Object object, String property) {
		return getProperty(object == null ? null : object.getClass(), object, property, false, false);
	}

	@Override
	public void setProperty(Class sender, Object object, String property, Object newValue, boolean useSuper, boolean fromInsideClass) {
		if (object instanceof BuilderBase<?> && isBuilderStateField(object.getClass(), property)) {
			throw new MissingPropertyException(property, object.getClass());
		}
		try {
			super.setProperty(sender, object, property, newValue, useSuper, fromInsideClass);
		} catch (MissingPropertyException missing) {
			Object result = invokeFallback(object, property, new Object[]{newValue});
			if (result != Fallback.MISSING) {
				return;
			}
			throw missing;
		}
	}

	@Override
	public void setProperty(Object object, String property, Object newValue) {
		setProperty(object == null ? null : object.getClass(), object, property, newValue, false, false);
	}

	private static boolean isBuilderStateField(Class<?> type, String property) {
		for (var current = type; current != null && BuilderBase.class.isAssignableFrom(current); current = current.getSuperclass()) {
			try {
				var field = current.getDeclaredField(property);
				return !Modifier.isStatic(field.getModifiers());
			} catch (NoSuchFieldException ignored) {
				// Continue through the builder hierarchy.
			}
		}
		return false;
	}

	private static Object invokeFallback(Object receiver, String requested, Object[] args) {
		if (receiver == null) {
			return Fallback.MISSING;
		}
		Class<?> type = receiver instanceof Class<?> c ? c : receiver.getClass();
		for (Method method : methodsOf(type)) {
			if (!Modifier.isPublic(method.getModifiers()) || method.isSynthetic() || method.isBridge()
				|| receiver instanceof Class<?> && !Modifier.isStatic(method.getModifiers()) || hidden(method)) {
				continue;
			}
			if (!matches(method, requested, args.length)) {
				continue;
			}
			Object[] actual = adaptArguments(method, args);
			if (actual == null) {
				continue;
			}
			try {
				return method.invoke(receiver instanceof Class<?> ? null : receiver, actual);
			} catch (InvocationTargetException ex) {
				Throwable cause = ex.getCause();
				if (cause instanceof RuntimeException runtime) {
					throw runtime;
				}
				if (cause instanceof Error error) {
					throw error;
				}
				throw new RuntimeException(cause);
			} catch (ReflectiveOperationException ignored) {
				// Continue to the next overload.
			}
		}

		if (args.length == 0 || args.length == 1) {
			String cap = requested.isEmpty() ? requested : Character.toUpperCase(requested.charAt(0)) + requested.substring(1);
			for (Method method : methodsOf(type)) {
				if (!Modifier.isPublic(method.getModifiers()) || method.isSynthetic() || method.isBridge()
					|| receiver instanceof Class<?> && !Modifier.isStatic(method.getModifiers()) || hidden(method)) {
					continue;
				}
				String getter = args.length == 0 ? "get" + cap : "set" + cap;
				if (!matches(method, getter, args.length) && !matches(method, getter, args.length, true)) {
					continue;
				}
				Object[] actual = adaptArguments(method, args);
				if (actual == null) {
					continue;
				}
				try {
					return method.invoke(receiver instanceof Class<?> ? null : receiver, actual);
				} catch (InvocationTargetException ex) {
					Throwable cause = ex.getCause();
					if (cause instanceof RuntimeException runtime) throw runtime;
					if (cause instanceof Error error) throw error;
					throw new RuntimeException(cause);
				} catch (ReflectiveOperationException ignored) {
				}
			}
		}

		if (args.length == 0) {
			for (Field field : type.getFields()) {
				if (hidden(field) || !Modifier.isPublic(field.getModifiers())) continue;
				var remap = field.getAnnotation(RemapForGY.class);
				if (remap != null && (remap.value().equals(requested) || remap.value().equalsIgnoreCase(requested))) {
					try {
						return field.get(receiver instanceof Class<?> ? null : receiver);
					} catch (ReflectiveOperationException ignored) {
					}
				}
			}
		}
		return Fallback.MISSING;
	}

	/**
	 * Invokes only context-leading methods before Groovy's six-argument call-site
	 * dispatch.  Groovy resolves these call sites directly and can otherwise
	 * attempt to load the sandboxed java.lang.Class type before methodMissing is
	 * reached.  Ordinary methods still take the normal Groovy path first.
	 */
	private static Object invokeContextFallback(Object receiver, String requested, Object[] args) {
		if (receiver == null) {
			return Fallback.MISSING;
		}
		Class<?> type = receiver instanceof Class<?> c ? c : receiver.getClass();
		for (Method method : methodsOf(type)) {
			if (!Modifier.isPublic(method.getModifiers()) || method.isSynthetic() || method.isBridge()
				|| receiver instanceof Class<?> && !Modifier.isStatic(method.getModifiers()) || hidden(method)
				|| !hasContext(method, args.length) || !matches(method, requested, args.length)) {
				continue;
			}
			if (hasNormalCandidate(type, requested, args.length)) {
				return Fallback.MISSING;
			}
			Object[] actual = adaptArguments(method, args);
			if (actual == null) {
				continue;
			}
			try {
				return method.invoke(receiver instanceof Class<?> ? null : receiver, actual);
			} catch (InvocationTargetException ex) {
				Throwable cause = ex.getCause();
				if (cause instanceof RuntimeException runtime) throw runtime;
				if (cause instanceof Error error) throw error;
				throw new RuntimeException(cause);
			} catch (ReflectiveOperationException ignored) {
				// Try another overload.
			}
		}
		return Fallback.MISSING;
	}

	/**
	 * Mixin interface injection can leave default methods visible through the
	 * interface but absent from the transformed concrete class's reflective
	 * method array. Include those declarations explicitly for bridge lookup.
	 */
	private static List<Method> methodsOf(Class<?> type) {
		var methods = new ArrayList<Method>();
		var seen = new java.util.HashSet<String>();
		for (Method method : type.getMethods()) {
			if (seen.add(method.toGenericString())) {
				methods.add(method);
			}
		}
		collectInterfaceMethods(type, methods, seen);
		return methods;
	}

	private static void collectInterfaceMethods(Class<?> type, List<Method> methods, java.util.Set<String> seen) {
		for (Class<?> iface : type.getInterfaces()) {
			for (Method method : iface.getDeclaredMethods()) {
				if (seen.add(method.toGenericString())) {
					methods.add(method);
				}
			}
			collectInterfaceMethods(iface, methods, seen);
		}
		if (type.getSuperclass() != null && type.getSuperclass() != Object.class) {
			collectInterfaceMethods(type.getSuperclass(), methods, seen);
		}
	}

	private static boolean hasNormalCandidate(Class<?> type, String requested, int argumentCount) {
		for (Method candidate : type.getMethods()) {
			if (Modifier.isPublic(candidate.getModifiers()) && !candidate.isSynthetic() && !candidate.isBridge()
				&& candidate.getName().equals(requested) && candidate.getParameterCount() == argumentCount
				&& !hasContext(candidate, argumentCount) && !hidden(candidate)) {
				return true;
			}
		}
		return false;
	}

	private static boolean matches(Method method, String requested, int argumentCount) {
		return matches(method, requested, argumentCount, false);
	}

	private static boolean matches(Method method, String requested, int argumentCount, boolean propertyAlias) {
		List<String> aliases = new ArrayList<>();
		aliases.add(method.getName());
		for (var alias : remappedAliases(method)) {
			aliases.add(alias.name());
		}
		for (String alias : aliases) {
			if (alias.equals(requested) || alias.equalsIgnoreCase(requested)) {
				return method.getParameterCount() == argumentCount || hasContext(method, argumentCount);
			}
		}
		return false;
	}

	private static RemapPrefixForGY prefixFor(Class<?> type) {
		RemapPrefixForGY prefix = type.getAnnotation(RemapPrefixForGY.class);
		if (prefix != null) {
			return prefix;
		}
		for (Class<?> iface : type.getInterfaces()) {
			prefix = prefixFor(iface);
			if (prefix != null) {
				return prefix;
			}
		}
		return type.getSuperclass() == null ? null : prefixFor(type.getSuperclass());
	}

	private static boolean hasContext(Method method, int argumentCount) {
		return method.getParameterCount() == argumentCount + 1 && Context.class.isAssignableFrom(method.getParameterTypes()[0]);
	}

	private static Object[] withContext(Method method, Object[] args) {
		if (hasContext(method, args.length)) {
			Context cx = GroovyConversions.getActiveContext();
			if (cx == null) {
				return null;
			}
			Object[] actual = new Object[args.length + 1];
			actual[0] = cx;
			System.arraycopy(args, 0, actual, 1, args.length);
			return actual;
		}
		return method.getParameterCount() == args.length ? args : null;
	}

	private static Object[] adaptArguments(Method method, Object[] args) {
		Object[] actual = withContext(method, args);
		if (actual == null) {
			return null;
		}
		Class<?>[] parameters = method.getParameterTypes();
		for (int i = 0; i < parameters.length; i++) {
			Object value = actual[i];
			if (value == null) {
				if (parameters[i].isPrimitive()) return null;
				continue;
			}
			if (parameters[i].isInstance(value)) {
				continue;
			}
			if (parameters[i].isPrimitive()) {
				Object converted = convertPrimitive(value, parameters[i]);
				if (converted == null) {
					return null;
				}
				actual[i] = converted;
				continue;
			}
			if (parameters[i].isEnum() && value instanceof CharSequence text) {
				try {
					@SuppressWarnings({"unchecked", "rawtypes"})
					Object converted = Enum.valueOf((Class<? extends Enum>) parameters[i], text.toString().toUpperCase(Locale.ROOT));
					actual[i] = converted;
					continue;
				} catch (IllegalArgumentException ignored) {
					return null;
				}
			}
			if (parameters[i].isInterface() && (value instanceof Closure<?> || value instanceof BaseFunction)) {
				try {
					actual[i] = GroovyConversions.toFunctionalInterface(value, parameters[i]);
				} catch (RuntimeException ignored) {
					return null;
				}
				continue;
			}
			return null;
		}
		return actual;
	}

	private static Object convertPrimitive(Object value, Class<?> type) {
		if (type == boolean.class) return value instanceof Boolean ? value : null;
		if (type == char.class) return value instanceof Character ? value : null;
		if (!(value instanceof Number number)) return null;
		if (type == byte.class) return number.byteValue();
		if (type == short.class) return number.shortValue();
		if (type == int.class) return number.intValue();
		if (type == long.class) return number.longValue();
		if (type == float.class) return number.floatValue();
		if (type == double.class) return number.doubleValue();
		return null;
	}

	private static boolean hidden(java.lang.reflect.AnnotatedElement member) {
		if (member.isAnnotationPresent(HideFromGY.class)) {
			return true;
		}
		if (member instanceof Method method) {
			return hasHideAnnotation(method.getDeclaringClass());
		}
		return false;
	}

	private static boolean hasHideAnnotation(Class<?> type) {
		if (type.isAnnotationPresent(HideFromGY.class)) {
			return true;
		}
		for (Class<?> iface : type.getInterfaces()) {
			if (hasHideAnnotation(iface)) {
				return true;
			}
		}
		return type.getSuperclass() != null && type.getSuperclass() != Object.class && hasHideAnnotation(type.getSuperclass());
	}

	private enum Fallback { MISSING }
}
