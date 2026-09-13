package dev.uncandango.kubegroovy.core.mixin;

import dev.uncandango.kubegroovy.core.ItemKGY;
import dev.uncandango.kubegroovy.item.ItemBehavior;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

@Mixin(value = Item.class, priority = 1001)
@RemapPrefixForGY("kgy$")
public abstract class ItemMixin implements ItemKGY {
	@Shadow
	public abstract DataComponentMap components();

	@Shadow
	@Final
	private Holder.Reference<Item> builtInRegistryHolder;

	@Unique
	private @Nullable ItemBehavior kgy$behavior;

	@Unique
	private @Nullable Map<String, Object> kgy$typeData;

	@Unique
	private @Nullable ResourceKey<Item> kgy$registryKey;

	@Unique
	private @Nullable String kgy$id;

	@Override
	@Nullable
	public ItemBehavior kgy$getItemBehavior() {
		return kgy$behavior;
	}

	@Override
	public void kgy$setItemBehavior(ItemBehavior b) {
		kgy$behavior = b;
	}

	@Override
	public Holder.Reference<Item> kgy$asHolder() {
		return builtInRegistryHolder;
	}

	@Override
	public ResourceKey<Item> kgy$getKey() {
		return kgy$asHolder().getKey();
	}

	@Override
	public String kgy$getId() {
		if (kgy$id == null) {
			kgy$id = ItemKGY.super.kgy$getId();
		}

		return kgy$id;
	}

	@Override
	public Map<String, Object> kgy$getTypeData() {
		if (kgy$typeData == null) {
			kgy$typeData = new HashMap<>();
		}

		return kgy$typeData;
	}

	@Override
	@Accessor("craftingRemainingItem")
	@Mutable
	public abstract void kgy$setCraftingRemainder(ItemStackTemplate i);

	@Inject(method = "isFoil", at = @At("HEAD"), cancellable = true)
	private void isFoil(ItemStack itemStack, CallbackInfoReturnable<Boolean> ci) {
		if (kgy$behavior != null && kgy$behavior.glow) {
			ci.setReturnValue(true);
		}
	}

	@Inject(method = "appendHoverText", at = @At("RETURN"))
	private void appendHoverText(
		ItemStack itemStack,
		Item.TooltipContext context,
		TooltipDisplay display,
		Consumer<Component> builder,
		TooltipFlag tooltipFlag,
		CallbackInfo ci
	) {
		if (kgy$behavior != null && !kgy$behavior.tooltip.isEmpty()) {
			for (var c : kgy$behavior.tooltip) {
				builder.accept(c);
			}
		}
	}

	@Inject(method = "isBarVisible", at = @At("HEAD"), cancellable = true)
	private void isBarVisible(ItemStack stack, CallbackInfoReturnable<Boolean> ci) {
		if (kgy$behavior != null && kgy$behavior.barWidth != null && kgy$behavior.barWidth.applyAsInt(stack) <= Item.MAX_BAR_WIDTH) {
			ci.setReturnValue(true);
		}
	}

	@Inject(method = "getBarWidth", at = @At("HEAD"), cancellable = true)
	private void getBarWidth(ItemStack stack, CallbackInfoReturnable<Integer> ci) {
		if (kgy$behavior != null && kgy$behavior.barWidth != null) {
			ci.setReturnValue(kgy$behavior.barWidth.applyAsInt(stack));
		}
	}

	@Inject(method = "getBarColor", at = @At("HEAD"), cancellable = true)
	private void getBarColor(ItemStack stack, CallbackInfoReturnable<Integer> ci) {
		if (kgy$behavior != null && kgy$behavior.barColor != null) {
			ci.setReturnValue(kgy$behavior.barColor.apply(stack).kgy$getRGB());
		}
	}

	@Inject(method = "getUseDuration", at = @At("HEAD"), cancellable = true)
	private void getUseDuration(ItemStack itemStack, LivingEntity entity, CallbackInfoReturnable<Integer> cir) {
		if (kgy$behavior != null && kgy$behavior.useDuration != null) {
			cir.setReturnValue(kgy$behavior.useDuration.applyAsInt(itemStack, entity));
		}
	}

	@Inject(method = "getUseAnimation", at = @At("HEAD"), cancellable = true)
	private void getUseAnimation(ItemStack itemStack, CallbackInfoReturnable<ItemUseAnimation> ci) {
		if (kgy$behavior != null && kgy$behavior.anim != null) {
			ci.setReturnValue(kgy$behavior.anim);
		}
	}

	@Inject(method = "getName", at = @At("HEAD"), cancellable = true)
	private void getName(ItemStack itemStack, CallbackInfoReturnable<Component> ci) {
		if (kgy$behavior != null && kgy$behavior.nameGetter != null) {
			ci.setReturnValue(kgy$behavior.nameGetter.apply(itemStack));
		}

		if (kgy$behavior != null && kgy$behavior.displayName != null && kgy$behavior.formattedDisplayName) {
			ci.setReturnValue(kgy$behavior.displayName);
		}
	}

	@Inject(method = "use", at = @At("HEAD"), cancellable = true)
	private void use(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		if (kgy$behavior != null && kgy$behavior.use != null) {
			if (kgy$behavior.use.use(level, player, hand)) {
				cir.setReturnValue(ItemUtils.startUsingInstantly(level, player, hand));
			} else {
				cir.setReturnValue(InteractionResult.FAIL);
			}
		}
	}

	@Inject(method = "finishUsingItem", at = @At("HEAD"), cancellable = true)
	private void finishUsingItem(ItemStack itemStack, Level level, LivingEntity livingEntity, CallbackInfoReturnable<ItemStack> ci) {
		if (kgy$behavior != null && kgy$behavior.finishUsing != null) {
			ci.setReturnValue(kgy$behavior.finishUsing.finishUsingItem(itemStack, level, livingEntity));
		}
	}

	@Inject(method = "releaseUsing", at = @At("HEAD"), cancellable = true)
	private void releaseUsing(ItemStack itemStack, Level level, LivingEntity entity, int remainingTime, CallbackInfoReturnable<Boolean> cir) {
		if (kgy$behavior != null && kgy$behavior.releaseUsing != null) {
			kgy$behavior.releaseUsing.releaseUsing(itemStack, level, entity, remainingTime);
		}
	}

	@Inject(method = "hurtEnemy", at = @At("HEAD"), cancellable = true)
	private void hurtEnemy(ItemStack itemStack, LivingEntity mob, LivingEntity attacker, CallbackInfo ci) {
		if (kgy$behavior != null && kgy$behavior.hurtEnemy != null) {
			kgy$behavior.hurtEnemy.test(new ItemBehavior.HurtEnemyContext(itemStack, mob, attacker));
		}
	}

	@Override
	public Ingredient kgy$asIngredient() {
		return Ingredient.of(kgy$self());
	}

	@Override
	@Accessor("descriptionId")
	@Mutable
	public abstract void kgy$setNameKey(String key);

	@Override
	@Accessor("canCombineRepair")
	@Mutable
	public abstract void kgy$setCanRepair(boolean repairable);
}
