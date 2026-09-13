package dev.uncandango.kubegroovy.core.mixin;

import com.mojang.authlib.GameProfile;
import dev.uncandango.kubegroovy.core.ClientPlayerKGY;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(AbstractClientPlayer.class)
@RemapPrefixForGY("kgy$")
public abstract class AbstractClientPlayerMixin extends Player implements ClientPlayerKGY {
	public AbstractClientPlayerMixin(Level level, BlockPos blockPos, float f, GameProfile gameProfile) {
		super(level, gameProfile);
	}
}
