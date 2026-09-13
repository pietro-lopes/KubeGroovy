package dev.uncandango.kubegroovy.item;

import dev.uncandango.kubegroovy.entity.KubeEntityEvent;
import dev.uncandango.kubegroovy.typings.Info;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

@Info("""
	Invoked when an entity eats food.
	""")
public class FoodEatenKubeEvent implements KubeEntityEvent {
	private final Entity entity;
	private final ItemStack item;

	public FoodEatenKubeEvent(LivingEntity e, ItemStack is) {
		entity = e;
		item = is;
	}

	@Override
	@Info("The entity that ate the food.")
	public Entity getEntity() {
		return entity;
	}

	@Info("The food that was eaten.")
	public ItemStack getItem() {
		return item;
	}
}