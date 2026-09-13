package dev.uncandango.kubegroovy.client.editor;

public interface EditorCallback<T> {
	void callback(T value, boolean success);
}
