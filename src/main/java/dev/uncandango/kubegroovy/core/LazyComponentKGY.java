package dev.uncandango.kubegroovy.core;

import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

@FunctionalInterface
public interface LazyComponentKGY extends Supplier<Component> {
	@Override
	Component get();
}