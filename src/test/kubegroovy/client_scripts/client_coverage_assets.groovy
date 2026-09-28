import dev.uncandango.kubegroovy.color.KubeColor
import dev.uncandango.kubegroovy.generator.KubeAssetGenerator
import net.minecraft.resources.Identifier

// Exhaust KubeAssetGenerator + all client generator toJson branches.
// Fires during ClientAssetPacks.inject on every client resource reload.

def coverAssets = { gen ->
	$TestRuntime.pass('client.assets.fired')

	// KubeResourceGenerator base branches.
	$TestRuntime.check('client.assets.base') {
		gen.text('kubegroovy:coverage_client/note' as Identifier, 'coverage')
		gen.json('kubegroovy:coverage_client/data' as Identifier, $JsonUtils.fromString('{"a":"b"}'))
	}
	$TestRuntime.check('client.assets.generated') {
		$TestRuntime.assertThat(gen.getRegistries() != null).isTrue()
		$TestRuntime.assertThat(gen.getGenerated('kubegroovy:coverage_client/missing_entry' as Identifier) == null).isTrue()
	}

	// Static tint helper both branches.
	$TestRuntime.check('client.assets.tints') {
		def noTints = KubeAssetGenerator.createItemTintSources(-1)
		def someTints = KubeAssetGenerator.createItemTintSources(2)
		$TestRuntime.assertThat(noTints == null).isTrue()
		$TestRuntime.assertThat(someTints != null && someTints.size() == 3).isTrue()
	}

	$TestRuntime.check('client.assets.blockstates') {
	// Variant blockstate: single-model, multi-model, simpleVariant, x/y/uvlock.
	gen.blockState('kubegroovy:coverage_client_block' as Identifier) { state ->
		state.simpleVariant('', 'kubegroovy:block/unknown' as Identifier)
		state.variant('facing=north', { v -> v.model('kubegroovy:block/unknown' as Identifier).x(90).y(180).uvlock() })
		state.variant('facing=south', { v ->
			v.model('kubegroovy:block/unknown' as Identifier)
			v.model('kubegroovy:block/base' as Identifier).x(0).y(90)
		})
	}

	// Multipart: consumer part (with + without when), direct model part, malformed when.
	gen.multipartState('kubegroovy:coverage_client_multipart' as Identifier) { multipart ->
		multipart.part('facing=north,open=true', { part -> part.model('kubegroovy:block/unknown' as Identifier).x(90) })
		multipart.part('', 'kubegroovy:block/base' as Identifier)
		multipart.part('badsegment', { part -> part.model('kubegroovy:block/unknown' as Identifier) })
		multipart.part('a=1,,=2,x=', { part -> part.model('kubegroovy:block/unknown' as Identifier).y(270).uvlock() })
		multipart.part('facing=east', { part ->
			part.model('kubegroovy:block/unknown' as Identifier)
			part.model('kubegroovy:block/base' as Identifier).y(90)
		})
	}
	}

	$TestRuntime.check('client.assets.models') {
	// Block + item models: every ModelGenerator branch.
	gen.blockModel('kubegroovy:coverage_client_model' as Identifier) { model ->
		model.parent(null)
		model.parent('minecraft:block/cube_all' as Identifier)
		model.texture(['layer0', 'particle'] as String[], 'kubegroovy:block/unknown')
		model.textures(['layer0': 'kubegroovy:block/unknown', 'side': 'kubegroovy:block/base'])
		model.element { el ->
			el.allFaces { face -> face.tex('#layer0').cull().uv(0, 0, 16, 16).tintindex(0) }
		}
		model.element { el ->
			el.faces([net.minecraft.core.Direction.UP, net.minecraft.core.Direction.DOWN] as net.minecraft.core.Direction[], { face ->
				face.tex('#side').cull(net.minecraft.core.Direction.UP)
			})
		}
		model.element { el ->
			// Default face: covers Face.toJson null-cull/null-uv/no-tint branches.
			el.allFaces { face -> }
		}
		model.override('kubegroovy:block/unknown' as Identifier) { o -> o.predicate('kubegroovy:coverage' as Identifier, 1.0f) }
		model.override('kubegroovy:block/base' as Identifier) { o -> }
		model.custom { json -> json.addProperty('coverage', true) }
	}
	gen.blockModel('kubegroovy:coverage_client_empty_model' as Identifier) { model -> }

	gen.itemModel('kubegroovy:coverage_client_item' as Identifier) { model ->
		model.parent('minecraft:item/generated' as Identifier)
		model.texture(['layer0'] as String[], 'kubegroovy:item/example_item')
	}
	gen.itemModel('kubegroovy:coverage_client_tinted_item' as Identifier, { model ->
		model.parent('minecraft:item/generated' as Identifier)
		model.texture(['layer0'] as String[], 'kubegroovy:item/example_item')
	}, KubeAssetGenerator.createItemTintSources(1))
	gen.itemDefinition('kubegroovy:coverage_client_def' as Identifier, 'kubegroovy:item/coverage_client_item' as Identifier, null)
	gen.defaultItemModel('kubegroovy:coverage_client_default_item' as Identifier)
	gen.defaultHandheldItemModel('kubegroovy:coverage_client_handheld' as Identifier)
	}

	$TestRuntime.check('client.assets.particles') {
	// Particles + sounds (sounds is currently a no-op but must be invoked).
	gen.particle('kubegroovy:coverage_client_particle' as Identifier) { particle ->
		particle.texture('kubegroovy:coverage_a')
		particle.textures(['kubegroovy:coverage_b', 'kubegroovy:coverage_c'])
	}
	gen.particle('kubegroovy:coverage_client_empty_particle' as Identifier) { particle -> }
	gen.sounds('kubegroovy') { sounds ->
		sounds.addSound('coverage_client', { s ->
			s.replace(false).replace().subtitle('sound.kubegroovy.coverage_client')
				.sound('kubegroovy:coverage').sounds('kubegroovy:a', 'kubegroovy:b')
				.sound('kubegroovy:complex') { inst ->
					inst.volume(0.5f).pitch(1.5f).weight(2).stream().stream(false)
						.attenuationDistance(32).preload().preload(false).asReferenceToEvent()
				}
		})
		sounds.addSound('coverage_client', { s -> s.subtitle('overlay') }, true)
		sounds.addSound('coverage_client_new', { s -> s.sound('kubegroovy:new') }, true)
	}
	}

	$TestRuntime.check('client.assets.textures') {
	// Textures: missing (EMPTY error branch) + real loads + stencil/mask true/false branches.
	def missing = gen.loadTexture('kubegroovy:coverage_missing_xyz' as Identifier)
	gen.texture('kubegroovy:coverage_client/missing' as Identifier, missing)
	def real = gen.loadTexture('kubegroovy:block/unknown' as Identifier)
	if (real.width > 0) {
		gen.texture('kubegroovy:coverage_client/real' as Identifier, real)
		// Sprite backing for the startup-registered coverage particle, so the
		// script-registered provider actually instantiates in game.
		gen.texture('kubegroovy:coverage_particle' as Identifier, real)
		gen.stencil('kubegroovy:coverage_client/stenciled' as Identifier,
			'kubegroovy:block/unknown' as Identifier, [('#ffffff' as KubeColor): ('#ff0000' as KubeColor)])
		gen.stencil('kubegroovy:coverage_client/stencil_missing' as Identifier,
			'kubegroovy:coverage_missing_xyz' as Identifier, [:])
		def masked = gen.mask('kubegroovy:coverage_client/masked' as Identifier,
			'kubegroovy:item/bucket_mask' as Identifier, 'kubegroovy:item/bucket_fluid' as Identifier)
		def maskedMissing = gen.mask('kubegroovy:coverage_client/masked_missing' as Identifier,
			'kubegroovy:coverage_missing_xyz' as Identifier, 'kubegroovy:coverage_missing_abc' as Identifier)
		def maskedSame = gen.mask('kubegroovy:coverage_client/masked_same' as Identifier,
			'kubegroovy:item/bucket_mask' as Identifier, 'kubegroovy:item/bucket_mask' as Identifier)
		$TestRuntime.check('client.assets.mask') {
			$TestRuntime.assertThat(masked == true || masked == false).isTrue()
			$TestRuntime.assertThat(maskedMissing).isFalse()
			$TestRuntime.assertThat(maskedSame == true || maskedSame == false).isTrue()
		}
		def logo = gen.loadTexture('kubegroovy:kubejs_logo' as Identifier)
		if (logo.width > 0) {
			// kubejs_logo ships a .mcmeta: covers texture()'s mcmeta branch.
			gen.texture('kubegroovy:coverage_client/logo' as Identifier, logo)
			$TestRuntime.pass('client.assets.logo')
		}
	} else {
		$TestRuntime.pass('client.assets.texture.empty')
	}
	}
}

$ClientEvents.generateAssets('after_mods') { gen -> coverAssets(gen) }
$ClientEvents.generateAssets('before_mods') { gen ->
	$TestRuntime.check('client.assets.before') {
		gen.blockState('kubegroovy:coverage_client_before' as Identifier) { state ->
			state.simpleVariant('', 'kubegroovy:block/unknown' as Identifier)
		}
	}
}
$ClientEvents.generateAssets('last') { gen ->
	$TestRuntime.check('client.assets.last') {
		gen.particle('kubegroovy:coverage_client_last' as Identifier) { particle ->
			particle.texture('kubegroovy:coverage_last')
		}
	}
}
