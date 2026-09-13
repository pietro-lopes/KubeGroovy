package dev.uncandango.kubegroovy.core.mixin;

import dev.uncandango.kubegroovy.core.AdvancementNodeKGY;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import net.minecraft.advancements.AdvancementNode;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(AdvancementNode.class)
@RemapPrefixForGY("kgy$")
public abstract class AdvancementNodeMixin implements AdvancementNodeKGY {
}
