package dev.uncandango.kubegroovy.core;

import dev.uncandango.kubegroovy.typings.Info;
import dev.uncandango.kubegroovy.typings.Param;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

@RemapPrefixForGY("kgy$")
public interface MessageSenderKGY {
	default Component kgy$getName() {
		throw new NoMixinException();
	}

	default Component kgy$getDisplayName() {
		return kgy$getName();
	}

	@Info(value = "Sends a message in chat to something.", params = {
		@Param(name = "message", value = "A text component. It may be a string, which will be implicitly wrapped into a text component."),
	})
	default void kgy$tell(Component message) {
		throw new NoMixinException();
	}

	default void kgy$setStatusMessage(Component message) {
	}

	@Info(value = "Runs the specified console command.", params = {
		@Param(name = "command", value = "The console command. Slash at the beginning is optional."),
	})
	default void kgy$runCommand(String command) {
	}

	@Info(value = "Runs the specified console command. The command won't output any logs in chat nor console.", params = {
		@Param(name = "command", value = "The console command. Slash at the beginning is optional."),
	})
	default void kgy$runCommandSilent(String command) {
		kgy$runCommand(command);
	}

	default void kgy$setActivePostShader(@Nullable Identifier id) {
	}
}