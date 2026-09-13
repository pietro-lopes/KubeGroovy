package dev.uncandango.kubegroovy.core;

import dev.uncandango.kubegroovy.player.EntityArrayList;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RemapPrefixForGY("kgy$")
public interface EntityCollectionKGY {
	Iterable<? extends Entity> kgy$getMcEntities();

	default List<? extends Player> kgy$getMcPlayers() {
		var list = new ArrayList<Player>(10);

		for (var entity : kgy$getMcEntities()) {
			if (entity instanceof Player p) {
				list.add(p);
			}
		}

		return list;
	}

	default EntityArrayList<? extends Player> kgy$getPlayers() {
		return new EntityArrayList<>(kgy$getMcPlayers());
	}

	default EntityArrayList<? extends Entity> kgy$getEntities() {
		return new EntityArrayList<>(kgy$getMcEntities());
	}

	default EntityArrayList<? extends Entity> kgy$getEntitiesWithin(AABB aabb) {
		if (aabb == null || aabb == AABB.INFINITE) {
			return kgy$getEntities();
		}

		var list = new EntityArrayList<>(10);

		for (var entity : kgy$getMcEntities()) {
			if (entity.getBoundingBox().intersects(aabb)) {
				list.add(entity);
			}
		}

		return list;
	}

	@Nullable
	default Entity kgy$getEntityByUUID(@Nullable UUID id) {
		if (id == null) {
			return null;
		}

		for (var entity : kgy$getMcEntities()) {
			if (entity.getUUID().equals(id)) {
				return entity;
			}
		}

		return null;
	}

	@Nullable
	default Entity kgy$getEntityByNetworkID(int id) {
		for (var entity : kgy$getMcEntities()) {
			if (entity.getId() == id) {
				return entity;
			}
		}

		return null;
	}
}
