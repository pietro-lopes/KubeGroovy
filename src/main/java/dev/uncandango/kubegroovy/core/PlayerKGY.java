package dev.uncandango.kubegroovy.core;

import com.mojang.authlib.GameProfile;
import dev.uncandango.kubegroovy.player.KubeGroovyInventoryListener;
import dev.uncandango.kubegroovy.player.PlayerStatsGY;
import dev.uncandango.kubegroovy.stages.Stages;
import dev.uncandango.kubegroovy.typings.Info;
import dev.uncandango.kubegroovy.typings.ThisIs;
import dev.uncandango.kubegroovy.util.NotificationToastData;
import dev.uncandango.kubegroovy.script.annotations.HideFromGY;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.jspecify.annotations.Nullable;

import javax.annotation.Nonnull;

@RemapPrefixForGY("kgy$")
public interface PlayerKGY extends LivingEntityKGY, DataSenderKGY, WithAttachedData<Player> {
	@Override
	@HideFromGY
	default Player kgy$self() {
		return (Player) this;
	}

	default Stages kgy$getStages() {
		throw new NoMixinException();
	}

	default PlayerStatsGY kgy$getStats() {
		throw new NoMixinException();
	}

	default boolean kgy$isMiningBlock() {
		throw new NoMixinException();
	}

	@Override
	@ThisIs(Player.class)
	@Info("Checks if the entity is a player entity.")
	default boolean kgy$isPlayer() {
		return true;
	}

	@ThisIs(FakePlayer.class)
	@Info("Checks if the player is fake.")
	default boolean kgy$isFake() {
		return this instanceof FakePlayer;
	}

	@Override
	@Nonnull
	@Info("Gets the player's profile.")
	default GameProfile kgy$getProfile() {
		return kgy$self().getGameProfile();
	}

	@Override
	@Info("Gets the player's username.")
	default String kgy$getUsername() {
		return kgy$self().getGameProfile().name();
	}

	default InventoryKGY kgy$getInventory() {
		throw new NoMixinException();
	}

	default InventoryKGY kgy$getCraftingGrid() {
		throw new NoMixinException();
	}

	default void kgy$sendInventoryUpdate() {
		kgy$self().getInventory().setChanged();
		kgy$self().inventoryMenu.getCraftSlots().setChanged();
		kgy$self().inventoryMenu.broadcastChanges();
	}

	default void kgy$give(ItemStack item) {
		kgy$self().getInventory().add(item);
	}

	default void kgy$giveInHand(ItemStack item) {
		kgy$self().getInventory().add(kgy$getSelectedSlot(), item);
	}

	default int kgy$getSelectedSlot() {
		return kgy$self().getInventory().getSelectedSlot();
	}

	default void kgy$setSelectedSlot(int index) {
		kgy$self().getInventory().setSelectedSlot(Mth.clamp(index, 0, 8));
	}

	default ItemStack kgy$getMouseItem() {
		return kgy$self().containerMenu.getCarried();
	}

	default void kgy$setMouseItem(ItemStack item) {
		kgy$self().containerMenu.setCarried(item);
	}

	@Override
	default void kgy$setStatusMessage(Component message) {
		kgy$self().sendOverlayMessage(message);
	}

	@Override
	default void kgy$spawn() {
	}

	default void kgy$addFood(int hunger, float saturation) {
		kgy$self().getFoodData().eat(hunger, saturation);
	}

	default int kgy$getFoodLevel() {
		return kgy$self().getFoodData().getFoodLevel();
	}

	default void kgy$setFoodLevel(int foodLevel) {
		kgy$self().getFoodData().setFoodLevel(foodLevel);
	}

	default float kgy$getSaturation() {
		return kgy$self().getFoodData().getSaturationLevel();
	}

	default void kgy$setSaturation(float saturation) {
		kgy$self().getFoodData().setSaturation(saturation);
	}

	default void kgy$addExhaustion(float exhaustion) {
		kgy$self().causeFoodExhaustion(exhaustion);
	}

	default void kgy$addXP(int xp) {
		kgy$self().giveExperiencePoints(xp);
	}

	default void kgy$addXPLevels(int levels) {
		kgy$self().giveExperienceLevels(levels);
	}

	default void kgy$setXp(int xp) {
		kgy$self().totalExperience = 0;
		kgy$self().experienceProgress = 0F;
		kgy$self().experienceLevel = 0;
		kgy$self().giveExperiencePoints(xp);
	}

	default int kgy$getXp() {
		return kgy$self().totalExperience;
	}

	default void kgy$setXpLevel(int levels) {
		kgy$self().totalExperience = 0;
		kgy$self().experienceProgress = 0F;
		kgy$self().experienceLevel = 0;
		kgy$self().giveExperienceLevels(levels);
	}

	default int kgy$getXpLevel() {
		return kgy$self().experienceLevel;
	}

	default void kgy$boostElytraFlight() {
		if (kgy$self().isFallFlying()) {
			var v = kgy$self().getLookAngle();
			var d0 = 1.5D;
			var d1 = 0.1D;
			var m = kgy$self().getDeltaMovement();
			kgy$self().setDeltaMovement(m.add(v.x * d1 + (v.x * d0 - m.x) * 0.5D, v.y * d1 + (v.y * d0 - m.y) * 0.5D, v.z * d1 + (v.z * d0 - m.z) * 0.5D));
		}
	}

	default AbstractContainerMenu kgy$getOpenInventory() {
		return kgy$self().containerMenu;
	}

	default void kgy$addItemCooldown(Item item, int ticks) {
		kgy$self().getCooldowns().addCooldown(item.getDefaultInstance(), ticks);
	}

	default KubeGroovyInventoryListener kgy$getInventoryChangeListener() {
		throw new NoMixinException();
	}

	default void kgy$notify(NotificationToastData builder) {
		throw new NoMixinException();
	}

	default void kgy$notify(Component title, @Nullable Component text) {
		kgy$notify(NotificationToastData.ofTitle(title, text));
	}
}
