package dev.uncandango.kubegroovy.core.mixin;

import dev.uncandango.kubegroovy.CommonProperties;
import dev.uncandango.kubegroovy.core.ContainerKGY;
import net.minecraft.world.Container;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(Container.class)
public interface ContainerMixin extends ContainerKGY {
	@ModifyConstant(method = "getMaxStackSize()I", constant = @Constant(intValue = 99))
	private int kgy$maxSlotSize(int original) {
		return CommonProperties.get().getMaxSlotSize(original);
	}
}
