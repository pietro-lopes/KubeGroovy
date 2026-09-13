package dev.uncandango.kubegroovy.item.creativetab;

import net.minecraft.network.chat.Component;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import org.jspecify.annotations.Nullable;

@FunctionalInterface
public interface CreativeTabContentSupplier {
	record Wrapper(@Nullable CreativeTabContentSupplier supplier) implements CreativeModeTab.DisplayItemsGenerator {
		@Override
		public void accept(CreativeModeTab.ItemDisplayParameters itemDisplayParameters, CreativeModeTab.Output output) {
			if (supplier == null) {
				var is = Items.PAPER.getDefaultInstance();
				is.kgy$setCustomName(Component.literal("Use .content(showRestrictedItems => ['kubegroovy:example']) to add more items!"));
				output.accept(is);
			} else {
				// todo again, context map?
				supplier.getContent(itemDisplayParameters.hasPermissions())
					.display()
					.resolve(ContextMap.EMPTY, SlotDisplay.ItemStackContentsFactory.INSTANCE)
					.forEach(output::accept);
			}
		}
	}

	Ingredient getContent(boolean showRestrictedItems);
}
