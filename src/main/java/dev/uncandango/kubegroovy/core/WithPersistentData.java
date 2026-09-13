package dev.uncandango.kubegroovy.core;

import dev.uncandango.kubegroovy.script.annotations.RemapForGY;
import net.minecraft.nbt.CompoundTag;

public interface WithPersistentData extends MessageSenderKGY {
	@RemapForGY("getPersistentData")
	default CompoundTag kgy$getPersistentData() {
		throw new NoMixinException();
	}
}
