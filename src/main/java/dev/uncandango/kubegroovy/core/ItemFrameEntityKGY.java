package dev.uncandango.kubegroovy.core;

import dev.uncandango.kubegroovy.typings.Info;
import dev.uncandango.kubegroovy.typings.ThisIs;
import dev.uncandango.kubegroovy.script.annotations.HideFromGY;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

@RemapPrefixForGY("kgy$")
public interface ItemFrameEntityKGY extends EntityKGY {
	@Override
	@HideFromGY
	default ItemFrame kgy$self() {
		return (ItemFrame) this;
	}

	@Override
	@ThisIs(ItemFrame.class)
	@Info("Checks if the entity is an item frame entity.")
	default boolean kgy$isFrame() {
		return true;
	}

	@Override
	@Nullable
	@Info("""
		Gets the item stack corresponding to the item in the item frame.
		Will be `null` if the contained stack is empty.
		""")
	default ItemStack kgy$getItem() {
		var stack = kgy$self().getItem();
		return stack.isEmpty() ? null : stack;
	}
}
