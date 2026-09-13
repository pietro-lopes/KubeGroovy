package dev.uncandango.kubegroovy.block.entity;

public interface BlockEntityEventCallback {
	void accept(KubeBlockEntity entity, int data);
}
