$ServerEvents.recipes { event ->
	$TestRuntime.check('coverage.recipe.event') {
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
		$TestRuntime.assertThat(event.countRecipes(coverageRecipe)).isEqualTo(1)
		$TestRuntime.assertThat(event.remove(coverageRecipe)).isEqualTo(1)
	}
}
