package dev.uncandango.kubegroovy.registry;

import net.minecraft.resources.Identifier;

public record BuilderType<T>(Identifier type, Class<? extends BuilderBase<? extends T>> builderClass, BuilderFactory factory) {
}