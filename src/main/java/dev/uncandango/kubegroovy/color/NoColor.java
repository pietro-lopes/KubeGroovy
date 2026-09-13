package dev.uncandango.kubegroovy.color;

import net.minecraft.network.chat.TextColor;

public final class NoColor implements KubeColor {
	private static final TextColor TEXT_COLOR = TextColor.fromRgb(0);

	@Override
	public int kgy$getARGB() {
		return 0;
	}

	@Override
	public int kgy$getRGB() {
		return 0;
	}

	@Override
	public String kgy$toHexString() {
		return "#00000000";
	}

	@Override
	public String kgy$serialize() {
		return "none";
	}

	@Override
	public TextColor kgy$createTextColor() {
		return TEXT_COLOR;
	}
}
