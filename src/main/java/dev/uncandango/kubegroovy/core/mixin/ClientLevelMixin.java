package dev.uncandango.kubegroovy.core.mixin;

import dev.uncandango.kubegroovy.core.ClientLevelKGY;
import dev.uncandango.kubegroovy.script.annotations.HideFromGY;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;

@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin implements ClientLevelKGY {
	@Shadow
	@Final
	@HideFromGY
	List<AbstractClientPlayer> players;

	@Shadow
	@HideFromGY
	public abstract List<AbstractClientPlayer> players();
}
