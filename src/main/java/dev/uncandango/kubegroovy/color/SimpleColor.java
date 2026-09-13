package dev.uncandango.kubegroovy.color;

import net.minecraft.network.chat.TextColor;
import org.jspecify.annotations.Nullable;

public class SimpleColor implements KubeColor {
	public static final SimpleColor BLACK = new SimpleColor(0xFF000000);
	public static final SimpleColor WHITE = new SimpleColor(0xFFFFFFFF);

	private final int value;
	private @Nullable TextColor textColor;

	public SimpleColor(int v) {
		value = 0xFF000000 | v;
	}

	@Override
	public int kgy$getARGB() {
		return value;
	}

	@Override
	public String kgy$toHexString() {
		return String.format("#%06X", kgy$getRGB());
	}

	@Override
	public String toString() {
		return kgy$toHexString();
	}

	@Override
	public TextColor kgy$createTextColor() {
		if (textColor == null) {
			textColor = TextColor.fromRgb(kgy$getRGB());
		}

		return textColor;
	}
}