package dev.uncandango.kubegroovy.core;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.inventory.MenuType;

public interface MenuTypeKGY extends RegistryObjectKGY<MenuType<?>> {
	@Override
	default ResourceKey<Registry<MenuType<?>>> kgy$getRegistryId() {
		return Registries.MENU;
	}

	@Override
	default Registry<MenuType<?>> kgy$getRegistry() {
		return BuiltInRegistries.MENU;
	}
}
