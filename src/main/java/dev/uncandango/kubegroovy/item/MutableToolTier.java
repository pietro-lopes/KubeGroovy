package dev.uncandango.kubegroovy.item;

import dev.uncandango.kubegroovy.script.annotations.RemapForGY;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.block.Block;

import java.util.List;

// TODO: Tier needs some rework
public class MutableToolTier {
	public final ToolMaterial parent;

	private TagKey<Block> incorrectBlocksForDrops;
	private int durability;
	private float speed;
	private float attackDamageBonus;
	private int enchantmentValue;
	private TagKey<Item> repairItems;

	public MutableToolTier(ToolMaterial p) {
		parent = p;
		incorrectBlocksForDrops = parent.incorrectBlocksForDrops();
		durability = parent.durability();
		speed = parent.speed();
		attackDamageBonus = parent.attackDamageBonus();
		enchantmentValue = parent.enchantmentValue();
		repairItems = parent.repairItems();
	}

	public ToolMaterial build() {
		return new ToolMaterial(
			incorrectBlocksForDrops,
			durability,
			speed,
			attackDamageBonus,
			enchantmentValue,
			repairItems
		);
	}

	public Tool createToolProperties(TagKey<Block> minesEfficiently) {
		var blocks = BuiltInRegistries.acquireBootstrapRegistrationLookup(BuiltInRegistries.BLOCK);
		return new Tool(
			List.of(
				Tool.Rule.deniesDrops(blocks.getOrThrow(incorrectBlocksForDrops)),
				Tool.Rule.minesAndDrops(blocks.getOrThrow(minesEfficiently), speed)
			),
			1.0F,
			1,
			true
		);
	}

	@RemapForGY("getUses")
	public int getUses() {
		return durability;
	}

	public void setUses(int i) {
		durability = i;
	}

	@RemapForGY("getSpeed")
	public float getSpeed() {
		return speed;
	}

	public void setSpeed(float f) {
		speed = f;
	}

	@RemapForGY("getAttackDamageBonus")
	public float getAttackDamageBonus() {
		return attackDamageBonus;
	}

	public void setAttackDamageBonus(float f) {
		attackDamageBonus = f;
	}

	@RemapForGY("getEnchantmentValue")
	public int getEnchantmentValue() {
		return enchantmentValue;
	}

	public void setEnchantmentValue(int i) {
		enchantmentValue = i;
	}

	public void setIncorrectBlocksForDropsTag(Identifier tag) {
		incorrectBlocksForDrops = BlockTags.create(tag);
	}

	public Identifier getIncorrectBlocksForDropsTag() {
		return incorrectBlocksForDrops.location();
	}

	public TagKey<Block> getIncorrectBlocksForDrops() {
		return incorrectBlocksForDrops;
	}

	public void setRepairItemsTag(Identifier tag) {
		repairItems = ItemTags.create(tag);
	}

	public Identifier getRepairItemsTag() {
		return repairItems.location();
	}

	public TagKey<Item> getRepairItems() {
		return repairItems;
	}
}
