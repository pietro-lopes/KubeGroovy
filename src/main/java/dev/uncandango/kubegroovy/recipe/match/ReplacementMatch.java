package dev.uncandango.kubegroovy.recipe.match;

import dev.uncandango.kubegroovy.plugin.builtin.wrapper.IngredientWrapper;
import dev.uncandango.kubegroovy.script.bridge.Context;
import org.jspecify.annotations.Nullable;

public interface ReplacementMatch {
	ReplacementMatch NONE = new ReplacementMatch() {
		@Override
		public String toString() {
			return "NONE";
		}
	};

	static ReplacementMatch wrap(Context cx, @Nullable Object o) {
		return switch (o) {
			case null -> NONE;
			case ReplacementMatch m -> m;
			default -> {
				var in = IngredientWrapper.wrap(cx, o);
				yield in.isEmpty() ? NONE : in;
			}
		};
	}
}
