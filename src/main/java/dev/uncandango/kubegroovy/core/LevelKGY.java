package dev.uncandango.kubegroovy.core;

import dev.uncandango.kubegroovy.level.CachedLevelBlock;
import dev.uncandango.kubegroovy.level.ExplosionProperties;
import dev.uncandango.kubegroovy.level.LevelBlock;
import dev.uncandango.kubegroovy.script.ScriptType;
import dev.uncandango.kubegroovy.script.ScriptTypeHolder;
import dev.uncandango.kubegroovy.typings.Info;
import dev.uncandango.kubegroovy.typings.Param;
import dev.uncandango.kubegroovy.script.annotations.RemapForGY;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.ServerLevelData;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

@RemapPrefixForGY("kgy$")
public interface LevelKGY extends WithAttachedData<Level>, ScriptTypeHolder, EntityGetterKGY {
	@Override
	default Level kgy$self() {
		return (Level) this;
	}

	@Override
	@RemapForGY("getSide")
	default ScriptType kgy$getScriptType() {
		return kgy$self().isClientSide() ? ScriptType.CLIENT : ScriptType.SERVER;
	}

	@Override
	default Component kgy$getName() {
		return Component.literal(kgy$getDimension().toString());
	}

	@Override
	default void kgy$tell(Component message) {
		for (var entity : kgy$self().players()) {
			entity.kgy$tell(message);
		}
	}

	@Override
	default void kgy$setStatusMessage(Component message) {
		for (var entity : kgy$self().players()) {
			entity.kgy$setStatusMessage(message);
		}
	}

	@Override
	@Info(value = "Each player in the level (world) runs the specified console command with their permission level.", params = {
		@Param(name = "command", value = "The console command. Slash at the beginning is optional."),
	})
	default void kgy$runCommand(String command) {
		for (var entity : kgy$self().players()) {
			entity.kgy$runCommand(command);
		}
	}

	@Override
	@Info(value = "Each player in the level (world) runs the specified console command with their permission level. The command won't output any logs in chat nor console", params = {
		@Param(name = "command", value = "The console command. Slash at the beginning is optional."),
	})
	default void kgy$runCommandSilent(String command) {
		for (var entity : kgy$self().players()) {
			entity.kgy$runCommandSilent(command);
		}
	}

	@Override
	default void kgy$setActivePostShader(@Nullable Identifier id) {
		for (var entity : kgy$self().players()) {
			entity.kgy$setActivePostShader(id);
		}
	}

	default Identifier kgy$getDimension() {
		return kgy$self().dimension().identifier();
	}

	default boolean kgy$isOverworld() {
		return kgy$self().dimension() == Level.OVERWORLD;
	}

	default void kgy$setTime(long time) {
		if (kgy$self().getLevelData() instanceof ServerLevelData d) {
			d.setGameTime(time);
		}
	}

	default LevelBlock kgy$getBlock(int x, int y, int z) {
		return kgy$getBlock(new BlockPos(x, y, z));
	}

	default LevelBlock kgy$getBlock(BlockPos pos) {
		return new CachedLevelBlock(kgy$self(), pos);
	}

	default LevelBlock kgy$getBlock(BlockEntity entity) {
		return new CachedLevelBlock(entity.getLevel(), entity.getBlockPos()).cache(entity).cache(entity.getBlockState());
	}

	default void kgy$explode(double x, double y, double z, ExplosionProperties properties) {
		properties.explode(kgy$self(), x, y, z);
	}

	@Nullable
	default Entity kgy$createEntity(EntityType<?> type) {
		return type.create(kgy$self(), EntitySpawnReason.COMMAND);
	}

	@Nullable
	default Entity kgy$createEntity(EntityType<?> type, EntitySpawnReason reason) {
		return type.create(kgy$self(), reason);
	}

	default void spawnEntity(EntityType<?> type, Consumer<Entity> callback) {
		var entity = type.create(kgy$self(), EntitySpawnReason.COMMAND);

		if (entity != null) {
			callback.accept(entity);
			kgy$self().addFreshEntity(entity);
		}
	}

	default void kgy$spawnFireworks(double x, double y, double z, Fireworks fireworks, int lifetime) {
		var stack = new ItemStack(Items.FIREWORK_ROCKET);
		stack.set(DataComponents.FIREWORKS, fireworks);

		var rocket = new FireworkRocketEntity(kgy$self(), x, y, z, stack);

		if (lifetime != -1) {
			((FireworkRocketEntityKGY) rocket).setLifetimeKGY(lifetime);
		}

		rocket.setInvisible(true);
		kgy$self().addFreshEntity(rocket);
	}

	default void kgy$spawnParticles(ParticleOptions options, boolean overrideLimiter, double x, double y, double z, double vx, double vy, double vz, int count, double speed) {
		var level = kgy$self();

		if (count == 0) {
			double d0 = speed * vx;
			double d2 = speed * vy;
			double d4 = speed * vz;

			try {
				level.addParticle(options, overrideLimiter, false, x, y, z, d0, d2, d4);
			} catch (Throwable ignored) {
			}
		} else {
			var random = level.getRandom();

			for (int i = 0; i < count; ++i) {
				double ox = random.nextGaussian() * vx;
				double oy = random.nextGaussian() * vy;
				double oz = random.nextGaussian() * vz;
				double d6 = random.nextGaussian() * speed;
				double d7 = random.nextGaussian() * speed;
				double d8 = random.nextGaussian() * speed;

				try {
					level.addParticle(options, overrideLimiter, false, x + ox, y + oy, z + oz, d6, d7, d8);
				} catch (Throwable ignored) {
					return;
				}
			}
		}
	}

	default void kgy$spawnLightning(double x, double y, double z, boolean visualOnly, @Nullable ServerPlayer cause) {
		var e = EntityType.LIGHTNING_BOLT.create(kgy$self(), EntitySpawnReason.COMMAND);
		e.snapTo(x, y, z);
		e.setCause(cause);
		e.setVisualOnly(visualOnly);
		kgy$self().addFreshEntity(e);
	}

	default void kgy$spawnLightning(double x, double y, double z, boolean visualOnly) {
		kgy$spawnLightning(x, y, z, visualOnly, null);
	}
}
