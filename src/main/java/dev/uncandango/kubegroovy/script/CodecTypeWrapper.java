package dev.uncandango.kubegroovy.script;

import com.mojang.serialization.Codec;
import dev.uncandango.kubegroovy.util.Cast;
import dev.uncandango.kubegroovy.util.RegistryAccessContainer;
import dev.uncandango.kubegroovy.script.bridge.Context;
import dev.uncandango.kubegroovy.script.type.TypeInfo;
import dev.uncandango.kubegroovy.script.bridge.TypeWrapperFactory;
import org.jspecify.annotations.Nullable;

public record CodecTypeWrapper<T>(Class<T> target, Codec<T> codec, @Nullable T defaultValue) implements TypeWrapperFactory<T> {
	@Override
	@Nullable
	public T wrap(Object o, TypeInfo target) {
		var cx = Context.getCurrent();

		if (o == null) {
			return defaultValue;
		} else if (target.asClass().isInstance(o)) {
			return Cast.to(o);
		} else {
			return RegistryAccessContainer.of().decode(codec, o);
		}
	}
}