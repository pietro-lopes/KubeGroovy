import net.minecraft.world.item.crafting.Ingredient
import net.minecraft.world.item.ItemStack

def stackOf = { value, count = 1 ->
	def stack = value as ItemStack
	stack.setCount(count)
	stack
}

$ServerEvents.recipes { event ->
	$TestRuntime.check('recipes.wiki') {
		event.shaped(stackOf('minecraft:stone', 3), ['A B', ' C ', 'B A'], [A: 'minecraft:andesite', B: 'minecraft:diorite', C: 'minecraft:granite']).with {
			id('kubegroovy:wiki_shaped')
		}
		event.shapeless(stackOf('minecraft:dandelion', 3), ['minecraft:bone_meal', 'minecraft:yellow_dye', '3x minecraft:ender_pearl'])
		event.smelting(stackOf('3x minecraft:gravel'), 'minecraft:stone')
		event.blasting(stackOf('10x minecraft:iron_nugget'), 'minecraft:iron_ingot')
		event.smoking(stackOf('minecraft:tinted_glass'), 'minecraft:glass').with {
			xp(0.35)
		}
		event.campfireCooking(stackOf('minecraft:torch'), 'minecraft:stick', 0.35, 600)
		event.stonecutting(stackOf('3x minecraft:stick'), '#minecraft:planks')
		event.smithing(stackOf('minecraft:netherite_ingot'), 'minecraft:netherite_upgrade_smithing_template', 'minecraft:iron_ingot', 'minecraft:black_dye')
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
	}
}
