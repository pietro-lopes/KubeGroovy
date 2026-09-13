package dev.uncandango.kubegroovy.core.mixin;

import com.mojang.authlib.GameProfile;
import dev.uncandango.kubegroovy.core.ServerPlayerKGY;
import dev.uncandango.kubegroovy.typings.Info;
import dev.uncandango.kubegroovy.typings.Param;
import dev.uncandango.kubegroovy.script.annotations.RemapForGY;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ServerPlayer.class)
@RemapPrefixForGY("kgy$")
public abstract class ServerPlayerMixin extends Player implements ServerPlayerKGY {
	public ServerPlayerMixin(Level level, BlockPos blockPos, float f, GameProfile gameProfile) {
		super(level, gameProfile);
	}

	@Shadow
	@RemapForGY("getStatsCounter")
	public abstract ServerStatsCounter getStats();

	@Shadow
	@Info(value = "Changes the player's gamemode.", params = {
		@Param(name = "gameMode", value = "One of: `'survival'`, `'creative'`, `'adventure'`, `'spectator'`.")
	})
	public abstract boolean setGameMode(GameType gameMode);
}
