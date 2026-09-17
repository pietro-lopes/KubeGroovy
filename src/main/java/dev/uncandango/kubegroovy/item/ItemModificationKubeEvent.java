package dev.uncandango.kubegroovy.item;

import dev.uncandango.kubegroovy.KubeGroovy;
import dev.uncandango.kubegroovy.core.component.ItemComponentFunctions;
import dev.uncandango.kubegroovy.event.KubeEvent;
import dev.uncandango.kubegroovy.typings.Info;
import dev.uncandango.kubegroovy.util.TickDuration;
import dev.uncandango.kubegroovy.util.registrypredicate.RegistryPredicate;
import dev.uncandango.kubegroovy.script.bridge.Context;
import dev.uncandango.kubegroovy.script.groovy.KubeGroovyMetaClassCreationHandle;
import dev.uncandango.kubegroovy.script.annotations.HideFromGY;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Util;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.component.Weapon;
import net.minecraft.world.item.enchantment.Enchantable;
import net.minecraft.world.item.enchantment.Repairable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

import static net.minecraft.world.item.Item.BASE_ATTACK_DAMAGE_ID;

@Info("""
	Invoked after all items are registered to modify them.
	""")
public class ItemModificationKubeEvent implements KubeEvent {
	private final ModifyDefaultComponentsEvent event;

	public ItemModificationKubeEvent(ModifyDefaultComponentsEvent event) {
		this.event = event;
	}

	@Info("""
		Modifies items matching the given ingredient.
		
		**NOTE**: tag ingredients are not supported at this time.
		""")
	// TODO: item with component filter support?
	public void modify(RegistryPredicate<Item> in, Consumer<ItemModifications> c) {
		//KubeGroovyMetaClassCreationHandle.refresh(ItemModifications.class);
		for (Item item : BuiltInRegistries.ITEM) {
			if (in.test(item.kgy$asHolder())) {
				event.modify(item, (builder, context, key) -> {
					var modifications = new ItemModifications(item, builder, context);
					//KubeGroovyMetaClassCreationHandle.refresh(ItemModifications.class);
					c.accept(modifications);
				});
			}
		}
	}

	@RemapPrefixForGY("kgy$")
	public record ItemModifications(Item item, DataComponentMap.Builder components, HolderLookup.Provider registries) implements ItemComponentFunctions, ItemBehaviorFunctions {
		@HideFromGY
		public static final Reference2IntOpenHashMap<Item> BURN_TIME_OVERRIDES = new Reference2IntOpenHashMap<>();

		@Override
		public <T> @Nullable T get(DataComponentType<? extends T> type) {
			return components.get(type);
		}

		@Override
		@HideFromGY
		public <T> void kgy$override(DataComponentType<T> type, @Nullable T value) {
			components.set(type, value);
		}

		@Override
		@HideFromGY
		public void kgy$setAttributeModifiers(ItemAttributeModifiers modifiers) {
			kgy$override(DataComponents.ATTRIBUTE_MODIFIERS, modifiers);
		}

		@Override
		@HideFromGY
		public void kgy$setFireResistant(Context cx) {
			kgy$addDamageResistance(registries.lookupOrThrow(Registries.DAMAGE_TYPE).get(DamageTypeTags.IS_FIRE).orElseThrow());
		}

		@Override
		@Deprecated
		@HideFromGY
		public void kgy$setFireResistant(Context cx, boolean fireResistant) {
			KubeGroovy.LOGGER.warn("kgy$setFireResistant(boolean) is deprecated due to changes in damage resistance, use the no-arg version instead");
			if (fireResistant) {
				kgy$setFireResistant(cx);
			}
		}

		public void removeComponent(DataComponentType<?> type) {
			components.set(type, null);
		}

		public void setBurnTime(TickDuration i) {
			BURN_TIME_OVERRIDES.put(item, i.intTicks());
		}

		// TODO: ItemStackTemplate support?
		public void setCraftingRemainder(Item item) {
			this.item.kgy$setCraftingRemainder(new ItemStackTemplate(item, 1, DataComponentPatch.EMPTY));
		}

		public void setTier(Consumer<MutableToolMaterial> builder) {
			var items = registries.lookupOrThrow(Registries.ITEM);
			var blocks = registries.lookupOrThrow(Registries.BLOCK);

			var material = Util.make(new MutableToolMaterial(ToolMaterial.IRON), builder).toToolMaterial();

			var attackDamageBaseline = 0F;
			var attackSpeedBaseline = 0F;

			var existing = kgy$getAttributeModifiers();

			if (existing != null) {
				for (var entry : existing.modifiers()) {
					if (entry.matches(Attributes.ATTACK_DAMAGE, BASE_ATTACK_DAMAGE_ID)) {
						attackDamageBaseline = (float) entry.modifier().amount();
					} else if (entry.matches(Attributes.ATTACK_SPEED, Item.BASE_ATTACK_SPEED_ID)) {
						attackSpeedBaseline = (float) entry.modifier().amount();
					}
				}
			}

			kgy$setMaxDamage(material.durability());
			kgy$override(DataComponents.ENCHANTABLE, new Enchantable(material.enchantmentValue()));
			kgy$override(DataComponents.REPAIRABLE, new Repairable(
				items.getOrThrow(material.repairItems())
			));

			var attackDamage = attackDamageBaseline + material.attackDamageBonus();

			var tool = get(DataComponents.TOOL);
			if (tool != null) {
				var minesEfficiently = kgy$inferMineableTag(tool);
				kgy$override(DataComponents.TOOL, MutableToolMaterial.createToolProperties(material, minesEfficiently));
				kgy$setAttributeModifiers(ItemAttributeModifiers.builder()
					.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, attackDamage, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
					.add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, attackSpeedBaseline, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
					.build()
				);
				kgy$override(DataComponents.WEAPON, new Weapon(2, 0F));
			} else {
				kgy$override(DataComponents.TOOL, new Tool(
					List.of(
						Tool.Rule.minesAndDrops(HolderSet.direct(Blocks.COBWEB.kgy$asHolder()), 15.0F),
						Tool.Rule.overrideSpeed(blocks.getOrThrow(BlockTags.SWORD_INSTANTLY_MINES), Float.MAX_VALUE),
						Tool.Rule.overrideSpeed(blocks.getOrThrow(BlockTags.SWORD_EFFICIENT), 1.5F)
					),
					1.0F,
					2,
					false
				));
				kgy$setAttributeModifiers(ItemAttributeModifiers.builder()
					.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, attackDamage, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
					.add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, attackSpeedBaseline, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
					.build()
				);
				kgy$override(DataComponents.WEAPON, new Weapon(1));
			}
		}

		private TagKey<Block> kgy$inferMineableTag(Tool tool) {
			for (var rule : tool.rules()) {
				var set = rule.blocks();

				if (set instanceof HolderSet.Named<Block> named) {
					var key = named.key();
					return TagKey.create(Registries.BLOCK, key.location());
				}
			}

			return BlockTags.MINEABLE_WITH_PICKAXE;
		}

		public void setNameKey(String key) {
			item.kgy$setNameKey(key);
		}

		public void disableRepair() {
			item.kgy$setCanRepair(false);
		}

		@Override
		public ItemBehavior kgy$getOrCreateBehavior() {
			var behavior = item.kgy$getItemBehavior();
			if (behavior == null) {
				behavior = new ItemBehavior();
				item.kgy$setItemBehavior(behavior);
			}
			return behavior;
		}
	}
}
