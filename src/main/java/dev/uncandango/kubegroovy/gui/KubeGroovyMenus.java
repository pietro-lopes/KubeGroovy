package dev.uncandango.kubegroovy.gui;

import dev.uncandango.kubegroovy.KubeGroovy;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public interface KubeGroovyMenus {
	DeferredRegister<MenuType<?>> REGISTRY = DeferredRegister.create(Registries.MENU, KubeGroovy.MOD_ID);

	Supplier<MenuType<KubeGroovyMenu>> MENU = REGISTRY.register("menu", () -> new MenuType<>(KubeGroovyMenu.FACTORY, FeatureFlags.VANILLA_SET));
}
