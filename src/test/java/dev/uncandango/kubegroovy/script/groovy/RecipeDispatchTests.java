package dev.uncandango.kubegroovy.groovytest;

import com.mojang.serialization.Codec;
import dev.uncandango.kubegroovy.error.KubeRuntimeException;
import dev.uncandango.kubegroovy.error.MissingComponentException;
import dev.uncandango.kubegroovy.recipe.KubeRecipe;
import dev.uncandango.kubegroovy.recipe.NamespaceFunction;
import dev.uncandango.kubegroovy.recipe.RecipeKey;
import dev.uncandango.kubegroovy.recipe.RecipeScriptContext;
import dev.uncandango.kubegroovy.recipe.RecipeTypeFunction;
import dev.uncandango.kubegroovy.recipe.component.ComponentRole;
import dev.uncandango.kubegroovy.recipe.component.RecipeComponent;
import dev.uncandango.kubegroovy.recipe.component.RecipeComponentType;
import dev.uncandango.kubegroovy.recipe.component.StringComponent;
import dev.uncandango.kubegroovy.recipe.schema.RecipeNamespace;
import dev.uncandango.kubegroovy.recipe.schema.RecipeSchema;
import dev.uncandango.kubegroovy.recipe.schema.function.RecipeFunctionInstance;
import dev.uncandango.kubegroovy.script.bridge.Context;
import dev.uncandango.kubegroovy.script.bridge.ContextFactory;
import dev.uncandango.kubegroovy.script.bridge.TypeWrappers;
import dev.uncandango.kubegroovy.script.groovy.KubeGroovySandbox;
import dev.uncandango.kubegroovy.script.type.TypeInfo;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.MissingMethodException;
import groovy.lang.MissingPropertyException;
import net.minecraft.resources.ResourceKey;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class RecipeDispatchTests {
	private final TypeWrappers wrappers = new TypeWrappers();
	private final Context context = new Context(new ContextFactory() {
		@Override
		public TypeWrappers getTypeWrappers() {
			return wrappers;
		}
	});

	private static KubeRecipe recipe(RecipeSchema schema) {
		var namespace = new RecipeNamespace(null, "test");
		namespace.register("dispatch", schema);
		var recipe = new KubeRecipe();
		recipe.type = new RecipeTypeFunction(null, namespace.get("dispatch"));
		recipe.initValues(false);
		return recipe;
	}

	private static GroovyShell shell(KubeRecipe recipe) {
		var configuration = new CompilerConfiguration();
		KubeGroovySandbox.customizers(null, new TypeWrappers()).forEach(configuration::addCompilationCustomizers);
		return new GroovyShell(new Binding(new HashMap<>(Map.of("recipe", recipe))), configuration);
	}

	@Test
	void componentAliasesUseLaterKeysAndKeepFluentAndJavaCalls() {
		ScopedValue.where(Context.CURRENT, context).run(() -> {
			var first = new RecipeKey<>(StringComponent.STRING, "first", ComponentRole.OTHER).functionNames("shared", "firstAlias");
			var second = new RecipeKey<>(StringComponent.STRING, "second", ComponentRole.OTHER).functionNames("shared");
			var recipe = recipe(new RecipeSchema(first, second));
			assertSame(recipe, shell(recipe).evaluate("recipe.firstAlias('first').shared('second')"));
			assertEquals("first", recipe.get("first"));
			assertEquals("second", recipe.get("second"));
			assertEquals("first", shell(recipe).evaluate("recipe.get('first')"));
			assertEquals("second", shell(recipe).evaluate("recipe.second"));
			assertTrue(recipe.changed);
		});
	}

	@Test
	void schemaFunctionsOverrideComponentsAndConvertDeclaredArguments() {
		wrappers.register(String.class, (value, target) -> String.valueOf(value), true);
		ScopedValue.where(Context.CURRENT, context).run(() -> {
			var key = new RecipeKey<>(StringComponent.STRING, "value", ComponentRole.OTHER).functionNames("shared");
			var received = new AtomicReference<List<Object>>();
			var schema = new RecipeSchema(key);
			schema.function(new RecipeFunctionInstance("shared", List.of(StringComponent.STRING, StringComponent.STRING), (cx, args) -> received.set(args)));
			schema.function(new RecipeFunctionInstance("zero", (cx, args) -> received.set(args)));
			var recipe = recipe(schema);
			var shell = shell(recipe);
			assertSame(recipe, shell.evaluate("recipe.shared(12, 'text')"));
			assertEquals(List.of("12", "text"), received.get());
			assertNull(recipe.get("value"));
			assertSame(recipe, shell.evaluate("recipe.zero()"));
			assertEquals(List.of(), received.get());
			assertThrows(KubeRuntimeException.class, () -> recipe.invokeMethod("shared", new Object[]{"only one"}));
			assertThrows(KubeRuntimeException.class, () -> recipe.invokeMethod("zero", new Object[]{"extra"}));
		});
	}

	@Test
	void singleArrayAndListArgumentsStayIntact() {
		ScopedValue.where(Context.CURRENT, context).run(() -> {
			var component = new IdentityComponent();
			var key = new RecipeKey<>(component, "payload", ComponentRole.OTHER);
			var received = new AtomicReference<Object>();
			var schema = new RecipeSchema(key);
			schema.function(new RecipeFunctionInstance("capture", List.of(component), (cx, args) -> received.set(args.getFirst())));
			var recipe = recipe(schema);
			var shell = shell(recipe);
			var array = new Object[]{"a", "b"};
			shell.getContext().setVariable("array", array);
			assertSame(recipe, shell.evaluate("recipe.payload(array)"));
			assertSame(array, recipe.get("payload"));
			shell.evaluate("recipe.capture(array)");
			assertSame(array, received.get());
			shell.evaluate("recipe.payload(['a', 'b'])");
			assertEquals(List.of("a", "b"), recipe.get("payload"));
			assertThrows(KubeRuntimeException.class, () -> shell.evaluate("recipe.payload()"));
			assertThrows(KubeRuntimeException.class, () -> shell.evaluate("recipe.payload('a', 'b')"));
		});
	}

	@Test
	void sandboxedDispatchRejectsMissingMethodsAndMetaclassReplacement() {
		ScopedValue.where(Context.CURRENT, context).run(() -> {
			var recipe = recipe(new RecipeSchema());
			var shell = shell(recipe);
			assertThrows(MissingComponentException.class, () -> shell.evaluate("recipe.missing()"));
			assertThrows(UnsupportedOperationException.class, () -> shell.evaluate("recipe.setMetaClass(null)"));
			assertThrows(UnsupportedOperationException.class, () -> shell.evaluate("recipe.metaClass = null"));
		});
	}

	@Test
	void namespaceIsCallableDirectlyAndThroughASavedBinding() {
		ScopedValue.where(Context.CURRENT, context).run(() -> {
			var namespace = new RecipeNamespace(null, "test");
			namespace.register("dispatch", new RecipeSchema());
			var function = new RecipeTypeFunction(null, namespace.get("dispatch"));
			var callable = new NamespaceFunction(namespace, Map.of("dispatch", function));
			var shell = new GroovyShell(new Binding(Map.of("namespace", callable)));
			assertEquals(namespace.keySet(), shell.evaluate("namespace()"));
			assertSame(function, shell.evaluate("def saved = namespace; saved('dispatch')"));
			assertThrows(IllegalArgumentException.class, () -> shell.evaluate("namespace('a', 'b')"));
		});
	}

	@Test
	void dottedNamespaceDispatchPreservesArgumentsAndNativeMembersInSandbox() {
		ScopedValue.where(Context.CURRENT, context).run(() -> {
			var namespace = new RecipeNamespace(null, "test");
			namespace.register("dispatch", new RecipeSchema());
			var received = new AtomicReference<Object[]>();
			var result = new KubeRecipe();
			var function = new RecipeTypeFunction(null, namespace.get("dispatch")) {
				@Override
				public Object call(Object... args) {
					received.set(args);
					return result;
				}
			};
			var callable = new NamespaceFunction(namespace, Map.of("dispatch", function, "mod", function, "toString", function));
			var shell = shell(result);
			shell.getContext().setVariable("namespace", callable);
			assertSame(function, shell.evaluate("namespace.dispatch"));
			assertSame(result, shell.evaluate("namespace.dispatch('output', ['A'], [A:'input'])"));
			assertArrayEquals(new Object[]{"output", List.of("A"), Map.of("A", "input")}, received.get());
			assertSame(result, shell.evaluate("def saved = namespace.dispatch; saved('saved')"));
			assertArrayEquals(new Object[]{"saved"}, received.get());
			assertSame(result, shell.evaluate("namespace.dispatch.call('explicit')"));
			var array = new Object[]{"a", "b"};
			shell.getContext().setVariable("array", array);
			assertSame(result, shell.evaluate("namespace.dispatch(array)"));
			assertEquals(1, received.get().length);
			assertSame(array, received.get()[0]);
			assertSame(result, shell.evaluate("namespace.dispatch()"));
			assertEquals(0, received.get().length);
			assertSame(function, shell.evaluate("namespace('dispatch')"));
			assertEquals(namespace.keySet(), shell.evaluate("namespace()"));
			assertEquals("test", shell.evaluate("namespace.mod"));
			assertEquals("test", shell.evaluate("namespace.toString()"));
			assertThrows(MissingPropertyException.class, () -> shell.evaluate("namespace.missing"));
			assertThrows(MissingMethodException.class, () -> shell.evaluate("namespace.missing('argument')"));
		});
	}

	private record IdentityComponent() implements RecipeComponent<Object> {
		@Override
		public ResourceKey<RecipeComponentType<?>> type() {
			return StringComponent.STRING.type();
		}

		@Override
		public Codec<Object> codec() {
			return Codec.STRING.xmap(value -> value, Object::toString);
		}

		@Override
		public TypeInfo typeInfo() {
			return TypeInfo.of(Object.class);
		}

		@Override
		public Object wrap(RecipeScriptContext cx, Object from) {
			return from;
		}
	}
}
