package dev.uncandango.kubegroovy.block.custom;

import com.mojang.serialization.MapCodec;
import dev.uncandango.kubegroovy.block.BlockBuilder;
import dev.uncandango.kubegroovy.color.KubeColor;
import dev.uncandango.kubegroovy.color.SimpleColor;
import dev.uncandango.kubegroovy.script.annotations.ReturnsSelf;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;

@ReturnsSelf
public class FallingBlockBuilder extends BlockBuilder {
	private KubeColor dustColor = new SimpleColor(0x807C7B);

	public FallingBlockBuilder(Identifier i) {
		super(i);
	}

	@Override
	public Block createObject() {
		return new KubeGroovyFallingBlock(createProperties());
	}

	public FallingBlockBuilder dustColor(KubeColor color) {
		dustColor = color;
		return this;
	}

	static class KubeGroovyFallingBlock extends FallingBlock {
		private static final MapCodec<KubeGroovyFallingBlock> CODEC = simpleCodec(KubeGroovyFallingBlock::new);

		public KubeGroovyFallingBlock(Properties p) {
			super(p);
		}

		@Override
		protected MapCodec<KubeGroovyFallingBlock> codec() {
			return CODEC;
		}

		@Override
		public int getDustColor(BlockState state, BlockGetter level, BlockPos pos) {
			return ((FallingBlockBuilder) kgy$getBlockBuilder()).dustColor.kgy$getARGB();
		}
	}
}
