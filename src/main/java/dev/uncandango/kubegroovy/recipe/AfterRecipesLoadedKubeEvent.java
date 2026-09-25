package dev.uncandango.kubegroovy.recipe;

import dev.uncandango.kubegroovy.DevProperties;
import dev.uncandango.kubegroovy.core.RecipeLikeKGY;
import dev.uncandango.kubegroovy.core.RecipeManagerKGY;
import dev.uncandango.kubegroovy.event.EventResult;
import dev.uncandango.kubegroovy.event.KubeEvent;
import dev.uncandango.kubegroovy.recipe.filter.ConstantFilter;
import dev.uncandango.kubegroovy.recipe.filter.RecipeFilter;
import dev.uncandango.kubegroovy.recipe.filter.RecipeMatchContext;
import dev.uncandango.kubegroovy.script.ConsoleGY;
import dev.uncandango.kubegroovy.util.RegistryAccessContainer;
import dev.uncandango.kubegroovy.util.RegistryOpsContainer;
import dev.uncandango.kubegroovy.script.bridge.Context;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class AfterRecipesLoadedKubeEvent implements KubeEvent {
	private final RecipeManagerKGY recipeManager;
	private final RegistryAccessContainer registries;
	private @Nullable List<RecipeLikeKGY> originalRecipes;
	private boolean changed;

	public AfterRecipesLoadedKubeEvent(ReloadableServerResources resources) {
		this.recipeManager = resources.getRecipeManager();
		this.registries = resources.kgy$getServerScriptManager().getRegistries();
		this.originalRecipes = null;
		this.changed = false;
	}

	private List<RecipeLikeKGY> getOriginalRecipes() {
		if (originalRecipes == null) {
			originalRecipes = new ArrayList<>(recipeManager.kgy$getRecipes().stream()
				.map(r -> (RecipeLikeKGY) r)
				.toList());
		}
		return originalRecipes;
	}

	public void forEachRecipe(RecipeFilter filter, Consumer<RecipeLikeKGY> consumer) {
		var cx = Context.getCurrent();
		if (filter == ConstantFilter.TRUE) {
			getOriginalRecipes().forEach(consumer);
		} else if (filter != ConstantFilter.FALSE) {
			getOriginalRecipes().stream().filter(r -> filter.test(new RecipeMatchContext.Impl(registries, registries, r))).forEach(consumer);
		}
	}

	public int countRecipes(RecipeFilter filter) {
		var cx = Context.getCurrent();
		if (filter == ConstantFilter.TRUE) {
			return getOriginalRecipes().size();
		} else if (filter != ConstantFilter.FALSE) {
			return (int) getOriginalRecipes().stream().filter(r -> filter.test(new RecipeMatchContext.Impl(registries, registries, r))).count();
		}

		return 0;
	}

	public int remove(RecipeFilter filter) {
		var cx = Context.getCurrent();
		int count = 0;
		var itr = getOriginalRecipes().iterator();

		while (itr.hasNext()) {
			var r = itr.next();

			if (filter.test(new RecipeMatchContext.Impl(registries, registries, r))) {
				itr.remove();
				count++;
				changed = true;

				if (DevProperties.get().logRemovedRecipes) {
					ConsoleGY.SERVER.info("- " + r);
				} else if (ConsoleGY.SERVER.shouldPrintDebug()) {
					ConsoleGY.SERVER.debug("- " + r);
				}
			}
		}

		return count;
	}

	@Override
	public void afterPosted(EventResult result) {
		if (changed) {
			var holders = new ArrayList<RecipeHolder<?>>();
			for (var r : getOriginalRecipes()) {
				holders.add((RecipeHolder<?>) r);
			}
			recipeManager.kgy$replaceRecipes(RecipeMap.create(holders));
		}
	}
}
