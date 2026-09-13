package dev.uncandango.kubegroovy.core.mixin;

import dev.uncandango.kubegroovy.core.ItemFrameEntityKGY;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import net.minecraft.world.entity.decoration.ItemFrame;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ItemFrame.class)
@RemapPrefixForGY("kgy$")
public abstract class ItemFrameEntityMixin implements ItemFrameEntityKGY {
}
