package dev.uncandango.kubegroovy.core;

import dev.uncandango.kubegroovy.player.EntityArrayList;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.EntityGetter;
import net.minecraft.world.phys.AABB;

import java.util.List;

@RemapPrefixForGY("kgy$")
public interface EntityGetterKGY extends EntityCollectionKGY {
	default EntityGetter kgy$self() {
		return (EntityGetter) this;
	}

	@Override
	default List<? extends Player> kgy$getMcPlayers() {
		return kgy$self().players();
	}

	@Override
	default EntityArrayList<? extends Player> kgy$getPlayers() {
		return new EntityArrayList<>(kgy$self().players());
	}

	@Override
	default Iterable<? extends Entity> kgy$getMcEntities() {
		return kgy$self().getEntities((Entity) null, AABB.INFINITE, EntityArrayList.ALWAYS_TRUE_PREDICATE);
	}

	@Override
	default EntityArrayList<? extends Entity> kgy$getEntitiesWithin(AABB aabb) {
		return new EntityArrayList<>(kgy$self().getEntities(null, aabb));
	}
}
