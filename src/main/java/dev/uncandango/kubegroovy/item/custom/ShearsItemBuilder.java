package dev.uncandango.kubegroovy.item.custom;

import dev.uncandango.kubegroovy.generator.KubeAssetGenerator;
import dev.uncandango.kubegroovy.item.ItemBuilder;
import dev.uncandango.kubegroovy.script.annotations.ReturnsSelf;
import net.minecraft.core.dispenser.ShearsDispenseItemBehavior;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;

@ReturnsSelf
public class ShearsItemBuilder extends ItemBuilder {
	public static final Identifier[] SHEAR_TAGS = {
		Tags.Items.TOOLS_SHEAR.location(),
	};

	public static boolean isCustomShears(ItemStack stack) {
		return stack.getItem() instanceof ShearsItemKGY;
	}

	protected float speedBaseline;

	public ShearsItemBuilder(Identifier i) {
		super(i);
		speedBaseline(5f);
		parentModel(KubeAssetGenerator.HANDHELD_ITEM_MODEL);
		unstackable();
		tag(SHEAR_TAGS);
		tool = ShearsItem.createToolProperties();
	}

	public ShearsItemBuilder speedBaseline(float f) {
		speedBaseline = f;
		return this;
	}

	@Override
	public Item createObject() {
		var item = new ShearsItemKGY(this);
		DispenserBlock.registerBehavior(item, new ShearsDispenseItemBehavior());
		return item;
	}

	public static class ShearsItemKGY extends ShearsItem {
		public final ShearsItemBuilder builder;

		public ShearsItemKGY(ShearsItemBuilder builder) {
			super(builder.createItemProperties());
			this.builder = builder;
		}

		@Override
		public float getDestroySpeed(ItemStack itemStack, BlockState blockState) {
			if (blockState.is(BlockTags.LEAVES)) {
				return 15F;
			} else if (blockState.is(Blocks.COBWEB)) {
				return builder.speedBaseline * 3F;
			} else if (blockState.is(Blocks.VINE) || blockState.is(Blocks.GLOW_LICHEN)) {
				return builder.speedBaseline / 2.5F;
			} else if (blockState.is(BlockTags.WOOL)) {
				return builder.speedBaseline;
			} else {
				return super.getDestroySpeed(itemStack, blockState);
			}
		}
	}
}
