package dev.uncandango.kubegroovy.core.mixin;

import dev.uncandango.kubegroovy.core.EntityGetterKGY;
import dev.uncandango.kubegroovy.script.annotations.HideFromGY;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.EntityGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;

@Mixin(EntityGetter.class)
@RemapPrefixForGY("kgy$")
public interface EntityGetterMixin extends EntityGetterKGY {
	@Shadow
	@HideFromGY
	List<? extends Player> players();
}
