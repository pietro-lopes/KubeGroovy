package dev.uncandango.kubegroovy.core.mixin.groovy;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.uncandango.kubegroovy.registry.BuilderBase;
import dev.uncandango.kubegroovy.script.annotations.HideFromGY;
import dev.uncandango.kubegroovy.script.annotations.RemapForGY;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import dev.uncandango.kubegroovy.script.bridge.Context;
import dev.uncandango.kubegroovy.script.groovy.AliasGeneratedMethod;
import dev.uncandango.kubegroovy.script.groovy.ContextGeneratedMethod;
import dev.uncandango.kubegroovy.script.groovy.TypeWrapperGeneratedMethod;
import dev.uncandango.kubegroovy.script.type.TypeInfo;
import dev.uncandango.kubegroovy.script.type.WithTypeInfo;
import groovy.lang.MetaClassImpl;
import groovy.lang.MetaMethod;
import groovy.lang.MissingPropertyException;
import org.codehaus.groovy.reflection.CachedClass;
import org.codehaus.groovy.reflection.CachedMethod;
import org.codehaus.groovy.runtime.metaclass.MetaMethodIndex;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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

	@Unique
	private final Map<Method, AliasGeneratedMethod> kgy$aliasMethods = new HashMap<>();
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

	@Inject(method = "addMetaMethodToIndex", at = @At(value = "HEAD"))
	private void addAliasMethodToIndex(MetaMethod method, Map<String, MetaMethodIndex.Cache> cacheIndex, CallbackInfo ci) {
		if (method instanceof CachedMethod cm) {
			var shouldWrap = kgy$shouldWrap.computeIfAbsent(cm.getCachedMethod(), reflectedMethod -> kgy$shouldTypeWrap(reflectedMethod, theCachedClass.getTheClass()));
			var alias = kgy$aliasRemapping.computeIfAbsent(cm.getCachedMethod(), MetaClassImplMixin::kgy$remappedAliases) ;
			alias.forEach(entry -> {
				var aliasMethod = kgy$aliasMethods.computeIfAbsent(cm.getCachedMethod(), key -> new AliasGeneratedMethod(entry.name(), cm.getCachedMethod(), theCachedClass));
				if (kgy$hasContextPrefix(cm.getCachedMethod())) {
					var contextMethod = new ContextGeneratedMethod(aliasMethod, theCachedClass.getTheClass());
					if (shouldWrap) {
						var typeWrapperMethod = new TypeWrapperGeneratedMethod(contextMethod, theCachedClass.getTheClass());
						metaMethodIndex.addMetaMethod(typeWrapperMethod, cacheIndex);
					}
					metaMethodIndex.addMetaMethod(contextMethod, cacheIndex);
				} else if (shouldWrap) {
					var typeWrapperMethod = new TypeWrapperGeneratedMethod(aliasMethod, theCachedClass.getTheClass());
					metaMethodIndex.addMetaMethod(typeWrapperMethod, cacheIndex);
				}
				metaMethodIndex.addMetaMethod(aliasMethod, cacheIndex);
			});
			if (kgy$hasContextPrefix(cm.getCachedMethod())) {
				var contextMethod = new ContextGeneratedMethod(cm, theCachedClass.getTheClass());
				if (shouldWrap) {
					var typeWrapperMethod = new TypeWrapperGeneratedMethod(contextMethod, theCachedClass.getTheClass());
					metaMethodIndex.addMetaMethod(typeWrapperMethod, cacheIndex);
				}
				metaMethodIndex.addMetaMethod(contextMethod, cacheIndex);
			} else if (shouldWrap) {
				var typeWrapperMethod = new TypeWrapperGeneratedMethod(cm, theCachedClass.getTheClass());
				metaMethodIndex.addMetaMethod(typeWrapperMethod, cacheIndex);
			}
		}
	}

	@ModifyExpressionValue(method = "fillMethodIndex", at = @At(value = "INVOKE", target = "Lorg/apache/groovy/ast/tools/ClassNodeUtils;isValidAccessorName(Ljava/lang/String;)Z"))
	private boolean addAliasBeanMethodToIndex(boolean original, @Local(name = "m") CachedMethod m, @Local(name = "c") CachedClass c) {
		var alias = kgy$aliasRemapping.computeIfAbsent(m.getCachedMethod(), MetaClassImplMixin::kgy$remappedAliases);
		alias.forEach(entry -> {
			if (isValidAccessorName(entry.name())) {
				var shouldWrap = kgy$shouldWrap.computeIfAbsent(m.getCachedMethod(), method -> kgy$shouldTypeWrap(method, theCachedClass.getTheClass()));
				var aliasMethod = kgy$aliasMethods.computeIfAbsent(m.getCachedMethod(), key -> new AliasGeneratedMethod(entry.name(), m.getCachedMethod(), theCachedClass));
				if (kgy$hasContextPrefix(m.getCachedMethod())) {
					var contextMethod = new ContextGeneratedMethod(aliasMethod, theCachedClass.getTheClass());
					if (shouldWrap) {
						var typeWrapperMethod = new TypeWrapperGeneratedMethod(contextMethod, theCachedClass.getTheClass());
						metaMethodIndex.addMetaMethod(typeWrapperMethod, metaMethodIndex.indexMap.computeIfAbsent(c.getTheClass(), k -> new HashMap<>()));
					}
					metaMethodIndex.addMetaMethod(contextMethod, metaMethodIndex.indexMap.computeIfAbsent(c.getTheClass(), k -> new HashMap<>()));
				} else {
					if (shouldWrap) {
						var typeWrapperMethod = new TypeWrapperGeneratedMethod(aliasMethod, theCachedClass.getTheClass());
						metaMethodIndex.addMetaMethod(typeWrapperMethod, metaMethodIndex.indexMap.computeIfAbsent(c.getTheClass(), k -> new HashMap<>()));
					}
				}
				metaMethodIndex.addMetaMethod(aliasMethod, metaMethodIndex.indexMap.computeIfAbsent(c.getTheClass(), k -> new HashMap<>()));
			}
		});
		return original;
	}

	@Inject(method = "addToAllMethodsIfPublic", at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z"))
	private void addAliasToAllMethods(MetaMethod metaMethod, CallbackInfo ci){
		if (metaMethod instanceof CachedMethod cm) {
			var alias = kgy$aliasRemapping.computeIfAbsent(cm.getCachedMethod(), MetaClassImplMixin::kgy$remappedAliases);
			alias.forEach(entry -> {
				var aliasMethod = kgy$aliasMethods.computeIfAbsent(cm.getCachedMethod(), key -> new AliasGeneratedMethod(entry.name(), cm.getCachedMethod(), theCachedClass));
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

	@Inject(method = "setProperty(Ljava/lang/Class;Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Object;ZZ)V", at = @At("HEAD"))
	private void preventBuilderFieldAssignment(Class<?> sender, Object object, String property, Object newValue, boolean useSuper, boolean fromInsideClass, CallbackInfo ci) {
		if (object instanceof BuilderBase<?> && kgy$isBuilderStateField(object.getClass(), property)) {
			throw new MissingPropertyException(property, object.getClass());
		}
	}

	// ----------------- Helper methods ----------------

	@Unique
	private static boolean kgy$shouldTypeWrap(Method method, Class<?> concreteClass) {
		var context = Context.getCurrent();
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
	private static boolean kgy$hasContextPrefix(Method method) {
		return method.getParameterCount() > 0 && Context.class.isAssignableFrom(method.getParameterTypes()[0]);
	}

	@Unique
	private static boolean kgy$isBuilderStateField(Class<?> type, String property) {
		for (var current = type; current != null && BuilderBase.class.isAssignableFrom(current); current = current.getSuperclass()) {
			try {
				var field = current.getDeclaredField(property);
				return !java.lang.reflect.Modifier.isStatic(field.getModifiers());
			} catch (NoSuchFieldException ignored) {
				// Continue through the builder hierarchy.
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
