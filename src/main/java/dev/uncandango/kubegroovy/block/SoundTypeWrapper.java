package dev.uncandango.kubegroovy.block;

import com.google.gson.JsonPrimitive;
import dev.uncandango.kubegroovy.error.KubeRuntimeException;
import dev.uncandango.kubegroovy.script.SourceLine;
import dev.uncandango.kubegroovy.script.bridge.Context;
import dev.uncandango.kubegroovy.script.bridge.Undefined;
import dev.uncandango.kubegroovy.script.type.RecordTypeInfo;
import dev.uncandango.kubegroovy.script.type.TypeInfo;
import dev.uncandango.kubegroovy.script.bridge.TypeWrapperFactory;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.SoundType;
import net.neoforged.neoforge.common.util.DeferredSoundType;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class SoundTypeWrapper implements TypeWrapperFactory<SoundType> {
	public static final SoundTypeWrapper INSTANCE = new SoundTypeWrapper();

	private @Nullable Map<String, SoundType> map;

	public Map<String, SoundType> getMap() {
		if (map == null) {
			map = new LinkedHashMap<>();
			map.put("empty", SoundType.EMPTY);

			try {
				for (var field : SoundType.class.getFields()) {
					if (field.getType() == SoundType.class && Modifier.isPublic(field.getModifiers()) && Modifier.isStatic(field.getModifiers())) {
						try {
							map.put(field.getName().toLowerCase(Locale.ROOT), (SoundType) field.get(null));
						} catch (Exception ex) {
							ex.printStackTrace();
						}
					}
				}
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}

		return map;
	}

	@Override
	public SoundType wrap(Object o, TypeInfo target) {
		var cx = Context.getCurrent();
		return switch (o) {
			case SoundType t -> t;
			case null -> throw new KubeRuntimeException("SoundType cannot be null!").source(SourceLine.of());
			case Undefined u -> throw new KubeRuntimeException("Cannot wrap undefined as SoundType!").source(SourceLine.of());
//			case Scriptable s when Undefined.isUndefined(s) -> throw new KubeRuntimeException("Cannot wrap undefined as SoundType!").source(SourceLine.of(cx));
			case JsonPrimitive j -> wrap(j.getAsString(), target);
			case Identifier id -> wrap(id.toString(), target);
			case CharSequence cs -> {
				var soundType = getMap().get(cs.toString());
				if (soundType != null) {
					yield soundType;
				}

				throw new KubeRuntimeException("Unknown SoundType '%s'".formatted(o)).source(SourceLine.of());
			}
			default -> ((WrappedSoundType) WrappedSoundType.TYPE_INFO.wrap(o, target)).toSoundType();
		};
	}

	private record WrappedSoundType(
		float volumeIn, float pitchIn,
		Holder<SoundEvent> breakSound,
		Holder<SoundEvent> stepSound,
		Holder<SoundEvent> placeSound,
		Holder<SoundEvent> hitSound,
		Holder<SoundEvent> fallSound
	) {
		private static final RecordTypeInfo TYPE_INFO = (RecordTypeInfo) TypeInfo.of(WrappedSoundType.class);

		public SoundType toSoundType() {
			return new DeferredSoundType(volumeIn, pitchIn, breakSound::value, stepSound::value, placeSound::value, hitSound::value, fallSound::value);
		}
	}
}
