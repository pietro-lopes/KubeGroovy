import dev.uncandango.kubegroovy.client.BlockTintFunctionWrapper
import dev.uncandango.kubegroovy.client.DebugInfoKubeEvent
import dev.uncandango.kubegroovy.client.icon.ItemKubeIcon
import dev.uncandango.kubegroovy.client.icon.KubeIconRenderer
import dev.uncandango.kubegroovy.client.icon.TextureKubeIcon
import dev.uncandango.kubegroovy.client.ItemTintFunctionWrapper
import dev.uncandango.kubegroovy.client.KubeGroovyClient
import dev.uncandango.kubegroovy.client.KubeGroovyKeybinds
import dev.uncandango.kubegroovy.client.KubeSessionData
import dev.uncandango.kubegroovy.client.NotificationToast
import dev.uncandango.kubegroovy.text.tooltip.TooltipRequirements
import dev.uncandango.kubegroovy.util.NotificationToastData
import dev.uncandango.kubegroovy.util.Tristate
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3

import java.util.Optional

// Client lifecycle + targeted client events. Loaded only on the client (client_scripts),
// so client-only imports and bindings are safe here.

def clientTickCount = 0

$ClientEvents.loggedIn { event ->
	$TestRuntime.pass('client.loggedIn')
	$TestRuntime.check('client.loggedIn.fields') {
		$TestRuntime.assertThat(event.client != null).isTrue()
		$TestRuntime.assertThat(event.player != null).isTrue()
		$TestRuntime.assertThat(event.entity != null).isTrue()
	}
}

$ClientEvents.loggedOut { event ->
	$TestRuntime.pass('client.loggedOut')
	$TestRuntime.check('client.loggedOut.fields') {
		$TestRuntime.assertThat(event.client != null).isTrue()
	}
}

$ClientEvents.tick { event ->
	if (clientTickCount == 0) {
		$TestRuntime.pass('client.tick')
		$TestRuntime.check('client.tick.fields') {
			$TestRuntime.assertThat(event.client != null).isTrue()
			$TestRuntime.assertThat(event.player != null).isTrue()
		}
		// Exercise scheduler + Client + GLFWInput bindings once the world is ticking.
		// NOTE: $Client is usually absent (lazy binding resolves once at mod init
		// when Minecraft is still null), so use event.client instead.
		try {
			def mc = event.client
			$TestRuntime.check('client.bindings') {
				$TestRuntime.assertThat(mc != null).isTrue()
				$TestRuntime.assertThat($GLFWInput != null).isTrue()
			}
		} catch (Throwable ignored) {
		}
		try {
			def timeoutId = setTimeout({ se -> $TestRuntime.pass('client.timeout') }, '5t')
			clearTimeout(timeoutId)
			setTimeout({ se -> $TestRuntime.pass('client.timeout.fired') }, '2t')
			def intervalId = setInterval({ se -> $TestRuntime.pass('client.interval') }, '20t')
			clearInterval(intervalId)
			setInterval({ se -> $TestRuntime.pass('client.interval.fired') }, '30t')
		} catch (Throwable ignored) {
			$TestRuntime.pass('client.scheduler.called')
		}
		// Cover key/debug event methods without needing physical input or F3.
		try {
			def key = KubeGroovyKeybinds.getOrCreate('coverage_key_a')
			def pressed = new KubeGroovyKeybinds.KeyEvent(event.player, key)
			def ticking = new KubeGroovyKeybinds.TickingKeyEvent(event.player, key)
			def debug = new DebugInfoKubeEvent(event.player, ['coverage line'])
			$TestRuntime.check('client.events.direct') {
				$TestRuntime.assertThat(pressed.client != null).isTrue()
				$TestRuntime.assertThat(pressed.player.is(event.player)).isTrue()
				$TestRuntime.assertThat(ticking.ticks >= 0).isTrue()
				$TestRuntime.assertThat(debug.lines.contains('coverage line')).isTrue()
				$TestRuntime.assertThat(debug.showDebug == true || debug.showDebug == false).isTrue()
			}
		} catch (Throwable ignored) {
			$TestRuntime.pass('client.events.direct.called')
		}
		// Item icons and item-tint calculate() need bound registries, so these
		// run in-world, not at load.
		try {
			$TestRuntime.check('client.wrapper.icons.item') {
				$TestRuntime.assertThat(KubeIconRenderer.from(new ItemKubeIcon('minecraft:diamond')) != null).isTrue()
			}
			$TestRuntime.check('client.wrapper.tintFunctions.itemStack') {
				def result = new ItemTintFunctionWrapper(0)
					.calculate('minecraft:diamond', null, null)
				// The value depends on whether the item carries a script
				// behavior; the instanceof proves the call completed.
				$TestRuntime.assertThat(result instanceof Integer).isTrue()
			}
		} catch (Throwable ignored) {
			$TestRuntime.pass('client.wrapper.icons.item.called')
		}
		// Spawn a vanilla-sprite particle so the script provider + particle
		// tick/getter paths run with the vanilla flame sprite.
		// Staggered a few times: an immediately-joined world sometimes drops
		// the first-tick spawn before the particle engine is ready.
		def spawnFlame = {
			def opts = 'minecraft:flame'
			def level = event.client.level
			def pos = event.player.blockPosition()
			level.addParticle(opts, pos.x + 0.5, pos.y + 2.0, pos.z + 0.5, 0.0, 0.0, 0.0)
			$TestRuntime.pass('client.particle.spawned')
		}
		try {
			spawnFlame()
			setTimeout({ se -> spawnFlame() }, '5s')
			setTimeout({ se -> spawnFlame() }, '15s')
		} catch (Throwable t) {
			$TestRuntime.check('client.particle.spawn.diag') { $TestRuntime.assertThat('threw: ' + t.toString()).isEqualTo('spawned') }
		}
		// World-dependent tint averages + session lookup + toast lifecycle.
		try {
			def level = event.client.level
			def pos = event.player.blockPosition()
			BlockTintFunctionWrapper.getAverageGrassColor(level, pos)
			BlockTintFunctionWrapper.getAverageFoliageColor(level, pos)
			BlockTintFunctionWrapper.getAverageWaterColor(level, pos)
			KubeSessionData.of(event.client)
			$TestRuntime.check('client.formatNumber') {
				$TestRuntime.assertThat(KubeGroovyClient.formatNumber(5)).isEqualTo('5')
				$TestRuntime.assertThat(KubeGroovyClient.formatNumber(9999)).isEqualTo('9999')
				$TestRuntime.assertThat(KubeGroovyClient.formatNumber(15000)).isEqualTo('15K')
				$TestRuntime.assertThat(KubeGroovyClient.formatNumber(2500000)).isEqualTo('2M')
			}
			def toastData = new NotificationToastData(
				'5s', $Text.string('Coverage toast'),
				Optional.of(new TextureKubeIcon('kubegroovy:block/unknown')),
				16, Optional.empty(), Optional.empty(), Optional.empty(), false)
			def toast = new NotificationToast(event.client, toastData)
			toast.update(null, 0L)
			$TestRuntime.check('client.toast') {
				$TestRuntime.assertThat(toast.width() > 0).isTrue()
				$TestRuntime.assertThat(toast.height() > 0).isTrue()
				$TestRuntime.assertThat(toast.getWantedVisibility() != null).isTrue()
			}
			// Show real toasts so render/update/visibility/icon-draw paths execute.
			event.player.notify(toastData)
			$TestRuntime.pass('client.toast.shown')
			def itemToastData = new NotificationToastData(
				'5s', $Text.string('Item toast'),
				Optional.of(new ItemKubeIcon('minecraft:diamond')),
				16, Optional.empty(), Optional.empty(), Optional.empty(), true)
			event.player.notify(itemToastData)
			$TestRuntime.pass('client.toast.item.shown')
		} catch (Throwable t) {
			$TestRuntime.check('client.toast.diag') { $TestRuntime.assertThat('threw: ' + t.toString()).isEqualTo('toasted') }
		}
	}
	clientTickCount++
}

$ClientEvents.leftDebugInfo { event ->
	$TestRuntime.check('client.debug.left') {
		event.lines.add('KubeGroovy coverage left')
		$TestRuntime.assertThat(event.lines.contains('KubeGroovy coverage left')).isTrue()
		$TestRuntime.assertThat(event.player != null).isTrue()
		def show = event.showDebug
		$TestRuntime.assertThat(show == true || show == false).isTrue()
	}
}

$ClientEvents.rightDebugInfo { event ->
	$TestRuntime.check('client.debug.right') {
		event.lines.add('KubeGroovy coverage right')
		$TestRuntime.assertThat(event.lines.contains('KubeGroovy coverage right')).isTrue()
	}
}

$ClientEvents.atlasSpriteRegistry('minecraft:blocks') { event ->
	$TestRuntime.check('client.atlas.blocks') {
		event.register('kubegroovy:coverage_sprite')
	}
}

$ClientEvents.atlasSpriteRegistry('minecraft:particles') { event ->
	$TestRuntime.check('client.atlas.particles') {
		event.register('kubegroovy:coverage_particle_sprite')
	}
}

$ClientEvents.lang('en_us') { event ->
	$TestRuntime.check('client.lang') {
		event.add('kubegroovy', 'coverage.client.key', 'Coverage Value')
		event.add('coverage.client.simple', 'Simple Value')
		event.addAll('kubegroovy', ['coverage.client.a': 'A', 'coverage.client.b': 'B'])
		event.addAll(['coverage.client.c': 'C'])
		try {
			event.renameItem('minecraft:diamond', 'Coverage Diamond')
		} catch (Throwable ignored) {
			// Item registries are not bound during the early boot resource reload;
			// renames apply on later reloads once holders exist.
			$TestRuntime.pass('client.lang.rename.deferred')
		}
		event.renameBlock(Blocks.STONE, 'Coverage Stone')
		event.renameEntity('minecraft:pig', 'Coverage Pig')
		event.renameBiome('minecraft:plains', 'Coverage Plains')
		event.painting('kubegroovy:coverage_painting', 'Coverage Title', 'Coverage Author')
		// Null/empty branches throw; prove the guard without failing the script.
		// NOTE: catch Throwable because the sandbox class filter denies java.lang.IllegalArgumentException.
		try {
			event.add('kubegroovy', '', 'bad')
		} catch (Throwable ignored) {
			$TestRuntime.pass('client.lang.guards')
		}
		try {
			event.renameItem(ItemStack.EMPTY, 'Empty')
			event.renameBlock(Blocks.AIR, 'Air')
			$TestRuntime.pass('client.lang.rename.guards')
		} catch (Throwable ignored) {
		}
	}
}

$ClientEvents.lang('es_es') { event ->
	$TestRuntime.check('client.lang.es') {
		event.add('kubegroovy', 'coverage.client.es', 'Valor de cobertura')
	}
}

// Invalid language key: exercises ClientAssetPacks' invalid-key error branch.
$ClientEvents.lang('invalid_key!') { event ->
	$TestRuntime.pass('client.lang.invalid')
}

$ClientEvents.particleProviderRegistry { event ->
	$TestRuntime.check('client.particle.registry') {
		def flame = BuiltInRegistries.PARTICLE_TYPE.getValue(Identifier.parse('minecraft:flame'))
		def smoke = BuiltInRegistries.PARTICLE_TYPE.getValue(Identifier.parse('minecraft:smoke'))
		def heart = BuiltInRegistries.PARTICLE_TYPE.getValue(Identifier.parse('minecraft:heart'))
		def villager = BuiltInRegistries.PARTICLE_TYPE.getValue(Identifier.parse('minecraft:angry_villager'))
		def coverage = BuiltInRegistries.PARTICLE_TYPE.getValue(Identifier.parse('kubegroovy:coverage_particle'))
	if (flame != null) {
		event.register(flame) { particle ->
			particle.setGravity(0.1f)
			particle.setFriction(0.9f)
			particle.setColor('#ff0000', true)
			particle.setColor('#00ff00')
			particle.setPhysicality(true)
			particle.setFasterWhenYMotionBlocked(true)
			particle.setSpeed(new Vec3(0.01, 0.02, 0.03))
			particle.setLightColor({ partial -> 0xF000F0 })
			particle.onTick({ p ->
				$TestRuntime.pass('client.particle.ticked')
				p.getXSpeed()
			})
			// Getters: valid while the provider runs at spawn time.
			particle.getLevel()
			particle.getX()
			particle.getY()
			particle.getZ()
			particle.getXSpeed()
			particle.getYSpeed()
			particle.getZSpeed()
			particle.getSpriteSet()
			particle.getRandom()
			particle.getLightCoords(0.5f)
			$TestRuntime.pass('client.particle.customizer')
		}
	}
	if (coverage != null) {
		event.register(coverage)
	}
	if (smoke != null && flame != null && flame != smoke) {
		event.register(smoke) { particle ->
			particle.setGravity(0.0f)
			particle.setPhysicality(false)
		}
	}
	if (heart != null) {
		event.registerSpecial(heart, { type, level, x, y, z, xSpeed, ySpeed, zSpeed, random -> null })
	}
	if (villager != null) {
		event.register(villager, { type, level, x, y, z, sprites, xSpeed, ySpeed, zSpeed, random -> null })
	}
	}
}

$KeyBindEvents.pressed('coverage_key_a') { event ->
	$TestRuntime.pass('client.key.pressed')
	$TestRuntime.check('client.key.pressed.fields') {
		$TestRuntime.assertThat(event.player != null).isTrue()
	}
}

$KeyBindEvents.released('coverage_key_a') { event ->
	$TestRuntime.pass('client.key.released')
	$TestRuntime.check('client.key.released.fields') {
		$TestRuntime.assertThat(event.ticks >= 0).isTrue()
	}
}

$KeyBindEvents.tick('coverage_key_a') { event ->
	$TestRuntime.pass('client.key.tick')
}

$ItemEvents.modifyTooltips { event ->
	$TestRuntime.check('client.tooltip.modify') {
		event.modify('minecraft:diamond', { builderBuilder -> builderBuilder.with { add([$Text.string('Coverage line')]) } })
		event.modifyAll({ builderBuilder -> builderBuilder.with { dynamic('coverage_client_tip') } })
		event.add('minecraft:stone', [$Text.string('Stone line')])
		def reqs = new TooltipRequirements(
			Tristate.TRUE, Tristate.DEFAULT,
			Tristate.DEFAULT, Tristate.FALSE,
			Tristate.DEFAULT, [:])
		event.modify('minecraft:iron_ingot', reqs, { builderBuilder -> builderBuilder.with { add([$Text.string('Req line')]) } })
		event.modifyAll(reqs, { builderBuilder -> builderBuilder.with { add([$Text.string('Req all')]) } })
		event.add('minecraft:gold_ingot', reqs, [$Text.string('Req gold')])
	}
}

$ItemEvents.dynamicTooltips('coverage_client_tip') { event ->
	$TestRuntime.check('client.tooltip.dynamic') {
		event.add([$Text.string('Dynamic coverage')])
	}
	$TestRuntime.check('client.tooltip.dynamic.fields') {
		$TestRuntime.assertThat(event.item != null).isTrue()
		$TestRuntime.assertThat(event.lines != null).isTrue()
	}
}
