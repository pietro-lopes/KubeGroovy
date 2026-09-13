package dev.uncandango.kubegroovy.event;

import dev.uncandango.kubegroovy.plugin.KubeGroovyPlugins;
import dev.uncandango.kubegroovy.plugin.builtin.BuiltinKubeGroovyPlugin;
import dev.uncandango.kubegroovy.util.Lazy;

import java.util.LinkedHashMap;
import java.util.Map;

/// A map storing all known event groups. Elements of this map are added as bindings by the [BuiltinKubeGroovyPlugin].
public record EventGroups(Map<String, EventGroup> map) implements EventGroupRegistry {
	public static final Lazy<EventGroups> ALL = Lazy.of(() -> {
		var groups = new EventGroups(new LinkedHashMap<>());
		KubeGroovyPlugins.forEachPlugin(p -> p.registerEvents(groups));
		return groups;
	});

	@Override
	public void register(EventGroup group) {
		map.put(group.name, group);
	}
}
