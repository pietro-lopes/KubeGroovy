package dev.uncandango.kubegroovy.util;

import dev.uncandango.kubegroovy.script.bridge.Context;
import net.minecraft.nbt.Tag;

public interface NBTSerializable {
	Tag toNBT();
}