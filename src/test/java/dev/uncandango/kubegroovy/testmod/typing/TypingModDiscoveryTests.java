package dev.uncandango.kubegroovy.testmod.typing;

import dev.uncandango.kubegroovy.core.AdvancementNodeKGY;
import dev.uncandango.kubegroovy.typing.MethodAliasGenerator;
import net.minecraft.advancements.AdvancementNode;
import net.neoforged.testframework.DynamicTest;
import net.neoforged.testframework.annotation.ForEachTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.GameTest;

import static org.assertj.core.api.Assertions.assertThat;

@ForEachTest(groups = "kubegroovy.typings")
public class TypingModDiscoveryTests {
	@GameTest
	@EmptyTemplate
	@TestHolder(value = "typing_mod_discovery", description = "Loaded mods expose receiver-aware KubeGroovy method aliases")
	static void typingModDiscovery(final DynamicTest test) {
		test.onGameTest(helper -> {
			var mods = MethodAliasGenerator.discoverLoadedMods();
			assertThat(mods).extracting(MethodAliasGenerator.ModData::modId).contains("kubegroovy", "testmod");

			var kubeGroovy = mods.stream().filter(mod -> mod.modId().equals("kubegroovy")).findFirst().orElseThrow();
			assertThat(kubeGroovy.receiverBridges()).anySatisfy(bridge -> {
				assertThat(bridge.receiverType()).isEqualTo(AdvancementNode.class);
				assertThat(bridge.interfaces()).contains(AdvancementNodeKGY.class);
			});

			var aliases = MethodAliasGenerator.methodAliases(mods);
			assertThat(aliases).contains(new MethodAliasGenerator.MethodAlias(
				"net.minecraft.world.damagesource.DamageSource", "getDirectEntity", "getImmediate"));
			helper.succeed();
		});
	}
}
