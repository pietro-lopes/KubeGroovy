package dev.uncandango.kubegroovy.core.mixin;

import dev.uncandango.kubegroovy.core.ServerLevelKGY;
import dev.uncandango.kubegroovy.script.annotations.HideFromGY;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.LevelEntityGetter;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.List;

@Mixin(ServerLevel.class)
@RemapPrefixForGY("kgy$")
public abstract class ServerLevelMixin implements ServerLevelKGY {
	@Unique
	private @Nullable CompoundTag kgy$persistentData;

	@Override
	public CompoundTag kgy$getPersistentData() {
		if (kgy$persistentData == null) {
			var t = kgy$self().dimension().identifier().toString();
			kgy$persistentData = kgy$self().getServer().kgy$getPersistentData().getCompound(t).orElseGet(CompoundTag::new);
			kgy$self().getServer().kgy$getPersistentData().put(t, kgy$persistentData);
		}

		return kgy$persistentData;
	}

	@Shadow
	@HideFromGY
	public abstract List<ServerPlayer> players();

	@Shadow
	@HideFromGY
	public abstract LevelEntityGetter<Entity> getEntities();
}
