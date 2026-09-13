package dev.uncandango.kubegroovy.item;

import net.minecraft.world.item.Item;

public class KubeGroovyItemProperties extends Item.Properties {
	public final ItemBuilder itemBuilder;

	public KubeGroovyItemProperties(ItemBuilder itemBuilder) {
		this.itemBuilder = itemBuilder;
	}
}
