package dev.uncandango.kubegroovy.plugin;

import com.google.gson.JsonElement;
import dev.uncandango.kubegroovy.client.LangKubeEvent;
import dev.uncandango.kubegroovy.client.icon.KubeIconTypeRegistry;
import dev.uncandango.kubegroovy.event.EventGroupRegistry;
import dev.uncandango.kubegroovy.event.EventGroups;
import dev.uncandango.kubegroovy.generator.KubeAssetGenerator;
import dev.uncandango.kubegroovy.generator.KubeDataGenerator;
import dev.uncandango.kubegroovy.plugin.builtin.event.ServerEvents;
import dev.uncandango.kubegroovy.recipe.KubeRecipe;
import dev.uncandango.kubegroovy.recipe.RecipesKubeEvent;
import dev.uncandango.kubegroovy.recipe.component.RecipeComponent;
import dev.uncandango.kubegroovy.recipe.component.RecipeComponentType;
import dev.uncandango.kubegroovy.recipe.component.RecipeComponentTypeRegistry;
import dev.uncandango.kubegroovy.recipe.ingredientaction.IngredientActionTypeRegistry;
import dev.uncandango.kubegroovy.recipe.schema.KubeRecipeFactory;
import dev.uncandango.kubegroovy.recipe.schema.RecipeFactoryRegistry;
import dev.uncandango.kubegroovy.recipe.schema.RecipeMappingRegistry;
import dev.uncandango.kubegroovy.recipe.schema.RecipeSchema;
import dev.uncandango.kubegroovy.recipe.schema.RecipeSchemaRegistry;
import dev.uncandango.kubegroovy.recipe.schema.function.CustomRecipeSchemaFunctionRegistry;
import dev.uncandango.kubegroovy.recipe.schema.function.RecipeSchemaFunctionRegistry;
import dev.uncandango.kubegroovy.recipe.schema.postprocessing.RecipePostProcessorTypeRegistry;
import dev.uncandango.kubegroovy.recipe.viewer.RecipeViewerEntryType;
import dev.uncandango.kubegroovy.registry.BuilderTypeRegistry;
import dev.uncandango.kubegroovy.registry.ServerRegistryRegistry;
import dev.uncandango.kubegroovy.script.BindingRegistry;
import dev.uncandango.kubegroovy.script.DataComponentTypeInfoRegistry;
import dev.uncandango.kubegroovy.script.RecordDefaultsRegistry;
import dev.uncandango.kubegroovy.script.ScriptManager;
import dev.uncandango.kubegroovy.script.ScriptType;
import dev.uncandango.kubegroovy.script.TypeDescriptionRegistry;
import dev.uncandango.kubegroovy.script.TypeWrapperRegistry;
import dev.uncandango.kubegroovy.server.DataExport;
import dev.uncandango.kubegroovy.util.AttachedData;
import dev.uncandango.kubegroovy.util.NameProvider;
import dev.uncandango.kubegroovy.web.LocalWebServer;
import dev.uncandango.kubegroovy.web.LocalWebServerAPIRegistry;
import dev.uncandango.kubegroovy.web.LocalWebServerRegistry;
import dev.uncandango.kubegroovy.script.bridge.Context;
import dev.uncandango.kubegroovy.script.type.TypeInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.Map;
import java.util.function.Consumer;

/// Extension interface for addon mods to integrate with KubeGroovy.
///
/// To add your own plugin, create a `kubegroovy.plugins.txt` file in your mod's `resources` root,
/// which will then be discovered and loaded by [KubeGroovyPlugins].
///
/// @apiNote All hooks in this class have empty default implementations; override only what you need.
public interface KubeGroovyPlugin {
	/// Called early during mod init, before startup scripts run.
	default void init() {
	}

	/// Called after startup scripts have been loaded, but before any registry stuff has taken place.
	default void initStartup() {
	}

	/// Called during `FMLLoadCompleteEvent`, after all mods have finished initializing.
	default void afterInit() {
	}

	/// Register named content builder types for registries, for example `sword` for items.
	default void registerBuilderTypes(BuilderTypeRegistry registry) {
	}

	/// Register custom datapack registries that scripts can register to via [ServerEvents#REGISTRY].
	default void registerServerRegistries(ServerRegistryRegistry registry) {
	}

	/// Register your custom event groups for scripts using [EventGroupRegistry#register].
	///
	/// @see EventGroups#ALL
	default void registerEvents(EventGroupRegistry registry) {
	}

	/// Register global class and member rules, once after plugin discovery.
	/// The policy is frozen before scripts load and reused across all script types and reloads.
	default void registerClasses(ClassFilter filter) {
	}

	/// Add global bindings (variables, constants, functions) to the top-level scope for a given [ScriptType].
	default void registerBindings(BindingRegistry bindings) {
	}

	/// Register GY-to-Java type wrappers so Groovy can explicitly convert script values
	/// (e.g. a string `"minecraft:stone"`) into the expected Java type (e.g. a [Block]).
	default void registerTypeWrappers(TypeWrapperRegistry registry) {
	}

	/// Register default field values for Java records that scripts can create them without specifying every field.
	default void registerRecordDefaults(RecordDefaultsRegistry registry) {
	}

	/// Register custom [TypeInfo] descriptors for Java types, which lets addon mods
	/// provide richer type reflection and autocompletion for those types in scripts.
	default void registerTypeDescriptions(TypeDescriptionRegistry registry) {
	}

	/// Register values offered when a script expression expects a particular type.
	default void registerTypeSuggestions(TypeSuggestionRegistry registry) {
	}

	/// Register custom [KubeRecipeFactory] objects for your mod's recipe types.
	/// This is only necessary if you want to override [KubeRecipe] in your recipe schema.
	///
	/// @see KubeRecipeFactory
	/// @see KubeRecipe
	/// @see RecipeSchema
	default void registerRecipeFactories(RecipeFactoryRegistry registry) {
	}

	/// Register custom aliases for your recipe types to be used during the recipe event,
	/// e.g. event.recipes.coolStuff => `my_mod:this_id_is_unwieldy_but_cool`
	default void registerRecipeMappings(RecipeMappingRegistry registry) {
	}

	/// Register custom recipe component types (ingredient, item result, count, etc.) for use in recipe schema definitions.
	///
	/// @see RecipeComponent
	/// @see RecipeComponentType
	/// @see RecipeSchema
	default void registerRecipeComponents(RecipeComponentTypeRegistry registry) {
	}

	/// Register custom recipe schemas that define the basic structure of a recipe for use in scripts.
	///
	/// @see RecipeSchema
	default void registerRecipeSchemas(RecipeSchemaRegistry registry) {
	}

	default void registerRecipeSchemaFunctionTypes(RecipeSchemaFunctionRegistry registry) {
	}

	default void registerCustomRecipeSchemaFunctions(CustomRecipeSchemaFunctionRegistry registry) {
	}

	default void registerRecipePostProcessors(RecipePostProcessorTypeRegistry registry) {
	}

	/// Register custom handlers to transform crafting inputs while crafting.
	default void registerIngredientActionTypes(IngredientActionTypeRegistry registry) {
	}

	default void registerRecipeViewerEntryTypes(Consumer<RecipeViewerEntryType> registry) {
	}

	/// Map Minecraft data component types to their script [TypeInfo],
	/// allowing scripts to read and write typed component data on item stacks.
	default void registerDataComponentTypeDescriptions(DataComponentTypeInfoRegistry registry) {
	}

	default void registerLocalWebServerAPIs(LocalWebServerAPIRegistry registry) {
	}

	default void registerLocalWebServer(LocalWebServerRegistry registry) {
	}

	default void registerLocalWebServerWithAuth(LocalWebServerRegistry registry) {
	}

	default void localWebServerStarted(LocalWebServer server) {
	}

	default void registerItemNameProviders(NameProvider.Registry<Item, ItemStack> registry) {
	}

	/// Register custom icon type renderers for the KubeGroovy client UI.
	default void registerIconTypes(KubeIconTypeRegistry registry) {
	}

	/// Attach custom data to the [server][MinecraftServer].
	///
	/// @see AttachedData
	default void attachServerData(AttachedData<MinecraftServer> event) {
	}

	/// Attach custom data to a [specific world][Level].
	///
	/// @see AttachedData
	default void attachLevelData(AttachedData<Level> event) {
	}

	/// Attach custom data to a [player][Player].
	///
	/// @see AttachedData
	default void attachPlayerData(AttachedData<Player> event) {
	}

	default void generateData(KubeDataGenerator generator) {
	}

	default void generateAssets(KubeAssetGenerator generator) {
	}

	default void generateLang(LangKubeEvent event) {
	}

	/// Add additional JSONs to the data export written to `local/kubegroovy/export/`.
	default void exportServerData(DataExport export) {
	}

	/// Called just before KubeGroovy processes recipes; `recipeJsons` is the raw map of all recipe JSONs
	/// and may be mutated to inject, remove, or modify recipes before scripts run.
	default void beforeRecipeLoading(RecipesKubeEvent event, Map<Identifier, JsonElement> recipeJsons) {
	}

	/// Called at the start of each [ScriptManager#reload()] cycle, before scripts are evaluated.
	default void beforeScriptsLoaded(ScriptManager manager) {
	}

	/// Called at the end of each [ScriptManager#reload()] cycle, after scripts have been evaluated.
	default void afterScriptsLoaded(ScriptManager manager) {
	}

	/// Called by Platform.breakpoint('abc', ...) from scripts, only used for debugging
	default void breakpoint(Object[] args) {
		var cx = Context.getCurrent();
	}
}
