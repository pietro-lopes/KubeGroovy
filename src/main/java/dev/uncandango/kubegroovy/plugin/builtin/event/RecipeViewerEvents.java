package dev.uncandango.kubegroovy.plugin.builtin.event;

import dev.uncandango.kubegroovy.event.EventGroup;
import dev.uncandango.kubegroovy.event.EventHandler;
import dev.uncandango.kubegroovy.event.EventTargetType;
import dev.uncandango.kubegroovy.event.TargetedEventHandler;
import dev.uncandango.kubegroovy.recipe.viewer.AddEntriesKubeEvent;
import dev.uncandango.kubegroovy.recipe.viewer.AddInformationKubeEvent;
import dev.uncandango.kubegroovy.recipe.viewer.GroupEntriesKubeEvent;
import dev.uncandango.kubegroovy.recipe.viewer.RecipeViewerEntryType;
import dev.uncandango.kubegroovy.recipe.viewer.RegisterSubtypesKubeEvent;
import dev.uncandango.kubegroovy.recipe.viewer.RemoveCategoriesKubeEvent;
import dev.uncandango.kubegroovy.recipe.viewer.RemoveEntriesKubeEvent;
import dev.uncandango.kubegroovy.recipe.viewer.RemoveRecipesKubeEvent;

/// Events for recipe viewer integrations (EMI, JEI, REI).
/// Handlers fire for all entry types by default, but you may use a [RecipeViewerEntryType] target to filter for only item / fluid / etc. entries.
public interface RecipeViewerEvents {
	EventGroup GROUP = EventGroup.of("RecipeViewerEvents");
	EventTargetType<RecipeViewerEntryType> TARGET = EventTargetType.create(RecipeViewerEntryType.class).transformer(RecipeViewerEntryType::fromString).identity();

	TargetedEventHandler<RecipeViewerEntryType> ADD_ENTRIES = GROUP.common("addEntries", () -> AddEntriesKubeEvent.class).requiredTarget(TARGET);
	TargetedEventHandler<RecipeViewerEntryType> REMOVE_ENTRIES = GROUP.common("removeEntries", () -> RemoveEntriesKubeEvent.class).requiredTarget(TARGET);
	TargetedEventHandler<RecipeViewerEntryType> REMOVE_ENTRIES_COMPLETELY = GROUP.common("removeEntriesCompletely", () -> RemoveEntriesKubeEvent.class).requiredTarget(TARGET);
	TargetedEventHandler<RecipeViewerEntryType> GROUP_ENTRIES = GROUP.common("groupEntries", () -> GroupEntriesKubeEvent.class).requiredTarget(TARGET);
	TargetedEventHandler<RecipeViewerEntryType> ADD_INFORMATION = GROUP.common("addInformation", () -> AddInformationKubeEvent.class).requiredTarget(TARGET);
	TargetedEventHandler<RecipeViewerEntryType> REGISTER_SUBTYPES = GROUP.common("registerSubtypes", () -> RegisterSubtypesKubeEvent.class).requiredTarget(TARGET);
	EventHandler REMOVE_CATEGORIES = GROUP.common("removeCategories", () -> RemoveCategoriesKubeEvent.class);
	EventHandler REMOVE_RECIPES = GROUP.common("removeRecipes", () -> RemoveRecipesKubeEvent.class);
}
