package dev.uncandango.kubegroovy.fluid;

import dev.uncandango.kubegroovy.generator.KubeAssetGenerator;
import dev.uncandango.kubegroovy.item.ItemBuilder;
import net.minecraft.world.item.BucketItem;

public class FluidBucketItemBuilder extends ItemBuilder {
	protected final FluidBuilder fluidBuilder;

	public FluidBucketItemBuilder(FluidBuilder b) {
		super(b.newID("", "_bucket"));
		fluidBuilder = b;
		maxStackSize(1);
	}

	@Override
	public BucketItem createObject() {
		return new BucketItem(fluidBuilder.get(), createItemProperties());
	}

	@Override
	public void generateAssets(KubeAssetGenerator generator) {
		if (hasCustomModel()) {
			super.generateAssets(generator);
		}
	}
}
