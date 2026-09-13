package dev.uncandango.kubegroovy.item.creativetab;

import dev.uncandango.kubegroovy.item.ItemPredicate;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

public interface CreativeTabCallback {
	void addAfter(ItemStack order, ItemStack[] items, CreativeModeTab.TabVisibility visibility);

	void addBefore(ItemStack order, ItemStack[] items, CreativeModeTab.TabVisibility visibility);

	void remove(ItemPredicate filter, boolean removeParent, boolean removeSearch);
}