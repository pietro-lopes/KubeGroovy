package dev.uncandango.kubegroovy.core.mixin;

import dev.uncandango.kubegroovy.core.ItemStackKGY;
import dev.uncandango.kubegroovy.script.bridge.Context;
import dev.uncandango.kubegroovy.script.annotations.HideFromGY;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;

@Mixin(ItemStack.class)
@RemapPrefixForGY("kgy$")
public abstract class ItemStackMixin implements ItemStackKGY {

	@Shadow
	@Final
	private PatchedDataComponentMap components;

	@Shadow
	@HideFromGY
	public abstract void enchant(Holder<Enchantment> enchantment, int level);

	@Shadow
	@HideFromGY
	public abstract ItemEnchantments getEnchantments();

	@Shadow
	@HideFromGY
	public abstract Holder<Item> typeHolder();

	// Moved to ExtraCodecsMixin which should now intercept globally
	/*@ModifyConstant(method = "lambda$static$3", constant = @Constant(intValue = 99))
	private static int kgy$maxSlotSize(int original) {
		return CommonProperties.get().getMaxSlotSize(original);
	}*/

	@Override
	public void kgy$resetComponents() {
		var cx = Context.getCurrent();
		components.restorePatch(DataComponentPatch.EMPTY);
	}

	@Inject(method = "consume", at = @At("HEAD"))
	private void kgy$onConsume(int amount, @Nullable LivingEntity owner, CallbackInfo ci) {
		if (amount <= 0 || owner == null) {
			return;
		}
		ItemStack stack = (ItemStack) (Object) this;
		if (!stack.has(DataComponents.FOOD) || !stack.has(DataComponents.CONSUMABLE)) {
			return;
		}
		if (owner instanceof Player player) {
			player.kgy$foodEaten(stack, Objects.requireNonNull(stack.get(DataComponents.FOOD)));
		}
	}
}
