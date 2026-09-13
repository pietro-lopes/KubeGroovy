package dev.uncandango.kubegroovy.event;

@FunctionalInterface
public interface IEventHandler {
	Object onEvent(KubeEvent event) throws EventExit;
}