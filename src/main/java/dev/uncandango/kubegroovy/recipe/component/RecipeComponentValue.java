package dev.uncandango.kubegroovy.recipe.component;

import dev.uncandango.kubegroovy.error.InvalidRecipeComponentException;
import dev.uncandango.kubegroovy.error.MissingRequiredValueException;
import dev.uncandango.kubegroovy.recipe.RecipeKey;
import dev.uncandango.kubegroovy.recipe.RecipeScriptContext;
import dev.uncandango.kubegroovy.recipe.filter.RecipeMatchContext;
import dev.uncandango.kubegroovy.recipe.match.ReplacementMatchInfo;
import dev.uncandango.kubegroovy.script.SourceLine;
import dev.uncandango.kubegroovy.util.WrappedGY;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

public final class RecipeComponentValue<T> implements WrappedGY, Map.Entry<RecipeKey<T>, T> {
	public static final RecipeComponentValue<?>[] EMPTY_ARRAY = new RecipeComponentValue[0];

	public final RecipeKey<T> key;
	public final int index;
	public @Nullable T value;
	public boolean write;

	public RecipeComponentValue(RecipeKey<T> key, int index) {
		this.key = key;
		this.index = index;
		this.value = null;
		this.write = false;
	}

	public RecipeComponentValue<T> copy() {
		var copy = new RecipeComponentValue<>(key, index);
		copy.value = value;
		copy.write = write;
		return copy;
	}

	public boolean matches(RecipeMatchContext cx, ReplacementMatchInfo match) {
		return value != null && (match.componentType().isEmpty() || key.component.equals(match.componentType().get())) && key.component.matches(cx, value, match);
	}

	public boolean replace(RecipeScriptContext cx, ReplacementMatchInfo match, Object with) {
		T newValue = value == null ? null : key.component.replace(cx, value, match, with);

		if (value != newValue) {
			value = newValue;
			write();
			return true;
		}

		return false;
	}

	@Override
	public RecipeKey<T> getKey() {
		return key;
	}

	public int getIndex() {
		return index;
	}

	@Override
	public T getValue() {
		return value;
	}

	@Override
	public T setValue(T newValue) {
		var v = value;
		value = newValue;
		return v;
	}

	public boolean shouldWrite() {
		return write;
	}

	public void write() {
		write = true;
	}

	@Override
	public String toString() {
		return "%s = %s".formatted(key.name, value);
	}

	@Override
	public boolean equals(Object obj) {
		return obj == this || obj instanceof Map.Entry<?, ?> e && key == e.getKey() && Objects.equals(value, e.getValue());
	}

	@Override
	public int hashCode() {
		return Objects.hash(key, value);
	}

	public void validate(RecipeValidationContext ctx, SourceLine sourceLine) {
		if (value != null) {
			try {
				key.component.validate(ctx, value);
			} catch (Throwable cause) {
				throw new InvalidRecipeComponentException(this, cause).source(sourceLine);
			}
		} else if (!key.optional()) {
			throw new InvalidRecipeComponentException(this, new MissingRequiredValueException()).source(sourceLine);
		}
	}
}
