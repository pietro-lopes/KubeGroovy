package dev.uncandango.kubegroovy.core.component;

import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.component.ItemAttributeModifiers;

@RemapPrefixForGY("kgy$")
public interface AttributeModifierSetter {
	void kgy$setAttributeModifiers(ItemAttributeModifiers modifiers);

	default void kgy$addAttributeModifier(Holder<Attribute> attribute, AttributeModifier mod, EquipmentSlotGroup slot) {
		if (this instanceof AttributeModifierGetter amf) {
			kgy$setAttributeModifiers(amf.kgy$getAttributeModifiers().withModifierAdded(attribute, mod, slot));
		}
	}
}
