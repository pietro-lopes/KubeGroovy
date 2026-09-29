package dev.uncandango.kubegroovy.core;

import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.fog.FogRenderer;

public interface GameRendererKGY {
	GuiRenderer kgy$getGuiRenderer();

	FogRenderer kgy$getFogRenderer();
}
