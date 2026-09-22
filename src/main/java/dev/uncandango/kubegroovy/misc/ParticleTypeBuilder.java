package dev.uncandango.kubegroovy.misc;

import com.mojang.serialization.MapCodec;
import dev.uncandango.kubegroovy.client.ParticleGenerator;
import dev.uncandango.kubegroovy.generator.KubeAssetGenerator;
import dev.uncandango.kubegroovy.registry.BuilderBase;
import dev.uncandango.kubegroovy.script.annotations.ReturnsSelf;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

@ReturnsSelf
public class ParticleTypeBuilder extends BuilderBase<ParticleType<?>> {
	protected boolean overrideLimiter;
	protected @Nullable MapCodec<ParticleOptions> codec;
	protected @Nullable StreamCodec<? super RegistryFriendlyByteBuf, ParticleOptions> streamCodec;
	protected Consumer<ParticleGenerator> assetGen;

	public ParticleTypeBuilder(Identifier i) {
		super(i);
		overrideLimiter = false;
		assetGen = gen -> gen.texture(id.toString());
	}

	@Override
	public ParticleType<?> createObject() {
		if (codec != null && streamCodec != null) {
			return new ComplexParticleType(this);
		}

		return new BasicParticleType(overrideLimiter);
	}

	public ParticleTypeBuilder overrideLimiter(boolean o) {
		overrideLimiter = o;
		return this;
	}

	public ParticleTypeBuilder codec(MapCodec<ParticleOptions> c) {
		codec = c;
		return this;
	}

	public ParticleTypeBuilder streamCodec(StreamCodec<? super RegistryFriendlyByteBuf, ParticleOptions> s) {
		streamCodec = s;
		return this;
	}

	public ParticleTypeBuilder textures(List<String> textures) {
		assetGen = g -> g.textures(textures);
		return this;
	}

	public ParticleTypeBuilder texture(String texture) {
		assetGen = g -> g.texture(texture);
		return this;
	}

	@Override
	public void generateAssets(KubeAssetGenerator generator) {
		generator.particle(id, assetGen);
	}
}
