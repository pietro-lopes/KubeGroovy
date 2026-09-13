package dev.uncandango.kubegroovy.registry;

import net.minecraft.resources.Identifier;

public interface BuilderFactory {
	BuilderBase createBuilder(Identifier id);
}