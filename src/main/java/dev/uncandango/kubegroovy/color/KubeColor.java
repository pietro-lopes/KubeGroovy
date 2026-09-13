package dev.uncandango.kubegroovy.color;

import com.mojang.serialization.Codec;
import dev.uncandango.kubegroovy.codec.KubeGroovyCodecs;
import dev.uncandango.kubegroovy.plugin.builtin.wrapper.ColorWrapper;
import dev.uncandango.kubegroovy.script.bridge.Context;
import dev.uncandango.kubegroovy.script.annotations.RemapForGY;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import dev.uncandango.kubegroovy.script.annotations.SpecialEquality;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

@RemapPrefixForGY("kgy$")
public interface KubeColor extends SpecialEquality {
	Codec<KubeColor> CODEC = KubeGroovyCodecs.stringResolverCodec(KubeColor::kgy$serialize, ColorWrapper::wrap);
	StreamCodec<ByteBuf, KubeColor> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);
	StreamCodec<ByteBuf, Optional<KubeColor>> OPTIONAL_STREAM_CODEC = ByteBufCodecs.optional(STREAM_CODEC);

	@RemapForGY("getArgb")
	int kgy$getARGB();

	@RemapForGY("getRgb")
	default int kgy$getRGB() {
		return kgy$getARGB() & 0xFFFFFF;
	}

	default int kgy$getFireworkRGB() {
		return kgy$getRGB();
	}

	default String kgy$toHexString() {
		return String.format("#%08X", kgy$getARGB());
	}

	default String kgy$serialize() {
		return kgy$toHexString();
	}

	default TextColor kgy$createTextColor() {
		return TextColor.fromRgb(kgy$getRGB());
	}

	@Override
	default boolean specialEquals(Context cx, @Nullable Object o, boolean shallow) {
		KubeColor c = ColorWrapper.wrap(o);
		return shallow ? (kgy$getARGB() == c.kgy$getARGB()) : (kgy$getRGB() == c.kgy$getRGB());
	}
}