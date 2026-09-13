package dev.uncandango.kubegroovy.fluid;

import dev.uncandango.kubegroovy.KubeGroovy;
import dev.uncandango.kubegroovy.block.BlockRenderType;
import net.minecraft.resources.Identifier;

public class ThinFluidBuilder extends FluidBuilder {
	public ThinFluidBuilder(Identifier i) {
		super(i);
		fluidType.tint(WATER_COLOR);
		fluidType.stillTexture(KubeGroovy.id("block/thin_fluid_still"));
		fluidType.flowingTexture(KubeGroovy.id("block/thin_fluid_flow"));
		fluidType.renderType(BlockRenderType.TRANSLUCENT);
		fluidType.fallDistanceModifier(0F);
	}
}