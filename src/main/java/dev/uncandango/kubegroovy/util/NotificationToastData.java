package dev.uncandango.kubegroovy.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.uncandango.kubegroovy.client.icon.KubeIcon;
import dev.uncandango.kubegroovy.codec.KubeGroovyCodecs;
import dev.uncandango.kubegroovy.codec.KubeGroovyStreamCodecs;
import dev.uncandango.kubegroovy.color.KubeColor;
import dev.uncandango.kubegroovy.color.SimpleColor;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Optional;

public record NotificationToastData(
	Duration duration,
	Component text,
	Optional<KubeIcon> icon,
	int iconSize,
	Optional<KubeColor> outlineColor,
	Optional<KubeColor> borderColor,
	Optional<KubeColor> backgroundColor,
	boolean textShadow
) {
	public static final Duration DEFAULT_DURATION = Duration.ofSeconds(5L);
	public static final KubeColor DEFAULT_OUTLINE_COLOR = SimpleColor.BLACK;
	public static final KubeColor DEFAULT_BORDER_COLOR = new SimpleColor(0x472954);
	public static final KubeColor DEFAULT_BACKGROUND_COLOR = new SimpleColor(0x241335);

	public static final MapCodec<NotificationToastData> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
		KubeGroovyCodecs.DURATION.optionalFieldOf("duration", DEFAULT_DURATION).forGetter(NotificationToastData::duration),
		ComponentSerialization.CODEC.optionalFieldOf("text", Component.empty()).forGetter(NotificationToastData::text),
		KubeIcon.CODEC.optionalFieldOf("icon").forGetter(NotificationToastData::icon),
		Codec.INT.optionalFieldOf("icon_size", 16).forGetter(NotificationToastData::iconSize),
		KubeColor.CODEC.optionalFieldOf("outline_color").forGetter(NotificationToastData::outlineColor),
		KubeColor.CODEC.optionalFieldOf("border_color").forGetter(NotificationToastData::borderColor),
		KubeColor.CODEC.optionalFieldOf("background_color").forGetter(NotificationToastData::backgroundColor),
		Codec.BOOL.optionalFieldOf("text_shadow", false).forGetter(NotificationToastData::textShadow)
	).apply(instance, NotificationToastData::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, NotificationToastData> STREAM_CODEC = KubeGroovyStreamCodecs.composite(
		KubeGroovyStreamCodecs.DURATION,
		NotificationToastData::duration,
		ComponentSerialization.STREAM_CODEC,
		NotificationToastData::text,
		KubeIcon.OPTIONAL_STREAM_CODEC,
		NotificationToastData::icon,
		ByteBufCodecs.VAR_INT,
		NotificationToastData::iconSize,
		KubeColor.OPTIONAL_STREAM_CODEC,
		NotificationToastData::outlineColor,
		KubeColor.OPTIONAL_STREAM_CODEC,
		NotificationToastData::borderColor,
		KubeColor.OPTIONAL_STREAM_CODEC,
		NotificationToastData::backgroundColor,
		ByteBufCodecs.BOOL,
		NotificationToastData::textShadow,
		NotificationToastData::new
	);

	public static NotificationToastData ofText(Component text) {
		return new NotificationToastData(DEFAULT_DURATION, text, Optional.empty(), 16, Optional.empty(), Optional.empty(), Optional.empty(), false);
	}

	public static NotificationToastData ofTitle(Component title, @Nullable Component text) {
		return text == null ? ofText(title) : ofText(Component.empty().append(title).append("\n").append(text));
	}
}
