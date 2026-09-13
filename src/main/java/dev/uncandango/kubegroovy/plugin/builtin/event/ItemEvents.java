package dev.uncandango.kubegroovy.plugin.builtin.event;

import dev.uncandango.kubegroovy.event.EventGroup;
import dev.uncandango.kubegroovy.event.EventHandler;
import dev.uncandango.kubegroovy.event.EventTargetType;
import dev.uncandango.kubegroovy.event.TargetedEventHandler;
import dev.uncandango.kubegroovy.item.DynamicItemTooltipsKubeEvent;
import dev.uncandango.kubegroovy.item.FoodEatenKubeEvent;
import dev.uncandango.kubegroovy.item.ItemClickedKubeEvent;
import dev.uncandango.kubegroovy.item.ItemCraftedKubeEvent;
import dev.uncandango.kubegroovy.item.ItemDestroyedKubeEvent;
import dev.uncandango.kubegroovy.item.ItemDroppedKubeEvent;
import dev.uncandango.kubegroovy.item.ItemEntityInteractedKubeEvent;
import dev.uncandango.kubegroovy.item.ItemModelPropertiesKubeEvent;
import dev.uncandango.kubegroovy.item.ItemModificationKubeEvent;
import dev.uncandango.kubegroovy.item.ItemPickedUpKubeEvent;
import dev.uncandango.kubegroovy.item.ItemSmeltedKubeEvent;
import dev.uncandango.kubegroovy.item.ModifyItemTooltipsKubeEvent;
import dev.uncandango.kubegroovy.item.custom.ItemToolMaterialRegistryKubeEvent;
import dev.uncandango.kubegroovy.plugin.builtin.wrapper.ItemWrapper;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

public interface ItemEvents {
	EventGroup GROUP = EventGroup.of("ItemEvents");
	EventTargetType<ResourceKey<Item>> TARGET = EventTargetType.registryKey(Registries.ITEM, Item.class);

	EventHandler MODIFICATION = GROUP.startup("modification", () -> ItemModificationKubeEvent.class);
	EventHandler TOOL_MATERIAL_REGISTRY = GROUP.startup("toolTierRegistry", () -> ItemToolMaterialRegistryKubeEvent.class);
	TargetedEventHandler<ResourceKey<Item>> RIGHT_CLICKED = GROUP.common("rightClicked", () -> ItemClickedKubeEvent.class).hasResult(ItemWrapper.TYPE_INFO).supportsTarget(TARGET);
	TargetedEventHandler<ResourceKey<Item>> CAN_PICK_UP = GROUP.common("canPickUp", () -> ItemPickedUpKubeEvent.class).hasResult().supportsTarget(TARGET);
	TargetedEventHandler<ResourceKey<Item>> PICKED_UP = GROUP.common("pickedUp", () -> ItemPickedUpKubeEvent.class).supportsTarget(TARGET);
	TargetedEventHandler<ResourceKey<Item>> DROPPED = GROUP.common("dropped", () -> ItemDroppedKubeEvent.class).hasResult().supportsTarget(TARGET);
	TargetedEventHandler<ResourceKey<Item>> ENTITY_INTERACTED = GROUP.common("entityInteracted", () -> ItemEntityInteractedKubeEvent.class).hasResult().supportsTarget(TARGET);
	TargetedEventHandler<ResourceKey<Item>> CRAFTED = GROUP.common("crafted", () -> ItemCraftedKubeEvent.class).supportsTarget(TARGET);
	TargetedEventHandler<ResourceKey<Item>> SMELTED = GROUP.common("smelted", () -> ItemSmeltedKubeEvent.class).supportsTarget(TARGET);
	TargetedEventHandler<ResourceKey<Item>> FOOD_EATEN = GROUP.common("foodEaten", () -> FoodEatenKubeEvent.class).hasResult().supportsTarget(TARGET);
	EventHandler MODIFY_TOOLTIPS = GROUP.common("modifyTooltips", () -> ModifyItemTooltipsKubeEvent.class);
	TargetedEventHandler<String> DYNAMIC_TOOLTIPS = GROUP.client("dynamicTooltips", () -> DynamicItemTooltipsKubeEvent.class).requiredTarget(EventTargetType.STRING);
	EventHandler MODEL_PROPERTIES = GROUP.startup("modelProperties", () -> ItemModelPropertiesKubeEvent.class);
	TargetedEventHandler<ResourceKey<Item>> FIRST_RIGHT_CLICKED = GROUP.common("firstRightClicked", () -> ItemClickedKubeEvent.class).supportsTarget(TARGET);
	TargetedEventHandler<ResourceKey<Item>> FIRST_LEFT_CLICKED = GROUP.common("firstLeftClicked", () -> ItemClickedKubeEvent.class).supportsTarget(TARGET);
	TargetedEventHandler<ResourceKey<Item>> ITEM_DESTROYED = GROUP.common("destroyed", () -> ItemDestroyedKubeEvent.class).supportsTarget(TARGET);
}
