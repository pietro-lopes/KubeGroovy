package dev.uncandango.kubegroovy.core.component;

import dev.uncandango.kubegroovy.typings.Info;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import java.util.ArrayList;
import java.util.List;

import static net.minecraft.world.item.Item.BASE_ATTACK_DAMAGE_ID;
import static net.minecraft.world.item.Item.BASE_ATTACK_SPEED_ID;

@RemapPrefixForGY("kgy$")
public interface AttributeModifierAccessor extends AttributeModifierGetter, AttributeModifierSetter {
	default void kgy$setAttributeModifiersWithTooltip(List<ItemAttributeModifiers.Entry> modifiers) {
		var out = new ArrayList<ItemAttributeModifiers.Entry>(modifiers.size());
		for (var e : modifiers) {
			out.add(new ItemAttributeModifiers.Entry(e.attribute(), e.modifier(), e.slot(), ItemAttributeModifiers.Display.attributeModifiers()));
		}
		kgy$setAttributeModifiers(new ItemAttributeModifiers(out));
	}

	default void kgy$setAttributeModifiersWithoutTooltip(List<ItemAttributeModifiers.Entry> modifiers) {
		var out = new ArrayList<ItemAttributeModifiers.Entry>(modifiers.size());
		for (var e : modifiers) {
			out.add(new ItemAttributeModifiers.Entry(e.attribute(), e.modifier(), e.slot(), ItemAttributeModifiers.Display.hidden()));
		}
		kgy$setAttributeModifiers(new ItemAttributeModifiers(out));
	}

	@Info("""
		Sets the attack speed of this item to the given value, **removing** all other modifiers to attack speed.
		Note that players have a default attack speed of 4.0, so this modifier is added on top of that.
		(Example: Swords have an attack speed of -2.4, leading to a total value of 1.6 without any other changes.)
		""")
	default void kgy$setAttackSpeed(double speed) {
		var oldMods = kgy$getAttributeModifiers();

		var list = new ArrayList<ItemAttributeModifiers.Entry>(oldMods.modifiers().size());
		for (var entry : oldMods.modifiers()) {
			if (entry.attribute().equals(Attributes.ATTACK_SPEED)) {
				continue;
			}
			list.add(entry);
		}

		ItemAttributeModifiers.Display display = ItemAttributeModifiers.Display.attributeModifiers();
		for (var entry : oldMods.modifiers()) {
			if (entry.attribute().equals(Attributes.ATTACK_SPEED)) {
				display = entry.display();
				break;
			}
		}

		list.add(new ItemAttributeModifiers.Entry(
			Attributes.ATTACK_SPEED,
			new AttributeModifier(BASE_ATTACK_SPEED_ID, speed, AttributeModifier.Operation.ADD_VALUE),
			EquipmentSlotGroup.MAINHAND,
			display
		));

		kgy$setAttributeModifiers(new ItemAttributeModifiers(list));
	}

	@Info("""
		Sets the attack damage of this item to the given value, **removing** all other modifiers to attack damage.
		Note that since players have a default attack damage of 1.0, total damage will be (dmg + 1.0) before other modifiers.
		(In practice, this simply means that most weapons have this value set to 1 less than what you might think.)
		""")
	default void kgy$setAttackDamage(double dmg) {
		var oldMods = kgy$getAttributeModifiers();

		var list = new ArrayList<ItemAttributeModifiers.Entry>(oldMods.modifiers().size());
		for (var entry : oldMods.modifiers()) {
			if (entry.attribute().equals(Attributes.ATTACK_DAMAGE)) {
				continue;
			}
			list.add(entry);
		}

		ItemAttributeModifiers.Display display = ItemAttributeModifiers.Display.attributeModifiers();
		for (var entry : oldMods.modifiers()) {
			if (entry.attribute().equals(Attributes.ATTACK_DAMAGE)) {
				display = entry.display();
				break;
			}
		}

		list.add(new ItemAttributeModifiers.Entry(
			Attributes.ATTACK_DAMAGE,
			new AttributeModifier(BASE_ATTACK_DAMAGE_ID, dmg, AttributeModifier.Operation.ADD_VALUE),
			EquipmentSlotGroup.MAINHAND,
			display
		));

		kgy$setAttributeModifiers(new ItemAttributeModifiers(list));
	}

	@Info("""
		Overrides the *base* attack speed of this item to be the given value, keeping other modifiers intact.
		Note that players have a default attack speed of 4.0, so this modifier is added on top of that.
		""")
	default void kgy$setBaseAttackSpeed(double speed) {
		kgy$addAttributeModifier(Attributes.ATTACK_SPEED,
			new AttributeModifier(BASE_ATTACK_SPEED_ID, speed, AttributeModifier.Operation.ADD_VALUE),
			EquipmentSlotGroup.MAINHAND);
	}

	@Info("""
		Overrides the *base* attack damage of this item to be the given value, keeping other modifiers intact.
		Note that since players have a default attack damage of 1.0, total damage will be (dmg + 1.0) before other modifiers.
		""")
	default void kgy$setBaseAttackDamage(double dmg) {
		kgy$addAttributeModifier(Attributes.ATTACK_DAMAGE,
			new AttributeModifier(BASE_ATTACK_DAMAGE_ID, dmg, AttributeModifier.Operation.ADD_VALUE),
			EquipmentSlotGroup.MAINHAND);
	}
}
