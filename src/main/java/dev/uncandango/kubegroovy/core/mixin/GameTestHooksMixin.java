package dev.uncandango.kubegroovy.core.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.uncandango.kubegroovy.DevProperties;
import net.neoforged.neoforge.gametest.GameTestHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/// Lets game tests (registration and the server test ticker) run in production when enabled through
/// `enable_game_tests` in `local/kubegroovy/dev.json` or the `-Dkubegroovy.enableGameTests=true` system property.
@Mixin(GameTestHooks.class)
public abstract class GameTestHooksMixin {
	@Unique
	private static final boolean kgy$PROPERTY_ENABLED = Boolean.getBoolean("kubegroovy.enableGameTests");

	@ModifyReturnValue(method = "isGametestEnabled", at = @At("RETURN"), remap = false)
	private static boolean kgy$isGametestEnabled(boolean original) {
		return original || kgy$PROPERTY_ENABLED || DevProperties.get().enableGameTests;
	}
}
