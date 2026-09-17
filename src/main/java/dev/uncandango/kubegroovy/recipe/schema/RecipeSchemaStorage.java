package dev.uncandango.kubegroovy.recipe.schema;

import com.google.common.base.Suppliers;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import dev.uncandango.kubegroovy.KubeGroovy;
import dev.uncandango.kubegroovy.codec.KubeGroovyCodecs;
import dev.uncandango.kubegroovy.plugin.KubeGroovyPlugin;
import dev.uncandango.kubegroovy.plugin.KubeGroovyPlugins;
import dev.uncandango.kubegroovy.plugin.builtin.event.ServerEvents;
import dev.uncandango.kubegroovy.recipe.component.RecipeComponent;
import dev.uncandango.kubegroovy.recipe.component.RecipeComponentType;
import dev.uncandango.kubegroovy.script.ScriptType;
import dev.uncandango.kubegroovy.server.ServerScriptManager;
import dev.uncandango.kubegroovy.util.ID;
import dev.uncandango.kubegroovy.util.JsonUtils;
import dev.uncandango.kubegroovy.util.RegistryAccessContainer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Supplier;

public class RecipeSchemaStorage {
	// TODO: Maybe some sort of BakeableMap<> could be used... or that's my tendency to engineer moar talking
	private static final Map<RecipeComponentType<?>, ResourceKey<RecipeComponentType<?>>> BAKED_COMPONENT_TYPE_LOOKUP = new HashMap<>();
	private static final Map<ResourceKey<RecipeComponentType<?>>, Supplier<RecipeComponentType<?>>> COMPONENT_TYPES = new IdentityHashMap<>();

	private final ServerScriptManager manager;

	public final Map<Identifier, KubeRecipeFactory> recipeTypes;
	public final Map<String, RecipeNamespace> namespaces;
	public final Map<String, Identifier> mappings;
	public final Map<String, RecipeSchemaType> schemaTypes;

	public static DataResult<RecipeComponentType<?>> getType(ResourceKey<RecipeComponentType<?>> key) {
		var stored = COMPONENT_TYPES.get(key);
		return stored != null && stored.get() != null
			? DataResult.success(stored.get())
			: DataResult.error(() -> "Unknown recipe component type '%s'".formatted(ID.reduceKjs(key.identifier())));
	}

	public static DataResult<RecipeComponentType<?>> getType(Identifier id) {
		return getType(RecipeComponentType.key(id));
	}

	public static DataResult<RecipeComponentType<?>> getType(RecipeComponent<?> component) {
		return getType(component.type());
	}

	private static DataResult<ResourceKey<RecipeComponentType<?>>> getTypeId(RecipeComponentType<?> type) {
		var id = BAKED_COMPONENT_TYPE_LOOKUP.get(type);
		return id != null
			? DataResult.success(id)
			: DataResult.error(() -> "Unregistered recipe component type %s???".formatted(type));
	}

	private static final Codec<RecipeComponentType<?>> TYPE_CODEC = KubeGroovyCodecs.KubeGroovy_ID
		.xmap(RecipeComponentType::key, ResourceKey::identifier)
		.flatXmap(RecipeSchemaStorage::getType, RecipeSchemaStorage::getTypeId);

	private static final Codec<RecipeComponent<?>> MAP_CODEC = TYPE_CODEC.partialDispatch("type",
		RecipeSchemaStorage::getType,
		type -> DataResult.success(type.mapCodec())
	);

	public static final Codec<RecipeComponent<?>> COMPONENT_CODEC = new Codec<>() {
		@Override
		public <T> DataResult<Pair<RecipeComponent<?>, T>> decode(final DynamicOps<T> ops, final T input) {
			DataResult<Pair<RecipeComponent<?>, T>> fromType = TYPE_CODEC.decode(ops, input)
				.flatMap(pair -> pair.getFirst().mapCodec().decode(ops, MapLike.empty())
					.map(result -> Pair.of(result, pair.getSecond())));

			if (fromType.isSuccess()) {
				return fromType;
			}

			return MAP_CODEC.decode(ops, input)
				.mapError(err -> "Failed to parse component. Input is not a unit type; map codec error: " + err);
		}

		@Override
		public <T> DataResult<T> encode(final RecipeComponent<?> input, final DynamicOps<T> ops, final T prefix) {
			return MAP_CODEC.encode(input, ops, prefix);
		}
	};

	public RecipeSchemaStorage(ServerScriptManager manager) {
		this.manager = manager;
		this.recipeTypes = new HashMap<>();
		this.namespaces = new HashMap<>();
		this.mappings = new HashMap<>();
		this.schemaTypes = new HashMap<>();
	}

	public RecipeNamespace namespace(String namespace) {
		return namespaces.computeIfAbsent(namespace, n -> new RecipeNamespace(this, n));
	}

	RegistryAccessContainer registries() {
		return manager.getRegistries();
	}

	public void fireEvents(RegistryAccessContainer registries, ResourceManager resourceManager) {
		recipeTypes.clear();
		namespaces.clear();
		mappings.clear();
		schemaTypes.clear();

		COMPONENT_TYPES.clear();
		BAKED_COMPONENT_TYPE_LOOKUP.clear();

		var typeEvent = new RecipeFactoryRegistry(this);
		KubeGroovyPlugins.forEachPlugin(typeEvent, KubeGroovyPlugin::registerRecipeFactories);

		for (var entry : resourceManager.listResources("kubegroovy", path -> path.getPath().endsWith("/recipe_mappings.json")).entrySet()) {
			try (var reader = entry.getValue().openAsReader()) {
				var json = JsonUtils.GSON.fromJson(reader, JsonObject.class);

				for (var entry1 : json.entrySet()) {
					var id = Identifier.fromNamespaceAndPath(entry.getKey().getNamespace(), entry1.getKey());

					if (entry1.getValue() instanceof JsonArray arr) {
						for (var n : arr) {
							mappings.put(n.getAsString(), id);
						}
					} else {
						mappings.put(entry1.getValue().getAsString(), id);
					}
				}
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}

		var mappingRegistry = new RecipeMappingRegistry(this);
		KubeGroovyPlugins.forEachPlugin(mappingRegistry, KubeGroovyPlugin::registerRecipeMappings);
		ServerEvents.RECIPE_MAPPING_REGISTRY.post(ScriptType.SERVER, mappingRegistry);

		KubeGroovyPlugins.forEachPlugin((k, v) -> COMPONENT_TYPES.put(k, () -> v), KubeGroovyPlugin::registerRecipeComponents);

		var ops = registries.json();
		for (var entry : resourceManager.listResources("kubegroovy", path -> path.getPath().endsWith("/recipe_components.json")).entrySet()) {
			try (var reader = entry.getValue().openAsReader()) {
				var json = JsonUtils.GSON.fromJson(reader, JsonObject.class);

				for (var componentDef : json.entrySet()) {
					var id = ID.kgy(componentDef.getKey());

					COMPONENT_TYPES.put(RecipeComponentType.key(id), Suppliers.memoize(() ->
							COMPONENT_CODEC.parse(ops, componentDef.getValue()).mapOrElse(c -> () -> MapCodec.unit(c), error -> {
								KubeGroovy.LOGGER.error("Failed to load recipe component {} from {}: {}", id, entry.getKey(), error.message());
								return null;
							})
					));
				}
			} catch (Exception ex) {
				KubeGroovy.LOGGER.error("Failed to load recipe component file {}: {}", entry.getKey(), ex);
			}
		}

		var iterator = COMPONENT_TYPES.entrySet().iterator();
		while (iterator.hasNext()) {
			var entry = iterator.next();
			var key = entry.getKey();
			try {
				var type = entry.getValue().get();
				BAKED_COMPONENT_TYPE_LOOKUP.put(type, key);
			} catch (StackOverflowError error) {
				var msg = "Encountered cyclic recipe component type reference while baking '" + key.identifier() + "'";
                KubeGroovy.LOGGER.error(msg, error);
				iterator.remove();
			} catch (Exception e) {
				var msg = "Encountered error while baking recipe component type '" + key.identifier() + "'";
				KubeGroovy.LOGGER.error(msg, e);
			}
		}

		for (var entry : BuiltInRegistries.RECIPE_SERIALIZER.entrySet()) {
			var id = entry.getKey().identifier();

			var ns = namespace(id.getNamespace());
			ns.put(id.getPath(), new UnknownRecipeSchemaType(ns, id, entry.getValue()));
		}

		var schemaRegistry = new RecipeSchemaRegistry(this);
		JsonRecipeSchemaLoader.load(ops, this, schemaRegistry, resourceManager);

		KubeGroovyPlugins.forEachPlugin(schemaRegistry, KubeGroovyPlugin::registerRecipeSchemas);
		ServerEvents.RECIPE_SCHEMA_REGISTRY.post(ScriptType.SERVER, schemaRegistry);
	}
}
