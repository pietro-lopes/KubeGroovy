package dev.uncandango.kubegroovy.core.component;

import dev.uncandango.kubegroovy.KubeGroovy;
import dev.uncandango.kubegroovy.color.KubeColor;
import dev.uncandango.kubegroovy.util.RegistryAccessContainer;
import dev.uncandango.kubegroovy.script.bridge.Context;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Instrument;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.DamageResistant;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.item.component.InstrumentComponent;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.MapItemColor;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.holdersets.OrHolderSet;

import java.util.List;

@RemapPrefixForGY("kgy$")
public interface ItemComponentFunctions extends DataComponentAccessor, AttributeModifierAccessor {
	default void kgy$setMaxStackSize(int size) {
		kgy$override(DataComponents.MAX_STACK_SIZE, size);
	}

	default void kgy$setMaxDamage(int maxDamage) {
		kgy$override(DataComponents.MAX_DAMAGE, maxDamage);
	}

	default void kgy$setDamage(int damage) {
		kgy$override(DataComponents.DAMAGE, damage);
	}

	default void kgy$setUnbreakable() {
		kgy$setUnit(DataComponents.UNBREAKABLE);
	}

	default void kgy$setUnbreakableWithTooltip() {
		kgy$setUnit(DataComponents.UNBREAKABLE);
		kgy$override(DataComponents.TOOLTIP_DISPLAY,
			getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT)
				.withHidden(DataComponents.UNBREAKABLE, false)
		);
	}

	default void kgy$setItemName(Component component) {
		kgy$override(DataComponents.ITEM_NAME, component);
	}

	default void kgy$setRepairCost(int repairCost) {
		kgy$override(DataComponents.REPAIR_COST, repairCost);
	}

	default void kgy$setFood(FoodProperties foodProperties) {
		kgy$override(DataComponents.FOOD, foodProperties);
	}

	default void kgy$setFood(int nutrition, float saturation) {
		kgy$setFood(new FoodProperties.Builder().nutrition(nutrition).saturationModifier(saturation).build());
	}

	default void kgy$clearDamageResistance() {
		kgy$remove(DataComponents.DAMAGE_RESISTANT);
	}

	default void kgy$setDamageResistance(HolderSet<DamageType> types) {
		kgy$override(DataComponents.DAMAGE_RESISTANT, new DamageResistant(types));
	}

	default void kgy$addDamageResistance(HolderSet<DamageType> types) {
		var existing = get(DataComponents.DAMAGE_RESISTANT);
		if (existing != null) {
			kgy$setDamageResistance(new OrHolderSet<>(existing.types(), types));
		} else {
			kgy$setDamageResistance(types);
		}
	}

	default void kgy$setFireResistant() {
		var cx = Context.getCurrent();
		kgy$addDamageResistance(RegistryAccessContainer.of().getOrThrow(DamageTypeTags.IS_FIRE));
	}

	@Deprecated
	default void kgy$setFireResistant(boolean fireResistant) {
		var cx = Context.getCurrent();
		KubeGroovy.LOGGER.warn("kgy$setFireResistant(boolean) is deprecated due to changes in damage resistance, use the no-arg version instead");
		if (fireResistant) {
			kgy$setFireResistant();
		}
	}

	default void kgy$setTool(Tool tool) {
		kgy$override(DataComponents.TOOL, tool);
	}

	default void kgy$setMapItemColor(KubeColor color) {
		kgy$override(DataComponents.MAP_COLOR, new MapItemColor(color.kgy$getRGB()));
	}

	default void kgy$setChargedProjectiles(List<ItemStack> items) {
		kgy$override(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.ofNonEmpty(items));
	}

	default void kgy$setBundleContents(List<ItemStack> items) {
		var mutable = new BundleContents.Mutable(BundleContents.EMPTY);
		items.forEach(mutable::tryInsert);
		kgy$override(DataComponents.BUNDLE_CONTENTS, mutable.toImmutable());
	}

	default void kgy$setBucketEntityData(CompoundTag tag) {
		kgy$override(DataComponents.BUCKET_ENTITY_DATA, CustomData.of(tag));
	}


	default void kgy$setBlockEntityData(BlockEntityType<?> type, CompoundTag tag) {
		kgy$override(DataComponents.BLOCK_ENTITY_DATA, TypedEntityData.of(type, tag));
	}

	default void kgy$setInstrument(Holder<Instrument> instrument) {
		kgy$override(DataComponents.INSTRUMENT, new InstrumentComponent(instrument));
	}

	default void kgy$setFireworkExplosion(FireworkExplosion explosion) {
		kgy$override(DataComponents.FIREWORK_EXPLOSION, explosion);
	}

	default void kgy$setFireworks(Fireworks fireworks) {
		kgy$override(DataComponents.FIREWORKS, fireworks);
	}

	default void kgy$setNoteBlockSound(Identifier id) {
		kgy$override(DataComponents.NOTE_BLOCK_SOUND, id);
	}

	@Override
	default ItemAttributeModifiers kgy$getAttributeModifiers() {
		var mods = get(DataComponents.ATTRIBUTE_MODIFIERS);
		return mods == null ? new ItemAttributeModifiers(List.of()) : mods;
	}

	@Override
	default void kgy$setAttributeModifiers(ItemAttributeModifiers modifiers) {
		kgy$override(DataComponents.ATTRIBUTE_MODIFIERS, modifiers);
	}
}
