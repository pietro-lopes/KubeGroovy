package dev.uncandango.kubegroovy.core;

import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.Set;

@RemapPrefixForGY("kgy$")
public interface AdvancementNodeKGY {
	default AdvancementNode kgy$self() {
		return (AdvancementNode) this;
	}

	default Identifier kgy$getId() {
		return kgy$self().holder().id();
	}

	@Nullable
	default AdvancementNode kgy$getParent() {
		return kgy$self().parent();
	}

	default Set<AdvancementNode> kgy$getChildren() {
		return (Set) kgy$self().children();
	}

	default void kgy$addChild(AdvancementNode a) {
		kgy$self().addChild(a);
	}

	default Component kgy$getDisplayText() {
		return kgy$self().advancement().name().orElse(Component.empty());
	}

	default boolean kgy$hasDisplay() {
		return kgy$self().advancement().display().isPresent();
	}

	default Component kgy$getTitle() {
		return kgy$self().advancement().display().map(DisplayInfo::getTitle).orElse(Component.empty());
	}

	default Component kgy$getDescription() {
		return kgy$self().advancement().display().map(DisplayInfo::getDescription).orElse(Component.empty());
	}

	@Nullable
	default DisplayInfo kgy$getDisplay() {
		return kgy$self().advancement().display().orElse(null);
	}
}
