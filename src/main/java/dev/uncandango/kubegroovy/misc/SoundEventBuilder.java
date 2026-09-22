package dev.uncandango.kubegroovy.misc;

import dev.uncandango.kubegroovy.client.SoundsGenerator;
import dev.uncandango.kubegroovy.generator.KubeAssetGenerator;
import dev.uncandango.kubegroovy.registry.BuilderBase;
import dev.uncandango.kubegroovy.script.annotations.ReturnsSelf;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

import java.util.function.Consumer;

@ReturnsSelf
public class SoundEventBuilder extends BuilderBase<SoundEvent> {

	protected Consumer<SoundsGenerator.SoundGen> assetGen;

	public SoundEventBuilder(Identifier i) {
		super(i);
		assetGen = gen -> gen.sound(id.toString()).subtitle(id.toLanguageKey("sound"));
	}

	public SoundEventBuilder sounds(Consumer<SoundsGenerator.SoundGen> gen) {
		assetGen = gen;
		return this;
	}

	@Override
	public SoundEvent createObject() {
		return SoundEvent.createVariableRangeEvent(id);
	}

	@Override
	public void generateAssets(KubeAssetGenerator generator) {
		generator.sounds(id.getNamespace(), g -> g.addSound(id.getPath(), assetGen));
	}
}
