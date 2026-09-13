package dev.uncandango.kubegroovy.core.mixin;

import dev.uncandango.kubegroovy.core.ItemEntityKGY;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ItemEntity.class)
@RemapPrefixForGY("kgy$")
public abstract class ItemEntityMixin implements ItemEntityKGY {
}
