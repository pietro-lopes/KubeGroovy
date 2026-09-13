package dev.uncandango.kubegroovy.recipe.filter;

import dev.uncandango.kubegroovy.recipe.match.ReplacementMatchInfo;

public record InputFilter(ReplacementMatchInfo match) implements RecipeFilter {
	@Override
	public boolean test(RecipeMatchContext cx) {
		return cx.recipe().hasInput(cx, match);
	}

	@Override
	public String toString() {
		return "InputFilter{" + match + '}';
	}
}
