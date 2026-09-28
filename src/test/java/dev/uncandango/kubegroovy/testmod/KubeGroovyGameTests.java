package dev.uncandango.kubegroovy.testmod;

import dev.uncandango.kubegroovy.KubeGroovy;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.testframework.TestFramework;
import net.neoforged.testframework.conf.FrameworkConfiguration;
import org.jspecify.annotations.Nullable;

/// Entry point for the {@code testmod} game-test mod. Stands up a NeoForge test framework that collects
/// the mod's annotated tests and registers them on the mod bus. Loaded only by the headless
/// {@code runGametest} ({@code gameTestServer}) run.
@Mod("testmod")
public class KubeGroovyGameTests {
	public static final String MOD_ID = "testmod";

	private static volatile @Nullable TestFramework framework;

	/// The live test framework, for the test-control web API (`GET /api/test/results`).
	/// Null until the mod constructs (and on runs without the test source set).
	public static @Nullable TestFramework framework() {
		return framework;
	}

	public KubeGroovyGameTests(IEventBus modBus, ModContainer container) {
		var created = FrameworkConfiguration
			.builder(Identifier.fromNamespaceAndPath(MOD_ID, "tests"))
			.build()
			.create();

		framework = created;
		created.init(modBus, container);
	}
}
