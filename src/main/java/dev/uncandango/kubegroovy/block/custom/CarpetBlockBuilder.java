package dev.uncandango.kubegroovy.block.custom;

import dev.uncandango.kubegroovy.generator.KubeAssetGenerator;
import dev.uncandango.kubegroovy.script.annotations.ReturnsSelf;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CarpetBlock;

@ReturnsSelf
public class CarpetBlockBuilder extends ShapedBlockBuilder {
	public static final Identifier[] CARPET_TAGS = {
		BlockTags.WOOL_CARPETS.location(),
	};

	private static final Identifier MODEL = Identifier.withDefaultNamespace("block/carpet");

	public CarpetBlockBuilder(Identifier i) {
		super(i, "_carpet");
		tagBoth(CARPET_TAGS);
	}

	@Override
	public Block createObject() {
		return new CarpetBlock(createProperties());
	}

	@Override
	protected void generateBlockModels(KubeAssetGenerator generator) {
		generator.blockModel(id, m -> {
			m.parent(MODEL);
			m.texture("wool", baseTexture);
		});
	}
}
