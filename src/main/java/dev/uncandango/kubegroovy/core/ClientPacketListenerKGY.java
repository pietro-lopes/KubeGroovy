package dev.uncandango.kubegroovy.core;

import dev.uncandango.kubegroovy.client.KubeSessionData;

public interface ClientPacketListenerKGY {
	default KubeSessionData kgy$sessionData() {
		throw new NoMixinException();
	}
}
