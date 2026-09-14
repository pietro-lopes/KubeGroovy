package dev.uncandango.kubegroovy.testmod.wrapper;

import net.neoforged.testframework.DynamicTest;
import net.neoforged.testframework.annotation.ForEachTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.GameTest;

import static dev.uncandango.kubegroovy.testmod.GameAsserts.assertFired;
import static dev.uncandango.kubegroovy.testmod.GameAsserts.assertVerified;

/// Category 2. Verifies explicit Groovy casts in the `wrapper_checks.groovy` fixture.
@ForEachTest(groups = "kubegroovy.wrapper")
public class WrapperTests {
	private static final String[] IDS = {
		"wrapper.vec3",
		"wrapper.blockpos",
		"wrapper.itemstack",
		"wrapper.component",
		"wrapper.nbt",
		"wrapper.color",
		"wrapper.id",
		"wrapper.uuid",
		"wrapper.tristate",
		"wrapper.duration",
		"wrapper.regexp",
		"wrapper.int_provider",
		"wrapper.map_color",
	};

	@GameTest
	@EmptyTemplate
	@TestHolder(value = "wrapper_conversions", description = "Explicit Groovy casts use the registered type wrappers")
	static void wrapperConversions(final DynamicTest test) {
		test.onGameTest(helper -> {
			var sequence = helper.startSequence();

			for (var id : IDS) {
				sequence = sequence
					.thenWaitUntil(() -> assertFired(helper, id))
					.thenExecute(() -> assertVerified(helper, id));
			}

			sequence.thenSucceed();
		});
	}
}
