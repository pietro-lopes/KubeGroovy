package dev.uncandango.kubegroovy.web;


import net.minecraft.resources.Identifier;

public interface LocalWebServerAPIRegistry {
	void register(Identifier id, int version);
}
