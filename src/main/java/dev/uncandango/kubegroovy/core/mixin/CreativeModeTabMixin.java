package dev.uncandango.kubegroovy.core.mixin;

import dev.uncandango.kubegroovy.core.CreativeModeTabKGY;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(CreativeModeTab.class)
public abstract class CreativeModeTabMixin implements CreativeModeTabKGY {
	@Shadow
	@Final
	@Mutable
	private Component displayName;

	@Shadow
	private ItemStack iconItemStack;

	@Override
	public void kgy$setDisplayName(Component component) {
		displayName = component;
	}

	@Override
	public void kgy$setIcon(ItemStack icon) {
		iconItemStack = icon;
	}
}
