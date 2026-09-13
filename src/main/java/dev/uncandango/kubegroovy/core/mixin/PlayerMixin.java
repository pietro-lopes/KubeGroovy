package dev.uncandango.kubegroovy.core.mixin;

import dev.uncandango.kubegroovy.core.InventoryKGY;
import dev.uncandango.kubegroovy.core.PlayerKGY;
import dev.uncandango.kubegroovy.player.KubeGroovyInventoryListener;
import dev.uncandango.kubegroovy.plugin.KubeGroovyPlugin;
import dev.uncandango.kubegroovy.plugin.KubeGroovyPlugins;
import dev.uncandango.kubegroovy.stages.StageEvents;
import dev.uncandango.kubegroovy.stages.Stages;
import dev.uncandango.kubegroovy.typings.Info;
import dev.uncandango.kubegroovy.util.AttachedData;
import dev.uncandango.kubegroovy.script.annotations.RemapForGY;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = Player.class, priority = 1001)
@RemapPrefixForGY("kgy$")
public abstract class PlayerMixin implements PlayerKGY {
	@Unique
	private @Nullable Stages kgy$stages;

	@Unique
	private @Nullable AttachedData<Player> kgy$attachedData;

	@Unique
	private @Nullable KubeGroovyInventoryListener kgy$inventoryChangeListener;

	@Override
	public Stages kgy$getStages() {
		if (kgy$stages == null) {
			kgy$stages = StageEvents.create(kgy$self());
		}

		return kgy$stages;
	}

	@Override
	public InventoryKGY kgy$getInventory() {
		return kgy$self().getInventory();
	}

	@Override
	public InventoryKGY kgy$getCraftingGrid() {
		return kgy$self().inventoryMenu.getCraftSlots();
	}

	@Override
	public AttachedData<Player> kgy$getData() {
		if (kgy$attachedData == null) {
			kgy$attachedData = new AttachedData<>(kgy$self());
			KubeGroovyPlugins.forEachPlugin(kgy$attachedData, KubeGroovyPlugin::attachPlayerData);
		}

		return kgy$attachedData;
	}

	@Shadow
	@RemapForGY("closeMenu")
	public abstract void closeContainer();

	@Override
	public KubeGroovyInventoryListener kgy$getInventoryChangeListener() {
		if (kgy$inventoryChangeListener == null) {
			kgy$inventoryChangeListener = new KubeGroovyInventoryListener((Player) (Object) this);
		}

		return kgy$inventoryChangeListener;
	}

	@Shadow
	@Info("Checks, whether the player is in Spectator mode.")
	public abstract boolean isSpectator();

	@Shadow
	@Info("Checks, whether the player is in Creative mode.")
	public abstract boolean isCreative();
}
