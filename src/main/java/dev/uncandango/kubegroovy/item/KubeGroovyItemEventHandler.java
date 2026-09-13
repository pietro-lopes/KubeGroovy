package dev.uncandango.kubegroovy.item;

import dev.uncandango.kubegroovy.KubeGroovy;
import dev.uncandango.kubegroovy.player.InventoryChangedKubeEvent;
import dev.uncandango.kubegroovy.plugin.builtin.event.ItemEvents;
import dev.uncandango.kubegroovy.plugin.builtin.event.PlayerEvents;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerDestroyItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = KubeGroovy.MOD_ID)
public class KubeGroovyItemEventHandler {
	@SubscribeEvent
	public static void rightClick(PlayerInteractEvent.RightClickItem event) {
		var stack = event.getItemStack();
		var key = stack.getItem().kgy$getKey();

		if (ItemEvents.RIGHT_CLICKED.hasListeners(key) && !event.getEntity().getCooldowns().isOnCooldown(stack)) {
			ItemEvents.RIGHT_CLICKED.post(event.getEntity(), key, new ItemClickedKubeEvent(event.getEntity(), event.getHand(), stack)).applyCancel(event);
		}
	}

	@SubscribeEvent
	public static void leftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
		// TODO: Implement me
	}

	@SubscribeEvent
	public static void itemPickupPre(ItemEntityPickupEvent.Pre event) {
		var key = event.getItemEntity().getItem().getItem().kgy$getKey();

		if (ItemEvents.CAN_PICK_UP.hasListeners(key)) {
			ItemEvents.CAN_PICK_UP.post(event.getPlayer(), key, new ItemPickedUpKubeEvent(event.getPlayer(), event.getItemEntity(), event.getItemEntity().getItem())).applyTristate(event::setCanPickup);
		}
	}

	@SubscribeEvent
	public static void itemPickupPost(ItemEntityPickupEvent.Post event) {
		var key = event.getCurrentStack().getItem().kgy$getKey();

		if (ItemEvents.PICKED_UP.hasListeners(key)) {
			ItemEvents.PICKED_UP.post(event.getPlayer(), key, new ItemPickedUpKubeEvent(event.getPlayer(), event.getItemEntity(), event.getCurrentStack()));
		}
	}

	@SubscribeEvent
	public static void itemDrop(ItemTossEvent event) {
		var key = event.getEntity().getItem().getItem().kgy$getKey();

		if (ItemEvents.DROPPED.hasListeners(key)) {
			ItemEvents.DROPPED.post(event.getPlayer(), key, new ItemDroppedKubeEvent(event.getPlayer(), event.getEntity())).applyCancel(event);
		}
	}

	@SubscribeEvent
	public static void entityInteract(PlayerInteractEvent.EntityInteract event) {
		var stack = event.getItemStack();
		var key = stack.getItem().kgy$getKey();

		if (ItemEvents.ENTITY_INTERACTED.hasListeners(key)) {
			ItemEvents.ENTITY_INTERACTED.post(event.getEntity(), key, new ItemEntityInteractedKubeEvent(event.getEntity(), event.getTarget(), event.getHand(), stack)).applyCancel(event);
		}
	}

	@SubscribeEvent
	public static void crafted(PlayerEvent.ItemCraftedEvent event) {
		if (!event.getCrafting().isEmpty()) {
			var key = event.getCrafting().getItem().kgy$getKey();

			if (ItemEvents.CRAFTED.hasListeners(key)) {
				ItemEvents.CRAFTED.post(event.getEntity(), key, new ItemCraftedKubeEvent(event.getEntity(), event.getCrafting(), event.getInventory()));
			}

			if (PlayerEvents.INVENTORY_CHANGED.hasListeners(key)) {
				PlayerEvents.INVENTORY_CHANGED.post(event.getEntity(), key, new InventoryChangedKubeEvent(event.getEntity(), event.getCrafting(), -1));
			}
		}
	}

	@SubscribeEvent
	public static void smelted(PlayerEvent.ItemSmeltedEvent event) {
		if (!event.getSmelting().isEmpty()) {
			var key = event.getSmelting().getItem().kgy$getKey();

			if (ItemEvents.SMELTED.hasListeners(key)) {
				ItemEvents.SMELTED.post(event.getEntity(), key, new ItemSmeltedKubeEvent(event.getEntity(), event.getSmelting()));
			}

			if (PlayerEvents.INVENTORY_CHANGED.hasListeners(key)) {
				PlayerEvents.INVENTORY_CHANGED.post(event.getEntity(), key, new InventoryChangedKubeEvent(event.getEntity(), event.getSmelting(), -1));
			}
		}
	}

	@SubscribeEvent
	public static void itemDestroyed(PlayerDestroyItemEvent event) {
		var key = event.getOriginal().getItem().kgy$getKey();

		if (ItemEvents.ITEM_DESTROYED.hasListeners(key)) {
			ItemEvents.ITEM_DESTROYED.post(event.getEntity(), key, new ItemDestroyedKubeEvent(event));
		}
	}
}