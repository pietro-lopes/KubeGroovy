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

	static ReplacementMatch wrap(@Nullable Object o) {
		var cx = Context.getCurrent();
		return switch (o) {
			case null -> NONE;
			case ReplacementMatch m -> m;
			default -> {
				var in = IngredientWrapper.wrap(o);
				yield in.isEmpty() ? NONE : in;
			}
		};
	}
}
