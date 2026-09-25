package dev.uncandango.kubegroovy.integration.rei;
/*

import dev.uncandango.kubegroovy.recipe.viewer.RecipeViewerEntryType;
import dev.uncandango.kubegroovy.recipe.viewer.RemoveEntriesKubeEvent;
import dev.uncandango.kubegroovy.script.bridge.Context;
import me.shedaniel.rei.api.client.entry.filtering.base.BasicFilteringRule;
import me.shedaniel.rei.api.common.entry.EntryStack;

import java.util.List;
import java.util.function.Predicate;

public class REIRemoveEntriesCompletelyKubeEvent implements RemoveEntriesKubeEvent {
	private final RecipeViewerEntryType type;
	private final List<EntryStack<?>> allEntries;
	private final BasicFilteringRule<?> rule;

	public REIRemoveEntriesCompletelyKubeEvent(RecipeViewerEntryType type, List<EntryStack<?>> allEntries, BasicFilteringRule<?> rule) {
		this.type = type;
		this.allEntries = allEntries;
		this.rule = rule;
	}

	@Override
	public void remove(Object filter) {
		var cx = Context.getCurrent();
		var predicate = (Predicate) type.wrapPredicate(filter);
		rule.hide(allEntries.stream().filter(e -> predicate.test(e.getValue())).toList());
	}
}*/
