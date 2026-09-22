package dev.uncandango.kubegroovy.core;

import dev.uncandango.kubegroovy.plugin.builtin.event.ServerEvents;
import dev.uncandango.kubegroovy.registry.RegistryObjectStorage;
import dev.uncandango.kubegroovy.server.ServerScriptManager;
import dev.uncandango.kubegroovy.server.tag.TagKubeEvent;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagLoader;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

public interface TagLoaderKGY<T> {
	default void kgy$customTags(Map<Identifier, List<TagLoader.EntryWithSource>> map) {
		var ssm = kgy$getServerScriptManager();
		var reg = kgy$getRegistry();

		if (ssm == null || reg == null) {
			return;
		}

		var event = TagKubeEvent.fromRegistry(reg, map);

		var objStorage = RegistryObjectStorage.of(reg.key());
		for (var builder : objStorage.objects.values()) {
			for (var s : builder.kgy$getDefaultTags()) {
				event.add(s, builder.getId());
			}
		}

		for (var e : ssm.serverRegistryTags.entrySet()) {
			for (var tag : e.getValue()) {
				event.add(tag, e.getKey());
			}
		}

		ServerEvents.TAGS.post(event, reg.key());

		// TODO: statistics? i used to like the "tag event posted; x added, y removed in z milliseconds"
		map.clear();
		event.build(map::put);
	}

	void kgy$init(@Nullable ServerScriptManager serverScriptManager, Registry<T> registry);

	@Nullable
	ServerScriptManager kgy$getServerScriptManager();

	@Nullable
	Registry<T> kgy$getRegistry();
}
