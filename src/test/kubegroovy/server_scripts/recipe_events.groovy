import net.minecraft.world.item.Items
import net.minecraft.world.item.crafting.Ingredient
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.ItemStackTemplate
import net.minecraft.core.component.DataComponents

def stackOf = { value, count = 1 ->
	def stack = value as ItemStack
	stack.setCount(count)
	stack
}

def registerRecipes = $ServerEvents.recipes
registerRecipes { event ->
	$TestRuntime.check('recipes.wiki') {
		event.shaped(stackOf('minecraft:stone', 3), ['A B', ' C ', 'B A'], [A: 'minecraft:andesite', B: 'minecraft:diorite', C: 'minecraft:granite']).with {
			id('kubegroovy:wiki_shaped')
		}
		event.shapeless(stackOf('minecraft:dandelion', 3), ['minecraft:bone_meal', 'minecraft:yellow_dye', '3x minecraft:ender_pearl'])
		def savedSmelting = event.smelting
		savedSmelting(stackOf('3x minecraft:gravel'), 'minecraft:stone')
		event.blasting(stackOf('10x minecraft:iron_nugget'), 'minecraft:iron_ingot')
		event.smoking(stackOf('minecraft:tinted_glass'), 'minecraft:glass').with {
			xp(0.35)
		}
		event.campfireCooking(stackOf('minecraft:torch'), 'minecraft:stick', 0.35, 600)
		event.stonecutting(stackOf('3x minecraft:stick'), '#minecraft:planks')
		event.smithing(stackOf('minecraft:netherite_ingot'), 'minecraft:netherite_upgrade_smithing_template', 'minecraft:iron_ingot', 'minecraft:black_dye')
		def modifiedTemplate = event.smithing('minecraft:netherite_ingot', 'minecraft:iron_ingot', 'minecraft:black_dye').with {
			template("minecraft:apple")
		}
		$TestRuntime.assertThat(modifiedTemplate.template.test("apple")).isTrue()

		event.replaceInput([input: 'minecraft:stick'], 'minecraft:stick', '#minecraft:saplings' as Ingredient)
		def potting = { output, pottedInput ->
			event.shaped(stackOf(output), ['BIB', ' B '], [B: 'minecraft:brick', I: pottedInput])
		}
		potting('minecraft:blast_furnace', 'minecraft:furnace')
		['oak', 'spruce', 'birch'].each { wood ->
			event.stonecutting(stackOf("4x minecraft:${wood}_button"), "minecraft:${wood}_planks")
		}
		event.remove([output: 'minecraft:stone_pickaxe'])
		event.remove([output: '#minecraft:wool'])
		event.remove([mod: 'farmersdelight'])
		event.remove([id: 'minecraft:glowstone'])
		def testItemStackTemplate = event.custom(
				[
						"type": "minecraft:crafting_shaped",
						"category": "misc",
						"key": [
								"W": "#minecraft:planks",
								"C": "#c:ingots/copper"
						],
						"pattern": [
								"CC",
								"WW",
								"WW"
						],
						"result": "minecraft:apple"
				]
		)
		$TestRuntime.assertThat(ItemStack.isSameItem(((ItemStackTemplate)testItemStackTemplate.get("result")).create(), Items.APPLE.defaultInstance)).isTrue()
		def shapelessTemplate = event.custom([
			type: 'minecraft:crafting_shapeless',
			ingredients: ['minecraft:stone'],
			result: 'minecraft:apple'
		])
		$TestRuntime.assertThat(ItemStack.isSameItem(((ItemStackTemplate)shapelessTemplate.get('result')).create(), Items.APPLE.defaultInstance)).isTrue()
		def componentTemplate = event.custom([
			type: 'minecraft:crafting_shapeless',
			ingredients: ['minecraft:dirt'],
			result: [id: 'minecraft:apple', count: 3, components: ['minecraft:enchantment_glint_override': true]]
		])
		def componentResult = (ItemStackTemplate)componentTemplate.get('result')
		$TestRuntime.assertThat(componentResult.count()).isEqualTo(3)
		$TestRuntime.assertThat(componentResult.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)).isTrue()
		componentTemplate.set('result', componentResult)
		componentTemplate.serialize()
		def roundTripJson = componentTemplate.@json.deepCopy()
		roundTripJson.addProperty('type', 'minecraft:crafting_shapeless')
		def roundTrip = event.custom(roundTripJson)
		$TestRuntime.assertThat(ItemStack.isSameItemSameComponents(((ItemStackTemplate)roundTrip.get('result')).create(), componentResult.create())).isTrue()
		$TestRuntime.assertThat(((ItemStackTemplate)roundTrip.get('result')).count()).isEqualTo(3)
		event.replaceOutput([id: 'minecraft:arrow'], 'minecraft:arrow', 'minecraft:golden_apple')
		def replacedResult = (ItemStackTemplate)event.findRecipes([id: 'minecraft:arrow']).first().get('result')
		$TestRuntime.assertThat(replacedResult.item().value()).isEqualTo(Items.GOLDEN_APPLE)
		$TestRuntime.assertThat(replacedResult.count()).isEqualTo(4)
	}
	$TestRuntime.check('recipes.namespace') {
		def allthemods = event
		allthemods.recipes.kubegroovy.shaped(stackOf('minecraft:stone'), ['A'], [A:'minecraft:dirt'])
			.id('kubegroovy:namespace_shaped').group('namespace_test').noMirror()
		def namespace = event.recipes.kubegroovy
		def shapeless = namespace.shapeless
		shapeless(stackOf('minecraft:gravel'), ['minecraft:dirt']).id('kubegroovy:namespace_saved_shapeless')
		namespace.shapeless.call(stackOf('minecraft:sand'), ['minecraft:dirt']).id('kubegroovy:namespace_explicit_shapeless')
		namespace('shapeless')(stackOf('minecraft:clay'), ['minecraft:dirt']).id('kubegroovy:namespace_callable_shapeless')
		$TestRuntime.assertThat(namespace.mod).isEqualTo('kubegroovy')
		$TestRuntime.assertThat(namespace().contains('shaped')).isTrue()
	}

}
