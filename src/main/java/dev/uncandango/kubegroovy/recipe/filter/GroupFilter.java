package dev.uncandango.kubegroovy.recipe.filter;

public class GroupFilter implements RecipeFilter {
	private final String group;

	public GroupFilter(String g) {
		group = g;
	}

	@Override
	public boolean test(RecipeMatchContext cx) {
		return cx.recipe().kgy$getGroup().equals(group);
	}

	@Override
	public String toString() {
		return "GroupFilter{" +
			"group='" + group + '\'' +
			'}';
	}
}
