package dev.uncandango.kubegroovy.registry;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import dev.uncandango.kubegroovy.error.KubeRuntimeException;
import dev.uncandango.kubegroovy.event.KubeEvent;
import dev.uncandango.kubegroovy.script.SourceLine;
import dev.uncandango.kubegroovy.util.KubeIdentifier;
import dev.uncandango.kubegroovy.script.bridge.Context;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

import java.util.List;
import java.util.function.Supplier;

public class ServerRegistryKubeEvent<T> implements KubeEvent {
	public final ResourceKey<Registry<T>> registryKey;
	private final BuilderTypeRegistryHandler.Info<T> builderInfo;
	public final DynamicOps<JsonElement> jsonOps;
	public final Codec<T> codec;
	private final List<BuilderBase<?>> builders;

	public ServerRegistryKubeEvent(ResourceKey<Registry<T>> registryKey, DynamicOps<JsonElement> jsonOps, Codec<T> codec, List<BuilderBase<?>> builders) {
		this.registryKey = registryKey;
		this.builderInfo = BuilderTypeRegistryHandler.info(registryKey);
		this.jsonOps = jsonOps;
		this.codec = codec;
		this.builders = builders;
	}

	public BuilderBase<? extends T> create(KubeIdentifier id, KubeIdentifier type) {
		var cx = Context.getCurrent();
		var sourceLine = SourceLine.of();
		var t = builderInfo == null ? null : builderInfo.namedType(type.wrapped());

		if (t == null) {
			throw new KubeRuntimeException("Unknown type '" + type + "' for object '" + id + "'!").source(sourceLine);
		}

		var b = t.factory().createBuilder(id.wrapped());

		if (b == null) {
			throw new KubeRuntimeException("Unknown type '" + type + "' for object '" + id + "'!").source(sourceLine);
		} else {
			b.sourceLine = sourceLine;
			b.registryKey = registryKey;
			builders.add(b);
			return b;
		}
	}

	public BuilderBase<? extends T> create(KubeIdentifier id) {
		var cx = Context.getCurrent();
		var sourceLine = SourceLine.of();
		var t = builderInfo == null ? null : builderInfo.defaultType();

		if (t == null && builderInfo != null && builderInfo.directCodec() != null) {
			return createCodecBuilder(sourceLine, id, null);
		} else if (t == null && builderInfo == null) {
			return createCodecBuilder(sourceLine, id, null);
		} else if (t == null) {
			throw new KubeRuntimeException("Registry '" + registryKey.identifier() + "' doesn't have a default type registered!").source(sourceLine);
		}

		var b = t.factory().createBuilder(id.wrapped());

		if (b == null) {
			throw new KubeRuntimeException("Unknown type '" + t.type() + "' for object '" + id + "'!").source(sourceLine);
		} else {
			b.sourceLine = sourceLine;
			b.registryKey = registryKey;
			builders.add(b);
			return b;
		}
	}

	public CustomBuilderObject createCustom(KubeIdentifier id, Supplier<Object> object) {
		var cx = Context.getCurrent();
		var sourceLine = SourceLine.of();

		if (object == null) {
			throw new KubeRuntimeException("Tried to register a null object with id: " + id).source(sourceLine);
		}

		var b = new CustomBuilderObject(id.wrapped(), object);
		b.sourceLine = sourceLine;
		b.registryKey = registryKey;
		builders.add(b);
		return b;
	}

	public CodecRegistryBuilder<T> createFromJson(KubeIdentifier id, JsonElement json) {
		var cx = Context.getCurrent();
		var sourceLine = SourceLine.of();
		return createCodecBuilder(sourceLine, id, json);
	}

	private CodecRegistryBuilder<T> createCodecBuilder(SourceLine sourceLine, KubeIdentifier id, JsonElement json) {
		var b = new CodecRegistryBuilder<>(id.wrapped(), jsonOps, codec);
		b.sourceLine = sourceLine;
		b.registryKey = registryKey;

		if (json != null) {
			b.json(json);
		}

		builders.add(b);
		return b;
	}
}
