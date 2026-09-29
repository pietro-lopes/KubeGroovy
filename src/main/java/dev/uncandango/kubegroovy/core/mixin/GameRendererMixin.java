package dev.uncandango.kubegroovy.core.mixin;

import dev.uncandango.kubegroovy.client.KubeSessionData;
import dev.uncandango.kubegroovy.core.GameRendererKGY;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin implements GameRendererKGY {
	@Shadow
	@Final
	Minecraft minecraft;

	@Shadow
	private @Nullable Identifier postEffectId;

	@Shadow
	public abstract void setPostEffect(Identifier identifier);

	@Inject(method = "checkEntityPostEffect", at = @At("HEAD"), cancellable = true)
	private void kgy$checkEntityPostEffect(@Nullable Entity cameraEntity, CallbackInfo ci) {
		var data = KubeSessionData.of(minecraft);
		if (data != null && data.activePostShader != null) {
			setPostEffect(data.activePostShader);
			ci.cancel();
		}
	}

	@Override
	@Accessor("guiRenderer")
	public abstract GuiRenderer kgy$getGuiRenderer();

	@Override
	@Accessor("fogRenderer")
	public abstract FogRenderer kgy$getFogRenderer();
}