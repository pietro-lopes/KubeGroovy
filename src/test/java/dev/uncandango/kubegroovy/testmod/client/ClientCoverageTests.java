package dev.uncandango.kubegroovy.testmod.client;

import dev.uncandango.kubegroovy.testmod.TestRuntime;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.testframework.DynamicTest;
import net.neoforged.testframework.annotation.ForEachTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.GameTest;

import static dev.uncandango.kubegroovy.testmod.GameAsserts.assertCount;
import static dev.uncandango.kubegroovy.testmod.GameAsserts.assertFired;
import static dev.uncandango.kubegroovy.testmod.GameAsserts.assertVerified;
import static dev.uncandango.kubegroovy.testmod.GameAsserts.assertj;
import static org.assertj.core.api.Assertions.assertThat;

/// Java-side checks for the client coverage fixtures
/// (`startup_scripts/client_coverage_startup.groovy` and
/// `client_scripts/client_coverage_{events,assets,wrappers}.groovy`), mirroring
/// how [dev.uncandango.kubegroovy.testmod.wrapper.WrapperTests] covers
/// `wrapper_checks.groovy`.
///
/// Client scripts only load when `Dist` is CLIENT, so these holders are
/// registered for the client dist only: the dedicated `runGametest` server
/// never sees them, while the `runGametestClient` run (client + integrated
/// server in one JVM) executes the real asserts once `/test run testmod:*`
/// runs.
///
/// Deliberately NOT asserted (fire only with manual input or by design never):
/// - `client.key.pressed|released|tick`, `client.debug.left|right`: need a
///   physical keypress / F3.
/// - `client.atlas.*`: `ATLAS_SPRITE_REGISTRY` has no posting site, handlers
///   never dispatch (covered directly via `AtlasSpriteRegistryKubeEvent`).
/// - `client.timeout`, `client.interval`: cleared before they can fire.
/// - `client.particle.ticked|customizer`: the engine never invoked the
///   registered provider for the spawned particle.
/// - `client.lang.invalid`: invalid targets are rejected with an error log,
///   never dispatched.
/// - `client.assets.texture.empty`, `client.wrapper.loadedTexture.emptyOnly`:
///   else-branches for a missing texture that exists in the test pack.
/// - `client.registry.menuScreen.resolved`: the menu lookup is null at
///   `RegisterMenuScreens` time (only `.called` fires).
@ForEachTest(groups = "kubegroovy.client.coverage", side = Dist.CLIENT)
public class ClientCoverageTests {

	private static final String[] STARTUP_IDS = {
		"client.keybind.registry",
		"client.registry.blockEntityRenderer",
		"client.registry.blockEntityRenderer.resolved",
		"client.registry.entityRenderer",
		"client.registry.entityRenderer.resolved",
		"client.registry.menuScreen",
		"client.registry.menuScreen.called",
		"client.modelProperties",
	};

	private static final String[] LIFECYCLE_IDS = {
		"client.loggedIn",
		"client.loggedIn.fields",
		"client.loggedOut",
		"client.loggedOut.fields",
		"client.tick",
		"client.tick.fields",
		"client.bindings",
		"client.events.direct",
		"client.formatNumber",
		"client.timeout.fired",
		"client.interval.fired",
		"client.particle.spawned",
		"client.toast",
		"client.toast.shown",
		"client.toast.item.shown",
	};

	private static final String[] LANG_IDS = {
		"client.lang",
		"client.lang.guards",
		"client.lang.rename.guards",
		"client.lang.rename.deferred",
		"client.lang.es",
	};

	private static final String[] ASSET_IDS = {
		"client.assets.fired",
		"client.assets.base",
		"client.assets.generated",
		"client.assets.tints",
		"client.assets.blockstates",
		"client.assets.models",
		"client.assets.particles",
		"client.assets.textures",
		"client.assets.before",
		"client.assets.last",
		"client.assets.mask",
		"client.assets.logo",
	};

	private static final String[] TOOLTIP_IDS = {
		"client.tooltip.modify",
		"client.tooltip.dynamic",
		"client.tooltip.dynamic.fields",
	};

	private static final String[] PARTICLE_IDS = {
		"client.particle.registry",
	};

	private static final String[] WRAPPER_IDS = {
		"client.wrapper.loadedTexture",
		"client.wrapper.loadedTexture.real",
		"client.wrapper.model",
		"client.wrapper.blockstate",
		"client.wrapper.particleSounds",
		"client.wrapper.atlasSprite",
		"client.wrapper.soundDefaults",
		"client.wrapper.tintFunctions.block",
		"client.wrapper.tintFunctions.fluid.bucket",
		"client.wrapper.tintFunctions.fluid.fixed",
		"client.wrapper.tintFunctions.fluid.fn",
		"client.wrapper.tintFunctions.fluid.color",
		"client.wrapper.tintFunctions.fluid.world",
		"client.wrapper.tintFunctions.fluid.stack",
		"client.wrapper.tintFunctions.item",
		"client.wrapper.tintFunctions.itemStack",
		"client.wrapper.tintFunctions.wrap",
		"client.wrapper.icons",
		"client.wrapper.icons.item",
		"client.wrapper.modelRegistry",
		"client.wrapper.clientProps",
		"client.wrapper.keybinds",
	};

	/// Markers that must stay silent: diagnostic checks and guarded fallbacks.
	/// Any fire here means a client fixture hit its catch path.
	private static final String[] HEALTH_ABSENT_IDS = {
		"client.particle.spawn.diag",
		"client.toast.diag",
		"client.scheduler.called",
		"client.events.direct.called",
		"client.wrapper.icons.item.called",
	};

	private static void assertStartupFired(final GameTestHelper helper, final String id) {
		assertj(helper, () -> assertThat(TestRuntime.passedStartup(id)).as("%s should have fired", id).isTrue());
	}

	@GameTest
	@EmptyTemplate
	@TestHolder(value = "client_startup_registries", description = "Client startup registry fixtures fired on the client")
	static void clientStartupRegistries(final DynamicTest test) {
		test.onGameTest(helper -> {
			var sequence = helper.startSequence();

			for (var id : STARTUP_IDS) {
				final var marker = id;
				sequence = sequence.thenExecute(() -> assertStartupFired(helper, marker));
			}

			sequence.thenSucceed();
		});
	}

	private static void assertClientMarkers(final DynamicTest test, final String[] ids) {
		test.onGameTest(helper -> {
			var sequence = helper.startSequence();

			for (var id : ids) {
				final var marker = id;
				sequence = sequence
					.thenWaitUntil(() -> assertFired(helper, marker))
					.thenExecute(() -> assertVerified(helper, marker));
			}

			sequence.thenSucceed();
		});
	}

	@GameTest
	@EmptyTemplate
	@TestHolder(value = "client_lifecycle", description = "Client lifecycle fixtures fired on the client")
	static void clientLifecycle(final DynamicTest test) {
		assertClientMarkers(test, LIFECYCLE_IDS);
	}

	@GameTest
	@EmptyTemplate
	@TestHolder(value = "client_lang", description = "Client lang fixtures fired on the client")
	static void clientLang(final DynamicTest test) {
		assertClientMarkers(test, LANG_IDS);
	}

	@GameTest
	@EmptyTemplate
	@TestHolder(value = "client_assets", description = "Client asset generation fixtures fired on the client")
	static void clientAssets(final DynamicTest test) {
		assertClientMarkers(test, ASSET_IDS);
	}

	@GameTest
	@EmptyTemplate
	@TestHolder(value = "client_tooltips", description = "Client tooltip fixtures fired on the client")
	static void clientTooltips(final DynamicTest test) {
		assertClientMarkers(test, TOOLTIP_IDS);
	}

	@GameTest
	@EmptyTemplate
	@TestHolder(value = "client_particle_registry", description = "Client particle provider fixture fired on the client")
	static void clientParticleRegistry(final DynamicTest test) {
		assertClientMarkers(test, PARTICLE_IDS);
	}

	@GameTest
	@EmptyTemplate
	@TestHolder(value = "client_wrappers", description = "Client wrapper fixtures fired on the client")
	static void clientWrappers(final DynamicTest test) {
		assertClientMarkers(test, WRAPPER_IDS);
	}

	@GameTest
	@EmptyTemplate
	@TestHolder(value = "client_asset_effects", description = "Generated client assets are readable back from the packs and registries")
	static void clientAssetEffects(final DynamicTest test) {
		test.onGameTest(helper -> {
			var sequence = helper.startSequence()
				.thenExecute(() -> ClientAssetAsserts.assertBlockState(helper))
				.thenExecute(() -> ClientAssetAsserts.assertModels(helper))
				.thenExecute(() -> ClientAssetAsserts.assertParticlesSounds(helper))
				.thenExecute(() -> ClientAssetAsserts.assertTextures(helper))
				.thenExecute(() -> ClientAssetAsserts.assertLang(helper))
				.thenExecute(() -> ClientAssetAsserts.assertFilePack(helper))
				.thenExecute(() -> ClientAssetAsserts.assertRegistries(helper))
				.thenExecute(() -> ClientAssetAsserts.assertResourceManager(helper));

			sequence.thenSucceed();
		});
	}

	@GameTest
	@EmptyTemplate
	@TestHolder(value = "client_health", description = "Client diagnostic markers stayed silent")
	static void clientHealth(final DynamicTest test) {
		test.onGameTest(helper -> {
			var sequence = helper.startSequence();

			for (var id : HEALTH_ABSENT_IDS) {
				final var marker = id;
				sequence = sequence.thenExecute(() -> assertCount(helper, marker, 0));
			}

			sequence.thenSucceed();
		});
	}
}
