package dev.uncandango.kubegroovy.recipe;

import com.google.gson.JsonObject;
import dev.uncandango.kubegroovy.error.KubeRuntimeException;
import dev.uncandango.kubegroovy.recipe.component.ComponentValueMap;
import dev.uncandango.kubegroovy.recipe.schema.RecipeSchemaType;
import dev.uncandango.kubegroovy.script.ConsoleGY;
import dev.uncandango.kubegroovy.script.SourceLine;
import dev.uncandango.kubegroovy.script.annotations.HideFromGY;
import dev.uncandango.kubegroovy.util.ErrorStack;
import dev.uncandango.kubegroovy.util.JsonUtils;
import dev.uncandango.kubegroovy.util.WrappedGY;
import dev.uncandango.kubegroovy.script.bridge.Context;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.RecipeSerializer;

import java.util.Arrays;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class RecipeTypeFunction implements WrappedGY {
	public static final Pattern SKIP_ERROR = ConsoleGY.methodPattern(RecipeTypeFunction.class, "call");

	public final RecipesKubeEvent event;
	public final ResourceKey<RecipeSerializer<?>> serializerKey;
	public final Identifier id;
	public final String idString;
	public final RecipeSchemaType schemaType;

	public RecipeTypeFunction(RecipesKubeEvent event, RecipeSchemaType schemaType) {
		this.event = event;
		this.serializerKey = schemaType.serializerKey;
		this.id = schemaType.id;
		this.idString = id.toString();
		this.schemaType = schemaType;
	}

	public Object call(Object... args0) {
		var sourceLine = SourceLine.of();
		var stack = new ErrorStack();

		try {
			return createRecipe(sourceLine, stack, args0);
		} catch (Throwable cause) {
			var r = schemaType.schema.recipeFactory.create(this, sourceLine, true);
			r.creationError = true;
			event.failedCount++;
			ConsoleGY.SERVER.error("Failed to create a '" + idString + "' recipe" + stack.atString() + " from args " + Arrays.toString(args0), sourceLine, cause, SKIP_ERROR);
			r.json = new JsonObject();
			r.json.addProperty("type", idString);
			r.newRecipe = true;
			return r;
		}
	}

	public KubeRecipe createRecipe(SourceLine sourceLine, ErrorStack stack, Object[] args) {
		var cx = Context.getCurrent();
		try {
			schemaType.getSerializer();

			var constructor = schemaType.schema.constructors().get(args.length);

			if (constructor == null) {
				if (args.length == 1 && (args[0] instanceof Map<?, ?> || args[0] instanceof JsonObject)) {
					var recipe = schemaType.schema.deserialize(sourceLine, this, null, JsonUtils.objectOf(args[0]));
					recipe.afterLoaded(stack);
					return event.addRecipe(recipe, true);
					// throw new RecipeExceptionGY("Use event.custom(json) for json recipes!");
				}

				throw new KubeRuntimeException("Constructor for " + id + " with " + args.length + " arguments not found!").source(sourceLine);
			}

			/*
			if (args1 == null || args1.isEmpty()) {
				throw new RecipeExceptionGY("Recipe requires at least one argument!");
			} else if (schemaType.schema.keys.length == 0 && args1.size() != 1) {
				throw new RecipeExceptionGY("Custom recipe has to use a single json object argument!");
			}
			 */

			var argMap = new ComponentValueMap(args.length);
			int index = 0;

			for (var key : constructor.keys) {
				argMap.put(key, args[index++]);
			}

			var recipe = constructor.create(sourceLine, this, schemaType, argMap);
			recipe.afterLoaded(stack);
			return event.addRecipe(recipe, false);
		} catch (KubeRuntimeException rex) {
			throw rex.source(sourceLine);
		} catch (Throwable ex) {
			throw new KubeRuntimeException("Failed to create a recipe for type '" + id + "'" + stack.atString() + " with args " + Arrays.stream(args).map(o -> o == null ? "null" : (o + ": " + o.getClass().getSimpleName())).collect(Collectors.joining(", ", "[", "]")), ex).source(sourceLine);
		}
	}

	@Override
	public String toString() {
		return idString;
	}

	@Override
	public int hashCode() {
		return idString.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		return idString.equals(obj.toString());
	}
}
