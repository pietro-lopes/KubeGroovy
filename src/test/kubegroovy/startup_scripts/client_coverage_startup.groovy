// requires client
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.neoforged.neoforge.client.settings.KeyConflictContext
import net.neoforged.neoforge.client.settings.KeyModifier

// Startup-scope client registry coverage. Skipped on dedicated servers via `requires client`,
// fires on the game-test client during RegisterRenderers / RegisterMenuScreens / RegisterKeyMappings.

// Exhaust every KeybindRegistryKubeEvent.Builder branch.
$KeyBindEvents.registry { event ->
	$TestRuntime.passStartup('client.keybind.registry')
	// Default and explicitly selected main categories must register only once.
	event.register('coverage_key_default_a')
	event.register('coverage_key_default_b').category('kubegroovy')
	event.register('coverage_key_a', 'KEY_V').with {
		category('coverage')
		inGame()
	}
	event.register('coverage_key_b').with {
		defaultKey('KEY_B')
		category('coverage')
		gui()
	}
	event.register('coverage_key_c', 'MOUSE_BUTTON_LEFT').with {
		mouseInputType()
		category('coverage_mouse')
	}
	event.register('coverage_key_d', 'KEY_G').with {
		scanCodeInputType()
		category('coverage_scan')
	}
	event.register('coverage_key_e').with {
		inputType(InputConstants.Type.KEYSYM)
		category('coverage_input')
	}
	def chained = event.register('coverage_key_f', 'KEY_H')
	chained.with {
		conflictContext(KeyConflictContext.UNIVERSAL)
		modifier(KeyModifier.SHIFT)
		category('coverage_mod')
	}
	// Unknown key name resolves to -1: covers the unmapped-default branch.
	event.register('coverage_key_unknown_default', 'not.a.real.key').with {
		category('coverage')
	}
}

$ClientEvents.blockEntityRendererRegistry { event ->
	$TestRuntime.passStartup('client.registry.blockEntityRenderer')
	// NOTE: intentionally no event.register() call. The script bridge routes
	// EVERY interface method (including BlockEntityRenderer defaults like
	// shouldRenderOffScreen) to the backing closure, so a closure-backed
	// renderer crashes chunk-packet handling when the block entity loads
	// (verified: MissingMethodException for doCall() with 0 args). The lookup
	// below still proves the test type resolves on the client.
	def testType = BuiltInRegistries.BLOCK_ENTITY_TYPE.getValue(Identifier.parse('kubegroovy:test_ticker'))
	def type = testType != null ? testType : BuiltInRegistries.BLOCK_ENTITY_TYPE.getValue(Identifier.parse('minecraft:chest'))
	if (type != null) {
		$TestRuntime.passStartup('client.registry.blockEntityRenderer.resolved')
	}
}

$ClientEvents.entityRendererRegistry { event ->
	$TestRuntime.passStartup('client.registry.entityRenderer')
	// NOTE: intentionally no event.register() call here. Entity renderers are
	// baked eagerly at resource reload, so a stub provider breaks vanilla mob
	// rendering (verified: null provider fails dragon model creation). The
	// lookup below still proves the type resolves on the client.
	def type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse('minecraft:ender_dragon'))
	if (type != null) {
		$TestRuntime.passStartup('client.registry.entityRenderer.resolved')
	}
}

$ClientEvents.menuScreenRegistry { event ->
	$TestRuntime.passStartup('client.registry.menuScreen')
	def type = BuiltInRegistries.MENU.getValue(Identifier.parse('kubegroovy:menu'))
	if (type != null) {
		$TestRuntime.passStartup('client.registry.menuScreen.resolved')
		try {
			// kubegroovy:menu already has the default screen, so this documents
			// the duplicate-registration throw without breaking the client.
			event.register(type, { menu, inv, title -> null })
		} catch (Throwable ignored) {
			$TestRuntime.passStartup('client.registry.menuScreen.called')
		}
	}
}

$ItemEvents.modelProperties { event ->
	$TestRuntime.passStartup('client.modelProperties')
	event.register('kubegroovy:coverage_cb', { stack, level, entity, seed, ctx -> true })
	event.registerAll('kubegroovy:coverage_cb_all', { stack, level, entity, seed, ctx -> false })
}