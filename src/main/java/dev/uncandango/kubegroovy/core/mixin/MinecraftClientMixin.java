package dev.uncandango.kubegroovy.core.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.uncandango.kubegroovy.client.ClientAssetPacks;
import dev.uncandango.kubegroovy.client.ClientPlayerKubeEvent;
import dev.uncandango.kubegroovy.client.ClientProperties;
import dev.uncandango.kubegroovy.client.ScheduledClientEvent;
import dev.uncandango.kubegroovy.core.MinecraftClientKGY;
import dev.uncandango.kubegroovy.plugin.builtin.event.ClientEvents;
import dev.uncandango.kubegroovy.script.ScriptType;
import dev.uncandango.kubegroovy.util.ScheduledEvents;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.packs.PackResources;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Mixin(Minecraft.class)
@RemapPrefixForGY("kgy$")
public abstract class MinecraftClientMixin implements MinecraftClientKGY {
	@Shadow
	@Nullable
	public LocalPlayer player;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void kgy$init(CallbackInfo ci) {
		CompletableFuture.runAsync(() -> kgy$afterResourcesLoaded(false), kgy$self());
	}

	@Inject(method = "createTitle", at = @At("HEAD"), cancellable = true)
	private void kgy$createTitle(CallbackInfoReturnable<String> ci) {
		var s = ClientProperties.get().windowTitle;

		if (!s.isEmpty()) {
			ci.setReturnValue(s);
		}
	}

	@Shadow
	protected abstract String createTitle();

	@Override
	public String kgy$getTitle() {
		return createTitle();
	}

	@ModifyExpressionValue(
		method = {"reloadResourcePacks(ZLnet/minecraft/client/Minecraft$GameLoadCookie;)Ljava/util/concurrent/CompletableFuture;", "<init>"},
		at = @At(value = "INVOKE", target = "Lnet/minecraft/server/packs/repository/PackRepository;openAllSelected()Ljava/util/List;")
	)
	private List<PackResources> kgy$loadPacks(List<PackResources> resources) {
		return ClientAssetPacks.INSTANCE.inject(resources);
	}

	@Inject(method = "startAttack", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;swing(Lnet/minecraft/world/InteractionHand;)V", shift = At.Shift.AFTER))
	private void kgy$startAttack(CallbackInfoReturnable<Boolean> cir) {
		kgy$startAttack0();
	}

	@Inject(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;startUseItem()V", ordinal = 0, shift = At.Shift.AFTER))
	private void kgy$startUseItem(CallbackInfo ci) {
		kgy$startUseItem0();
	}

	@Inject(method = "tick", at = @At("RETURN"))
	private void kgy$postTickClient(CallbackInfo ci) {
		if (kgy$self().level != null && kgy$self().player != null) {
			ScheduledClientEvent.EVENTS.tickAll(kgy$self().level.getGameTime());

			if (ClientEvents.TICK.hasListeners()) {
				ClientEvents.TICK.post(ScriptType.CLIENT, new ClientPlayerKubeEvent(player));
			}
		}
	}

	@Override
	public ScheduledEvents kgy$getScheduledEvents() {
		return ScheduledClientEvent.EVENTS;
	}

	@Inject(method = "reloadResourcePacks(ZLnet/minecraft/client/Minecraft$GameLoadCookie;)Ljava/util/concurrent/CompletableFuture;", at = @At("TAIL"))
	private void kgy$endResourceReload(boolean bl, Minecraft.GameLoadCookie gameLoadCookie, CallbackInfoReturnable<CompletableFuture<Void>> cir) {
		CompletableFuture.runAsync(() -> kgy$afterResourcesLoaded(true), kgy$self());
	}
}