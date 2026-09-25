package dev.uncandango.kubegroovy.integration.jei;

import dev.uncandango.kubegroovy.recipe.viewer.RecipeViewerEntryType;
import dev.uncandango.kubegroovy.recipe.viewer.RegisterSubtypesKubeEvent;
import dev.uncandango.kubegroovy.recipe.viewer.SubtypeInterpreter;
import dev.uncandango.kubegroovy.script.bridge.Context;
import mezz.jei.api.ingredients.IIngredientTypeWithSubtypes;
import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.registration.ISubtypeRegistration;
import net.minecraft.core.component.DataComponentType;
import org.jspecify.annotations.Nullable;

import java.util.List;

@SuppressWarnings({"rawtypes", "unchecked"})
public class JEIRegisterSubtypesKubeEvent implements RegisterSubtypesKubeEvent {
	public record JEISubtypeInterpreter<T>(SubtypeInterpreter interpreter) implements ISubtypeInterpreter<T> {
		@Override
		@Nullable
		public Object getSubtypeData(T ingredient, UidContext context) {
			return interpreter.apply(ingredient);
		}
	}

	private final RecipeViewerEntryType type;
	private final IIngredientTypeWithSubtypes ingredientType;
	private final ISubtypeRegistration registration;

	public JEIRegisterSubtypesKubeEvent(RecipeViewerEntryType type, IIngredientTypeWithSubtypes<?, ?> ingredientType, ISubtypeRegistration registration) {
		this.type = type;
		this.ingredientType = ingredientType;
		this.registration = registration;
	}

	@Override
	public void register(Object filter, SubtypeInterpreter interpreter) {
		var cx = Context.getCurrent();
		var in = new JEISubtypeInterpreter<>(interpreter);

		for (var item : JEIIntegration.getEntries(type, cx, filter)) {
			registration.registerSubtypeInterpreter(ingredientType, type.getBase(item), in);
		}
	}

	@Override
	public void useComponents(Object filter, List<DataComponentType<?>> components) {
		var cx = Context.getCurrent();
		var in = DataComponentTypeInterpreter.of(components);

		for (var item : JEIIntegration.getEntries(type, cx, filter)) {
			registration.registerSubtypeInterpreter(ingredientType, type.getBase(item), in);
		}
	}
}