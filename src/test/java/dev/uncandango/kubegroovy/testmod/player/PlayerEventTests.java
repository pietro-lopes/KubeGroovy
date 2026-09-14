package dev.uncandango.kubegroovy.testmod.player;

import dev.uncandango.kubegroovy.testmod.TestRuntime;
import net.minecraft.world.level.GameType;
import net.neoforged.testframework.DynamicTest;
import net.neoforged.testframework.annotation.ForEachTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.GameTest;

import static dev.uncandango.kubegroovy.testmod.GameAsserts.assertFired;

@ForEachTest(groups = "kubegroovy.player.event")
public class PlayerEventTests {
	@GameTest
	@EmptyTemplate(floor = true)
	@TestHolder(value = "player_tick", description = "KubeGroovy PlayerEvents.tick fires while a player ticks")
	static void playerTick(final DynamicTest test) {
		test.onGameTest(helper -> helper.startSequence(() -> helper.makeTickingMockServerPlayerInCorner(GameType.SURVIVAL))
			.thenExecute(() -> TestRuntime.clear("player.tick"))
			.thenIdle(2)
			.thenWaitUntil(() -> assertFired(helper, "player.tick"))
			.thenSucceed());
	}
}
