package dev.uncandango.kubegroovy.testmod.syntax;

import net.neoforged.testframework.DynamicTest;
import net.neoforged.testframework.annotation.ForEachTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.GameTest;

import static dev.uncandango.kubegroovy.testmod.GameAsserts.assertFired;
import static dev.uncandango.kubegroovy.testmod.GameAsserts.assertVerified;

/// Category 4. Verifies the Groovy syntax exercised by `syntax_checks.groovy`.
@ForEachTest(groups = "kubegroovy.syntax")
public class SyntaxTests {
	private static final String[] IDS = {
		"syntax.closures",
		"syntax.multiple_assignment",
		"syntax.gstrings",
		"syntax.for_in",
		"syntax.collection_pipelines",
		"syntax.map_and_set",
		"syntax.default_parameters",
		"syntax.spread_arguments",
		"syntax.normal_cast",
	};

	@GameTest
	@EmptyTemplate
	@TestHolder(value = "syntax_features", description = "Core Groovy language features evaluate in scripts")
	static void syntaxFeatures(final DynamicTest test) {
		test.onGameTest(helper -> {
			for (var id : IDS) {
				assertFired(helper, id);
				assertVerified(helper, id);
			}

			helper.succeed();
		});
	}
}
