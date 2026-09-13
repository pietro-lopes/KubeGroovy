package dev.uncandango.kubegroovy.core.mixin;

import com.mojang.authlib.GameProfile;
import dev.uncandango.kubegroovy.core.LocalClientPlayerKGY;
import dev.uncandango.kubegroovy.script.annotations.RemapForGY;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.stats.StatsCounter;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin extends AbstractClientPlayerMixin implements LocalClientPlayerKGY {
	public LocalPlayerMixin(Level level, BlockPos blockPos, float f, GameProfile gameProfile) {
		super(level, blockPos, f, gameProfile);
	}

	@Shadow
	@RemapForGY("getStatsCounter")
	public abstract StatsCounter getStats();

	@Shadow
	@Final
	public ClientPacketListener connection;

	@Shadow
	@Final
	protected Minecraft minecraft;

	@Override
	@Accessor("minecraft")
	public abstract Minecraft kgy$getMinecraft();
}
