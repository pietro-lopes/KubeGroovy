package dev.uncandango.kubegroovy.client;

import dev.uncandango.kubegroovy.core.ClientPacketListenerKGY;
import dev.uncandango.kubegroovy.net.KubeServerData;
import dev.uncandango.kubegroovy.recipe.viewer.server.RecipeViewerData;
import dev.uncandango.kubegroovy.recipe.viewer.server.RemoteRecipeViewerDataUpdatedEvent;
import dev.uncandango.kubegroovy.text.tooltip.ItemTooltipData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.common.NeoForge;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class KubeSessionData {
	@Nullable
	public static KubeSessionData of(@Nullable ClientPacketListener listener) {
		return listener == null ? null : ((ClientPacketListenerKGY) listener).kgy$sessionData();
	}

	@Nullable
	public static KubeSessionData of(@Nullable Minecraft mc) {
		return mc == null ? null : of(mc.getConnection());
	}

	public @Nullable Identifier activePostShader = null;
	public @Nullable RecipeViewerData recipeViewerData = null;
	public List<ItemTooltipData> itemTooltips = List.of();

	public void sync(KubeServerData data) {
		recipeViewerData = data.recipeViewerData().orElse(null);
		itemTooltips = List.copyOf(data.itemTooltipData());

		NeoForge.EVENT_BUS.post(new RemoteRecipeViewerDataUpdatedEvent(recipeViewerData));
	}
}
