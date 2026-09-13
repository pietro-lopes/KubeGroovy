package dev.uncandango.kubegroovy.recipe.filter;

public record NotFilter(RecipeFilter original) implements RecipeFilter {
	@Override
	public boolean test(RecipeMatchContext cx) {
		return !original.test(cx);
	}

	@Override
	public String toString() {
		return "NotFilter{" + original + '}';
	}
}
