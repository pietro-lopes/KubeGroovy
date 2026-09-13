package dev.uncandango.kubegroovy.core.mixin;

import dev.uncandango.kubegroovy.core.LevelKGY;
import dev.uncandango.kubegroovy.plugin.KubeGroovyPlugin;
import dev.uncandango.kubegroovy.plugin.KubeGroovyPlugins;
import dev.uncandango.kubegroovy.util.AttachedData;
import dev.uncandango.kubegroovy.script.annotations.HideFromGY;
import dev.uncandango.kubegroovy.script.annotations.RemapForGY;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.LevelEntityGetter;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.UUID;

@Mixin(Level.class)
@RemapPrefixForGY("kgy$")
public abstract class LevelMixin implements LevelKGY {
	@Unique
	private @Nullable AttachedData<Level> kgy$attachedData;

	@Override
	public AttachedData<Level> kgy$getData() {
		if (kgy$attachedData == null) {
			kgy$attachedData = new AttachedData<>(kgy$self());
			KubeGroovyPlugins.forEachPlugin(kgy$attachedData, KubeGroovyPlugin::attachLevelData);
		}

		return kgy$attachedData;
	}

	@Shadow
	@Nullable
	@HideFromGY
	public abstract Entity getEntity(UUID uniqueId);

	@Shadow
	@RemapForGY("getDimensionKey")
	public abstract ResourceKey<Level> dimension();

	@Shadow
	@HideFromGY
	protected abstract LevelEntityGetter<Entity> getEntities();

	@Override
	public Iterable<? extends Entity> kgy$getMcEntities() {
		return getEntities().getAll();
	}

	@Override
	@Nullable
	public Entity kgy$getEntityByUUID(UUID id) {
		return getEntities().get(id);
	}

	@Override
	@Nullable
	public Entity kgy$getEntityByNetworkID(int id) {
		return getEntities().get(id);
	}
}
