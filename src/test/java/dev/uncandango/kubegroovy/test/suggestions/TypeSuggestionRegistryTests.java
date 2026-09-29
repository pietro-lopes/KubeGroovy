package dev.uncandango.kubegroovy.test.suggestions;

import dev.uncandango.kubegroovy.plugin.TypeSuggestionCatalog;
import dev.uncandango.kubegroovy.plugin.TypeSuggestionRegistry;

import dev.uncandango.kubegroovy.script.KubeGroovyContext;
import dev.uncandango.kubegroovy.script.type.TypeInfo;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TypeSuggestionRegistryTests {
	@Test
	void providersReceiveTheirTypeAndRetainDuplicatesAndEncounterOrder() throws Exception {
		var context = contextWithoutInitialization();
		var registry = new TypeSuggestionRegistry();
		var closed = new AtomicBoolean();
		registry.setCurrentOwner("example.addon.Plugin");
		registry.register(Item.class, (cx, typeInfo) -> {
			assertSame(context, cx);
			assertEquals(Item.class, typeInfo.asClass());
			return Stream.of("addon:first", "addon:first").parallel().onClose(() -> closed.set(true));
		});
		registry.register(TypeInfo.of(Item.class), (cx, typeInfo) -> Stream.of("addon:last"));

		var catalog = registry.resolve(context);

		assertEquals(List.of("addon:first", "addon:first", "addon:last"),
			catalog.values().get(TypeSuggestionCatalog.typeName(TypeInfo.of(Item.class))));
		assertTrue(closed.get());
	}

	@Test
	void modifierUsesElementTypeAndDelegatesKeepPrefixAndGenericRule() throws Exception {
		var context = contextWithoutInitialization();
		var registry = new TypeSuggestionRegistry();
		registry.registerModifierFor(Item.class, TagKey.class, (cx, elementType) -> {
			assertEquals(Item.class, elementType.asClass());
			return Stream.of("addon:tag");
		});
		registry.registerDelegate(Ingredient.class, TypeInfo.of(TagKey.class).withParams(TypeInfo.of(Item.class)), "#");
		registry.registerDelegate(Optional.class);
		registry.registerDelegate(ItemStack.class, Item.class);

		var catalog = registry.resolve(context);

		assertEquals(List.of("addon:tag"), catalog.values().get(
			TypeSuggestionCatalog.typeName(TypeInfo.of(TagKey.class).withParams(TypeInfo.of(Item.class)))));
		assertEquals(List.of(new TypeSuggestionCatalog.Delegate(
			TypeSuggestionCatalog.typeName(TypeInfo.of(Ingredient.class)),
			TypeSuggestionCatalog.typeName(TypeInfo.of(TagKey.class).withParams(TypeInfo.of(Item.class))), "#"),
			new TypeSuggestionCatalog.Delegate(TypeSuggestionCatalog.typeName(TypeInfo.of(ItemStack.class)),
				TypeSuggestionCatalog.typeName(TypeInfo.of(Item.class)), "")), catalog.delegates());
		assertEquals(Map.of(TypeSuggestionCatalog.className(Optional.class), 0), catalog.genericDelegates());
	}

	@Test
	void genericDelegateRejectsAmbiguousArityAndProviderErrorsNameTheirOwnerAndType() throws Exception {
		var registry = new TypeSuggestionRegistry();
		assertThrows(IllegalArgumentException.class, () -> registry.registerDelegate(Map.class));
		assertThrows(IllegalArgumentException.class, () -> registry.registerDelegate(String.class));

		registry.setCurrentOwner("example.broken.Plugin");
		registry.register(Item.class, (cx, typeInfo) -> Stream.of("broken")
			.map(value -> { throw new IllegalStateException("provider failed"); }));
		var failure = assertThrows(IllegalStateException.class, () -> registry.resolve(contextWithoutInitialization()));
		assertTrue(failure.getMessage().contains("example.broken.Plugin"));
		assertTrue(failure.getMessage().contains(Item.class.getCanonicalName()));
	}

	private static KubeGroovyContext contextWithoutInitialization() throws Exception {
		Field field = Unsafe.class.getDeclaredField("theUnsafe");
		field.setAccessible(true);
		return (KubeGroovyContext) ((Unsafe) field.get(null)).allocateInstance(KubeGroovyContext.class);
	}
}
