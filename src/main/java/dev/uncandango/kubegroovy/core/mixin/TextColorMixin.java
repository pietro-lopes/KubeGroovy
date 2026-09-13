package dev.uncandango.kubegroovy.core.mixin;

import dev.uncandango.kubegroovy.color.KubeColor;
import net.minecraft.network.chat.TextColor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(TextColor.class)
public abstract class TextColorMixin implements KubeColor {
	@Shadow
	@Final
	private int value;

	@Override
	public int kgy$getARGB() {
		return 0xFF000000 | value;
	}

	@Override
	public int kgy$getRGB() {
		return value;
	}

	@Override
	@Invoker("serialize")
	public abstract String kgy$serialize();
}
