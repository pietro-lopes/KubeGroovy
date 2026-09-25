package dev.uncandango.kubegroovy.core;

import dev.uncandango.kubegroovy.util.RegistryAccessContainer;
import dev.uncandango.kubegroovy.script.bridge.Context;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import dev.uncandango.kubegroovy.script.annotations.SpecialEquality;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

@RemapPrefixForGY("kgy$")
public interface RegistryObjectKGY<T> extends SpecialEquality {
	@Override
	default boolean specialEquals(@Nullable Object o, boolean shallow) {
		var cx = Context.getCurrent();
		return switch (o) {
			case CharSequence cs -> kgy$getId().equals(cs.toString());
			case Identifier id -> kgy$getIdLocation().equals(id);
			case null, default -> equals(o);
		};

	}

	default ResourceKey<Registry<T>> kgy$getRegistryId() {
		throw new NoMixinException();
	}

	default Registry<T> kgy$getRegistry() {
		return RegistryAccessContainer.current.lookupOrThrow(kgy$getRegistryId());
	}

	@SuppressWarnings("unchecked")
	default Holder<T> kgy$asHolder() {
		try {
			return kgy$getRegistry().wrapAsHolder((T) this);
		} catch (Exception ex) {
			return (Holder<T>) Holder.direct(this);
		}
	}

	@NullUnmarked // should not be null in practical scenarios (i.e. when holders are bound)
	@SuppressWarnings("unchecked")
	default ResourceKey<T> kgy$getKey() {
		try {
			return kgy$asHolder().getKey();
		} catch (Exception ex) {
			return kgy$getRegistry().getResourceKey((T) this).orElseThrow();
		}
	}

	default Identifier kgy$getIdLocation() {
		return kgy$getKey().identifier();
	}

	default String kgy$getId() {
		return kgy$getIdLocation().toString();
	}

	default String kgy$getMod() {
		return kgy$getIdLocation().getNamespace();
	}

	default List<TagKey<T>> kgy$getTagKeys() {
		return kgy$asHolder().tags().toList();
	}

	default List<Identifier> kgy$getTags() {
		return kgy$asHolder().tags().map(TagKey::location).toList();
	}

	default boolean kgy$hasTag(Identifier tag) {
		return kgy$asHolder().is(TagKey.create(kgy$getRegistryId(), tag));
	}
}
