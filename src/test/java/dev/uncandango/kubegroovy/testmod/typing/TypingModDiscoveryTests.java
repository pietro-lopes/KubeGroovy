package dev.uncandango.kubegroovy.testmod.typing;

import dev.uncandango.kubegroovy.core.AdvancementNodeKGY;
import dev.uncandango.kubegroovy.typing.ModGdslGenerator;
import net.minecraft.advancements.AdvancementNode;
import net.neoforged.testframework.DynamicTest;
import net.neoforged.testframework.annotation.ForEachTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.GameTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ForEachTest(groups = "kubegroovy.typings")
public class TypingModDiscoveryTests {
	@GameTest
	@EmptyTemplate
	@TestHolder(value = "typing_mod_discovery", description = "Loaded mods generate receiver-aware KubeGroovy GDSL")
	static void typingModDiscovery(final DynamicTest test) {
		test.onGameTest(helper -> {
			var mods = ModGdslGenerator.discoverLoadedMods();
			assertThat(mods).extracting(ModGdslGenerator.ModData::modId).contains("kubegroovy", "testmod");

			var kubeGroovy = mods.stream().filter(mod -> mod.modId().equals("kubegroovy")).findFirst().orElseThrow();
			assertThat(kubeGroovy.receiverBridges()).anySatisfy(bridge -> {
				assertThat(bridge.receiverType()).isEqualTo(AdvancementNode.class);
				assertThat(bridge.interfaces()).contains(AdvancementNodeKGY.class);
			});

			var generated = ModGdslGenerator.renderAll(mods, List.of());
			assertThat(generated.keySet()).contains("kubegroovy", "testmod");
			var text = generated.get("kubegroovy");
			assertThat(text).contains("contributor(context(ctype: 'net.minecraft.advancements.AdvancementNode'))");
			assertThat(text).contains("method(name: 'getId', type: 'net.minecraft.resources.Identifier')");
			assertThat(text).doesNotContain("kgy$getId");
			helper.succeed();
		});
	}
}
