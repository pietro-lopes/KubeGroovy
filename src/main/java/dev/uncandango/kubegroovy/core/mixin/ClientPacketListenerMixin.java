package dev.uncandango.kubegroovy.core.mixin;

import dev.uncandango.kubegroovy.client.KubeSessionData;
import dev.uncandango.kubegroovy.core.ClientPacketListenerKGY;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin implements ClientPacketListenerKGY {
	@Unique
	private final KubeSessionData kgy$sessionData = new KubeSessionData();

	@Override
	public KubeSessionData kgy$sessionData() {
		return kgy$sessionData;
	}
}
