package dev.uncandango.kubegroovy.server;

import dev.latvian.mods.betteradvancedtooltips.BATIcons;
import dev.uncandango.kubegroovy.plugin.builtin.wrapper.TextIcons;
import dev.uncandango.kubegroovy.util.TimeGY;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class ChangesForChat {
	public static int recipesAdded = 0;
	public static int recipesRemoved = 0;
	public static int recipesModified = 0;
	public static long recipesMs = 0L;

	public static void print(Consumer<Component> out) {
		if (recipesAdded != 0 || recipesRemoved != 0 || recipesModified != 0 || recipesMs != 0L) {
			out.accept(Component.empty()
				.append(Component.empty().append(TextIcons.CRAFTING).kgy$hover(Component.literal("Recipe Changes")))
				.append(Component.empty()
					.kgy$hover(Component.literal("Added Recipes"))
					.append(" ")
					.append(BATIcons.PLUS)
					.append(BATIcons.SMALL_SPACE)
					.append(String.valueOf(recipesAdded))
					.append(" ")
				)
				.append("|")
				.append(Component.empty()
					.kgy$hover(Component.literal("Removed Recipes"))
					.append(" ")
					.append(BATIcons.MINUS)
					.append(BATIcons.SMALL_SPACE)
					.append(String.valueOf(recipesRemoved))
					.append(" ")
				)
				.append("|")
				.append(Component.empty()
					.kgy$hover(Component.literal("Modified Recipes"))
					.append(" ")
					.append(BATIcons.TILDE)
					.append(BATIcons.SMALL_SPACE)
					.append(String.valueOf(recipesModified))
					.append(" ")
				)
				.append("| " + TimeGY.msToString(recipesMs))

			);
		}
	}
}