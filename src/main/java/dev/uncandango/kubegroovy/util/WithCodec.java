package dev.uncandango.kubegroovy.util;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import dev.uncandango.kubegroovy.script.bridge.Context;
import net.minecraft.nbt.Tag;

public interface WithCodec extends NBTSerializable, JsonSerializable {
	Codec<?> getCodec();

	@Override
	default Tag toNBT() {
		return getCodec().encodeStart(RegistryAccessContainer.of().nbt(), Cast.to(this)).getOrThrow();
	}

	@Override
	default JsonElement toJson() {
		return getCodec().encodeStart(RegistryAccessContainer.of().json(), Cast.to(this)).getOrThrow();
	}
}