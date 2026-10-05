package dev.uncandango.kubegroovy.testmod.recipe;

import net.neoforged.testframework.DynamicTest;
import net.neoforged.testframework.annotation.ForEachTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.GameTest;

import static dev.uncandango.kubegroovy.testmod.GameAsserts.assertFired;
import static dev.uncandango.kubegroovy.testmod.GameAsserts.assertVerified;

@ForEachTest(groups = "kubegroovy.recipe.event")
public class FluidStackTemplateTests {
	@GameTest
	@EmptyTemplate(floor = true)
	@TestHolder(value = "fluid_stack_template", description = "Fluid templates parse, round-trip, wrap, and preserve amounts during replacement")
	static void fluidStackTemplate(final DynamicTest test) {
		test.onGameTest(helper -> {
			assertFired(helper, "recipes.fluid_template");
			assertVerified(helper, "recipes.fluid_template");
			helper.succeed();
		});
	}
}
