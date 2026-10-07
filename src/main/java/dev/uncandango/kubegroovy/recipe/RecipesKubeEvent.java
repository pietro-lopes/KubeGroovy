package dev.uncandango.kubegroovy.recipe;

import com.google.common.base.Stopwatch;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import dev.uncandango.kubegroovy.CommonProperties;
import dev.uncandango.kubegroovy.DevProperties;
import dev.uncandango.kubegroovy.error.KubeRuntimeException;
import dev.uncandango.kubegroovy.error.RecipeComponentException;
import dev.uncandango.kubegroovy.event.KubeEvent;
import dev.uncandango.kubegroovy.plugin.KubeGroovyPlugins;
import dev.uncandango.kubegroovy.plugin.builtin.event.ServerEvents;
import dev.uncandango.kubegroovy.recipe.filter.ConstantFilter;
import dev.uncandango.kubegroovy.recipe.filter.IDFilter;
import dev.uncandango.kubegroovy.recipe.filter.OrFilter;
import dev.uncandango.kubegroovy.recipe.filter.RecipeFilter;
import dev.uncandango.kubegroovy.recipe.filter.RecipeMatchContext;
import dev.uncandango.kubegroovy.recipe.filter.RegexIDFilter;
import dev.uncandango.kubegroovy.recipe.match.ReplacementMatchInfo;
import dev.uncandango.kubegroovy.recipe.schema.RecipeSchema;
import dev.uncandango.kubegroovy.recipe.schema.RecipeSchemaStorage;
import dev.uncandango.kubegroovy.recipe.schema.RecipeSchemaType;
import dev.uncandango.kubegroovy.recipe.schema.UnknownRecipeSchema;
import dev.uncandango.kubegroovy.recipe.special.SpecialRecipeSerializerManager;
import dev.uncandango.kubegroovy.script.ConsoleGY;
import dev.uncandango.kubegroovy.script.ScriptType;
import dev.uncandango.kubegroovy.script.SourceLine;
import dev.uncandango.kubegroovy.server.ChangesForChat;
import dev.uncandango.kubegroovy.server.DataExport;
import dev.uncandango.kubegroovy.server.ServerScriptManager;
import dev.uncandango.kubegroovy.util.ErrorStack;
import dev.uncandango.kubegroovy.util.ID;
import dev.uncandango.kubegroovy.util.JsonIO;
import dev.uncandango.kubegroovy.util.JsonUtils;
import dev.uncandango.kubegroovy.util.RegistryAccessContainer;
import dev.uncandango.kubegroovy.util.RegistryOpsContainer;
import dev.uncandango.kubegroovy.util.TimeGY;
import dev.uncandango.kubegroovy.script.bridge.Context;
import dev.uncandango.kubegroovy.script.annotations.HideFromGY;
import it.unimi.dsi.fastutil.objects.Reference2ObjectLinkedOpenHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.neoforge.common.conditions.ConditionalOps;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RecipesKubeEvent implements KubeEvent {
	public static final ScopedValue<RecipesKubeEvent> INSTANCE = ScopedValue.newInstance();

	public static final Pattern POST_SKIP_ERROR = ConsoleGY.methodPattern(RecipesKubeEvent.class, "post");
	public static final Pattern CREATE_RECIPE_SKIP_ERROR = ConsoleGY.methodPattern(RecipesKubeEvent.class, "createRecipe");
	private static final Predicate<KubeRecipe> RECIPE_NOT_REMOVED = r -> r != null && !r.removed;
	private static final Predicate<KubeRecipe> RECIPE_IS_SYNTHETIC = r -> !r.newRecipe;

	private final Stopwatch overallTimer;

	public final RecipeSchemaStorage recipeSchemaStorage;
	public final RegistryAccessContainer registries;
	public final RegistryOpsContainer ops;
	public final Map<Identifier, KubeRecipe> originalRecipes;
	public final Collection<KubeRecipe> addedRecipes;
	public final Collection<KubeRecipe> removedRecipes;

	int modifiedCount, failedCount;

	private final Map<Identifier, KubeRecipe> takenIds;

	private final Map<String, Object> recipeFunctions;
	public final transient RecipeTypeFunction vanillaShaped;
	public final transient RecipeTypeFunction vanillaShapeless;
	public final RecipeTypeFunction shaped;
	public final RecipeTypeFunction shapeless;
	public final RecipeTypeFunction smelting;
	public final RecipeTypeFunction blasting;
	public final RecipeTypeFunction smoking;
	public final RecipeTypeFunction campfireCooking;
	public final RecipeTypeFunction stonecutting;
	public final RecipeTypeFunction smithing;
	public final RecipeTypeFunction smithingTrim;

	public RecipesKubeEvent(ServerScriptManager manager) {
		ConsoleGY.SERVER.info("Initializing recipe event...");
		this.overallTimer = Stopwatch.createStarted();

		this.recipeSchemaStorage = manager.recipeSchemaStorage;
		this.registries = manager.getRegistries();
		this.ops = new RegistryOpsContainer(
			new KubeRecipeEventOps<>(this, registries.nbt()),
			new KubeRecipeEventOps<>(this, registries.json()),
			new KubeRecipeEventOps<>(this, registries.java())
		);
		this.originalRecipes = new HashMap<>();
		this.addedRecipes = new ConcurrentLinkedQueue<>();
		this.removedRecipes = new ConcurrentLinkedQueue<>();
		this.takenIds = new ConcurrentHashMap<>();
		var canonicalFunctions = new HashMap<String, RecipeTypeFunction>();
		this.recipeFunctions = RecipeFunctionLookup.<Object>create(recipeSchemaStorage, CommonProperties.get().serverOnly,
			type -> {
				var function = new RecipeTypeFunction(this, type);
				canonicalFunctions.put(type.id.toString(), function);
				return function;
			},
			(name, members) -> {
				var typed = new HashMap<String, RecipeTypeFunction>();
				members.forEach((key, value) -> typed.put(key, (RecipeTypeFunction) value));
				return new NamespaceFunction(recipeSchemaStorage.namespaces.get(name), typed);
			});

		vanillaShaped = canonicalFunctions.get("minecraft:crafting_shaped");
		vanillaShapeless = canonicalFunctions.get("minecraft:crafting_shapeless");
		shaped = CommonProperties.get().serverOnly ? vanillaShaped : canonicalFunctions.get("kubegroovy:shaped");
		shapeless = CommonProperties.get().serverOnly ? vanillaShapeless : canonicalFunctions.get("kubegroovy:shapeless");
		smelting = canonicalFunctions.get("minecraft:smelting");
		blasting = canonicalFunctions.get("minecraft:blasting");
		smoking = canonicalFunctions.get("minecraft:smoking");
		campfireCooking = canonicalFunctions.get("minecraft:campfire_cooking");
		stonecutting = canonicalFunctions.get("minecraft:stonecutting");
		smithing = canonicalFunctions.get("minecraft:smithing_transform");
		smithingTrim = canonicalFunctions.get("minecraft:smithing_trim");
	}

	@HideFromGY
	@ApiStatus.Internal
	public void post(RegistryOps<JsonElement> ops, Map<Identifier, JsonElement> datapackRecipeMap) {
		discoverRecipes(ops, datapackRecipeMap);
		postEvent();
		applyChanges(datapackRecipeMap);
		finishEvent();
	}

	@HideFromGY
	public void discoverRecipes(RegistryOps<JsonElement> ops, Map<Identifier, JsonElement> recipeJsons) {
		var timer = Stopwatch.createStarted();

		KubeGroovyPlugins.forEachPlugin(p -> p.beforeRecipeLoading(this, recipeJsons));
		AtomicInteger skippedRecipes = new AtomicInteger();

		for (var entry : recipeJsons.entrySet()) {
			Identifier recipeId = entry.getKey();

			//Forge: filter anything beginning with "_" as it's used for metadata.
			//noinspection ConstantValue
			if (recipeId == null || recipeId.getPath().startsWith("_")) {
				infoSkip("Skipping recipe %s, filename starts with _".formatted(recipeId));
				skippedRecipes.getAndIncrement();
				continue;
			}

			if (!(entry.getValue() instanceof JsonObject json)) {
				warnSkip("Skipping recipe %s, not a json object".formatted(recipeId));
				continue;
			}

			if (!json.has("type")) {
				warnSkip("Skipping recipe %s, missing 'type' field".formatted(recipeId));
				continue;
			}

			var codec = ConditionalOps.createConditionalCodec(MapCodec.unitCodec(json));

			switch (codec.parse(ops, json)) {
				case DataResult.Success(var result, _) -> {
					result.ifPresentOrElse(
						originalJson -> parseOriginalRecipe(originalJson, recipeId),
						() -> {
							infoSkip("Skipping recipe %s, conditions not met".formatted(recipeId));
							skippedRecipes.incrementAndGet();
						}
					);
				}
				case DataResult.Error<?> error -> errorSkip("Skipping recipe %s, error parsing conditions: %s".formatted(recipeId, error.message()));
			}
		}

		takenIds.putAll(originalRecipes);
		ConsoleGY.SERVER.info("Found %,d recipes (skipped %,d) in %s".formatted(originalRecipes.size(), skippedRecipes.get(), timer.stop()));
	}

	private void parseOriginalRecipe(JsonObject json, Identifier recipeId) {
		var typeStr = GsonHelper.getAsString(json, "type");
		var recipeIdAndType = recipeId + "[" + typeStr + "]";
		var type = getRecipeFunction(typeStr);

		if (type == null) {
			warnSkip("Skipping recipe %s, unknown type: %s".formatted(recipeId, typeStr));
			return;
		}

		var stack = new ErrorStack();

		try {
			var recipe = type.schemaType.schema.deserialize(SourceLine.UNKNOWN, type, recipeId, json);
			recipe.afterLoaded(stack);
			originalRecipes.put(recipeId, recipe);

			if (ConsoleGY.SERVER.shouldPrintDebug()) {
				var original = recipe.getOriginalRecipe();

				if (original == null || SpecialRecipeSerializerManager.INSTANCE.isSpecial(original)) {
					ConsoleGY.SERVER.debug("Loaded recipe " + recipeIdAndType + ": <dynamic>");
				} else {
					ConsoleGY.SERVER.debug("Loaded recipe " + recipeIdAndType + ": " + recipe.getFromToString());
				}
			}
		} catch (Throwable ex) {
			var recipeStr = "'%s'%s".formatted(recipeIdAndType, stack.atString());

			if (ex instanceof RecipeComponentException || DevProperties.get().logErroringParsedRecipes) {
				ConsoleGY.SERVER.warn("Failed to parse recipe %s! Falling back to vanilla".formatted(recipeStr), ex, POST_SKIP_ERROR);
			}

			try {
				originalRecipes.put(recipeId, UnknownRecipeSchema.SCHEMA.deserialize(SourceLine.UNKNOWN, type, recipeId, json));
			} catch (NullPointerException | IllegalArgumentException | JsonParseException ex2) {
				if (DevProperties.get().logErroringParsedRecipes) {
					ConsoleGY.SERVER.error("Failed to parse recipe %s".formatted(recipeStr), ex2, POST_SKIP_ERROR);
				}
			} catch (Exception ex3) {
				ConsoleGY.SERVER.error("Failed to parse recipe %s".formatted(recipeStr), ex3, POST_SKIP_ERROR);
			}
		}
	}

	private void infoSkip(String s) {
		if (DevProperties.get().logSkippedRecipes) {
			ConsoleGY.SERVER.info(s);
		} else {
			RecipeManager.LOGGER.debug(s);
		}
	}

	private void warnSkip(String s) {
		if (DevProperties.get().logSkippedRecipes) {
			ConsoleGY.SERVER.warn(s);
		} else {
			RecipeManager.LOGGER.warn(s);
		}
	}

	private void errorSkip(String s) {
		if (DevProperties.get().logSkippedRecipes) {
			ConsoleGY.SERVER.error(s);
		} else {
			RecipeManager.LOGGER.error(s);
		}
	}

	@HideFromGY
	public void postEvent() {
		var timer = Stopwatch.createStarted();

		ServerEvents.RECIPES.post(ScriptType.SERVER, this);

		for (var r : originalRecipes.values()) {
			if (r.removed) {
				removedRecipes.add(r);
			} else if (r.hasChanged()) {
				modifiedCount++;
			}
		}

		ConsoleGY.SERVER.info("Posted recipe events in " + TimeGY.msToString(timer.stop().elapsed(TimeUnit.MILLISECONDS)));
	}

	@HideFromGY
	public void applyChanges(Map<Identifier, JsonElement> map) {
		var timer = Stopwatch.createStarted();
		addedRecipes.removeIf(RECIPE_IS_SYNTHETIC);

		map.clear();
		map.putAll(originalRecipes.values().parallelStream()
			.filter(RECIPE_NOT_REMOVED)
			.map(KubeRecipe::serializeChanges)
			.filter(RECIPE_NOT_REMOVED)
			.peek(this::addToExport)
			.collect(Collectors.toConcurrentMap(KubeRecipe::getOrCreateId, recipe -> recipe.json, (a, b) -> b)));

		map.putAll(addedRecipes.parallelStream()
			.filter(RECIPE_NOT_REMOVED)
			.map(KubeRecipe::serializeChanges)
			.filter(RECIPE_NOT_REMOVED)
			.peek(this::addToExport)
			.collect(Collectors.toConcurrentMap(KubeRecipe::getOrCreateId, recipe -> recipe.json, (a, b) -> {
				ConsoleGY.SERVER.warn("KubeGroovy has found two recipes with the same ID in your custom recipes! Picking the last one encountered!");
				ConsoleGY.SERVER.warn("Recipe A JSON: " + a);
				ConsoleGY.SERVER.warn("Recipe B JSON: " + b);
				return b;
			})));

		ConsoleGY.SERVER.info("KubeGroovy modifications to recipe manager finished in %s".formatted(timer.stop()));
	}

	@HideFromGY
	public void finishEvent() {
		var successfulAddedRecipes = addedRecipes.stream().filter(RECIPE_NOT_REMOVED).toList();
		ChangesForChat.recipesAdded = successfulAddedRecipes.size();
		ChangesForChat.recipesModified = modifiedCount;
		ChangesForChat.recipesRemoved = removedRecipes.size();
		ChangesForChat.recipesMs = overallTimer.stop().elapsed(TimeUnit.MILLISECONDS);

		ConsoleGY.SERVER.info("Added %d recipes, removed %d recipes, modified %d recipes, with %d failed recipes taking %s in total".formatted(successfulAddedRecipes.size(), removedRecipes.size(), modifiedCount, failedCount, TimeGY.msToString(ChangesForChat.recipesMs)));

		if (DataExport.export != null) {
			for (var r : removedRecipes) {
				DataExport.export.addJson("removed_recipes/" + r.getId() + ".json", r.json);
			}
		}

		if (DevProperties.get().logRecipeDebug) {
			ConsoleGY.SERVER.info("======== Debug output of all added recipes ========");

			for (var r : successfulAddedRecipes) {
				ConsoleGY.SERVER.info(r.getOrCreateId() + ": " + r.json);
			}

			ConsoleGY.SERVER.info("======== Debug output of all modified recipes ========");

			for (var r : originalRecipes.values()) {
				if (!r.removed && r.hasChanged()) {
					ConsoleGY.SERVER.info(r.getOrCreateId() + ": " + r.json + " FROM " + r.originalJson);
				}
			}

			ConsoleGY.SERVER.info("======== Debug output of all removed recipes ========");

			for (var r : removedRecipes) {
				ConsoleGY.SERVER.info(r.getOrCreateId() + ": " + r.json);
			}
		}

		RegexIDFilter.clearInternCache();
	}

	private void addToExport(KubeRecipe r) {
		var path = r.kgy$getMod() + "/" + r.getPath();
		if (DataExport.export != null) {
			DataExport.export.addJson("recipes/%s.json".formatted(path), r.json);

			if (r.newRecipe) {
				DataExport.export.addJson("added_recipes/%s.json".formatted(path), r.json);
			}
		}
	}

	@HideFromGY
	public void handleFailedRecipe(Identifier id, JsonElement json, Throwable ex) {
		// only handle recipes that failed because of KubeGroovy interfering
		if (json instanceof JsonObject obj && obj.has(KubeRecipe.CHANGED_MARKER)) {
			var sourceLine = SourceLine.fromJson(obj.remove(KubeRecipe.CHANGED_MARKER).getAsJsonObject());
			if (DevProperties.get().logErroringRecipes) {
				ConsoleGY.SERVER.error("Error parsing recipe %s (details below this line)".formatted(id), sourceLine, ex, null);
				ConsoleGY.SERVER.stopCapturingErrors();
				ConsoleGY.SERVER.error("Recipe JSON for %s: %s".formatted(id, json), sourceLine, null, null);
				ConsoleGY.SERVER.startCapturingErrors();
			}
			failedCount++;
		}
	}

	public Map<String, Object> getRecipes() {
		return recipeFunctions;
	}

	public KubeRecipe addRecipe(KubeRecipe r, boolean json) {
		addedRecipes.add(r);

		if (DevProperties.get().logAddedRecipes) {
			ConsoleGY.SERVER.info("+ " + r.kgy$getType() + ": " + r.getFromToString() + (json ? " [json]" : ""));
		} else if (ConsoleGY.SERVER.shouldPrintDebug()) {
			ConsoleGY.SERVER.debug("+ " + r.kgy$getType() + ": " + r.getFromToString() + (json ? " [json]" : ""));
		}

		return r;
	}

	private record RecipeStreamFilter(Context cx, RecipeFilter filter) implements Predicate<KubeRecipe> {
		private RecipeStreamFilter {
			RecipeFilter.rejectClosure(filter);
		}

		@Override
		public boolean test(KubeRecipe r) {
			return r != null && !r.removed && filter.test(new RecipeMatchContext.Impl(r));
		}
	}

	public Stream<KubeRecipe> recipeStream(RecipeFilter filter) {
		var cx = Context.getCurrent();
		if (filter == ConstantFilter.FALSE) {
			return Stream.empty();
		} else if (filter instanceof IDFilter(Identifier id1)) {
			var r = originalRecipes.get(id1);
			return r == null || r.removed ? Stream.empty() : Stream.of(r);
		}

		exit:
		if (filter instanceof OrFilter or) {
			if (or.list.isEmpty()) {
				return Stream.empty();
			}

			for (var recipeFilter : or.list) {
				if (!(recipeFilter instanceof IDFilter)) {
					break exit;
				}
			}

			return or.list.stream().map(idf -> originalRecipes.get(((IDFilter) idf).id())).filter(RECIPE_NOT_REMOVED);
		}

		return originalRecipes.values().stream().filter(new RecipeStreamFilter(cx, filter));
	}

	private <T> T reduceRecipesAsync(Context cx, RecipeFilter filter, Function<Stream<KubeRecipe>, T> function) {
		return function.apply(recipeStream(filter));
	}

	public void forEachRecipe(RecipeFilter filter, Consumer<KubeRecipe> consumer) {
		var cx = Context.getCurrent();
		if (filter instanceof IDFilter(Identifier id1)) {
			var r = originalRecipes.get(id1);

			if (r != null && !r.removed) {
				consumer.accept(r);
			}
		} else {
			recipeStream(filter).forEach(consumer);
		}
	}

	public int countRecipes(RecipeFilter filter) {
		var cx = Context.getCurrent();
		return reduceRecipesAsync(cx, filter, s -> (int) s.count());
	}

	public boolean containsRecipe(RecipeFilter filter) {
		var cx = Context.getCurrent();
		return reduceRecipesAsync(cx, filter, s -> s.findAny().isPresent());
	}

	public Collection<KubeRecipe> findRecipes(RecipeFilter filter) {
		var cx = Context.getCurrent();
		return reduceRecipesAsync(cx, filter, Stream::toList);
	}

	public Collection<Identifier> findRecipeIds(RecipeFilter filter) {
		var cx = Context.getCurrent();
		return reduceRecipesAsync(cx, filter, s -> s.map(KubeRecipe::getOrCreateId).toList());
	}

	public void remove(RecipeFilter filter) {
		var cx = Context.getCurrent();
		forEachRecipe(filter, KubeRecipe::remove);
	}

	public void replaceInput(RecipeFilter filter, ReplacementMatchInfo match, Object with) {
		var cx = Context.getCurrent();
		var dstring = (DevProperties.get().logModifiedRecipes || ConsoleGY.SERVER.shouldPrintDebug()) ? (": IN " + match + " -> " + with) : "";

		forEachRecipe(filter, r -> {
			if (r.replaceInput(new RecipeScriptContext.Impl(r), match, with)) {
				if (DevProperties.get().logModifiedRecipes) {
					ConsoleGY.SERVER.info("~ " + r + dstring);
				} else if (ConsoleGY.SERVER.shouldPrintDebug()) {
					ConsoleGY.SERVER.debug("~ " + r + dstring);
				}
			}
		});
	}

	public void replaceOutput(RecipeFilter filter, ReplacementMatchInfo match, Object with) {
		var cx = Context.getCurrent();
		var dstring = (DevProperties.get().logModifiedRecipes || ConsoleGY.SERVER.shouldPrintDebug()) ? (": OUT " + match + " -> " + with) : "";

		forEachRecipe(filter, r -> {
			if (r.replaceOutput(new RecipeScriptContext.Impl(r), match, with)) {
				if (DevProperties.get().logModifiedRecipes) {
					ConsoleGY.SERVER.info("~ " + r + dstring);
				} else if (ConsoleGY.SERVER.shouldPrintDebug()) {
					ConsoleGY.SERVER.debug("~ " + r + dstring);
				}
			}
		});
	}

	@Nullable
	public RecipeTypeFunction getRecipeFunction(@Nullable String id) {
		if (id == null || id.isEmpty()) {
			return null;
		} else if (recipeFunctions.get(ID.string(id)) instanceof RecipeTypeFunction fn) {
			return fn;
		} else {
			return null;
		}
	}

	public KubeRecipe custom(JsonObject json) {
		var cx = Context.getCurrent();
		return parseJson(json, SourceLine.of()).getPartialOrThrow(KubeRuntimeException::new);
	}

	@HideFromGY
	public DataResult<KubeRecipe> parseJson(JsonObject json, SourceLine sourceLine) {
		if (json == null || !json.has("type")) {
			return DataResult.error(() -> "JSON must contain 'type'!");
		}

		var type = getRecipeFunction(json.get("type").getAsString());

		if (type == null) {
			return DataResult.error(() -> "Unknown recipe type: " + json.get("type").getAsString());
		}

		var stack = new ErrorStack();

		try {
			var recipe = type.schemaType.schema.deserialize(sourceLine, type, null, json);
			recipe.afterLoaded(stack);
			return DataResult.success(addRecipe(recipe, true));
		} catch (Throwable cause) {
			var recipe = type.schemaType.schema.recipeFactory.create(type, sourceLine, true);
			recipe.creationError = true;
			var errorString = "Failed to create custom recipe" + stack.atString() + " from json " + JsonUtils.toString(json);
			ConsoleGY.SERVER.error(errorString, sourceLine, cause, POST_SKIP_ERROR);
			recipe.json = json;
			recipe.newRecipe = true;

			// importantly, we return a partial result here!
			return DataResult.error(() -> errorString, recipe);
		}
	}

	private void printTypes(Predicate<RecipeSchemaType> predicate, boolean all) {
		int t = 0;
		var map = new Reference2ObjectLinkedOpenHashMap<RecipeSchema, Set<Identifier>>();

		for (var ns : recipeSchemaStorage.namespaces.values()) {
			for (var type : ns.values()) {
				if (predicate.test(type)) {
					t++;
					map.computeIfAbsent(type.schema, s -> new LinkedHashSet<>()).add(type.id);
				}
			}
		}

		if (all) {
			ConsoleGY.SERVER.info("- All recipe types");
			ConsoleGY.SERVER.info("  - .id(id)");
			ConsoleGY.SERVER.info("  - .group(string)");
			ConsoleGY.SERVER.info("  - .set(key, value)");
			ConsoleGY.SERVER.info("  - .merge(json)");
			ConsoleGY.SERVER.info("- All crafting table recipe types");
			ConsoleGY.SERVER.info("  - .stage(string)");
			ConsoleGY.SERVER.info("  - .damageIngredient(filter, int?)");
			ConsoleGY.SERVER.info("  - .replaceIngredient(filter, item_stack)");
			ConsoleGY.SERVER.info("  - .customIngredientAction(filter, string)");
			ConsoleGY.SERVER.info("  - .keepIngredient(filter)");
			ConsoleGY.SERVER.info("  - .consumeIngredient(filter)");
			ConsoleGY.SERVER.info("  - .modifyResult(string)");
		}

		for (var entry : map.entrySet()) {
			ConsoleGY.SERVER.info("- " + entry.getValue().stream().map(Identifier::toString).collect(Collectors.joining(", ")));

			for (var c : entry.getKey().constructors().values()) {
				ConsoleGY.SERVER.info("  - " + c);
			}

			for (var key : entry.getKey().keys) {
				var name = key.getPrimaryFunctionName();

				if (RecipeKey.isValidIdentifier(name.toCharArray())) {
					ConsoleGY.SERVER.info("  - ." + name + "(" + key.component + ")");
				}
			}

			for (var f : entry.getKey().functions.values()) {
				if (RecipeKey.isValidIdentifier(f.name().toCharArray())) {
					ConsoleGY.SERVER.info("  - ." + f);
				}
			}
		}

		ConsoleGY.SERVER.info(t + " types");
	}

	public void printTypes() {
		var cx = Context.getCurrent();
		ConsoleGY.SERVER.info("== All recipe types [used] ==");
		var set = reduceRecipesAsync(cx, ConstantFilter.TRUE, s -> s.map(r -> r.type.id).collect(Collectors.toSet()));
		printTypes(t -> set.contains(t.id), false);
	}

	public void printAllTypes() {
		ConsoleGY.SERVER.info("== All recipe types [available] ==");
		printTypes(t -> BuiltInRegistries.RECIPE_SERIALIZER.containsKey(t.id), true);
	}

	public void printExamples(String type) {
		var list = originalRecipes.values().stream().filter(recipeGY -> recipeGY.type.toString().equals(type)).collect(Collectors.toList());
		Collections.shuffle(list);

		ConsoleGY.SERVER.info("== Random examples of '" + type + "' ==");

		for (var i = 0; i < Math.min(list.size(), 5); i++) {
			var r = list.get(i);
			ConsoleGY.SERVER.info("- " + r.getOrCreateId() + ":\n" + JsonIO.toPrettyString(r.json));
		}
	}

	public synchronized Identifier takeId(KubeRecipe recipe, String prefix, String ids) {
		int i = 2;
		var id = Identifier.parse(prefix + ids);

		while (takenIds.containsKey(id)) {
			id = Identifier.parse(prefix + ids + '_' + i);
			i++;
		}

		takenIds.put(id, recipe);
		return id;
	}

	public void stage(RecipeFilter filter, String stage) {
		var cx = Context.getCurrent();
		forEachRecipe(filter, r -> r.stage(stage));
	}
}
