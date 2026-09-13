package dev.uncandango.kubegroovy.core.mixin;

import dev.uncandango.kubegroovy.typings.Info;
import dev.uncandango.kubegroovy.script.annotations.HideFromGY;
import dev.uncandango.kubegroovy.script.annotations.RemapForGY;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.Optional;
import java.util.function.Predicate;

@Mixin(Holder.class)
@RemapPrefixForGY("kgy$")
public interface HolderMixin<T> {
	@Shadow
	@HideFromGY
	boolean is(TagKey<T> tagKey);

	@Shadow
	@HideFromGY
	boolean is(ResourceKey<T> resourceKey);

	@Shadow
	@Deprecated
	@HideFromGY
	boolean is(Holder<T> holder);

	@Shadow
	@Info("Tests the resource key held by the holder using a predicate.")
	@RemapForGY("test")
	boolean is(Predicate<ResourceKey<T>> predicate);

	@Shadow
	Optional<ResourceKey<T>> unwrapKey();

	@Shadow
	@Info("Test if the holder holds an object registered with given resource location.")
	boolean is(Identifier location);

	@Unique
	@Info("Test if a tag matches the object this holder holds.")
	default boolean kgy$isTag(Identifier tagKey) {
		return this.unwrapKey().map(resourceKey -> is(TagKey.create(resourceKey.registryKey(), tagKey))).orElse(false);
	}
}
