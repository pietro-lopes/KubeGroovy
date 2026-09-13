package dev.uncandango.kubegroovy.plugin.builtin.event;

import dev.uncandango.kubegroovy.entity.AfterLivingEntityHurtKubeEvent;
import dev.uncandango.kubegroovy.entity.BeforeLivingEntityHurtKubeEvent;
import dev.uncandango.kubegroovy.entity.CheckLivingEntitySpawnKubeEvent;
import dev.uncandango.kubegroovy.entity.EntitySpawnedKubeEvent;
import dev.uncandango.kubegroovy.entity.LivingEntityDeathKubeEvent;
import dev.uncandango.kubegroovy.entity.LivingEntityDropsKubeEvent;
import dev.uncandango.kubegroovy.event.EventGroup;
import dev.uncandango.kubegroovy.event.EventTargetType;
import dev.uncandango.kubegroovy.event.TargetedEventHandler;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;

public interface EntityEvents {
	EventGroup GROUP = EventGroup.of("EntityEvents");
	EventTargetType<ResourceKey<EntityType<?>>> TARGET = EventTargetType.registryKey(Registries.ENTITY_TYPE, EntityType.class);

	TargetedEventHandler<ResourceKey<EntityType<?>>> DEATH = GROUP.common("death", () -> LivingEntityDeathKubeEvent.class).hasResult().supportsTarget(TARGET);
	TargetedEventHandler<ResourceKey<EntityType<?>>> BEFORE_HURT = GROUP.common("beforeHurt", () -> BeforeLivingEntityHurtKubeEvent.class).hasResult().supportsTarget(TARGET);
	TargetedEventHandler<ResourceKey<EntityType<?>>> AFTER_HURT = GROUP.common("afterHurt", () -> AfterLivingEntityHurtKubeEvent.class).supportsTarget(TARGET);
	TargetedEventHandler<ResourceKey<EntityType<?>>> CHECK_SPAWN = GROUP.common("checkSpawn", () -> CheckLivingEntitySpawnKubeEvent.class).hasResult().supportsTarget(TARGET);
	TargetedEventHandler<ResourceKey<EntityType<?>>> SPAWNED = GROUP.common("spawned", () -> EntitySpawnedKubeEvent.class).hasResult().supportsTarget(TARGET);
	TargetedEventHandler<ResourceKey<EntityType<?>>> ENTITY_DROPS = GROUP.common("drops", () -> LivingEntityDropsKubeEvent.class).hasResult().supportsTarget(TARGET);
}
