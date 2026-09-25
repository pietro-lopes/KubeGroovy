package dev.uncandango.kubegroovy.integration.rei;
/*

import dev.uncandango.kubegroovy.item.ItemPredicate;
import dev.uncandango.kubegroovy.recipe.viewer.RecipeViewerEntryType;
import dev.uncandango.kubegroovy.recipe.viewer.RegisterSubtypesKubeEvent;
import dev.uncandango.kubegroovy.recipe.viewer.SubtypeInterpreter;
import dev.uncandango.kubegroovy.script.bridge.Context;
import me.shedaniel.rei.api.common.entry.comparison.EntryComparator;
import me.shedaniel.rei.api.common.entry.comparison.ItemComparatorRegistry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.Item;

import java.util.List;

public class REIRegisterItemSubtypesKubeEvent implements RegisterSubtypesKubeEvent {
	private final ItemComparatorRegistry registry;

	public REIRegisterItemSubtypesKubeEvent(ItemComparatorRegistry registry) {
		this.registry = registry;
	}

	@Override
	public void register(Object filter, SubtypeInterpreter interpreter) {
		var cx = Context.getCurrent();
		var in = (ItemPredicate) RecipeViewerEntryType.ITEM.wrapPredicate(filter);
		registry.register((ctx, stack) -> {
			var result = interpreter.apply(stack);

			if (result == null) {
				return 0L;
			} else if (result instanceof Number n) {
				return Double.doubleToLongBits(n.doubleValue());
			} else {
				return result.hashCode();
			}
		}, in.kgy$getItemTypes().toArray(new Item[0]));
	}

	@Override
	public void useComponents(Object filter, List<DataComponentType<?>> components) {
		var cx = Context.getCurrent();
		var in = (ItemPredicate) RecipeViewerEntryType.ITEM.wrapPredicate(filter);
		registry.register((EntryComparator) DataComponentComparator.of(components), in.kgy$getItemTypes().toArray(new Item[0]));
	}
}
*/
