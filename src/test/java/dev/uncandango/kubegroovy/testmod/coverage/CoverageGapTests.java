package dev.uncandango.kubegroovy.testmod.coverage;

import dev.uncandango.kubegroovy.core.WithPersistentData;
import dev.uncandango.kubegroovy.testmod.TestRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.testframework.DynamicTest;
import net.neoforged.testframework.annotation.ForEachTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.GameTest;

import java.util.UUID;
import java.util.stream.Collectors;

import static dev.uncandango.kubegroovy.testmod.GameAsserts.assertFired;
import static dev.uncandango.kubegroovy.testmod.GameAsserts.assertVerified;
import static dev.uncandango.kubegroovy.testmod.GameAsserts.assertj;
import static org.assertj.core.api.Assertions.assertThat;

@ForEachTest(groups = "kubegroovy.coverage")
public class CoverageGapTests {
	private static final BlockPos ENTITY_POS = new BlockPos(1, 2, 1);

	@GameTest
	@EmptyTemplate(floor = true)
	@TestHolder(value = "coverage_core_entity", description = "A Groovy handler reads and moves an entity through EntityKGY")
	static void coreEntity(final DynamicTest test) {
		test.onGameTest(helper -> helper.startSequence(() -> {
			TestRuntime.clear("coverage.core.entity");
			return helper.spawnWithNoFreeWill(EntityType.SHEEP, ENTITY_POS);
		})
			.thenWaitUntil(() -> assertFired(helper, "coverage.core.entity"))
			.thenExecute(() -> assertVerified(helper, "coverage.core.entity"))
			.thenExecute(sheep -> {
				double originalX = helper.absolutePos(ENTITY_POS).getX() + 0.5d;
				assertj(helper, () -> assertThat(sheep.getX())
					.as("sheep moved four blocks through the script API")
					.isBetween(originalX + 3.9d, originalX + 4.1d));
			})
			.thenSucceed());
	}

	@GameTest
	@EmptyTemplate(floor = true)
	@TestHolder(value = "coverage_core_inventory", description = "Groovy inventory helpers simulate insertion, insert, count, find, and extract")
	static void coreInventory(final DynamicTest test) {
		test.onGameTest(helper -> {
			var player = helper.makeTickingMockServerPlayerInCorner(GameType.SURVIVAL);
			player.getInventory().clearContent();
			helper.startSequence()
			.thenExecute(() -> {
				TestRuntime.clear("coverage.core.inventory", "coverage.core.inventory.started");
				helper.getLevel().getServer().getCommands()
					.performPrefixedCommand(player.createCommandSourceStack(), "coverage_inventory");
			})
			.thenWaitUntil(() -> assertFired(helper, "coverage.core.inventory"))
			.thenExecute(() -> assertVerified(helper, "coverage.core.inventory"))
			.thenExecute(() -> assertj(helper, () -> {
				assertThat(TestRuntime.passed("coverage.core.inventory.started"))
					.as("the script reached the inventory event player")
					.isTrue();
				var commandPlayer = helper.getLevel().getServer().getPlayerList().getPlayers().stream()
					.filter(candidate -> candidate.entityTags().contains("kubegroovy_coverage_inventory_target"))
					.findFirst()
					.orElse(null);
				assertThat(commandPlayer).as("the Java holder locates the player mutated by the script").isNotNull();
				if (commandPlayer != null) {
					assertThat(commandPlayer.getUUID())
						.as("the command uses the mock player held by this GameTest")
						.isEqualTo(player.getUUID());
					assertThat(commandPlayer.getInventory().getItem(0).getItem())
						.as("the script's real insertion and extraction are visible in the mock player's inventory")
						.isEqualTo(Items.DIAMOND);
					assertThat(commandPlayer.getInventory().getItem(0).getCount())
						.as("two diamonds remain after extraction")
						.isEqualTo(2);
				}
			}))
			.thenSucceed();
		});
	}

	@GameTest
	@EmptyTemplate(floor = true)
	@TestHolder(value = "coverage_recipes", description = "Groovy recipe queries, JSON creation, and after-load removal affect the live manager")
	static void recipes(final DynamicTest test) {
		test.onGameTest(helper -> helper.startSequence()
			.thenWaitUntil(() -> assertFired(helper, "coverage.recipe.event"))
			.thenExecute(() -> assertVerified(helper, "coverage.recipe.event"))
			.thenExecute(() -> assertFired(helper, "coverage.recipe.after"))
			.thenExecute(() -> assertVerified(helper, "coverage.recipe.after"))
			.thenExecute(() -> {
				var ids = helper.getLevel().getServer().getRecipeManager().getRecipes().stream()
					.map(holder -> holder.id().identifier().toString())
					.collect(Collectors.toSet());
				assertj(helper, () -> {
					assertThat(ids).contains("kubegroovy:coverage_custom_json");
					assertThat(ids).doesNotContain("kubegroovy:coverage_after_removed");
				});
			})
			.thenSucceed());
	}

	@GameTest
	@EmptyTemplate(floor = true)
	@TestHolder(value = "coverage_wrappers", description = "Groovy text, item, and NBT wrapper helpers preserve their values")
	static void wrappers(final DynamicTest test) {
		test.onGameTest(helper -> helper.startSequence()
			.thenWaitUntil(() -> assertFired(helper, "coverage.wrappers"))
			.thenExecute(() -> assertVerified(helper, "coverage.wrappers"))
			.thenSucceed());
	}

	@GameTest
	@EmptyTemplate(floor = true)
	@TestHolder(value = "coverage_commands", description = "A public Groovy command trims input and round-trips server persistent data")
	static void commands(final DynamicTest test) {
		test.onGameTest(helper -> {
			var player = helper.makeTickingMockServerPlayerInCorner(GameType.SURVIVAL);
			helper.startSequence()
			.thenExecute(() -> {
				TestRuntime.clear("coverage.command.public");
				player.clearOutboundPackets();
				helper.getLevel().getServer().getCommands()
					.performPrefixedCommand(player.createCommandSourceStack(), "coverage_public_echo    trimmed input   ");
			})
			.thenWaitUntil(() -> assertFired(helper, "coverage.command.public"))
			.thenExecute(() -> assertVerified(helper, "coverage.command.public"))
			.thenExecute(() -> assertj(helper, () -> assertThat(player.getOutboundPackets(ClientboundSystemChatPacket.class)
				.map(packet -> packet.content().getString())
				.toList()).contains("echo:trimmed input")))
			.thenExecute(() -> assertj(helper, () -> assertPersistentDataCommand(helper.getLevel().getServer())))
			.thenSucceed();
		});
	}

	private static void assertPersistentDataCommand(MinecraftServer server) {
		var data = ((WithPersistentData) server).kgy$getPersistentData();
		String key = "kubegroovy_coverage_probe_" + UUID.randomUUID().toString().replace("-", "");
		var source = server.createCommandSourceStack().withSuppressedOutput();

		try {
			server.getCommands().performPrefixedCommand(source, "kgy persistent-data server merge {" + key + ":7}");
			assertThat(data.getIntOr(key, 0)).isEqualTo(7);
		} finally {
			server.getCommands().performPrefixedCommand(source, "kgy persistent-data server remove " + key);
		}

		assertThat(data.contains(key)).isFalse();
	}
}
