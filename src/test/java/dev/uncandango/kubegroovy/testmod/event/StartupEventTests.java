package dev.uncandango.kubegroovy.testmod.event;

import dev.uncandango.kubegroovy.testmod.TestRuntime;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.testframework.DynamicTest;
import net.neoforged.testframework.annotation.ForEachTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.GameTest;

import static dev.uncandango.kubegroovy.testmod.GameAsserts.assertj;
import static org.assertj.core.api.Assertions.assertThat;

@ForEachTest(groups = "kubegroovy.startup.event")
public class StartupEventTests {

	private static final String[] STARTUP_IDS = {
		"startup.postInit"
	};

	@GameTest
	@EmptyTemplate
	@TestHolder(value = "startup_events", description = "Startup events fixtures fired")
	static void startupEvents(final DynamicTest test) {
		test.onGameTest(helper -> {
			var sequence = helper.startSequence();

			for (var id : STARTUP_IDS) {
				final var marker = id;
				sequence = sequence.thenExecute(() -> assertStartupFired(helper, marker));
			}

			sequence.thenSucceed();
		});
	}

	private static void assertStartupFired(final GameTestHelper helper, final String id) {
		assertj(helper, () -> assertThat(TestRuntime.passedStartup(id)).as("%s should have fired", id).isTrue());
	}
}
