package dev.uncandango.kubegroovy.block;

import dev.uncandango.kubegroovy.client.LangKubeEvent;
import dev.uncandango.kubegroovy.generator.KubeAssetGenerator;
import dev.uncandango.kubegroovy.item.ItemBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

public class BlockItemBuilder extends ItemBuilder {
	protected final BlockBuilder blockBuilder;

	public BlockItemBuilder(BlockBuilder b, Identifier i) {
		super(i);
		this.blockBuilder = b;
	}

	@dev.uncandango.kubegroovy.script.annotations.HideFromGY
	public final BlockBuilder kgy$getBlockBuilder() {
		return blockBuilder;
	}

	@Override
	public Item createObject() {
		return new BlockItem(blockBuilder.get(),
			createItemProperties()
				.overrideDescription(blockBuilder.getBuilderTranslationKey())
		);
	}

	@Override
	public String getTranslationKeyGroup() {
		return "block";
	}

	@Override
	public void generateAssets(KubeAssetGenerator generator) {
	}

	@Override
	public void generateLang(LangKubeEvent lang) {
		// To allow subclasses like SeedItemBuilder to still generate normally
		if (getClass() != BlockItemBuilder.class) {
			super.generateLang(lang);
		}
	}
}
