package dev.uncandango.kubegroovy.core.mixin;

import dev.uncandango.kubegroovy.core.FireworkRocketEntityKGY;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(FireworkRocketEntity.class)
public abstract class FireworkRocketEntityMixin implements FireworkRocketEntityKGY {
	@Override
	@Accessor("lifetime")
	@Mutable
	public abstract void setLifetimeKGY(int lifetime);
}