import net.minecraft.core.Holder
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.ToolMaterial
import dev.uncandango.kubegroovy.item.MutableToolTier
import dev.uncandango.kubegroovy.item.custom.ShearsItemBuilder

// Every public item/food/behavior method has a direct DSL call in this fixture.
$StartupEvents.registry('item') { event ->
	def item = event.create('coverage_item')
	item.translationKey('item.kubegroovy.coverage_item')
		.displayName('Coverage Item').formattedDisplayName('Coverage Formatted').formattedDisplayName()
		.texture('kubegroovy:item/coverage_item')
		.texture(['layer0'], 'kubegroovy:item/coverage_item_layer')
		.textures([layer0: 'kubegroovy:item/coverage_item', layer1: 'kubegroovy:item/coverage_item_overlay'])
		.parentModel('minecraft:item/generated')
		.modelGenerator { model -> model.parent('minecraft:item/generated') }
		.component("custom_name" as DataComponentType<?>, Component.literal('Coverage'))
		.maxStackSize(32).unstackable().maxDamage(42).burnTime(40)
		.containerItem('minecraft:bucket')
		.subtypes { stack -> [stack] }
		.rarity('rare').group(null)
		.color(0) { stack -> 0xFFFFFFFF }
		.color { stack -> 0xFF00FFFF }
		.food(3, 0.6f)
		.food { food ->
			food.nutrition(4).saturation(0.8f)
				.alwaysEdible(false).alwaysEdible()
				.eatSeconds(1.2f).fastToEat()
				.usingConvertsTo(ItemStack.EMPTY)
				.effect("luck" as Holder<MobEffect>, 20, 0, 1.0f)
				.removeEffect("luck" as Holder<MobEffect>)
				.eaten { eaten -> }
			food.getUsingConvertsTo()
			food.getEatSeconds()
			food.getEffects()
		}
		.fireResistant(false).fireResistant()
		.jukeboxPlayable('minecraft:13').disableRepair()
		.glow(true)
		.tooltip('Coverage tooltip')
		.barColor { stack -> 0xFFAA5500 }
		.barWidth { stack -> 8 }
		.name { stack -> Component.literal('Dynamic coverage') }
		.useAnimation('eat')
		.useDuration { stack, entity -> 20 }
		.use { level, player, hand -> true }
		.finishUsing { stack, level, entity -> stack }
		.releaseUsing { stack, level, entity, ticks -> }
		.hurtEnemy { context -> true }
		.isPiglinCurrency(true)
		.isPiglinCurrency { stack -> true }
		.protectFromGaze(true)
		.protectFromGaze { stack, player, entity -> true }
		.makesPiglinsNeutral(true)
		.makesPiglinsNeutral { stack, entity -> true }
		.craftingRemainingItem { stack -> stack }
		.getEntityLifespan(600)
		.getEntityLifespan { stack, level -> 600 }
		.canWalkOnPowderedSnow(true)
		.canWalkOnPowderedSnow { stack, entity -> true }
		.canPerformAction { stack, action -> true }
		.canBeHurtBy { stack, source -> true }
		.onlyHurtBy([])
		.immuneTo([])
		.applyEnchantments { stack, enchantments -> stack }

	event.create('coverage_sword', 'sword')
		.tier(new MutableToolTier(ToolMaterial.DIAMOND)).attackDamageBaseline(5).disableBlockingForSeconds(2).speedBaseline(-2.0)
		.modifyTier { tier ->
			tier.setUses(1000)
			tier.setSpeed(9)
			tier.setAttackDamageBonus(4)
			tier.setEnchantmentValue(15)
		}
		.attackDamageBonus(4).speed(1.5)
	event.create('coverage_pickaxe', 'pickaxe').attackDamageBaseline(2).speedBaseline(-2.5)
	event.create('coverage_axe', 'axe').attackDamageBaseline(7).speedBaseline(-3.0)
	event.create('coverage_shovel', 'shovel').attackDamageBaseline(2).speedBaseline(-2.8)
	event.create('coverage_hoe', 'hoe').attackDamageBaseline(1).speedBaseline(-2.2)
	event.create('coverage_shears', 'shears').speedBaseline(7)
	ShearsItemBuilder.isCustomShears(ItemStack.EMPTY)
	event.create('coverage_helmet', 'helmet').material('iron')
	event.create('coverage_chestplate', 'chestplate').material('gold')
	event.create('coverage_leggings', 'leggings').material('diamond')
	event.create('coverage_boots', 'boots').material('netherite')
	event.create('coverage_animal_armor', 'animal_armor').kind('wolf').material('iron')

	def template = event.create('coverage_smithing', 'smithing_template')
	template.appliesTo('Custom Armor').ingredients('Custom Ingots')
		.appliesToSlotDescription('Custom base').ingredientsSlotDescription('Custom ingredient')
		.addAppliesToSlotIcon('minecraft:item/empty_armor_slot_helmet')
		.addIngredientsSlotIcon('minecraft:item/empty_slot_ingot')
		.armorIcons().equipmentIcons().toolIcons().ingotAndCrystalIcons().crystalIcons()
		.ingotIcon().dustIcon().shardIcon().diamondIcon().emeraldIcon().quartzIcon().lapisIcon()
		.swordIcon().shovelIcon().axeIcon().pickaxeIcon().hoeIcon()
		.helmetIcon().chestplateIcon().leggingsIcon().bootsIcon()
		.displayName('Coverage Smithing')
}
