package dev.uncandango.kubegroovy.testmod.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;
import net.neoforged.testframework.DynamicTest;
import net.neoforged.testframework.annotation.ForEachTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.GameTest;

import static dev.uncandango.kubegroovy.testmod.GameAsserts.assertFired;
import static dev.uncandango.kubegroovy.testmod.GameAsserts.assertj;
import static org.assertj.core.api.Assertions.assertThat;

/// Category 3. A KubeGroovy script reacts to spawned entities and manipulates them through the injected
/// *KGY bindings; this verifies the effects Java-side, proving those bindings work end to end.
@ForEachTest(groups = "kubegroovy.entity.behavior")
public class EntityBehaviorTests {
	private static final BlockPos POS = new BlockPos(1, 2, 1);

	@GameTest
	@EmptyTemplate(floor = true)
	@TestHolder(value = "entity_behavior_zombie_held_item", description = "A script gives a spawned zombie a held item via LivingEntityKGY")
	static void zombieHeldItem(final DynamicTest test) {
		test.onGameTest(helper -> helper.startSequence(() -> helper.spawnWithNoFreeWill(EntityType.ZOMBIE, POS))
			.thenWaitUntil(() -> assertFired(helper, "entity.behavior.zombie"))
			.thenExecute(zombie -> assertj(helper, () -> assertThat(zombie.getItemInHand(InteractionHand.MAIN_HAND).getItem())
				.as("zombie main-hand item")
				.isEqualTo(Items.IRON_SWORD)))
			.thenSucceed());
	}

	@GameTest
	@EmptyTemplate(floor = true)
	@TestHolder(value = "entity_behavior_cow_moved", description = "A script moves a spawned cow 5 blocks south via EntityKGY")
	static void cowMovedSouth(final DynamicTest test) {
		test.onGameTest(helper -> helper.startSequence(() -> helper.spawnWithNoFreeWill(EntityType.COW, POS))
			.thenWaitUntil(() -> assertFired(helper, "entity.behavior.cow"))
			.thenExecute(cow -> {
				double blockZ = helper.absolutePos(POS).getZ();
				assertj(helper, () -> assertThat(cow.getZ())
					.as("cow z after being moved south")
					.isBetween(blockZ + 5.0, blockZ + 6.0));
			})
			.thenSucceed());
	}
}
