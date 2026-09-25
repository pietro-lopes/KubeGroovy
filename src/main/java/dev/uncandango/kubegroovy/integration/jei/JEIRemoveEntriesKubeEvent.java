package dev.uncandango.kubegroovy.integration.jei;

import dev.uncandango.kubegroovy.event.EventResult;
import dev.uncandango.kubegroovy.recipe.viewer.RecipeViewerEntryType;
import dev.uncandango.kubegroovy.recipe.viewer.RemoveEntriesKubeEvent;
import dev.uncandango.kubegroovy.script.bridge.Context;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.runtime.IJeiRuntime;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.function.Predicate;

public class JEIRemoveEntriesKubeEvent implements RemoveEntriesKubeEvent {
	private final IJeiRuntime runtime;
	private final RecipeViewerEntryType type;
	private final IIngredientType ingredientType;
	private final Collection<Object> hidden;
	private final List<Object> allIngredients;

	public JEIRemoveEntriesKubeEvent(IJeiRuntime r, RecipeViewerEntryType type, IIngredientType<?> t) {
		this.runtime = r;
		this.type = type;
		this.ingredientType = t;
		this.hidden = new HashSet<>();
		this.allIngredients = List.copyOf(runtime.getIngredientManager().getAllIngredients(ingredientType));
	}

	@Override
	public void remove(Object filter) {
		var cx = Context.getCurrent();
		var predicate = (Predicate) type.wrapPredicate(filter);

		for (var value : allIngredients) {
			if (predicate.test(value)) {
				hidden.add(value);
			}
		}
	}

	@Override
	public void afterPosted(EventResult result) {
		if (!hidden.isEmpty()) {
			runtime.getIngredientManager().removeIngredientsAtRuntime(ingredientType, hidden);
		}
	}
}