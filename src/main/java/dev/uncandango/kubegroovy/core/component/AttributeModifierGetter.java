package dev.uncandango.kubegroovy.core.component;

import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import dev.uncandango.kubegroovy.script.annotations.ReturnsSelf;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import org.jspecify.annotations.Nullable;

@RemapPrefixForGY("kgy$")
@ReturnsSelf
public interface AttributeModifierGetter {
	ItemAttributeModifiers kgy$getAttributeModifiers();

	default boolean kgy$hasAttributeModifier(Holder<Attribute> attribute, Identifier id) {
		for (var entry : kgy$getAttributeModifiers().modifiers()) {
			if (entry.matches(attribute, id)) {
				return true;
			}
		}
		return false;
	}

	@Nullable
	default AttributeModifier kgy$getAttributeModifier(Holder<Attribute> attribute, Identifier id) {
		for (var entry : kgy$getAttributeModifiers().modifiers()) {
			if (entry.matches(attribute, id)) {
				return entry.modifier();
			}
		}
		return null;
	}

	default double kgy$getAttackDamage() {
		var base = kgy$getBaseAttackDamage();
		var sum = base;

		for (var entry : kgy$getAttributeModifiers().modifiers()) {
			if (entry.matches(Attributes.ATTACK_DAMAGE, net.minecraft.world.item.Item.BASE_ATTACK_DAMAGE_ID)) {
				continue;
			}

			var mod = entry.modifier();
			double d1 = mod.amount();

			sum += switch (mod.operation()) {
				case ADD_VALUE -> d1;
				case ADD_MULTIPLIED_BASE -> d1 * base;
				case ADD_MULTIPLIED_TOTAL -> d1 * sum;
			};
		}
		return sum;
	}

	default double kgy$getAttackSpeed() {
		var base = kgy$getBaseAttackSpeed();
		var sum = base;

		for (var entry : kgy$getAttributeModifiers().modifiers()) {
			if (entry.matches(Attributes.ATTACK_SPEED, net.minecraft.world.item.Item.BASE_ATTACK_SPEED_ID)) {
				continue;
			}

			var mod = entry.modifier();
			double d1 = mod.amount();

			sum += switch (mod.operation()) {
				case ADD_VALUE -> d1;
				case ADD_MULTIPLIED_BASE -> d1 * base;
				case ADD_MULTIPLIED_TOTAL -> d1 * sum;
			};
		}
		return sum;
	}

	default double kgy$getBaseAttackDamage() {
		for (var modifier : kgy$getAttributeModifiers().modifiers()) {
			if (modifier.matches(Attributes.ATTACK_DAMAGE, net.minecraft.world.item.Item.BASE_ATTACK_DAMAGE_ID)) {
				return modifier.modifier().amount();
			}
		}
		return 0.0;
	}

	default double kgy$getBaseAttackSpeed() {
		for (var modifier : kgy$getAttributeModifiers().modifiers()) {
			if (modifier.matches(Attributes.ATTACK_SPEED, net.minecraft.world.item.Item.BASE_ATTACK_SPEED_ID)) {
				return modifier.modifier().amount();
			}
		}
		return 0.0;
	}
}
