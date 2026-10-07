package dev.uncandango.kubegroovy.groovytest;

import dev.uncandango.kubegroovy.error.KubeRuntimeException;
import dev.uncandango.kubegroovy.recipe.filter.ConstantFilter;
import dev.uncandango.kubegroovy.recipe.filter.AndFilter;
import dev.uncandango.kubegroovy.recipe.filter.OrFilter;
import dev.uncandango.kubegroovy.recipe.filter.NotFilter;
import dev.uncandango.kubegroovy.recipe.filter.RecipeFilter;
import dev.uncandango.kubegroovy.script.bridge.Context;
import dev.uncandango.kubegroovy.script.bridge.ContextFactory;
import groovy.lang.Closure;
import groovy.lang.GroovyShell;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RecipeFilterClosureTests {
	@Test
	void wrapperRejectsJavascriptObjectSyntaxAndBooleanClosuresWithoutCallingThem() {
		ScopedValue.where(Context.CURRENT, new Context(new ContextFactory())).run(() -> {
			var shell = new GroovyShell();
			var mistaken = (Closure<?>) shell.evaluate("def filter = {someGarbage: 'moreGarbage'}; filter");
			assertEquals("moreGarbage", mistaken.call(new Object()));
			var error = assertThrows(KubeRuntimeException.class, () -> RecipeFilter.wrap(mistaken));
			assertTrue(error.getMessage().contains("square brackets"));
			assertTrue(error.getMessage().contains("event.remove([id: 'minecraft:diamond_block'])"));
			var mustNotRun = (Closure<?>) shell.evaluate("def filter = { throw new AssertionError('closure was executed') }; filter");
			assertThrows(KubeRuntimeException.class, () -> RecipeFilter.wrap(mustNotRun));
			assertThrows(KubeRuntimeException.class, () -> RecipeFilter.wrap(shell.evaluate("def filter = { true }; filter")));
			assertThrows(KubeRuntimeException.class, () -> RecipeFilter.wrap(shell.evaluate("def filter = { false }; filter")));
		});
	}

	@Test
	void nativeClosureProxiesAreRejectedBeforeMatchingIncludingNestedFilters() {
		var context = new Context(new ContextFactory());
		ScopedValue.where(Context.CURRENT, context).run(() -> {
			var closure = (Closure<?>) new GroovyShell().evaluate("def filter = { id: 'minecraft:diamond_block' }; filter");
			assertThrows(KubeRuntimeException.class, () -> RecipeFilter.wrap(List.of(closure, ConstantFilter.FALSE)));
			var proxy = (RecipeFilter) new GroovyShell().evaluate("def filter = { throw new AssertionError('closure was executed') }; filter as dev.uncandango.kubegroovy.recipe.filter.RecipeFilter");
			assertTrue(Proxy.isProxyClass(proxy.getClass()));
			assertThrows(KubeRuntimeException.class, () -> RecipeFilter.rejectClosure(proxy));
			assertThrows(KubeRuntimeException.class, () -> RecipeFilter.wrap(proxy));
			var or = new OrFilter();
			or.list.add(ConstantFilter.TRUE);
			or.list.add(proxy);
			var and = new AndFilter();
			and.list.add(ConstantFilter.FALSE);
			and.list.add(or);
			assertThrows(KubeRuntimeException.class, () -> RecipeFilter.rejectClosure(new NotFilter(and)));
			assertSame(ConstantFilter.FALSE, RecipeFilter.wrap(ConstantFilter.FALSE));
			RecipeFilter javaFilter = recipe -> false;
			assertSame(javaFilter, RecipeFilter.wrap(javaFilter));
		});
	}

	@Test
	void addonFiltersAndOtherJavaProxiesRemainSupported() {
		RecipeFilter javaFilter = recipe -> true;
		var proxy = (RecipeFilter) Proxy.newProxyInstance(RecipeFilter.class.getClassLoader(), new Class<?>[]{RecipeFilter.class},
			(instance, method, args) -> true);
		assertDoesNotThrow(() -> RecipeFilter.rejectClosure(javaFilter));
		assertDoesNotThrow(() -> RecipeFilter.rejectClosure(proxy));
		ScopedValue.where(Context.CURRENT, new Context(new ContextFactory())).run(() -> assertSame(proxy, RecipeFilter.wrap(proxy)));
	}
}
