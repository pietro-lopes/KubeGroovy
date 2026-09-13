package dev.uncandango.kubegroovy.core.mixin;

import dev.uncandango.kubegroovy.core.MenuTypeKGY;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.inventory.MenuType;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(MenuType.class)
public abstract class MenuTypeMixin implements MenuTypeKGY {
	@Unique
	private @Nullable ResourceKey<MenuType<?>> kgy$registryKey;

	@Unique
	private @Nullable String kgy$id;

	@Override
	public ResourceKey<MenuType<?>> kgy$getKey() {
		if (kgy$registryKey == null) {
			kgy$registryKey = MenuTypeKGY.super.kgy$getKey();
		}

		return kgy$registryKey;
	}

	@Override
	public String kgy$getId() {
		if (kgy$id == null) {
			kgy$id = MenuTypeKGY.super.kgy$getId();
		}

		return kgy$id;
	}
}
