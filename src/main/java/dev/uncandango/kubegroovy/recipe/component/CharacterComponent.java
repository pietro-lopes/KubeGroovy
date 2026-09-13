package dev.uncandango.kubegroovy.recipe.component;

import com.google.gson.JsonPrimitive;
import dev.uncandango.kubegroovy.codec.KubeGroovyCodecs;
import dev.uncandango.kubegroovy.recipe.RecipeScriptContext;
import dev.uncandango.kubegroovy.recipe.filter.RecipeMatchContext;
import dev.uncandango.kubegroovy.util.OpsContainer;
import dev.uncandango.kubegroovy.script.bridge.TypeInfo;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

public class CharacterComponent extends SimpleRecipeComponent<Character> {
	public static final CharacterComponent CHARACTER = new CharacterComponent(RecipeComponentType.builtin("character"));

	public CharacterComponent(ResourceKey<RecipeComponentType<?>> type) {
		super(type, KubeGroovyCodecs.CHARACTER, TypeInfo.CHARACTER);
	}

	@Override
	public boolean hasPriority(RecipeMatchContext cx, @Nullable Object from) {
		return from instanceof Character || from instanceof CharSequence || from instanceof JsonPrimitive json && json.isString();
	}

	@Override
	public Character wrap(RecipeScriptContext cx, Object from) {
		// hack: Rhino stores numeric object keys like '0' as integer index properties
		return from instanceof Number ? String.valueOf(from).charAt(0) : super.wrap(cx, from);
	}

	@Override
	public boolean isEmpty(Character value) {
		return value == '\0';
	}

	@Override
	public String toString(OpsContainer ops, Character value) {
		return "'" + value + "'";
	}
}
