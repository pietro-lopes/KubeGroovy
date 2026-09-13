package dev.uncandango.kubegroovy.core;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.uncandango.kubegroovy.entity.KubeRayTraceResult;
import dev.uncandango.kubegroovy.level.LevelBlock;
import dev.uncandango.kubegroovy.player.EntityArrayList;
import dev.uncandango.kubegroovy.script.ScriptType;
import dev.uncandango.kubegroovy.script.ScriptTypeHolder;
import dev.uncandango.kubegroovy.typings.Info;
import dev.uncandango.kubegroovy.typings.Param;
import dev.uncandango.kubegroovy.typings.ThisIs;
import dev.uncandango.kubegroovy.util.UtilsGY;
import dev.uncandango.kubegroovy.script.annotations.HideFromGY;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.EndTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.commands.TeleportCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Team;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

@RemapPrefixForGY("kgy$")
public interface EntityKGY extends WithPersistentData, MessageSenderKGY, ScriptTypeHolder {
	@HideFromGY
	default Entity kgy$self() {
		return (Entity) this;
	}

	default Level kgy$getLevel() {
		return kgy$self().level();
	}

	@Nullable
	default MinecraftServer kgy$getServer() {
		return kgy$getLevel().getServer();
	}

	default String kgy$getType() {
		return kgy$self().getType().kgy$getId();
	}

	@ThisIs(classNames = "net.minecraft.client.player.LocalPlayer")
	@Info("Checks, whether the entity is a reference to yourself - that is - the client player you are controlling.")
	default boolean kgy$isSelf() {
		return false;
	}

	@Nullable
	@Info("If the entity is a player, gets the player's profile, otherwise returns `null`.")
	default GameProfile kgy$getProfile() {
		return null;
	}

	@Info("Gets the entity's custom name, or entity ID if entity has no custom name.")
	default String kgy$getUsername() {
		Component customName = kgy$self().getCustomName();
		return customName != null ? customName.getString() : kgy$getType();
	}

	@Override
	default Component kgy$getName() {
		return kgy$self().getName();
	}

	@Override
	default Component kgy$getDisplayName() {
		return kgy$self().getDisplayName();
	}

	@Info(value = "Sends a message in chat to the entity.", params = {
		@Param(name = "message", value = "A text component. It may be a string, which will be implicitly wrapped into a text component."),
	})
	// TODO: move to PlayerKGY? maybe split MessageSender?
	@Override
	default void kgy$tell(Component message) {
		if (kgy$self() instanceof Player player) {
			player.sendSystemMessage(message);
		}
	}

	@Override
	@Info(value = "Runs the specified console command with permission level of the entity.", params = {
		@Param(name = "command", value = "The console command. Slash at the beginning is optional."),
	})
	default void kgy$runCommand(String command) {
		if (kgy$getLevel() instanceof ServerLevel level) {
			level.getServer().getCommands().performPrefixedCommand(kgy$self().createCommandSourceStackForNameResolution(level), command);
		}
	}

	@Override
	@Info(value = "Runs the specified console command with permission level of the entity. The command won't output any logs in chat nor console.", params = {
		@Param(name = "command", value = "The console command. Slash at the beginning is optional."),
	})
	default void kgy$runCommandSilent(String command) {
		if (kgy$getLevel() instanceof ServerLevel level) {
			level.getServer().getCommands().performPrefixedCommand(kgy$self().createCommandSourceStackForNameResolution(level).withSuppressedOutput(), command);
		}
	}

	@ThisIs(Player.class)
	@Info("Checks if the entity is a player entity.")
	default boolean kgy$isPlayer() {
		return false;
	}

	@ThisIs(ServerPlayer.class)
	@Info("Checks if the entity is a server-side player.")
	default boolean kgy$isServerPlayer() {
		return false;
	}

	@ThisIs(classNames = "net.minecraft.client.player.AbstractClientPlayer")
	@Info("Checks if the entity is a client-side player.")
	default boolean kgy$isClientPlayer() {
		return false;
	}

	@Nullable
	@Info("""
		Gets the item stack corresponding to either:
		- the item contained in the item entity,
		- the item in the item frame.
		Will be `null` if the entity is neither an item entity nor an item frame.
		""")
	default ItemStack kgy$getItem() {
		return null;
	}

	@ThisIs(ItemFrame.class)
	@Info("Checks if the entity is an item frame entity.")
	default boolean kgy$isFrame() {
		return this instanceof ItemFrame;
	}

	@ThisIs(ItemEntity.class)
	@Info("Checks if the entity is an item entity.")
	default boolean kgy$isItem() {
		return this instanceof ItemEntity;
	}

	@ThisIs(LivingEntity.class)
	@Info("Checks if the entity is a `LivingEntity`.")
	default boolean kgy$isLiving() {
		return false;
	}

	@Info("Checks if the entity is a monster.")
	default boolean kgy$isMonster() {
		return !kgy$self().getType().getCategory().isFriendly();
	}

	@Info("Checks if the entity is an animal.")
	default boolean kgy$isAnimal() {
		return kgy$self().getType().getCategory().isPersistent();
	}

	@Info("Checks if the entity is an ambient creature.")
	default boolean kgy$isAmbientCreature() {
		return kgy$self().getType().getCategory() == MobCategory.AMBIENT;
	}

	@Info("Checks if the entity is a water creature.")
	default boolean kgy$isWaterCreature() {
		return kgy$self().getType().getCategory() == MobCategory.WATER_CREATURE;
	}

	@Info("Checks if the entity is a peaceful creature (not a monster).")
	default boolean kgy$isPeacefulCreature() {
		return kgy$self().getType().getCategory().isFriendly();
	}

	default void kgy$setX(double x) {
		kgy$setPosition(x, kgy$self().getY(), kgy$self().getZ());
	}

	default void kgy$setY(double y) {
		kgy$setPosition(kgy$self().getX(), y, kgy$self().getZ());
	}

	default void kgy$setZ(double z) {
		kgy$setPosition(kgy$self().getX(), kgy$self().getY(), z);
	}

	default double kgy$getMotionX() {
		return kgy$self().getDeltaMovement().x;
	}

	default void kgy$setMotionX(double x) {
		var m = kgy$self().getDeltaMovement();
		kgy$self().setDeltaMovement(x, m.y, m.z);
	}

	default double kgy$getMotionY() {
		return kgy$self().getDeltaMovement().y;
	}

	default void kgy$setMotionY(double y) {
		var m = kgy$self().getDeltaMovement();
		kgy$self().setDeltaMovement(m.x, y, m.z);
	}

	default double kgy$getMotionZ() {
		return kgy$self().getDeltaMovement().z;
	}

	default void kgy$setMotionZ(double z) {
		var m = kgy$self().getDeltaMovement();
		kgy$self().setDeltaMovement(m.x, m.y, z);
	}

	private void checkDestinationValidity(BlockPos blockPos, float yaw, float pitch) throws IllegalArgumentException {
		if (!Level.isInSpawnableBounds(blockPos)) {
			throw new IllegalArgumentException("The provided coordinates are out of bounds.");
		}
		if (Float.isNaN(yaw)) {
			throw new IllegalArgumentException("Yaw is not a number.");
		}
		if (Float.isNaN(pitch)) {
			throw new IllegalArgumentException("Pitch is not a number.");
		}
	}

	@Info(value = "Teleports an entity to a specified `ServerLevel`, to specified coordinates and rotation.", params = {
		@Param(name = "level", value = "A `ServerLevel` to teleport the entity to."),
		@Param(name = "x", value = "The `x` target coordinate."),
		@Param(name = "y", value = "The `y` target coordinate."),
		@Param(name = "z", value = "The `z` target coordinate."),
		@Param(name = "yaw", value = "The entity's target yaw."),
		@Param(name = "pitch", value = "The entity's target pitch.")
	})
	default boolean kgy$teleportToLevel(ServerLevel level, double x, double y, double z, float yaw, float pitch) throws IllegalArgumentException {
		Entity self = kgy$self();
		Level previousLevel = kgy$getLevel();

		checkDestinationValidity(BlockPos.containing(x, y, z), yaw, pitch);

		if (level == previousLevel) {
			kgy$setPositionAndRotation(x, y, z, yaw, pitch);
			return true;
		}

		float adjustedYaw = Mth.wrapDegrees(yaw);
		float adjustedPitch = Mth.wrapDegrees(pitch);

		boolean teleportSucceeded = self.teleportTo(level, x, y, z, Set.of(), adjustedYaw, adjustedPitch, false);
		if (!teleportSucceeded) {
			return false;
		}

		try {
			TeleportCommand.performTeleport(
				kgy$self().createCommandSourceStackForNameResolution(level),
				kgy$self(),
				level,
				x, y, z,
				Set.of(),
				yaw, pitch,
				null
			);
		} catch (CommandSyntaxException e) {
			throw new IllegalArgumentException(e.getRawMessage().getString());
		}
		return true;
	}

	@Info(value = "Teleports an entity to a dimension of specified ID, to specified coordinates and rotation.", params = {
		@Param(name = "dimension", value = "An `Identifier` of the target dimension. It can be a string representing the dimension ID."),
		@Param(name = "x", value = "The `x` target coordinate."),
		@Param(name = "y", value = "The `y` target coordinate."),
		@Param(name = "z", value = "The `z` target coordinate."),
		@Param(name = "yaw", value = "The entity's target yaw."),
		@Param(name = "pitch", value = "The entity's target pitch.")
	})
	default boolean kgy$teleportTo(Identifier dimension, double x, double y, double z, float yaw, float pitch) throws IllegalArgumentException {
		ServerLevel level = kgy$getServer().getLevel(ResourceKey.create(Registries.DIMENSION, dimension));

		if (level == null) {
			throw new IllegalArgumentException("The provided dimension ID is invalid.");
		}

		return kgy$teleportToLevel(level, x, y, z, yaw, pitch);
	}

	@Info(value = "Teleports an entity to a dimension of specified ID, to specified coordinates and rotation.", params = {
		@Param(name = "x", value = "The `x` target coordinate."),
		@Param(name = "y", value = "The `y` target coordinate."),
		@Param(name = "z", value = "The `z` target coordinate."),
		@Param(name = "yaw", value = "The entity's target yaw."),
		@Param(name = "pitch", value = "The entity's target pitch.")
	})
	default void kgy$teleportTo(double x, double y, double z, float yaw, float pitch) throws IllegalArgumentException {
		checkDestinationValidity(BlockPos.containing(x, y, z), yaw, pitch);
		kgy$setPositionAndRotation(x, y, z, yaw, pitch);
	}

	default void kgy$setPosition(LevelBlock block) {
		kgy$teleportTo(block.getDimension(), block.getX(), block.getY(), block.getZ(), kgy$self().getYRot(), kgy$self().getXRot());
	}

	default void kgy$setPositionAndRotation(double x, double y, double z, float yaw, float pitch) {
		kgy$self().snapTo(x, y, z, yaw, pitch);
	}

	default void kgy$setPosition(double x, double y, double z) {
		kgy$setPositionAndRotation(x, y, z, kgy$self().getYRot(), kgy$self().getXRot());
	}

	default void kgy$setRotation(float yaw, float pitch) {
		kgy$setPositionAndRotation(kgy$self().getX(), kgy$self().getY(), kgy$self().getZ(), yaw, pitch);
	}

	@Info("Gets a list of all passengers of the entity.")
	default EntityArrayList<? extends Entity> kgy$getPassengers() {
		return new EntityArrayList<>(kgy$self().getPassengers());
	}

	@Deprecated
	@Info("Replaced by `entity.getTeamName()`")
	default String kgy$getTeamId() {
		return kgy$getTeamName();
	}

	@Info("Gets the name of the team entity is in, or `''` (empty string) if the entity is not part of any team")
	default String kgy$getTeamName() {
		var team = kgy$self().getTeam();
		return team == null ? "" : team.getName();
	}

	@Info("Checks, whether the entity is part of any team.")
	default boolean kgy$isOnScoreboardTeam() {
		return kgy$self().getTeam() != null;
	}

	@Info(value = "Checks, whether the entity is part of a team called `teamName`.", params = {
		@Param(name = "teamName", value = "The name of the team to check.")
	})
	default boolean kgy$isOnScoreboardTeam(String teamName) {
		Team team = kgy$self().level().getScoreboard().getPlayerTeam(teamName);
		return team != null && kgy$self().isAlliedTo(team);
	}

	@Info("""
		Gets the entity's facing direction.
		If the entity faces more than 45 degrees up or down, the resulting facing direction is respectively `up` or `down`.
		Otherwise, the resulting facing direction is determined by whichever cardinal direction is closer to entity's yaw.
		""")
	default Direction kgy$getFacing() {
		if (kgy$self().getXRot() > 45F) {
			return Direction.DOWN;
		} else if (kgy$self().getXRot() < -45F) {
			return Direction.UP;
		}

		return kgy$self().getDirection();
	}

	@Info("Gets a block at the position of the entity.")
	default LevelBlock kgy$getBlock() {
		return kgy$getLevel().kgy$getBlock(kgy$self().blockPosition());
	}

	default CompoundTag kgy$getNbt() {
		var registries = kgy$self().level().registryAccess();
		var problems = new ProblemReporter.Collector(() -> "KubeGroovy");

		var out = TagValueOutput.createWithContext(problems, registries);
		kgy$self().saveWithoutId(out);
		return out.buildResult();
	}

	default void kgy$setNbt(@Nullable CompoundTag nbt) {
		if (nbt == null) {
			return;
		}
		var registries = kgy$self().level().registryAccess();
		var problems = new ProblemReporter.Collector(() -> "KubeGroovy");

		var in = TagValueInput.create(problems, registries, nbt);
		kgy$self().load(in);
	}


	default Entity kgy$mergeNbt(@Nullable CompoundTag tag) {
		if (tag == null || tag.isEmpty()) {
			return kgy$self();
		}

		var nbt = kgy$getNbt();

		for (var k : tag.keySet()) {
			var t = tag.get(k);

			if (t == null || t == EndTag.INSTANCE) {
				nbt.remove(k);
			} else {
				nbt.put(k, t);
			}
		}

		kgy$setNbt(nbt);
		return kgy$self();
	}

	default void kgy$spawn() {
		kgy$getLevel().addFreshEntity(kgy$self());
	}

	@Info(value = "Damages an entity by a given amount of HP dealing generic damage.", params = {
		@Param(name = "hp", value = "The amount of damage to deal."),
	})
	default boolean kgy$damage(float hp) {
		return kgy$damage(hp, kgy$self().damageSources().generic());
	}

	@Info(value = "Damages an entity by a given amount of HP dealing a specific type of damage.", params = {
		@Param(name = "hp", value = "The amount of damage to deal."),
		@Param(name = "source", value = "The damage source. It may be a string specifying a damage source, like `'minecraft:cramming'`.")
	})
	default boolean kgy$damage(float hp, DamageSource source) {
		if (kgy$getLevel() instanceof ServerLevel serverLevel) {
			return kgy$self().hurtServer(serverLevel, source, hp);
		}
		return false;
	}

	@Deprecated
	default void kgy$attack(float hp) {
		kgy$damage(hp);
	}

	@Info("Replaced by `entity.damage(hp, damageSource)`")
	@Deprecated
	default void kgy$attack(DamageSource source, float hp) {
		kgy$damage(hp, source);
	}

	@Info("Measures the distance of entity to block at specified `BlockPos`.")
	default double kgy$distanceToBlock(BlockPos pos) {
		return Math.sqrt(kgy$distanceToBlockSqr(pos));
	}

	@Info("Measures the **square** of a distance of entity to the block at specified `BlockPos`.")
	default double kgy$distanceToBlockSqr(BlockPos pos) {
		return kgy$self().distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
	}

	@Info("Measures the distance of entity to the point at specified `x`, `y` and `z`.")
	default double kgy$distanceTo(double x, double y, double z) {
		return Math.sqrt(kgy$self().distanceToSqr(x, y, z));
	}

	@Info("Measures the distance of entity to the point at specified 3D position vector.")
	default double kgy$distanceTo(Vec3 position) {
		return Math.sqrt(kgy$self().distanceToSqr(position));
	}

	@Deprecated
	@Info("Replaced by `entity.distanceToSqr(x, y, z)`.")
	default double kgy$getDistanceSq(double x, double y, double z) {
		return kgy$self().distanceToSqr(x, y, z);
	}

	@Deprecated
	@Info("Replaced by `entity.distanceTo(x, y, z)`.")
	default double kgy$getDistance(double x, double y, double z) {
		return kgy$distanceTo(x, y, z);
	}

	@Deprecated
	@Info("Replaced by `entity.distanceToBlockSqr(pos)`.")
	default double kgy$getDistanceSq(BlockPos pos) {
		return kgy$distanceToBlockSqr(pos);
	}

	default KubeRayTraceResult kgy$rayTrace(double distance, boolean fluids) {
		var entity = kgy$self();
		var hitResult = entity.pick(distance, 0F, fluids);
		var eyePosition = entity.getEyePosition();
		var lookVector = entity.getViewVector(1.0f);
		var traceEnd = eyePosition.add(lookVector.x * distance, lookVector.y * distance, lookVector.z * distance);
		var bound = entity.getBoundingBox().expandTowards(lookVector.scale(distance)).inflate(1.0, 1.0, 1.0);
		var distanceSquared = hitResult.getType() != HitResult.Type.MISS ? hitResult.getLocation().distanceToSqr(eyePosition) : distance * distance;
		var entityHitResult = ProjectileUtil.getEntityHitResult(entity, eyePosition, traceEnd, bound, ent -> !ent.isSpectator() && ent.isPickable(), distanceSquared);
		if (entityHitResult != null) {
			var entityDistanceSquared = eyePosition.distanceToSqr(entityHitResult.getLocation());
			if (entityDistanceSquared < distanceSquared || hitResult.getType() == HitResult.Type.MISS) {
				hitResult = entityHitResult;
			}
		}
		return new KubeRayTraceResult(entity, hitResult, distance);
	}

	default KubeRayTraceResult kgy$rayTrace(double distance) {
		return kgy$rayTrace(distance, true);
	}

	@Nullable
	default Entity kgy$rayTraceEntity(double distance, @Nullable Predicate<? super Entity> filter) {
		double d0 = Double.MAX_VALUE;
		Entity entity = null;

		var start = kgy$self().getEyePosition();
		var end = start.add(kgy$self().getLookAngle().scale(distance));

		for (Entity entity1 : kgy$self().level().getEntities(kgy$self(), new AABB(start, end), filter == null ? UtilsGY.ALWAYS_TRUE : filter)) {
			double d1;
			AABB aabb = entity1.getBoundingBox();
			Optional<Vec3> optional = aabb.clip(start, end);
			if (optional.isEmpty() || !((d1 = start.distanceToSqr(optional.get())) < d0)) {
				continue;
			}
			entity = entity1;
			d0 = d1;
		}

		return entity;
	}

	@Nullable
	@HideFromGY
	default CompoundTag kgy$getRawPersistentData() {
		throw new NoMixinException();
	}

	@HideFromGY
	default void kgy$setRawPersistentData(@Nullable CompoundTag tag) {
		throw new NoMixinException();
	}

	@Override
	default ScriptType kgy$getScriptType() {
		return kgy$getLevel().kgy$getScriptType();
	}

	@Nullable
	default ItemEntity kgy$spawnAtLocation(ItemStack itemStack, Vec3 offset) {
		if (kgy$getLevel() instanceof ServerLevel serverLevel) {
			return kgy$self().spawnAtLocation(serverLevel, itemStack, offset);
		}

		return null;
	}
}
