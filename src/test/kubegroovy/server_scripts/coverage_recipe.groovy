import dev.uncandango.kubegroovy.recipe.filter.RecipeFilter
import org.assertj.core.api.Assertions

$ServerEvents.recipes { event ->
	$TestRuntime.check('coverage.recipe.event') {
		// JavaScript object syntax is a valid Groovy closure, but must never become a recipe predicate.
		def recipeCount = event.countRecipes('*')
		Assertions.assertThatThrownBy(() -> event.remove({someGarbage: "moreGarbage"}))
			.hasMessageContaining('Closures are not supported as recipe filters')
			.hasMessageContaining('square brackets')
		def mistakenFilter = { id: 'minecraft:diamond_block' }
		Assertions.assertThatThrownBy(() -> event.remove(mistakenFilter))
			.hasMessageContaining('Closures are not supported as recipe filters')
		Assertions.assertThatThrownBy(() -> mistakenFilter as RecipeFilter)
			.hasMessageContaining('Closures are not supported as recipe filters')
		RecipeFilter convertedFilter = mistakenFilter
		Assertions.assertThatThrownBy(() -> event.remove(convertedFilter))
			.hasMessageContaining('Closures are not supported as recipe filters')
		Assertions.assertThatThrownBy(() -> event.remove([mistakenFilter, [id: 'minecraft:diamond_block']]))
			.hasMessageContaining('Closures are not supported as recipe filters')
		$TestRuntime.assertThat(event.countRecipes('*')).isEqualTo(recipeCount)
		def vanillaDiamondBlock = [id: 'minecraft:diamond_block']
		$TestRuntime.assertThat(event.countRecipes(vanillaDiamondBlock)).isEqualTo(1)
		$TestRuntime.assertThat(event.containsRecipe(vanillaDiamondBlock)).isTrue()
		$TestRuntime.assertThat(event.findRecipes(vanillaDiamondBlock).size()).isEqualTo(1)
		$TestRuntime.assertThat(event.findRecipeIds(vanillaDiamondBlock).any { it.toString() == 'minecraft:diamond_block' }).isTrue()

		def customJson = $JsonUtils.fromString('''{
			"type":"minecraft:crafting_shapeless",
			"ingredients":["minecraft:diamond"],
			"result":{"id":"minecraft:emerald","count":2}
		}''').getAsJsonObject()
		event.custom(customJson).with {
			id('kubegroovy:coverage_custom_json')
		}

		event.shapeless('minecraft:lapis_lazuli', ['minecraft:diamond']).with {
			id('kubegroovy:coverage_after_removed')
		}
	}
}

$ServerEvents.afterRecipes { event ->
	$TestRuntime.check('coverage.recipe.after') {
		def coverageRecipe = [id: 'kubegroovy:coverage_after_removed']
		def recipeCount = event.countRecipes('*')
		Assertions.assertThatThrownBy(() -> event.remove({ id: 'minecraft:diamond_block' }))
			.hasMessageContaining('Closures are not supported as recipe filters')
		$TestRuntime.assertThat(event.countRecipes('*')).isEqualTo(recipeCount)
		$TestRuntime.assertThat(event.countRecipes(coverageRecipe)).isEqualTo(1)
		$TestRuntime.assertThat(event.remove(coverageRecipe)).isEqualTo(1)
	}
}
