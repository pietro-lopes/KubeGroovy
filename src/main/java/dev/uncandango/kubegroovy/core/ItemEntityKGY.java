package dev.uncandango.kubegroovy.core;

import dev.uncandango.kubegroovy.typings.Info;
import dev.uncandango.kubegroovy.typings.ThisIs;
import dev.uncandango.kubegroovy.script.annotations.HideFromGY;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

@RemapPrefixForGY("kgy$")
public interface ItemEntityKGY extends EntityKGY {
	@Override
	@HideFromGY
	default ItemEntity kgy$self() {
		return (ItemEntity) this;
	}

	@Override
	@Nullable
	@Info("""
		Gets the item stack corresponding to the item contained in the item entity.
		Will be `null` if the contained stack is empty.
		""")
	default ItemStack kgy$getItem() {
		var stack = kgy$self().getItem();
		return stack.isEmpty() ? null : stack;
	}

	@Override
	@ThisIs(ItemEntity.class)
	default boolean kgy$isItem() {
		return true;
	}

	default int kgy$getLifespan() {
		return kgy$self().lifespan;
	}

	default void kgy$setLifespan(int lifespan) {
		kgy$self().lifespan = lifespan;
	}

	default void kgy$setDefaultPickUpDelay() {
		kgy$self().setPickUpDelay(10);
	}

	default void kgy$setNoPickUpDelay() {
		kgy$self().setPickUpDelay(0);
	}

	default void kgy$setInfinitePickUpDelay() {
		kgy$self().setPickUpDelay(Short.MAX_VALUE);
	}

	default void kgy$setNoDespawn() {
		kgy$self().setUnlimitedLifetime();
	}

	default int kgy$getTicksUntilDespawn() {
		return kgy$getLifespan() - kgy$self().age;
	}

	default void kgy$setTicksUntilDespawn(int ticks) {
		kgy$self().age = kgy$getLifespan() - ticks;
	}
}
