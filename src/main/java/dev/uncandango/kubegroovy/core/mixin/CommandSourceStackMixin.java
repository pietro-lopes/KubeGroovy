package dev.uncandango.kubegroovy.core.mixin;

import dev.uncandango.kubegroovy.core.LazyComponentKGY;
import dev.uncandango.kubegroovy.script.annotations.HideFromGY;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.function.Supplier;

@Mixin(CommandSourceStack.class)
@RemapPrefixForGY("kgy$")
public abstract class CommandSourceStackMixin {
	@Shadow
	@HideFromGY
	public abstract void sendSuccess(Supplier<Component> supplier, boolean bl);

	@Unique
	public void kgy$sendSuccess(Component component, boolean broadcastToAdmins) {
		kgy$sendSuccessLazy(() -> component, broadcastToAdmins);
	}

	@Unique
	public void kgy$sendSuccessLazy(LazyComponentKGY component, boolean broadcastToAdmins) {
		sendSuccess(component, broadcastToAdmins);
	}
}
