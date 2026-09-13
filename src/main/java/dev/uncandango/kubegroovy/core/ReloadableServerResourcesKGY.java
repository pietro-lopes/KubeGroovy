package dev.uncandango.kubegroovy.core;

import dev.uncandango.kubegroovy.server.ServerScriptManager;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentInitializers;
import org.jspecify.annotations.Nullable;

import java.util.List;

public interface ReloadableServerResourcesKGY {
	@Nullable
	default ServerScriptManager kgy$getServerScriptManager() {
		throw new NoMixinException();
	}

	default void kgy$setServerScriptManager(ServerScriptManager serverScriptManager) {
		throw new NoMixinException();
	}

	default List<Registry.PendingTags<?>> kgy$getPostponedTags() {
		throw new NoMixinException();
	}

	List<DataComponentInitializers.PendingComponents<?>> kgy$getNewComponents();
}
