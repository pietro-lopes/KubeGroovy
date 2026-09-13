package dev.uncandango.kubegroovy.core.mixin;

import com.mojang.serialization.Codec;
import dev.uncandango.kubegroovy.util.WithCodec;
import dev.uncandango.kubegroovy.script.bridge.Context;
import net.minecraft.network.chat.ClickEvent;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ClickEvent.class)
public interface ClickEventMixin extends WithCodec {
	@Override
	default Codec<?> getCodec(Context cx) {
		return ClickEvent.CODEC;
	}
}
