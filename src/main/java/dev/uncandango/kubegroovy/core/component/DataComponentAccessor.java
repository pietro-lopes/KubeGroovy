package dev.uncandango.kubegroovy.core.component;

import dev.uncandango.kubegroovy.color.KubeColor;
import dev.uncandango.kubegroovy.component.CustomModelDataType;
import dev.uncandango.kubegroovy.util.Cast;
import dev.uncandango.kubegroovy.script.bridge.Context;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import net.minecraft.advancements.criterion.ItemPredicate;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.component.SeededContainerLoot;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static dev.uncandango.kubegroovy.component.DataComponentWrapper.tryWrapComponent;

/// A generic readable and writable accessor for data components.
/// Contains various helpers for getters and setters.
@RemapPrefixForGY("kgy$")
public interface DataComponentAccessor extends DataComponentGetter, DataComponentSetter {
	// Getters //
	default CompoundTag kgy$getCustomData() {
		return getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
	}

	@Nullable
	default Component kgy$getCustomName() {
		return get(DataComponents.CUSTOM_NAME);
	}

	default CustomModelData kgy$getCustomModelData() {
		return getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.EMPTY);
	}

	// Setters //
	default void kgy$setCustomData(CompoundTag tag) {
		if (tag.isEmpty()) {
			kgy$remove(DataComponents.CUSTOM_DATA);
		} else {
			kgy$override(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		}
	}

	default void kgy$setRarity(Rarity rarity) {
		kgy$override(DataComponents.RARITY, rarity);
	}

	default void kgy$setCustomName(@Nullable Component name) {
		if (name != null) {
			kgy$override(DataComponents.CUSTOM_NAME, name);
		} else {
			kgy$remove(DataComponents.CUSTOM_NAME);
		}
	}

	default void kgy$setLore(List<Component> lines) {
		kgy$override(DataComponents.LORE, new ItemLore(List.copyOf(lines)));
	}

	default void kgy$setLore(List<Component> lines, List<Component> styledLines) {
		kgy$override(DataComponents.LORE, new ItemLore(List.copyOf(lines), List.copyOf(styledLines)));
	}

	default void kgy$setCustomModelData(CustomModelDataType type, List<?> values) {
		CustomModelData existing = getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.EMPTY);
		kgy$override(DataComponents.CUSTOM_MODEL_DATA, type.apply(existing, values));
	}

	default void kgy$setTooltipHidden() {
		TooltipDisplay display = getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);
		kgy$override(DataComponents.TOOLTIP_DISPLAY, new TooltipDisplay(true, display.hiddenComponents()));
	}

	default void kgy$setAdditionalTooltipHidden() {
		TooltipDisplay display = getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);

		display = display.withHidden(DataComponents.BANNER_PATTERNS, true);
		display = display.withHidden(DataComponents.BEES, true);
		display = display.withHidden(DataComponents.BLOCK_ENTITY_DATA, true);
		display = display.withHidden(DataComponents.BLOCK_STATE, true);
		display = display.withHidden(DataComponents.WRITTEN_BOOK_CONTENT, true);

		kgy$override(DataComponents.TOOLTIP_DISPLAY, display);
	}

	default void kgy$setGlintOverride(boolean override) {
		kgy$override(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, override);
	}

	default void kgy$setDyedColor(KubeColor color) {
		kgy$override(DataComponents.DYED_COLOR, new DyedItemColor(color.kgy$getRGB()));
	}

	default void kgy$setDyedColorWithTooltip(KubeColor color) {
		kgy$override(DataComponents.DYED_COLOR, new DyedItemColor(color.kgy$getRGB()));
	}

	default void kgy$setPotionContents(PotionContents contents) {
		kgy$override(DataComponents.POTION_CONTENTS, contents);
	}

	default void kgy$setPotionId(Holder<Potion> potion) {
		kgy$override(DataComponents.POTION_CONTENTS, new PotionContents(potion));
	}

	default void kgy$setEntityData(CompoundTag tag) {
		if (tag.isEmpty()) {
			kgy$remove(DataComponents.ENTITY_DATA);
			return;
		}

		var id = tag.getString("id");
		if (id.isEmpty()) {
			throw new IllegalArgumentException("ENTITY_DATA tag must contain non-empty \"id\"");
		}

		var type = EntityType.byString(id.get()).orElseThrow(() -> new IllegalArgumentException("Unknown entity id: " + id));
		kgy$override(DataComponents.ENTITY_DATA, TypedEntityData.of(type, tag));
	}

	default void kgy$setProfile(com.mojang.authlib.GameProfile profile) {
		kgy$override(DataComponents.PROFILE, ResolvableProfile.createResolved(profile));
	}

	default void kgy$setProfile(@Nullable String name, @Nullable UUID uuid) {
		var cleanName = name != null && name.isBlank() ? null : name;
		var cleanUuid = uuid != null && uuid.getLeastSignificantBits() == 0L && uuid.getMostSignificantBits() == 0L ? null : uuid;

		if (cleanUuid != null) {
			kgy$override(DataComponents.PROFILE, ResolvableProfile.createUnresolved(cleanUuid));
		} else if (cleanName != null) {
			kgy$override(DataComponents.PROFILE, ResolvableProfile.createUnresolved(cleanName));
		} else {
			kgy$remove(DataComponents.PROFILE);
		}
	}

	default void kgy$setBaseColor(DyeColor color) {
		kgy$override(DataComponents.BASE_COLOR, color);
	}

	default void kgy$setBlockStateProperties(Map<String, String> properties) {
		kgy$override(DataComponents.BLOCK_STATE, new BlockItemStateProperties(properties));
	}

	default void kgy$setLockCode(ItemPredicate lock) {
		kgy$override(DataComponents.LOCK, new net.minecraft.world.LockCode(lock));
	}

	default void kgy$setContainerLootTable(ResourceKey<LootTable> lootTable) {
		kgy$override(DataComponents.CONTAINER_LOOT, new SeededContainerLoot(lootTable, 0L));
	}

	default void kgy$setContainerLootTable(ResourceKey<LootTable> lootTable, long seed) {
		kgy$override(DataComponents.CONTAINER_LOOT, new SeededContainerLoot(lootTable, seed));
	}

	default void kgy$set(DataComponentType<?> component, Object value) {
		var cx = Context.getCurrent();
		var wrapped = tryWrapComponent(component, value)
			.getOrThrow(msg ->
				new dev.uncandango.kubegroovy.error.KubeRuntimeException("Failed to wrap data component %s from '%s': %s".formatted(component, value, msg))
					.source(dev.uncandango.kubegroovy.script.SourceLine.of())
			);

		if (wrapped.isPresent()) {
			kgy$override(component, Cast.to(wrapped.get()));
		} else {
			kgy$remove(component);
		}
	}

	default String kgy$getComponentString() {
		var cx = Context.getCurrent();
		return "[]";
	}
}
