package dev.uncandango.kubegroovy.integration.rei;
/*

import dev.uncandango.kubegroovy.recipe.viewer.AddInformationKubeEvent;
import dev.uncandango.kubegroovy.recipe.viewer.RecipeViewerEntryType;
import dev.uncandango.kubegroovy.script.bridge.Context;
import me.shedaniel.rei.plugin.client.BuiltinClientPlugin;
import net.minecraft.network.chat.Component;

import java.util.List;

public class REIAddInformationKubeEvent implements AddInformationKubeEvent {
	private final RecipeViewerEntryType type;

	public REIAddInformationKubeEvent(RecipeViewerEntryType type) {
		this.type = type;
	}

	@Override
	public void add(Context cx, Object filter, List<Component> info) {
		if (info.isEmpty()) {
			return;
		}

		var in = REIIntegration.ingredientOf(cx, type, filter);

		if (in.isEmpty()) {
			return;
		}

		BuiltinClientPlugin.getInstance().registerInformation(in, info.getFirst(), components -> {
			for (int i = 1; i < info.size(); i++) {
				components.add(info.get(i));
			}

			return components;
		});
	}
}
*/
