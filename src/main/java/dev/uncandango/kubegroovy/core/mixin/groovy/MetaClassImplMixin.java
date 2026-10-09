package dev.uncandango.kubegroovy.core.mixin.groovy;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.uncandango.kubegroovy.error.MissingContextException;
import dev.uncandango.kubegroovy.registry.BuilderBase;
import dev.uncandango.kubegroovy.script.annotations.HideFromGY;
import dev.uncandango.kubegroovy.script.annotations.RemapForGY;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import dev.uncandango.kubegroovy.script.bridge.Context;
import dev.uncandango.kubegroovy.script.groovy.AliasGeneratedMethod;
import dev.uncandango.kubegroovy.script.groovy.TypeWrapperGeneratedConstructor;
import dev.uncandango.kubegroovy.script.groovy.TypeWrapperGeneratedMethod;
import dev.uncandango.kubegroovy.script.type.TypeInfo;
import dev.uncandango.kubegroovy.script.type.WithTypeInfo;
import groovy.lang.MetaClassImpl;
import groovy.lang.MetaClassRegistry;
import groovy.lang.MetaMethod;
import groovy.lang.MissingPropertyException;
import org.codehaus.groovy.reflection.CachedClass;
import org.codehaus.groovy.reflection.CachedConstructor;
import org.codehaus.groovy.reflection.CachedMethod;
import org.codehaus.groovy.runtime.metaclass.MetaMethodIndex;
import org.codehaus.groovy.util.FastArray;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.apache.groovy.ast.tools.ClassNodeUtils.isValidAccessorName;

@Mixin(MetaClassImpl.class)
public abstract class MetaClassImplMixin {
	@Shadow
	@Final
	protected MetaMethodIndex metaMethodIndex;

	@Shadow
	@Final
	protected CachedClass theCachedClass;

	@Shadow
	protected abstract void addToAllMethodsIfPublic(MetaMethod metaMethod);

	@Shadow
	private FastArray constructors;
	@Unique
	private final Map<Method, Map<String, AliasGeneratedMethod>> kgy$aliasMethods = new HashMap<>();
	@Unique
	private final Map<Method, List<RemappedAlias>> kgy$aliasRemapping = new HashMap<>();
	@Unique
	private final Map<Method, Boolean> kgy$shouldWrap = new HashMap<>();

	@ModifyExpressionValue(method = "fillMethodIndex", at = @At(value = "INVOKE", target = "Lorg/codehaus/groovy/reflection/CachedClass;getMethods()[Lorg/codehaus/groovy/reflection/CachedMethod;"))
	private CachedMethod[] filterHiddenMethods0(CachedMethod[] methods) {
		return Stream.of(methods)
			.filter(method -> !kgy$hidden(method.getCachedMethod()))
			.toArray(CachedMethod[]::new);
	}

	@ModifyExpressionValue(method = "populateMethods", at = @At(value = "INVOKE", target = "Lorg/codehaus/groovy/reflection/CachedClass;getMethods()[Lorg/codehaus/groovy/reflection/CachedMethod;"))
	private CachedMethod[] filterHiddenMethods1(CachedMethod[] methods) {
		return Stream.of(methods)
			.filter(method -> !kgy$hidden(method.getCachedMethod()))
			.toArray(CachedMethod[]::new);
	}

	@Inject(method = "addMetaMethodToIndex", at = @At("HEAD"), cancellable = true)
	private void addAliasMethodToIndex(MetaMethod method, Map<String, MetaMethodIndex.Cache> cacheIndex, CallbackInfo ci) {
		if (method instanceof CachedMethod cm) {
			for (var entry : kgy$aliasRemapping.computeIfAbsent(cm.getCachedMethod(), MetaClassImplMixin::kgy$remappedAliases)) {
				kgy$addMethod(kgy$aliasMethod(cm, entry.name()), cm.getCachedMethod(), cacheIndex);
			}
			if (kgy$shouldWrap.computeIfAbsent(cm.getCachedMethod(), reflected -> kgy$shouldTypeWrap(reflected, theCachedClass.getTheClass()))) {
				metaMethodIndex.addMetaMethod(new TypeWrapperGeneratedMethod(cm, theCachedClass.getTheClass()), cacheIndex);
				if (kgy$hasWrappedContainer(cm.getCachedMethod())) {
					// Groovy prefers the erased signature, so it must convert elements too.
					metaMethodIndex.addMetaMethod(new TypeWrapperGeneratedMethod(cm, theCachedClass.getTheClass(), true), cacheIndex);
					ci.cancel();
				}
			}
		}
	}

	@ModifyExpressionValue(method = "fillMethodIndex", at = @At(value = "INVOKE", target = "Lorg/apache/groovy/ast/tools/ClassNodeUtils;isValidAccessorName(Ljava/lang/String;)Z"))
	private boolean addAliasBeanMethodToIndex(boolean original, @Local(name = "m") CachedMethod m, @Local(name = "c") CachedClass c) {
		for (var entry : kgy$aliasRemapping.computeIfAbsent(m.getCachedMethod(), MetaClassImplMixin::kgy$remappedAliases)) {
			if (isValidAccessorName(entry.name())) {
				kgy$addMethod(kgy$aliasMethod(m, entry.name()), m.getCachedMethod(), metaMethodIndex.indexMap.computeIfAbsent(c.getTheClass(), k -> new HashMap<>()));
			}
		}
		return original;
	}

	@Inject(method = "addToAllMethodsIfPublic", at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z"))
	private void addAliasToAllMethods(MetaMethod metaMethod, CallbackInfo ci){
		if (metaMethod instanceof CachedMethod cm) {
			var alias = kgy$aliasRemapping.computeIfAbsent(cm.getCachedMethod(), MetaClassImplMixin::kgy$remappedAliases);
			alias.forEach(entry -> {
				var aliasMethod = kgy$aliasMethod(cm, entry.name());
				addToAllMethodsIfPublic(aliasMethod);
			});

		}
	}

	@Inject(method = "reinitialize", at = @At(value = "INVOKE", target = "Lgroovy/lang/MetaClassImpl;setInitialized(Z)V"))
	private void clearUpMethods(CallbackInfo ci) {
		kgy$aliasMethods.clear();
		kgy$aliasRemapping.clear();
		kgy$shouldWrap.clear();
	}

	@Inject(method = "<init>(Lgroovy/lang/MetaClassRegistry;Ljava/lang/Class;)V", at = @At("TAIL"))
	private void kgy$injectConstructor(MetaClassRegistry registry, Class<?> theClass, CallbackInfo ci){
		var candidates = new FastArray();
		for (var candidate : this.constructors.toList()) {
			var constructor = (CachedConstructor) candidate;
			if (kgy$shouldTypeWrap(constructor.getCachedConstructor(), theClass)) {
				candidates.add(new TypeWrapperGeneratedConstructor(constructor));
			}
			if (kgy$hasWrappedContainer(WithTypeInfo.generateParamsTypeInfo(constructor.getCachedConstructor(), theClass, false))) {
				candidates.add(new TypeWrapperGeneratedConstructor(constructor, true));
			} else {
				candidates.add(constructor);
			}
		}
		this.constructors = candidates;
	}

	// ----------------- Helper methods ----------------

	@Unique
	private AliasGeneratedMethod kgy$aliasMethod(CachedMethod method, String name) {
		return kgy$aliasMethods.computeIfAbsent(method.getCachedMethod(), key -> new HashMap<>())
			.computeIfAbsent(name, key -> new AliasGeneratedMethod(name, method.getCachedMethod(), theCachedClass));
	}

	@Unique
	private void kgy$addMethod(MetaMethod method, Method reflected, Map<String, MetaMethodIndex.Cache> cacheIndex) {
		if (kgy$shouldWrap.computeIfAbsent(reflected, key -> kgy$shouldTypeWrap(key, theCachedClass.getTheClass()))) {
			metaMethodIndex.addMetaMethod(new TypeWrapperGeneratedMethod(method, theCachedClass.getTheClass()), cacheIndex);
		}
		metaMethodIndex.addMetaMethod(kgy$hasWrappedContainer(reflected) ? new TypeWrapperGeneratedMethod(method, theCachedClass.getTheClass(), true) : method, cacheIndex);
	}

	@Unique
	private boolean kgy$hasWrappedContainer(Method method) {
		return kgy$hasWrappedContainer(WithTypeInfo.generateParamsTypeInfo(method, theCachedClass.getTheClass(), false));
	}

	@Unique
	private static boolean kgy$hasWrappedContainer(TypeInfo[] params) {
		try {
			var context = Context.getCurrent();
			if (context == null) {
				return false;
			}
			for (var param : params) {
				if (context.factory.getTypeWrappers().hasWrappedContainer(param)) {
					return true;
				}
			}
		} catch (MissingContextException ignored) {
			return false;
		}
		return false;
	}

	@Unique
	private static boolean kgy$shouldTypeWrap(Constructor<?> method, Class<?> concreteClass) {
		Context context;
		try {
			context = Context.getCurrent();
		} catch (MissingContextException e) {
			return false;
		}
		var typeWrappers = context == null ? null : context.factory.getTypeWrappers();
		for (var param : WithTypeInfo.generateParamsTypeInfo(method, concreteClass, false)) {
			if (param.asClass().isArray()) {
				return true;
			}
			if (typeWrappers != null && typeWrappers.hasAnyWrapper(param)) {
				return true;
			}
		}
		return false;
	}

	@Unique
	private static boolean kgy$shouldTypeWrap(Method method, Class<?> concreteClass) {
		Context context;
		try {
			context = Context.getCurrent();
		} catch (MissingContextException e) {
			return false;
		}
		var typeWrappers = context == null ? null : context.factory.getTypeWrappers();
		for (var param : WithTypeInfo.generateParamsTypeInfo(method, concreteClass, false)) {
			if (param.asClass().isArray()) {
				return true;
			}
			if (typeWrappers != null && typeWrappers.hasAnyWrapper(param)) {
				return true;
			}
		}
		return false;
	}

	@Unique
	private static boolean kgy$hidden(java.lang.reflect.AnnotatedElement member) {
		if (member.isAnnotationPresent(HideFromGY.class)) {
			return true;
		}
		if (member instanceof Method method) {
			return kgy$hasHideAnnotation(method.getDeclaringClass());
		}
		return false;
	}

	@Unique
	private static boolean kgy$hasHideAnnotation(Class<?> type) {
		if (type.isAnnotationPresent(HideFromGY.class)) {
			return true;
		}
		for (Class<?> iface : type.getInterfaces()) {
			if (kgy$hasHideAnnotation(iface)) {
				return true;
			}
		}
		return type.getSuperclass() != null && type.getSuperclass() != Object.class && kgy$hasHideAnnotation(type.getSuperclass());
	}

	@Unique
	private static List<RemappedAlias> kgy$remappedAliases(Method method) {
		var aliases = new ArrayList<RemappedAlias>(2);
		var remap = method.getAnnotation(RemapForGY.class);
		if (remap != null) {
			kgy$addRemappedAlias(aliases, remap.value(), true);
		}
		var prefix = kgy$prefixFor(method.getDeclaringClass());
		if (prefix != null && !prefix.value().isEmpty() && method.getName().startsWith(prefix.value())) {
			kgy$addRemappedAlias(aliases, method.getName().substring(prefix.value().length()), false);
		}
		return aliases;
	}

	@Unique
	private static void kgy$addRemappedAlias(List<RemappedAlias> aliases, String name, boolean explicit) {
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

	@Unique
	private static RemapPrefixForGY kgy$prefixFor(Class<?> type) {
		RemapPrefixForGY prefix = type.getAnnotation(RemapPrefixForGY.class);
		if (prefix != null) {
			return prefix;
		}
		for (Class<?> iface : type.getInterfaces()) {
			prefix = kgy$prefixFor(iface);
			if (prefix != null) {
				return prefix;
			}
		}
		return type.getSuperclass() == null ? null : kgy$prefixFor(type.getSuperclass());
	}

	private record RemappedAlias(String name, boolean explicit) {
	}
}
