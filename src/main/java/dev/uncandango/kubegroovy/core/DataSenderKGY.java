package dev.uncandango.kubegroovy.core;

import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import net.minecraft.nbt.CompoundTag;
import org.jspecify.annotations.Nullable;

@RemapPrefixForGY("kgy$")
public interface DataSenderKGY {
	default void kgy$sendData(String channel, @Nullable CompoundTag data) {
		throw new NoMixinException();
	}

	default void kgy$sendData(String channel) {
		kgy$sendData(channel, null);
	}
}