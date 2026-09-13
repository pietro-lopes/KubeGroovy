package dev.uncandango.kubegroovy.block.custom;

import dev.uncandango.kubegroovy.client.VariantBlockStateGenerator;
import dev.uncandango.kubegroovy.generator.KubeAssetGenerator;
import dev.uncandango.kubegroovy.util.ID;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;

public class SlabBlockBuilder extends ShapedBlockBuilder {
	public static final Identifier[] SLAB_TAGS = {
		BlockTags.SLABS.location(),
	};

	private static final Identifier MODEL = Identifier.withDefaultNamespace("block/slab");
	private static final Identifier TOP_MODEL = Identifier.withDefaultNamespace("block/slab_top");

	public SlabBlockBuilder(Identifier i) {
		super(i, "_slab");
		tagBoth(SLAB_TAGS);
	}

	@Override
	public Block createObject() {
		return new SlabBlock(createProperties());
	}

	@Override
	protected void generateBlockState(VariantBlockStateGenerator bs) {
		bs.variant("type=bottom", v -> v.model(id.withPath(ID.BLOCK)));
		bs.variant("type=top", v -> v.model(newID("block/", "_top")));
		bs.variant("type=double", v -> v.model(newID("block/", "_double")));
	}

	@Override
	protected void generateBlockModels(KubeAssetGenerator generator) {
		generator.blockModel(id, m -> {
			m.parent(MODEL);
			m.texture("bottom", baseTexture);
			m.texture("top", baseTexture);
			m.texture("side", baseTexture);
		});

		generator.blockModel(newID("", "_top"), m -> {
			m.parent(TOP_MODEL);
			m.texture("bottom", baseTexture);
			m.texture("top", baseTexture);
			m.texture("side", baseTexture);
		});

		generator.blockModel(newID("", "_double"), m -> {
			m.parent(KubeAssetGenerator.CUBE_ALL_BLOCK_MODEL);
			m.texture("all", baseTexture);
		});
	}
}
