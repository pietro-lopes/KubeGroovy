package dev.uncandango.kubegroovy.recipe.filter;

import dev.uncandango.kubegroovy.script.bridge.Context;
import net.neoforged.bus.api.Event;

import java.util.List;
import java.util.Map;

public class RecipeFilterParseEvent extends Event {
	public final Context cx;
	public final List<RecipeFilter> filters;
	public final Map<?, ?> map;

	public RecipeFilterParseEvent(Context cx, List<RecipeFilter> filters, Map<?, ?> map) {
		this.cx = cx;
		this.filters = filters;
		this.map = map;
	}
}
