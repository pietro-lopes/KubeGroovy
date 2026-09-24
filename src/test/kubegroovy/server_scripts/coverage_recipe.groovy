import dev.uncandango.kubegroovy.recipe.filter.RecipeFilter
import dev.uncandango.kubegroovy.util.KubeIdentifier

ServerEvents.recipes { event ->
	TestRuntime.check('coverage.recipe.event') {
		def vanillaDiamondBlock = [id: 'minecraft:diamond_block'] as RecipeFilter
		TestRuntime.assertThat(event.countRecipes(vanillaDiamondBlock)).isEqualTo(1)
		TestRuntime.assertThat(event.containsRecipe(vanillaDiamondBlock)).isTrue()
		TestRuntime.assertThat(event.findRecipes(vanillaDiamondBlock).size()).isEqualTo(1)
		TestRuntime.assertThat(event.findRecipeIds(vanillaDiamondBlock).any { it.toString() == 'minecraft:diamond_block' }).isTrue()

		def customJson = JsonUtils.fromString('''{
			"type":"minecraft:crafting_shapeless",
			"ingredients":["minecraft:diamond"],
			"result":{"id":"minecraft:emerald","count":2}
		}''').getAsJsonObject()
		event.custom(customJson).id('kubegroovy:coverage_custom_json' as KubeIdentifier)

		event.shapeless('minecraft:lapis_lazuli', ['minecraft:diamond'])
			.id('kubegroovy:coverage_after_removed' as KubeIdentifier)
	}
}

ServerEvents.afterRecipes { event ->
	TestRuntime.check('coverage.recipe.after') {
		def coverageRecipe = [id: 'kubegroovy:coverage_after_removed'] as RecipeFilter
		TestRuntime.assertThat(event.countRecipes(coverageRecipe)).isEqualTo(1)
		TestRuntime.assertThat(event.remove(coverageRecipe)).isEqualTo(1)
	}
}
