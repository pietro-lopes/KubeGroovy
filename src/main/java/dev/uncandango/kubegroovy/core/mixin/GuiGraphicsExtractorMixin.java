package dev.uncandango.kubegroovy.core.mixin;

import dev.uncandango.kubegroovy.CommonProperties;
import dev.uncandango.kubegroovy.client.ClientProperties;
import dev.uncandango.kubegroovy.client.KubeGroovyClient;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsExtractorMixin {

	@Inject(
		method = "itemCount",
		at = @At("HEAD"),
		cancellable = true
	)
	private void kgy$renderItemCount(Font font, ItemStack itemStack, int x, int y, String countText, CallbackInfo ci) {
		if (countText == null && CommonProperties.get().removeSlotLimit && ClientProperties.get().customStackSizeText && itemStack.getCount() > 1) {
			KubeGroovyClient.drawStackSize((GuiGraphicsExtractor) (Object) this, font, itemStack.getCount(), x, y, -1, true);
			ci.cancel();
		}
	}
}