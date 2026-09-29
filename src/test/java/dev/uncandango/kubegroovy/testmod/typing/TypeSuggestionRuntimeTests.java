package dev.uncandango.kubegroovy.testmod.typing;

import com.google.gson.JsonParser;
import dev.uncandango.kubegroovy.core.ReloadableServerResourcesKGY;
import dev.uncandango.kubegroovy.plugin.KubeGroovyPlugins;
import dev.uncandango.kubegroovy.plugin.TypeSuggestionCatalog;
import dev.uncandango.kubegroovy.plugin.TypeSuggestionRegistry;
import dev.uncandango.kubegroovy.script.KubeGroovyContext;
import dev.uncandango.kubegroovy.script.type.TypeInfo;
import dev.uncandango.kubegroovy.testmod.TestRuntimePlugin;
import dev.uncandango.kubegroovy.typing.KubeGroovyTypingGenerator;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.testframework.DynamicTest;
import net.neoforged.testframework.annotation.ForEachTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.GameTest;

import java.util.Optional;

import static dev.uncandango.kubegroovy.testmod.GameAsserts.assertj;
import static org.assertj.core.api.Assertions.assertThat;

@ForEachTest(groups = "kubegroovy.typing")
public final class TypeSuggestionRuntimeTests {
	@GameTest
	@EmptyTemplate
	@TestHolder(value = "runtime_type_suggestion_catalog", description = "Suggestion providers resolve real registries, tags, and addon values against the loaded server context")
	static void runtimeCatalogUsesServerContext(final DynamicTest test) {
		test.onGameTest(helper -> {
			ReloadableServerResourcesKGY managers = helper.getLevel().getServer().getServerResources().managers();
			var manager = managers.kgy$getServerScriptManager();
			KubeGroovyContext context = manager.context;
			var suggestions = new TypeSuggestionRegistry();
			suggestions.setCurrentOwner("KubeGroovy built-ins");
			suggestions.register(Item.class, (cx, typeInfo) -> cx.lookupRegistry(typeInfo, null).entrySet().stream()
				.map(entry -> entry.getKey().identifier().toString()));
			suggestions.registerModifierFor(Item.class, TagKey.class, (cx, typeInfo) -> cx.lookupRegistry(typeInfo, null)
				.listTagIds().map(tag -> tag.location().toString()));
			suggestions.register(Registry.class, (cx, typeInfo) -> cx.getRegistries().registryAccess().registries()
				.map(entry -> entry.key().identifier().toString()));
			suggestions.registerDelegate(ItemStack.class, Item.class);
			suggestions.registerDelegate(Ingredient.class, Item.class);
			suggestions.registerDelegate(Ingredient.class,
				TypeInfo.of(TagKey.class).withParams(TypeInfo.of(Item.class)), "#");
			suggestions.registerDelegate(Optional.class);
			suggestions.registerDelegate(Holder.class);
			suggestions.registerDelegate(Holder.Reference.class);
			suggestions.registerDelegate(ResourceKey.class);
			for (var plugin : KubeGroovyPlugins.getAll()) {
				suggestions.setCurrentOwner(plugin.getClass().getName());
				plugin.registerTypeSuggestions(suggestions);
			}
			TypeSuggestionCatalog catalog = suggestions.resolve(context);
			var json = JsonParser.parseString(KubeGroovyTypingGenerator.renderTypeSuggestionsJson(catalog)).getAsJsonObject();
			var jsonValues = json.getAsJsonObject("values");

			assertj(helper, () -> {
				assertThat(context).as("loaded server script context").isNotNull();
				var itemValues = catalog.values().get(TypeSuggestionCatalog.typeName(TypeInfo.of(Item.class)));
				assertThat(itemValues).contains("minecraft:stone");
				assertThat(jsonValues.getAsJsonArray(TypeSuggestionCatalog.typeName(TypeInfo.of(Item.class)))
					.asList().stream().map(value -> value.getAsString()).toList()).contains("minecraft:stone");
				var itemTags = catalog.values().get(TypeSuggestionCatalog.typeName(
					TypeInfo.of(TagKey.class).withParams(TypeInfo.of(Item.class))));
				assertThat(itemTags).isNotEmpty().allMatch(tag -> tag.contains(":"));
				assertThat(jsonValues.getAsJsonArray(TypeSuggestionCatalog.typeName(
					TypeInfo.of(TagKey.class).withParams(TypeInfo.of(Item.class)))).size()).isEqualTo(itemTags.size());
				var registryValues = catalog.values().get(TypeSuggestionCatalog.typeName(TypeInfo.of(Registry.class)));
				assertThat(registryValues).contains("minecraft:item");
				assertThat(catalog.values().get(TypeSuggestionCatalog.typeName(
					TypeInfo.of(TestRuntimePlugin.TestSuggestionValue.class))))
					.containsExactly("testmod:addon_suggestion");
				assertThat(jsonValues.getAsJsonArray(TypeSuggestionCatalog.typeName(
					TypeInfo.of(TestRuntimePlugin.TestSuggestionValue.class))).get(0).getAsString())
					.isEqualTo("testmod:addon_suggestion");
				assertThat(catalog.delegates()).anySatisfy(delegate -> {
					assertThat(delegate.source()).isEqualTo(TypeSuggestionCatalog.typeName(TypeInfo.of(Ingredient.class)));
					assertThat(delegate.prefix()).isEqualTo("#");
				});
			});
			helper.succeed();
		});
	}
}
