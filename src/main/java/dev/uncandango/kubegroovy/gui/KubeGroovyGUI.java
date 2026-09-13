package dev.uncandango.kubegroovy.gui;

import dev.uncandango.kubegroovy.core.InventoryKGY;
import dev.uncandango.kubegroovy.plugin.builtin.wrapper.TextIcons;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;

public class KubeGroovyGUI {
	public static final SimpleContainer EMPTY_CONTAINER = new SimpleContainer(0);

	public int width = 176;
	public int height = 166;
	public Component title = TextIcons.NAME;
	public int inventoryLabelX = -1;
	public int inventoryLabelY = -1;
	public InventoryKGY inventory = EMPTY_CONTAINER;
	public int inventoryWidth = 0;
	public int inventoryHeight = 0;
	public int playerSlotsX = -1;
	public int playerSlotsY = -1;

	public KubeGroovyGUI() {
	}

	public KubeGroovyGUI(FriendlyByteBuf buf) {
		width = buf.readShort();
		height = buf.readShort();
		inventoryLabelX = buf.readShort();
		inventoryLabelY = buf.readShort();
		inventory = new SimpleContainer(buf.readUnsignedByte());
		inventoryWidth = buf.readUnsignedByte();
		inventoryHeight = buf.readUnsignedByte();
		playerSlotsX = buf.readShort();
		playerSlotsY = buf.readShort();
	}

	public void write(FriendlyByteBuf buf) {
		buf.writeShort(width);
		buf.writeShort(height);
		buf.writeShort(inventoryLabelX);
		buf.writeShort(inventoryLabelY);
		buf.writeByte(inventory.kgy$getSlots());
		buf.writeByte(inventoryWidth);
		buf.writeByte(inventoryHeight);
		buf.writeShort(playerSlotsX);
		buf.writeShort(playerSlotsY);
	}

	public void setInventory(InventoryKGY inv) {
		inventory = inv;
		inventoryWidth = inv.kgy$getWidth();
		inventoryHeight = inv.kgy$getHeight();
		height = 114 + inventoryHeight * 18;
		width = 14 + inventoryWidth * 18;
		inventoryLabelY = height - 94;
		playerSlotsX = 8;
		playerSlotsY = 103;
	}
}
