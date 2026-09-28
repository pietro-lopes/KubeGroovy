package dev.uncandango.kubegroovy.testmod.pack;

import dev.uncandango.kubegroovy.script.data.GeneratedData;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.testframework.DynamicTest;
import net.neoforged.testframework.annotation.ForEachTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.GameTest;

import java.nio.charset.StandardCharsets;

import static dev.uncandango.kubegroovy.testmod.GameAsserts.assertj;
import static org.assertj.core.api.Assertions.assertThat;

/// Java-side checks for the file datapack fixtures (`src/test/kubegroovy/data`,
/// synced into the run's `kubegroovy/` directory by `copyGameTestScripts`).
/// `data/` is synced to both the dedicated and the client game-test runs, so
/// these holders run ungated on both: they read the files back through the
/// sided server resource manager, exercising `KubeFileResourcePack`'s walk,
/// skip rules, lookups, listings, and its `INTERNAL_RELOAD` close/re-walk path.
@ForEachTest(groups = "kubegroovy.pack")
public class FilePackTests {
	private static Identifier id(String id) {
		return Identifier.parse(id);
	}

	private static ResourceManager resources(GameTestHelper helper) {
		return helper.getLevel().getServer().getResourceManager();
	}

	private static String readText(ResourceManager manager, Identifier id) {
		var resource = manager.getResource(id);
		assertThat(resource.isPresent()).as("resource present " + id).isTrue();

		try (var in = resource.get().open()) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (Exception ex) {
			throw new AssertionError("Failed reading " + id + ": " + ex);
		}
	}

	@GameTest
	@EmptyTemplate
	@TestHolder(value = "pack_data_files", description = "File datapack fixtures are readable through the server resource manager")
	static void packDataFiles(final DynamicTest test) {
		test.onGameTest(helper -> helper.startSequence()
			.thenExecute(() -> assertj(helper, () -> {
				var manager = resources(helper);
				assertThat(readText(manager, id("kubegroovy:coverage/data.json"))).as("data json").contains("\"coverage\"");
				assertThat(readText(manager, id("kubegroovy:coverage/note.txt"))).as("note text").contains("file datapack note");
				assertThat(manager.getResource(id("kubegroovy:coverage/missing_xyz.txt")).isPresent())
					.as("missing file absent").isFalse();
				assertThat(manager.listResources("coverage", i -> true).keySet())
					.as("coverage files listed")
					.contains(id("kubegroovy:coverage/data.json"), id("kubegroovy:coverage/note.txt"));
				assertThat(manager.getNamespaces()).as("namespaces").contains("kubegroovy");
			}))
			.thenSucceed());
	}

	@GameTest
	@EmptyTemplate
	@TestHolder(value = "pack_data_reload", description = "Reading the internal-reload marker resets the file pack and files stay readable")
	static void packDataReload(final DynamicTest test) {
		test.onGameTest(helper -> helper.startSequence()
			.thenExecute(() -> assertj(helper, () -> {
				var manager = resources(helper);
				assertThat(manager.getResource(GeneratedData.INTERNAL_RELOAD.id()).isPresent())
					.as("internal reload marker").isTrue();
				assertThat(readText(manager, id("kubegroovy:coverage/note.txt"))).as("note after reset").contains("file datapack note");
			}))
			.thenSucceed());
	}
}
