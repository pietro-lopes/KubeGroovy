package dev.uncandango.kubegroovy.core;

import com.google.common.collect.Iterators;
import com.mojang.serialization.Codec;
import dev.uncandango.kubegroovy.color.KubeColor;
import dev.uncandango.kubegroovy.plugin.builtin.wrapper.TextWrapper;
import dev.uncandango.kubegroovy.util.WithCodec;
import dev.uncandango.kubegroovy.util.WrappedGY;
import dev.uncandango.kubegroovy.script.bridge.Context;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

/// Extensions for components, will be injected into
/// [MutableComponent] at runtime.
@RemapPrefixForGY("kgy$")
public interface ComponentKGY extends Component, WithCodec, WrappedGY {

	default Iterable<Component> kgy$asIterable() {
		return () -> {
			if (!kgy$hasSiblings()) {
				return Iterators.forArray(kgy$self());
			}

			List<Component> list = new LinkedList<>();
			list.add(kgy$self());

			for (var child : getSiblings()) {
				if (child instanceof ComponentKGY wrapped) {
					wrapped.forEach(list::add);
				} else {
					list.add(child);
				}
			}

			return list.iterator();
		};
	}

	default MutableComponent kgy$self() {
		return (MutableComponent) this;
	}

	@Override
	default Codec<?> getCodec() {
		var cx = Context.getCurrent();
		return ComponentSerialization.CODEC;
	}

	default boolean kgy$hasStyle() {
		return getStyle() != null && !getStyle().isEmpty();
	}

	default boolean kgy$hasSiblings() {
		return !getSiblings().isEmpty();
	}

	default void forEach(Consumer<? super Component> action) {
		kgy$asIterable().forEach(action);
	}

	// region ChatFormatting extensions
	default MutableComponent kgy$black() {
		return kgy$self().withStyle(ChatFormatting.BLACK);
	}

	default MutableComponent kgy$darkBlue() {
		return kgy$self().withStyle(ChatFormatting.DARK_BLUE);
	}

	default MutableComponent kgy$darkGreen() {
		return kgy$self().withStyle(ChatFormatting.DARK_GREEN);
	}

	default MutableComponent kgy$darkAqua() {
		return kgy$self().withStyle(ChatFormatting.DARK_AQUA);
	}

	default MutableComponent kgy$darkRed() {
		return kgy$self().withStyle(ChatFormatting.DARK_RED);
	}

	default MutableComponent kgy$darkPurple() {
		return kgy$self().withStyle(ChatFormatting.DARK_PURPLE);
	}

	default MutableComponent kgy$gold() {
		return kgy$self().withStyle(ChatFormatting.GOLD);
	}

	default MutableComponent kgy$gray() {
		return kgy$self().withStyle(ChatFormatting.GRAY);
	}

	default MutableComponent kgy$darkGray() {
		return kgy$self().withStyle(ChatFormatting.DARK_GRAY);
	}

	default MutableComponent kgy$blue() {
		return kgy$self().withStyle(ChatFormatting.BLUE);
	}

	default MutableComponent kgy$green() {
		return kgy$self().withStyle(ChatFormatting.GREEN);
	}

	default MutableComponent kgy$aqua() {
		return kgy$self().withStyle(ChatFormatting.AQUA);
	}

	default MutableComponent kgy$red() {
		return kgy$self().withStyle(ChatFormatting.RED);
	}

	default MutableComponent kgy$lightPurple() {
		return kgy$self().withStyle(ChatFormatting.LIGHT_PURPLE);
	}

	default MutableComponent kgy$yellow() {
		return kgy$self().withStyle(ChatFormatting.YELLOW);
	}

	default MutableComponent kgy$white() {
		return kgy$self().withStyle(ChatFormatting.WHITE);
	}
	// endregion ChatFormatting extensions

	// region Style extensions
	default MutableComponent kgy$color(@Nullable KubeColor c) {
		var col = c == null ? null : c.kgy$createTextColor();
		return kgy$self().setStyle(getStyle().withColor(col));
	}

	default MutableComponent kgy$noColor() {
		return kgy$color(null);
	}

	default MutableComponent kgy$bold(@Nullable Boolean value) {
		return kgy$self().setStyle(getStyle().withBold(value));
	}

	default MutableComponent kgy$bold() {
		return kgy$bold(true);
	}

	default MutableComponent kgy$italic(@Nullable Boolean value) {
		return kgy$self().setStyle(getStyle().withItalic(value));
	}

	default MutableComponent kgy$italic() {
		return kgy$italic(true);
	}

	default MutableComponent kgy$underlined(@Nullable Boolean value) {
		return kgy$self().setStyle(getStyle().withUnderlined(value));
	}

	default MutableComponent kgy$underlined() {
		return kgy$underlined(true);
	}

	default MutableComponent kgy$strikethrough(@Nullable Boolean value) {
		return kgy$self().setStyle(getStyle().withStrikethrough(value));
	}

	default MutableComponent kgy$strikethrough() {
		return kgy$strikethrough(true);
	}

	default MutableComponent kgy$obfuscated(@Nullable Boolean value) {
		return kgy$self().setStyle(getStyle().withObfuscated(value));
	}

	default MutableComponent kgy$obfuscated() {
		return kgy$obfuscated(true);
	}

	default MutableComponent kgy$insertion(@Nullable String s) {
		return kgy$self().setStyle(getStyle().withInsertion(s));
	}

	default MutableComponent kgy$font(@Nullable Identifier s) {
		FontDescription fd = s == null ? FontDescription.DEFAULT : new FontDescription.Resource(s);
		return kgy$self().setStyle(getStyle().withFont(fd));
	}


	default MutableComponent kgy$click(@Nullable ClickEvent s) {
		return kgy$self().setStyle(getStyle().withClickEvent(s));
	}

	default MutableComponent kgy$clickRunCommand(String command) {
		return kgy$click(new ClickEvent.RunCommand(command));
	}

	default MutableComponent kgy$clickSuggestCommand(String command) {
		return kgy$click(new ClickEvent.SuggestCommand(command));
	}

	default MutableComponent kgy$clickCopy(String text) {
		return kgy$click(new ClickEvent.CopyToClipboard(text));
	}

	default MutableComponent kgy$clickChangePage(String page) {
		int p;
		try {
			p = Integer.parseInt(page);
		} catch (NumberFormatException ignored) {
			p = 1;
		}
		return kgy$click(new ClickEvent.ChangePage(Math.max(1, p)));
	}

	default MutableComponent kgy$clickOpenUrl(String url) {
		try {
			return kgy$click(new ClickEvent.OpenUrl(URI.create(url)));
		} catch (IllegalArgumentException ignored) {
			return (MutableComponent) this;
		}
	}

	default MutableComponent kgy$clickOpenFile(String path) {
		return kgy$click(new ClickEvent.OpenFile(path));
	}


	default MutableComponent kgy$hover(@Nullable Component s) {
		return kgy$self().setStyle(getStyle().withHoverEvent(s == null ? null : new HoverEvent.ShowText(s)));
	}

	default boolean kgy$isEmpty() {
		return TextWrapper.isEmpty(kgy$self());
	}

	// endregion Style extensions
}
