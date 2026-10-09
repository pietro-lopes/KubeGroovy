package dev.uncandango.kubegroovy.script;

import com.mojang.serialization.MapCodec;
import dev.uncandango.kubegroovy.util.Cast;
import dev.uncandango.kubegroovy.util.RegistryAccessContainer;
import dev.uncandango.kubegroovy.script.bridge.Context;
import dev.uncandango.kubegroovy.script.type.TypeInfo;
import dev.uncandango.kubegroovy.script.bridge.TypeWrapperFactory;

public record MapCodecTypeWrapper<T>(Class<T> target, MapCodec<T> codec, T defaultValue) implements TypeWrapperFactory<T> {
	@Override
	public T wrap(Object o, TypeInfo target) {
		var cx = Context.getCurrent();

		if (o == null) {
			return defaultValue;
		} else if (target.asClass().isInstance(o)) {
			return Cast.to(o);
		} else {
			return RegistryAccessContainer.of().decodeMap(codec, o);
		}
	}
}