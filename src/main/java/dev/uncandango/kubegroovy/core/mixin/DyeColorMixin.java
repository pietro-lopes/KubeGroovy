package dev.uncandango.kubegroovy.core.mixin;

import dev.uncandango.kubegroovy.color.KubeColor;
import net.minecraft.world.item.DyeColor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(DyeColor.class)
public abstract class DyeColorMixin implements KubeColor {
	@Shadow
	@Final
	private int textColor;

	@Shadow
	@Final
	private int fireworkColor;

	@Override
	public int kgy$getARGB() {
		return 0xFF000000 | textColor;
	}

	@Override
	public int kgy$getRGB() {
		return textColor;
	}

	@Override
	public int kgy$getFireworkRGB() {
		return fireworkColor;
	}
}
