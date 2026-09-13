package dev.uncandango.kubegroovy.gui;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.IContainerFactory;

public class KubeGroovyMenu extends AbstractContainerMenu {
	public static final IContainerFactory<KubeGroovyMenu> FACTORY = KubeGroovyMenu::new;

	public final Player player;
	public final KubeGroovyGUI guiData;

	public KubeGroovyMenu(int id, Inventory inventory, KubeGroovyGUI guiData) {
		super(KubeGroovyMenus.MENU.get(), id);
		this.player = inventory.player;
		this.guiData = guiData;

		int xOffset = 89 - 9 * this.guiData.inventoryWidth;
		if (guiData.inventory.kgy$getSlots() > 0) {
			int k = (guiData.inventoryHeight - 4) * 18;

			for (int l = 0; l < guiData.inventoryHeight; ++l) {
				for (int m = 0; m < guiData.inventoryWidth; ++m) {
					this.addSlot(new InventoryKGYSlot(guiData.inventory, m + l * guiData.inventoryWidth, xOffset + m * 18, 18 + l * 18));
				}
			}

			if (guiData.playerSlotsX >= 0 && guiData.playerSlotsY >= 0) {
				for (int l = 0; l < 3; ++l) {
					for (int m = 0; m < 9; ++m) {
						this.addSlot(new Slot(inventory, m + l * 9 + 9, guiData.playerSlotsX + m * 18, guiData.playerSlotsY + l * 18 + k));
					}
				}

				for (int l = 0; l < 9; ++l) {
					this.addSlot(new Slot(inventory, l, guiData.playerSlotsX + l * 18, guiData.playerSlotsY + 58 + k));
				}
			}
		}
	}

	public KubeGroovyMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
		this(id, inventory, new KubeGroovyGUI(buf));
	}

	@Override
	public ItemStack quickMoveStack(Player player, int i) {
		var itemStack = ItemStack.EMPTY;
		var slot = this.slots.get(i);

		int slotCount = guiData.inventory.kgy$getSlots();

		if (slot != null && slot.hasItem()) {
			var itemStack2 = slot.getItem();
			itemStack = itemStack2.copy();

			if (i < slotCount) {
				if (!this.moveItemStackTo(itemStack2, slotCount, this.slots.size(), true)) {
					return ItemStack.EMPTY;
				}
			} else if (!this.moveItemStackTo(itemStack2, 0, slotCount, false)) {
				return ItemStack.EMPTY;
			}

			if (itemStack2.isEmpty()) {
				slot.setByPlayer(ItemStack.EMPTY);
			} else {
				slot.setChanged();
			}
		}

		return itemStack;
	}

	@Override
	public boolean stillValid(Player player) {
		return true;
	}
}
