package dev.uncandango.kubegroovy.core;

import dev.uncandango.kubegroovy.item.ItemBehavior;
import dev.uncandango.kubegroovy.script.annotations.RemapForGY;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStackTemplate;
import org.jspecify.annotations.Nullable;

import java.util.Map;

@RemapPrefixForGY("kgy$")
public interface ItemKGY extends IngredientSupplierKGY, RegistryObjectKGY<Item> {
	@RemapForGY("getItem")
	default Item kgy$self() {
		return (Item) this;
	}

	default DataComponentMap kgy$getComponents() {
		return this.kgy$self().components();
	}

	@Override
	default ResourceKey<Registry<Item>> kgy$getRegistryId() {
		return Registries.ITEM;
	}

	@Override
	default Registry<Item> kgy$getRegistry() {
		return BuiltInRegistries.ITEM;
	}

	@Nullable
	default ItemBehavior kgy$getItemBehavior() {
		throw new NoMixinException();
	}

	default void kgy$setItemBehavior(ItemBehavior b) {
		throw new NoMixinException();
	}

	default Map<String, Object> kgy$getTypeData() {
		throw new NoMixinException();
	}

	default void kgy$setCraftingRemainder(ItemStackTemplate i) {
		throw new NoMixinException();
	}

	default void kgy$setNameKey(String key) {
		throw new NoMixinException();
	}

	default void kgy$setCanRepair(boolean repairable) {
		throw new NoMixinException();
	}
}
