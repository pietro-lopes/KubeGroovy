package dev.uncandango.kubegroovy.item.creativetab;

import dev.uncandango.kubegroovy.core.CreativeModeTabKGY;
import dev.uncandango.kubegroovy.event.KubeEvent;
import dev.uncandango.kubegroovy.item.ItemPredicate;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

public class CreativeTabKubeEvent implements KubeEvent {
	public final CreativeModeTab tab;
	public final boolean showRestrictedItems;
	private final CreativeTabCallback callback;

	public CreativeTabKubeEvent(CreativeModeTab tab, boolean showRestrictedItems, CreativeTabCallback callback) {
		this.tab = tab;
		this.showRestrictedItems = showRestrictedItems;
		this.callback = callback;
	}

	public void setDisplayName(Component component) {
		((CreativeModeTabKGY) tab).kgy$setDisplayName(component);
	}

	public void setIcon(ItemStack icon) {
		((CreativeModeTabKGY) tab).kgy$setIcon(icon);
	}

	public void addAfter(ItemStack order, ItemStack[] items, CreativeModeTab.TabVisibility visibility) {
		callback.addAfter(order, items, visibility);
	}

	public void addAfter(ItemStack order, ItemStack[] items) {
		callback.addAfter(order, items, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
	}

	public void add(ItemStack[] items, CreativeModeTab.TabVisibility visibility) {
		callback.addAfter(ItemStack.EMPTY, items, visibility);
	}

	public void add(ItemStack[] items) {
		callback.addAfter(ItemStack.EMPTY, items, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
	}

	public void addBefore(ItemStack order, ItemStack[] items, CreativeModeTab.TabVisibility visibility) {
		callback.addBefore(order, items, visibility);
	}

	public void addBefore(ItemStack order, ItemStack[] items) {
		callback.addBefore(order, items, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
	}

	public void removeFromParent(ItemPredicate filter) {
		callback.remove(filter, true, false);
	}

	public void removeFromSearch(ItemPredicate filter) {
		callback.remove(filter, false, true);
	}

	public void remove(ItemPredicate filter) {
		callback.remove(filter, true, true);
	}
}
