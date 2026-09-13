package dev.uncandango.kubegroovy.core.mixin;

import dev.uncandango.kubegroovy.script.annotations.RemapForGY;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(DamageSource.class)
@RemapPrefixForGY("kgy$")
public abstract class DamageSourceMixin {
	@Shadow
	@RemapForGY("getType")
	public abstract String getMsgId();

	@Shadow
	@RemapForGY("getImmediate")
	public abstract Entity getDirectEntity();

	@Shadow
	@RemapForGY("getActual")
	public abstract Entity getEntity();

	@Nullable
	@Unique
	public Player kgy$getPlayer() {
		return getEntity() instanceof Player p ? p : null;
	}
}