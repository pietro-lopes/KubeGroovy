package dev.uncandango.kubegroovy.block.custom;

import dev.uncandango.kubegroovy.block.BlockBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public abstract class ShapedBlockBuilder extends BlockBuilder {
	public ShapedBlockBuilder(Identifier i, String... suffixes) {
		super(i);
		notSolid();
		property(BlockStateProperties.WATERLOGGED);
		texture("kubegroovy:block/unknown");

		for (var s : suffixes) {
			if (id.getPath().endsWith(s)) {
				texture(id.withPath("block/" + id.getPath().substring(0, id.getPath().length() - s.length())).toString());
				break;
			}
		}
	}
}
