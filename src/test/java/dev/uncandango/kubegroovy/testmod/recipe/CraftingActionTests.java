package dev.uncandango.kubegroovy.testmod.recipe;

import net.neoforged.testframework.DynamicTest;
import net.neoforged.testframework.annotation.ForEachTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.GameTest;

import static dev.uncandango.kubegroovy.testmod.GameAsserts.assertFired;
import static dev.uncandango.kubegroovy.testmod.GameAsserts.assertVerified;

@ForEachTest(groups = "kubegroovy.recipe.event")
public class CraftingActionTests {
	@GameTest
	@EmptyTemplate
	@TestHolder(value = "crafting_actions", description = "Groovy ingredient actions and result callbacks survive recipe loading for shaped and shapeless crafting")
	static void craftingActions(final DynamicTest test) {
		test.onGameTest(helper -> {
			for (var marker : new String[]{"recipes.actions.registration", "recipes.actions.loaded",
				"recipes.actions.callback", "recipes.result.loaded", "recipes.result.callback"}) {
				assertFired(helper, marker);
				assertVerified(helper, marker);
			}
			helper.succeed();
		});
	}
}
