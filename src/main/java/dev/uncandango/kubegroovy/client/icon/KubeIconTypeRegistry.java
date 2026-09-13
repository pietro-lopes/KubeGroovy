package dev.uncandango.kubegroovy.client.icon;

@FunctionalInterface
public interface KubeIconTypeRegistry {
	void register(KubeIconType<?> type);
}
