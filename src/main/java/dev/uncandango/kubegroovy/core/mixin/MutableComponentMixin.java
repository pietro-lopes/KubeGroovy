package dev.uncandango.kubegroovy.core.mixin;

import dev.uncandango.kubegroovy.core.ComponentKGY;
import dev.uncandango.kubegroovy.script.annotations.HideFromGY;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(MutableComponent.class)
@RemapPrefixForGY("kgy$")
public abstract class MutableComponentMixin implements ComponentKGY {

	// hidden to avoid ambiguity, the type wrapper should wrap strings to TextComponent anyways
	@HideFromGY
	@Shadow
	public abstract MutableComponent append(String string);
}
