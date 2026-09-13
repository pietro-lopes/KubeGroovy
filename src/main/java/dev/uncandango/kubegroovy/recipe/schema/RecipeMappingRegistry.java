package dev.uncandango.kubegroovy.recipe.schema;

import dev.uncandango.kubegroovy.event.KubeEvent;
import dev.uncandango.kubegroovy.script.annotations.HideFromGY;
import net.minecraft.resources.Identifier;

public class RecipeMappingRegistry implements KubeEvent {
	private final RecipeSchemaStorage storage;

	public RecipeMappingRegistry(RecipeSchemaStorage storage) {
		this.storage = storage;
	}

	public void register(String name, Identifier type) {
		storage.mappings.put(name, type);
	}

	@HideFromGY
	public void register(String name, String type) {
		register(name, Identifier.parse(type));
	}
}