package dev.uncandango.kubegroovy.fluid;

import dev.uncandango.kubegroovy.registry.BuilderBase;
import net.minecraft.world.level.material.FlowingFluid;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;

public class FlowingFluidBuilder extends BuilderBase<FlowingFluid> {
	protected final FluidBuilder fluidBuilder;

	public FlowingFluidBuilder(FluidBuilder b) {
		super(b.newID("flowing_", ""));
		fluidBuilder = b;
	}

	@Override
	public FlowingFluid createObject() {
		return new BaseFlowingFluid.Flowing(fluidBuilder.createProperties());
	}
}
