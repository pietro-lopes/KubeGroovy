package dev.uncandango.kubegroovy.event;

public interface KubeStartupEvent extends KubeEvent {
	KubeStartupEvent BASIC = new KubeStartupEvent() {
	};
}
