package dev.uncandango.kubegroovy.plugin.builtin.event;

import dev.uncandango.kubegroovy.event.EventGroup;
import dev.uncandango.kubegroovy.event.EventTargetType;
import dev.uncandango.kubegroovy.event.TargetedEventHandler;
import dev.uncandango.kubegroovy.net.NetworkKubeEvent;

public interface NetworkEvents {
	EventGroup GROUP = EventGroup.of("NetworkEvents");

	TargetedEventHandler<String> DATA_RECEIVED = GROUP.common("dataReceived", () -> NetworkKubeEvent.class).hasResult().requiredTarget(EventTargetType.STRING);
}
