package dev.uncandango.kubegroovy.core;

import dev.uncandango.kubegroovy.KubeGroovy;
import dev.uncandango.kubegroovy.entity.EntityPotionEffectsGY;
import dev.uncandango.kubegroovy.entity.KubeRayTraceResult;
import dev.uncandango.kubegroovy.item.FoodEatenKubeEvent;
import dev.uncandango.kubegroovy.item.ItemPredicate;
import dev.uncandango.kubegroovy.plugin.builtin.event.ItemEvents;
import dev.uncandango.kubegroovy.typings.Info;
import dev.uncandango.kubegroovy.typings.Param;
import dev.uncandango.kubegroovy.typings.ThisIs;
import dev.uncandango.kubegroovy.script.annotations.HideFromGY;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Predicate;

@RemapPrefixForGY("kgy$")
public interface LivingEntityKGY extends EntityKGY {
	Identifier KGY_PLAYER_CUSTOM_SPEED = KubeGroovy.id("player.speed.modifier");

	@Override
	@HideFromGY
	default LivingEntity kgy$self() {
		return (LivingEntity) this;
	}

	default void kgy$foodEaten(ItemStack eatenStack, FoodProperties food) {
		var event = new FoodEatenKubeEvent(kgy$self(), eatenStack);
		var item = eatenStack.getItem();
		var behavior = item.kgy$getItemBehavior();

		if (behavior != null && behavior.foodEaten != null) {
			behavior.foodEaten.accept(event);
		}

		var key = item.kgy$getKey();

		if (ItemEvents.FOOD_EATEN.hasListeners(key)) {
			ItemEvents.FOOD_EATEN.post(kgy$self(), key, event);
		}
	}

	@Override
	@ThisIs(LivingEntity.class)
	default boolean kgy$isLiving() {
		return true;
	}

	@Info(value = "Sets the entity's maximum health to specified HP.", params = {
		@Param(name = "hp", value = "The new maximum health of the entity.")
	})
	default void kgy$setMaxHealth(float hp) {
		kgy$self().kgy$setAttributeBaseValue(Attributes.MAX_HEALTH, hp);
	}

	default boolean kgy$isUndead() {
		return kgy$self().isInvertedHealAndHarm();
	}

	default EntityPotionEffectsGY kgy$getPotionEffects() {
		return new EntityPotionEffectsGY(kgy$self());
	}

	default void kgy$swing(InteractionHand hand) {
		kgy$self().swing(hand, true);
	}

	default void kgy$swing() {
		kgy$self().swing(InteractionHand.MAIN_HAND, true);
	}

	default ItemStack kgy$getEquipment(EquipmentSlot slot) {
		return kgy$self().getItemBySlot(slot);
	}

	default void kgy$setEquipment(EquipmentSlot slot, ItemStack item) {
		kgy$self().setItemSlot(slot, item);
	}

	default ItemStack kgy$getHeldItem(InteractionHand hand) {
		return kgy$self().getItemInHand(hand);
	}

	default void kgy$setHeldItem(InteractionHand hand, ItemStack item) {
		kgy$self().setItemInHand(hand, item);
	}

	default ItemStack kgy$getMainHandItem() {
		return kgy$getEquipment(EquipmentSlot.MAINHAND);
	}

	default void kgy$setMainHandItem(ItemStack item) {
		kgy$setEquipment(EquipmentSlot.MAINHAND, item);
	}

	default ItemStack kgy$getOffHandItem() {
		return kgy$getEquipment(EquipmentSlot.OFFHAND);
	}

	default void kgy$setOffHandItem(ItemStack item) {
		kgy$setEquipment(EquipmentSlot.OFFHAND, item);
	}

	default ItemStack kgy$getHeadArmorItem() {
		return kgy$getEquipment(EquipmentSlot.HEAD);
	}

	default void kgy$setHeadArmorItem(ItemStack item) {
		kgy$setEquipment(EquipmentSlot.HEAD, item);
	}

	default ItemStack kgy$getChestArmorItem() {
		return kgy$getEquipment(EquipmentSlot.CHEST);
	}

	default void kgy$setChestArmorItem(ItemStack item) {
		kgy$setEquipment(EquipmentSlot.CHEST, item);
	}

	default ItemStack kgy$getLegsArmorItem() {
		return kgy$getEquipment(EquipmentSlot.LEGS);
	}

	default void kgy$setLegsArmorItem(ItemStack item) {
		kgy$setEquipment(EquipmentSlot.LEGS, item);
	}

	default ItemStack kgy$getFeetArmorItem() {
		return kgy$getEquipment(EquipmentSlot.FEET);
	}

	default void kgy$setFeetArmorItem(ItemStack item) {
		kgy$setEquipment(EquipmentSlot.FEET, item);
	}

	default void kgy$damageEquipment(EquipmentSlot slot, int amount, Consumer<ItemStack> onBroken) {
		if (!(kgy$self().level() instanceof ServerLevel serverLevel)) {
			return;
		}

		var stack = kgy$self().getItemBySlot(slot);

		if (!stack.isEmpty()) {
			stack.hurtAndBreak(amount, serverLevel, kgy$self(), item -> onBroken.accept(stack));

			if (stack.isEmpty()) {
				kgy$self().setItemSlot(slot, ItemStack.EMPTY);
			}
		}
	}

	default void kgy$damageEquipment(EquipmentSlot slot, int amount) {
		kgy$damageEquipment(slot, amount, stack -> {
		});
	}

	default void kgy$damageEquipment(EquipmentSlot slot) {
		kgy$damageEquipment(slot, 1);
	}

	default void kgy$damageHeldItem(InteractionHand hand, int amount, Consumer<ItemStack> onBroken) {
		kgy$damageEquipment(hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND, amount, onBroken);
	}

	default void kgy$damageHeldItem(InteractionHand hand, int amount) {
		kgy$damageHeldItem(hand, amount, stack -> {
		});
	}

	default void kgy$damageHeldItem() {
		kgy$damageHeldItem(InteractionHand.MAIN_HAND, 1);
	}

	default boolean kgy$isHoldingInAnyHand(ItemPredicate itemPredicate) {
		return itemPredicate.test(kgy$self().getItemInHand(InteractionHand.MAIN_HAND)) || itemPredicate.test(kgy$self().getItemInHand(InteractionHand.OFF_HAND));
	}

	default double kgy$getTotalMovementSpeed() {
		return kgy$self().getAttributeValue(Attributes.MOVEMENT_SPEED);
	}

	default double kgy$getDefaultMovementSpeed() {
		return kgy$self().getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
	}

	default void kgy$setDefaultMovementSpeed(double speed) {
		kgy$self().kgy$setAttributeBaseValue(Attributes.MOVEMENT_SPEED, speed);
	}

	default void kgy$setMovementSpeedAddition(double speed) {
		AttributeInstance instance = kgy$self().getAttribute(Attributes.MOVEMENT_SPEED);
		if (instance != null) {
			instance.removeModifier(KGY_PLAYER_CUSTOM_SPEED);
			instance.addTransientModifier(kgy$createSpeedModifier(speed, AttributeModifier.Operation.ADD_VALUE));
		}
	}

	default void kgy$setDefaultMovementSpeedMultiplier(double speed) {
		AttributeInstance instance = kgy$self().getAttribute(Attributes.MOVEMENT_SPEED);
		if (instance != null) {
			instance.removeModifier(KGY_PLAYER_CUSTOM_SPEED);
			instance.addTransientModifier(kgy$createSpeedModifier(speed, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		}
	}

	default void kgy$setTotalMovementSpeedMultiplier(double speed) {
		AttributeInstance instance = kgy$self().getAttribute(Attributes.MOVEMENT_SPEED);
		if (instance != null) {
			instance.removeModifier(KGY_PLAYER_CUSTOM_SPEED);
			instance.addTransientModifier(kgy$createSpeedModifier(speed, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}

	default boolean kgy$canEntityBeSeen(LivingEntity entity) {
		return BehaviorUtils.canSee(kgy$self(), entity);
	}

	default double kgy$getReachDistance() {
		return kgy$self().getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
	}

	default KubeRayTraceResult kgy$rayTrace() {
		return kgy$rayTrace(kgy$getReachDistance());
	}

	@Nullable
	default Entity kgy$rayTraceEntity(Predicate<Entity> filter) {
		return kgy$rayTraceEntity(kgy$getReachDistance(), filter);
	}

	default double kgy$getAttributeTotalValue(Holder<Attribute> attribute) {
		AttributeInstance instance = kgy$self().getAttribute(attribute);
		if (instance != null) {
			return instance.getValue();
		}
		return 0.0;
	}

	default double kgy$getAttributeBaseValue(Holder<Attribute> attribute) {
		AttributeInstance instance = kgy$self().getAttribute(attribute);
		if (instance != null) {
			return instance.getBaseValue();
		}
		return 0.0;
	}

	default void kgy$setAttributeBaseValue(Holder<Attribute> attribute, double value) {
		AttributeInstance instance = kgy$self().getAttribute(attribute);
		if (instance != null) {
			instance.setBaseValue(value);
		}
	}

	default void kgy$modifyAttribute(Holder<Attribute> attribute, Identifier id, double amount, AttributeModifier.Operation operation) {
		AttributeInstance instance = kgy$self().getAttribute(attribute);
		if (instance != null) {
			instance.removeModifier(id);
			instance.addTransientModifier(new AttributeModifier(id, amount, operation));
		}
	}

	default void kgy$removeAttribute(Holder<Attribute> attribute, Identifier id) {
		AttributeInstance instance = kgy$self().getAttribute(attribute);
		if (instance != null) {
			instance.removeModifier(id);
		}
	}

	private AttributeModifier kgy$createSpeedModifier(double speed, AttributeModifier.Operation operation) {
		return new AttributeModifier(
			KGY_PLAYER_CUSTOM_SPEED,
			speed,
			operation);
	}
}