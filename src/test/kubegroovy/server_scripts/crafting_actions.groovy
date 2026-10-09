import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.crafting.CraftingInput

def kinds = ['shaped', 'shapeless']
def prefix = 'kubegroovy:crafting_actions_'

$ServerEvents.modifyRecipeIngredient('testmod:crafting_replace') { event ->
	$TestRuntime.check('recipes.actions.callback') {
		$TestRuntime.assertThat(event.index).isEqualTo(4)
		$TestRuntime.assertThat(event.width).isEqualTo(3)
		$TestRuntime.assertThat(event.height).isEqualTo(3)
		$TestRuntime.assertThat(event.item.item).isEqualTo(Items.DIRT)
		$TestRuntime.assertThat(event.grid.getItem(4).item).isEqualTo(Items.DIRT)
	}
	event.exit('2x minecraft:gravel')
}

$ServerEvents.modifyRecipeResult('testmod:crafting_mutate') { event ->
	$TestRuntime.check('recipes.result.callback') {
		$TestRuntime.assertThat(event.index).isEqualTo(0)
		$TestRuntime.assertThat(event.width).isEqualTo(1)
		$TestRuntime.assertThat(event.height).isEqualTo(1)
		$TestRuntime.assertThat(event.grid.getItem(0).item).isEqualTo(Items.DIRT)
		$TestRuntime.assertThat(event.item.item).isEqualTo(Items.APPLE)
		$TestRuntime.assertThat(event.item.count).isEqualTo(1)
	}
	event.item.setCount(3)
	event.item.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
	event.exit()
}
$ServerEvents.modifyRecipeResult('testmod:crafting_exit') { event ->
	event.exit('2x minecraft:golden_apple')
}
$ServerEvents.modifyRecipeResult('testmod:crafting_empty') { event ->
	event.exit(ItemStack.EMPTY)
}
$ServerEvents.modifyRecipeResult('testmod:crafting_unchanged') { event ->
	event.exit()
}

$ServerEvents.recipes { event ->
	$TestRuntime.check('recipes.actions.registration') {
		kinds.each { kind ->
			def recipe = kind == 'shaped'
				? event.shaped('minecraft:apple', ['ABC', 'DEF', 'G H'], [
					A: 'minecraft:stick', B: 'minecraft:iron_pickaxe', C: 'minecraft:water_bucket',
					D: 'minecraft:milk_bucket', E: 'minecraft:dirt', F: 'minecraft:milk_bucket',
					G: 'minecraft:coal', H: 'minecraft:diamond'])
				: event.shapeless('minecraft:apple', ['minecraft:stick', 'minecraft:iron_pickaxe',
					'minecraft:water_bucket', 'minecraft:milk_bucket', 'minecraft:dirt',
					'minecraft:milk_bucket', 'minecraft:coal', 'minecraft:diamond'])
			recipe.id(prefix + kind)
				.keepIngredient('minecraft:stick')
				.damageIngredient(1, 2)
				.replaceIngredient([item: 'minecraft:water_bucket', index: 2], '2x minecraft:glass_bottle')
				.consumeIngredient(3)
				.customIngredientAction([item: 'minecraft:dirt', index: 4], 'testmod:crafting_replace')
				// The first matching action wins; this must not replace the kept stick.
				.replaceIngredient(0, 'minecraft:diamond')
				// Neither half of a combined filter can match on its own.
				.consumeIngredient([item: 'minecraft:milk_bucket', index: 6])
				.keepIngredient([:])

			def single = { suffix, ingredient ->
				def result = kind == 'shaped'
					? event.shaped('minecraft:apple', ['A'], [A: ingredient])
					: event.shapeless('minecraft:apple', [ingredient])
				result.id(prefix + kind + '_' + suffix)
			}
			single('default_damage', 'minecraft:iron_pickaxe').damageIngredient(0)
			// No catch-all action: vanilla remainders and ordinary consumption must survive.
			single('fallback', 'minecraft:milk_bucket').consumeIngredient('minecraft:dirt')
			single('plain', 'minecraft:dirt')
			['mutate', 'exit', 'empty', 'unchanged'].each { mode ->
				single(mode, 'minecraft:dirt').modifyResult('testmod:crafting_' + mode)
			}
		}
	}
}

$ServerEvents.afterRecipes { event ->
	def loaded = { id ->
		def recipe = null
		event.forEachRecipe([id: id]) { holder -> recipe = holder.value() }
		$TestRuntime.assertThat(recipe).isNotNull()
		recipe
	}
	def stack = { item, count = 1 ->
		def result = item as ItemStack
		result.setCount(count)
		result
	}
	def assertStack = { actual, item, count ->
		$TestRuntime.assertThat(actual.item).isEqualTo(item)
		$TestRuntime.assertThat(actual.count).isEqualTo(count)
	}
	$TestRuntime.check('recipes.actions.loaded') {
		kinds.each { kind ->
			def recipe = loaded(prefix + kind)
			def tool = stack('minecraft:iron_pickaxe')
			tool.setDamageValue(7)
			def input = CraftingInput.of(3, 3, [stack('minecraft:stick', 3), tool,
				stack('minecraft:water_bucket'), stack('minecraft:milk_bucket'), stack('minecraft:dirt'),
				stack('minecraft:milk_bucket'), stack('minecraft:coal'), ItemStack.EMPTY, stack('minecraft:diamond')])
			def remaining = recipe.getRemainingItems(input)
			$TestRuntime.assertThat(remaining.size()).isEqualTo(9)
			assertStack(remaining[0], Items.STICK, 1)
			assertStack(remaining[1], Items.IRON_PICKAXE, 1)
			$TestRuntime.assertThat(remaining[1].damageValue).isEqualTo(9)
			assertStack(remaining[2], Items.GLASS_BOTTLE, 2)
			$TestRuntime.assertThat(remaining[3].isEmpty()).isTrue()
			assertStack(remaining[4], Items.GRAVEL, 2)
			assertStack(remaining[5], Items.MILK_BUCKET, 1)
			assertStack(remaining[6], Items.COAL, 1)
			$TestRuntime.assertThat(remaining[7].isEmpty()).isTrue()
			assertStack(remaining[8], Items.DIAMOND, 1)
			// Actions work on copies, and replacements are fresh for each craft.
			$TestRuntime.assertThat(tool.damageValue).isEqualTo(7)
			$TestRuntime.assertThat(input.getItem(0).count).isEqualTo(3)
			remaining[2].setCount(1)
			assertStack(recipe.getRemainingItems(input)[2], Items.GLASS_BOTTLE, 2)
			tool.setDamageValue(tool.maxDamage - 1)
			$TestRuntime.assertThat(recipe.getRemainingItems(input)[1].isEmpty()).isTrue()
			$TestRuntime.assertThat(tool.damageValue).isEqualTo(tool.maxDamage - 1)

			def defaultDamage = loaded(prefix + kind + '_default_damage')
			def freshTool = stack('minecraft:iron_pickaxe')
			def damaged = defaultDamage.getRemainingItems(CraftingInput.of(1, 1, [freshTool]))[0]
			$TestRuntime.assertThat(damaged.damageValue).isEqualTo(1)
			$TestRuntime.assertThat(freshTool.damageValue).isEqualTo(0)
			def fallback = loaded(prefix + kind + '_fallback')
			assertStack(fallback.getRemainingItems(CraftingInput.of(1, 1, [stack('minecraft:milk_bucket')]))[0], Items.BUCKET, 1)
			def plain = loaded(prefix + kind + '_plain')
			$TestRuntime.assertThat(plain.getRemainingItems(CraftingInput.of(1, 1, [stack('minecraft:dirt')]))[0].isEmpty()).isTrue()
		}
	}
	$TestRuntime.check('recipes.result.loaded') {
		kinds.each { kind ->
			def input = CraftingInput.of(1, 1, [stack('minecraft:dirt')])
			def mutated = loaded(prefix + kind + '_mutate')
			def first = mutated.assemble(input)
			assertStack(first, Items.APPLE, 3)
			$TestRuntime.assertThat(first.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)).isTrue()
			first.setCount(1)
			assertStack(mutated.assemble(input), Items.APPLE, 3)
			assertStack(loaded(prefix + kind + '_exit').assemble(input), Items.GOLDEN_APPLE, 2)
			$TestRuntime.assertThat(loaded(prefix + kind + '_empty').assemble(input).isEmpty()).isTrue()
			assertStack(loaded(prefix + kind + '_unchanged').assemble(input), Items.APPLE, 1)
			def plain = loaded(prefix + kind + '_plain')
			def original = plain.assemble(input)
			assertStack(original, Items.APPLE, 1)
			$TestRuntime.assertThat(original.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)).isNull()
			original.setCount(5)
			assertStack(plain.assemble(input), Items.APPLE, 1)
			assertStack(input.getItem(0), Items.DIRT, 1)
		}
	}
}
