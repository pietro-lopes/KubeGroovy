package dev.uncandango.kubegroovy.plugin;

import dev.uncandango.kubegroovy.script.annotations.HideFromGY;
import dev.uncandango.kubegroovy.script.groovy.KubeGroovyClassLoader;
import groovy.lang.MetaMethod;
import org.codehaus.groovy.reflection.CachedMethod;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/// Global script access policy. Register through KubeGroovyPlugin.registerClasses,
/// then freeze before compilation. A new filter has no rules; KubeGroovyPlugins.createClassFilter
/// installs the sandbox baseline first. Plugins are trusted not to reopen it.
public final class ClassFilter {
	private final Set<String> deniedClasses = new HashSet<>();
	private final Set<String> allowedClasses = new HashSet<>();
	private final Set<String> deniedPackages = new HashSet<>();
	private final Set<String> allowedPackages = new HashSet<>();
	private final List<Predicate<Member>> deniedMembers = new ArrayList<>();
	private final List<Predicate<Member>> allowedMembers = new ArrayList<>();
	private final ConcurrentHashMap<String, Boolean> names = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Class<?>, Boolean> classes = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Member, Boolean> members = new ConcurrentHashMap<>();
	private final List<Predicate<Class<?>>> deniedTypes = new ArrayList<>();
	private final List<Predicate<String>> deniedMethodNames = new ArrayList<>();
	private final List<BiPredicate<Class<?>, String>> deniedMemberNames = new ArrayList<>();
	private volatile boolean frozen;

	public void freeze() { frozen = true; }
	public boolean isFrozen() { return frozen; }
	private void changing() {
		if (frozen) throw new IllegalStateException("Class filter registration is closed");
		names.clear(); classes.clear(); members.clear();
	}
	/// Deny runtime types matching the rule, including array components, receivers, return values,
	/// and parameters. Class allows do not override it.
	public void denyTypes(Predicate<Class<?>> rule) { changing(); deniedTypes.add(rule); }
	/// Deny method names on every receiver, including early dynamic dispatch and compile-time checks.
	public void denyMethodNames(Predicate<String> rule) { changing(); deniedMethodNames.add(rule); }
	/// Deny member names for receivers matching the rule; member allows do not override it.
	public void denyMemberNames(BiPredicate<Class<?>, String> rule) { changing(); deniedMemberNames.add(rule); }
	public boolean isTypeDenied(Class<?> type) {
		if (type.isArray()) return isTypeDenied(type.getComponentType());
		return deniedTypes.stream().anyMatch(rule -> rule.test(type));
	}
	public void deny(String name) { changing(); deniedClasses.add(name.trim()); }
	public void deny(Class<?> type) { deny(type.getName()); }
	public void allow(String name) { changing(); allowedClasses.add(name.trim()); }
	public void allow(Class<?> type) { allow(type.getName()); }
	public void denyPackage(String name) { changing(); deniedPackages.add(packageName(name)); }
	public void allowPackage(String name) { changing(); allowedPackages.add(packageName(name)); }
	public void denyMethodName(String name) { denyMember(m -> m.name.equals(name)); }
	public void allowMethodName(String name) { allowMember(m -> m.name.equals(name)); }
	public void denyMethods(Class<?> owner, String name) { denyMember(m -> m.ownerMatches(owner) && m.name.equals(name)); }
	public void allowMethods(Class<?> owner, String name) { allowMember(m -> m.ownerMatches(owner) && m.name.equals(name)); }
	public void denyMethod(Class<?> owner, String name, Class<?>... parameters) { denyMember(m -> m.ownerMatches(owner) && m.name.equals(name) && m.parameters.equals(List.of(parameters))); }
	public void allowMethod(Class<?> owner, String name, Class<?>... parameters) { allowMember(m -> m.ownerMatches(owner) && m.name.equals(name) && m.parameters.equals(List.of(parameters))); }
	public void denyReturnType(Class<?> type) { denyMember(m -> type.isAssignableFrom(m.result)); }
	public void allowReturnType(Class<?> type) { allowMember(m -> type.isAssignableFrom(m.result)); }
	public void denyParameterType(Class<?> type) { denyMember(m -> m.parameters.stream().anyMatch(type::isAssignableFrom)); }
	public void allowParameterType(Class<?> type) { allowMember(m -> m.parameters.stream().anyMatch(type::isAssignableFrom)); }
	public void denyConstructor(Class<?> owner, Class<?>... parameters) { denyMethod(owner, "<init>", parameters); }
	public void allowConstructor(Class<?> owner, Class<?>... parameters) { allowMethod(owner, "<init>", parameters); }
	public void denyField(Class<?> owner, String name) { denyMember(m -> m.field && m.ownerMatches(owner) && m.name.equals(name)); }
	public void allowField(Class<?> owner, String name) { allowMember(m -> m.field && m.ownerMatches(owner) && m.name.equals(name)); }
	private void denyMember(Predicate<Member> rule) { changing(); deniedMembers.add(rule); }
	private void allowMember(Predicate<Member> rule) { changing(); allowedMembers.add(rule); }
	private static String packageName(String name) {
		var result = name.trim();
		while (result.endsWith(".")) result = result.substring(0, result.length() - 1);
		if (result.isEmpty()) throw new IllegalArgumentException("Empty package rule");
		return result;
	}
	public boolean isAllowed(String name) {
		return names.computeIfAbsent(name, n -> !deniedClasses.contains(n)
			&& (allowedClasses.contains(n) || packageAllowed(n)));
	}
	private boolean packageAllowed(String name) {
		var deny = deniedPackages.stream().filter(p -> inPackage(name, p)).mapToInt(String::length).max().orElse(-1);
		var allow = allowedPackages.stream().filter(p -> inPackage(name, p)).mapToInt(String::length).max().orElse(-1);
		return deny < 0 || allow > deny;
	}
	public boolean isAllowed(Class<?> type) {
		if (type.isArray()) return isAllowed(type.getComponentType());
		return classes.computeIfAbsent(type, t -> !hidden(t) && !isTypeDenied(t)
			&& (Path.class.isAssignableFrom(t) || isAllowed(t.getName())
				|| t.isHidden() && t.isSynthetic() && allowedClasses.contains(t.getNestHost().getName()) && !deniedClasses.contains(t.getNestHost().getName()) && !isTypeDenied(t.getNestHost()) && !deniedClasses.contains(t.getName())));
	}
	public boolean isMethodAllowed(Class<?> receiver, Method method) {
		if (!isMethodVisibilityAllowed(method.getDeclaringClass(), method.getModifiers())) return false;
		return permitted(new Member(receiver, method.getDeclaringClass(), method.getName(), method.getReturnType(), List.of(method.getParameterTypes()), false), method);
	}
	public boolean isMethodAllowed(Class<?> receiver, MetaMethod method) {
		if (method instanceof CachedMethod
			&& !isMethodVisibilityAllowed(method.getDeclaringClass().getTheClass(), method.getModifiers())) return false;
		if (method.getName().equals("use") && !(method instanceof CachedMethod)
			&& method.getClass().getName().startsWith("org.codehaus.groovy.")) return false;
		return permitted(new Member(receiver, method.getDeclaringClass().getTheClass(), method.getName(), method.getReturnType(), List.of(method.getNativeParameterTypes()), false), method instanceof CachedMethod cm ? cm.getCachedMethod() : method.getDeclaringClass().getTheClass());
	}
	public boolean isConstructorAllowed(Constructor<?> constructor) {
		var type = constructor.getDeclaringClass();
		return permitted(new Member(type, type, "<init>", type, List.of(constructor.getParameterTypes()), false), constructor);
	}
	/// Groovy's accessibility override must not expose package-private static Java helpers.
	public static boolean isMethodVisibilityAllowed(Class<?> owner, int modifiers) {
		if (!Modifier.isStatic(modifiers) || Modifier.isPublic(modifiers)
			|| Modifier.isProtected(modifiers) || Modifier.isPrivate(modifiers)) return true;
		for (var loader = owner.getClassLoader(); loader != null; loader = loader.getParent()) {
			if (loader instanceof KubeGroovyClassLoader) return true;
		}
		return false;
	}
	public boolean isFieldAllowed(Class<?> receiver, Field field) {
		return permitted(new Member(receiver, field.getDeclaringClass(), field.getName(), field.getType(), List.of(), true), field);
	}
	private boolean permitted(Member member, AnnotatedElement element) {
		if (element.isAnnotationPresent(HideFromGY.class)) return false;
		return members.computeIfAbsent(member, m -> {
			if (!isAllowed(m.receiver) || !isAllowed(m.owner) || !isAllowed(m.result)) return false;
			if (!isMemberNameAllowed(m.receiver, m.name) || !isMemberNameAllowed(m.owner, m.name)) return false;
			if (m.parameters.stream().anyMatch(this::isTypeDenied)) return false;
			if (deniedMembers.stream().anyMatch(p -> p.test(m))) return false;
			if (allowedMembers.stream().anyMatch(p -> p.test(m))) return true;
			return true;
		});
	}
	/// Early dynamic-name check; overload rules are evaluated on the selected member.
	public boolean isMemberNameAllowed(Class<?> receiver, String name) {
		return isGlobalMethodNameAllowed(name) && deniedMemberNames.stream().noneMatch(rule -> rule.test(receiver, name));
	}
	public boolean isGlobalMethodNameAllowed(String name) { return deniedMethodNames.stream().noneMatch(rule -> rule.test(name)); }
	private static boolean inPackage(String name, String pkg) { return name.startsWith(pkg + "."); }
	private static boolean hidden(Class<?> type) {
		if (type.isAnnotationPresent(HideFromGY.class)) return true;
		if (Arrays.stream(type.getInterfaces()).anyMatch(ClassFilter::hidden)) return true;
		return type.getSuperclass() != null && hidden(type.getSuperclass());
	}
	private record Member(Class<?> receiver, Class<?> owner, String name, Class<?> result, List<Class<?>> parameters, boolean field) {
		boolean ownerMatches(Class<?> type) { return type.isAssignableFrom(receiver) || type.isAssignableFrom(owner); }
	}
}
