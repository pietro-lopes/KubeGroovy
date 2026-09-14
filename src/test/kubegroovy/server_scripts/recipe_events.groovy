import dev.uncandango.kubegroovy.recipe.filter.RecipeFilter
import dev.uncandango.kubegroovy.recipe.match.ReplacementMatchInfo
import dev.uncandango.kubegroovy.util.KubeIdentifier
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.common.crafting.SizedIngredient

def stackOf = { value, count = 1 ->
	def stack = value as ItemStack
	stack.setCount(count)
	stack
}
def ingredientOf = { value -> value as net.minecraft.world.item.crafting.Ingredient }
def sizedIngredientOf = { value -> value as SizedIngredient }

ServerEvents.recipes { event ->
	TestRuntime.check('recipes.wiki') {
		event.shaped(stackOf('minecraft:stone', 3), ['A B', ' C ', 'B A'], [A: ingredientOf('minecraft:andesite'), B: ingredientOf('minecraft:diorite'), C: ingredientOf('minecraft:granite')])
			.id('kubegroovy:wiki_shaped' as KubeIdentifier)
		event.shapeless(stackOf('minecraft:dandelion', 3), [sizedIngredientOf('minecraft:bone_meal'), sizedIngredientOf('minecraft:yellow_dye'), sizedIngredientOf('3x minecraft:ender_pearl')])
		event.smelting(stackOf('3x minecraft:gravel'), ingredientOf('minecraft:stone'))
		event.blasting(stackOf('10x minecraft:iron_nugget'), ingredientOf('minecraft:iron_ingot'))
		event.smoking(stackOf('minecraft:tinted_glass'), ingredientOf('minecraft:glass')).xp(0.35)
		event.campfireCooking(stackOf('minecraft:torch'), ingredientOf('minecraft:stick'), 0.35, 600)
		event.stonecutting(stackOf('3x minecraft:stick'), ingredientOf('#minecraft:planks'))
		event.smithing(stackOf('minecraft:netherite_ingot'), ingredientOf('minecraft:netherite_upgrade_smithing_template'), ingredientOf('minecraft:iron_ingot'), ingredientOf('minecraft:black_dye'))
		event.replaceInput([input: 'minecraft:stick'] as RecipeFilter, 'minecraft:stick' as ReplacementMatchInfo, '#minecraft:saplings' as net.minecraft.world.item.crafting.Ingredient)
		def potting = { output, pottedInput ->
			event.shaped(stackOf(output), ['BIB', ' B '], [B: ingredientOf('minecraft:brick'), I: ingredientOf(pottedInput)])
		}
		potting('minecraft:blast_furnace', 'minecraft:furnace')
		['oak', 'spruce', 'birch'].each { wood ->
			event.stonecutting(stackOf("4x minecraft:${wood}_button"), ingredientOf("minecraft:${wood}_planks"))
		}
		event.remove([output: 'minecraft:stone_pickaxe'] as RecipeFilter)
		event.remove([output: '#minecraft:wool'] as RecipeFilter)
		event.remove([mod: 'farmersdelight'] as RecipeFilter)
		event.remove([id: 'minecraft:glowstone'] as RecipeFilter)
	}
}
