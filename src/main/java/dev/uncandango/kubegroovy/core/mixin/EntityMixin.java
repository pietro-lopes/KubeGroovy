package dev.uncandango.kubegroovy.core.mixin;

import dev.uncandango.kubegroovy.core.EntityKGY;
import dev.uncandango.kubegroovy.typings.Info;
import dev.uncandango.kubegroovy.script.annotations.HideFromGY;
import dev.uncandango.kubegroovy.script.annotations.RemapForGY;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.UUID;

@Mixin(Entity.class)
@RemapPrefixForGY("kgy$")
public abstract class EntityMixin implements EntityKGY {
	@Shadow
	private Level level;

	@Shadow
	public abstract void playerTouch(Player arg);

	@Unique
	private @Nullable CompoundTag kgy$persistentData;

	@Override
	public CompoundTag kgy$getPersistentData() {
		if (kgy$persistentData == null) {
			kgy$persistentData = new CompoundTag();
		}

		return kgy$persistentData;
	}

	@Shadow(remap = false)
	@RemapForGY("getForgePersistentData")
	public abstract CompoundTag getPersistentData();

	@Inject(method = "saveWithoutId", at = @At("RETURN"))
	private void saveKGY(ValueOutput output, CallbackInfo ci) {
		if (kgy$persistentData != null && !kgy$persistentData.isEmpty()) {
			output.store("kubegroovyPersistentData", CompoundTag.CODEC, kgy$persistentData);
		}
	}

	@Inject(method = "load", at = @At("RETURN"))
	private void loadKGY(ValueInput input, CallbackInfo ci) {
		kgy$persistentData = input
			.read("kubegroovyPersistentData", CompoundTag.CODEC)
			.orElse(null);
	}

	@Override
	@Nullable
	@HideFromGY
	public CompoundTag kgy$getRawPersistentData() {
		return kgy$persistentData;
	}

	@Override
	@HideFromGY
	public void kgy$setRawPersistentData(@Nullable CompoundTag tag) {
		kgy$persistentData = tag;
	}

	@Shadow
	@RemapForGY("tickCount")
	public int tickCount;

	@Shadow
	@RemapForGY("getUuid")
	public abstract UUID getUUID();

	@Shadow
	@RemapForGY("getStringUuid")
	public abstract String getStringUUID();

	@Shadow
	@RemapForGY("isGlowing")
	public abstract boolean isCurrentlyGlowing();

	@Shadow
	@RemapForGY("setGlowing")
	public abstract void setGlowingTag(boolean glowing);

	@Shadow
	@RemapForGY("getYaw")
	public abstract float getYRot();

	@Shadow
	@RemapForGY("setYaw")
	public abstract void setYRot(float yaw);

	@Shadow
	@RemapForGY("getPitch")
	public abstract float getXRot();

	@Shadow
	@RemapForGY("setPitch")
	public abstract void setXRot(float pitch);

	@Shadow
	@RemapForGY("setBodyYaw")
	@Info("Sets the entity's body yaw.")
	public abstract void setYBodyRot(float yBodyRot);

	@Shadow
	@RemapForGY("getBodyYaw")
	@Info("Gets the entity's body yaw (if the entity is a `LivingEntity`), or the entity's visual rotation (if the entity is an item entity or an item frame).")
	public abstract float getVisualRotationYInDegrees();

	@Shadow
	@RemapForGY("setMotion")
	public abstract void setDeltaMovement(double x, double y, double z);

	@Shadow
	public abstract void setRot(float yaw, float pitch);

	@Shadow
	@RemapForGY("addMotion")
	public abstract void push(double x, double y, double z);

	/// Replaced in GY by [EntityKGY#kgy$getPassengers()].
	@Shadow
	@HideFromGY
	public abstract List<Entity> getPassengers();

	@Shadow
	@RemapForGY("isOnSameTeam")
	public abstract boolean isAlliedTo(Entity e);

	/// KubeGroovy adds [EntityKGY#kgy$getFacing()] which also can specify whether the entity is looking down or up.
	@Shadow
	@RemapForGY("getHorizontalFacing")
	public abstract Direction getDirection();

	@Shadow
	@RemapForGY("extinguish")
	public abstract void extinguishFire();

	/// Replaced in GY by [EntityKGY#kgy$damage]
	@Shadow
	@HideFromGY
	public abstract void hurt(DamageSource source, float damage);

	// The remaps:
	// distanceToSqr(double x, double y, double z) - remains as is
	// distanceToSqr(Vec3 vector) - remains as is
	// distanceToSqr(Entity entity) - remapped to distanceToEntitySqr
	// distanceToBlockSqr(BlockPos block) is added by EntityKGY

	/// [EntityKGY#kgy$getType()] returns a string in GY.
	@Shadow
	@RemapForGY("getEntityType")
	public abstract EntityType<?> getType();

	@Shadow
	@Info("Measures the **square** of a distance of entity to the point at specified 3D position vector.")
	public abstract double distanceToSqr(Vec3 vec);

	@Shadow
	@Info("Measures the distance of entity to the point at specified `x`, `y` and `z`.")
	public abstract double distanceToSqr(double x, double y, double z);

	@Shadow
	@RemapForGY("distanceToEntitySqr")
	@Info("Measures the **square** of a distance of entity to another entity.")
	public abstract double distanceToSqr(Entity arg);

	@Shadow
	@RemapForGY("distanceToEntity")
	@Info("Measures the distance of entity to another entity.")
	public abstract float distanceTo(Entity arg);

	/// Replaced in GY by [EntityKGY#kgy$getLevel()].
	@Shadow
	@HideFromGY
	public abstract Level level();

	@Shadow
	@HideFromGY
	public abstract @Nullable ItemEntity spawnAtLocation(ServerLevel level, ItemStack itemStack);

	@Shadow
	@HideFromGY
	public abstract @Nullable ItemEntity spawnAtLocation(ServerLevel level, ItemStack itemStack, Vec3 offset);

	@Shadow
	@HideFromGY
	public abstract ItemEntity spawnAtLocation(ServerLevel level, ItemLike resource);

	@Shadow
	@HideFromGY
	public abstract ItemEntity spawnAtLocation(ServerLevel level, ItemStack itemStack, float offset);

	/// Disambiguates [Entity#snapTo(Vec3, float, float)] in GY.
	@Shadow
	@RemapForGY("snapToBlockPos")
	public abstract void snapTo(BlockPos spawnPos, float yRot, float xRot);
}
