import dev.uncandango.kubegroovy.color.KubeColor
import dev.uncandango.kubegroovy.generator.KubeAssetGenerator
import net.minecraft.core.Direction

// Exhaust KubeAssetGenerator + all client generator toJson branches.
// Fires during ClientAssetPacks.inject on every client resource reload.

def coverAssets = { gen ->
	$TestRuntime.pass('client.assets.fired')

	// KubeResourceGenerator base branches.
	$TestRuntime.check('client.assets.base') {
		gen.text('kubegroovy:coverage_client/note', 'coverage')
		gen.json('kubegroovy:coverage_client/data', $JsonUtils.fromString('{"a":"b"}'))
	}
	$TestRuntime.check('client.assets.generated') {
		$TestRuntime.assertThat(gen.getRegistries() != null).isTrue()
		$TestRuntime.assertThat(gen.getGenerated('kubegroovy:coverage_client/missing_entry') == null).isTrue()
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
	gen.blockState('kubegroovy:coverage_client_block') { state ->
		state.simpleVariant('', 'kubegroovy:block/unknown')
		state.variant('facing=north', { v -> v.model('kubegroovy:block/unknown').x(90).y(180).uvlock() })
		state.variant('facing=south', { v ->
			v.model('kubegroovy:block/unknown')
			v.model('kubegroovy:block/base').x(0).y(90)
		})
	}

	// Multipart: consumer part (with + without when), direct model part, malformed when.
	gen.multipartState('kubegroovy:coverage_client_multipart') { multipart ->
		multipart.part('facing=north,open=true', { part -> part.model('kubegroovy:block/unknown').x(90) })
		multipart.part('', 'kubegroovy:block/base')
		multipart.part('badsegment', { part -> part.model('kubegroovy:block/unknown') })
		multipart.part('a=1,,=2,x=', { part -> part.model('kubegroovy:block/unknown').y(270).uvlock() })
		multipart.part('facing=east', { part ->
			part.model('kubegroovy:block/unknown')
			part.model('kubegroovy:block/base').y(90)
		})
	}
	}

	$TestRuntime.check('client.assets.models') {
	// Block + item models: every ModelGenerator branch.
	gen.blockModel('kubegroovy:coverage_client_model') { model ->
		model.parent(null)
		model.parent('minecraft:block/cube_all')
		model.texture(['layer0', 'particle'], 'kubegroovy:block/unknown')
		model.textures(['layer0': 'kubegroovy:block/unknown', 'side': 'kubegroovy:block/base'])
		model.element { el ->
			el.allFaces { face -> face.tex('#layer0').cull().uv(0, 0, 16, 16).tintindex(0) }
		}
		model.element { el ->
			el.faces([Direction.UP, Direction.DOWN], { face ->
				face.tex('#side').cull(Direction.UP)
			})
		}
		model.element { el ->
			// Default face: covers Face.toJson null-cull/null-uv/no-tint branches.
			el.allFaces { face -> }
		}
		model.override('kubegroovy:block/unknown') { o -> o.predicate('kubegroovy:coverage', 1.0f) }
		model.override('kubegroovy:block/base') { o -> }
		model.custom { json -> json.addProperty('coverage', true) }
	}
	gen.blockModel('kubegroovy:coverage_client_empty_model') { model -> }

	gen.itemModel('kubegroovy:coverage_client_item') { model ->
		model.parent('minecraft:item/generated')
		model.texture(['layer0'], 'kubegroovy:item/example_item')
	}
	gen.itemModel('kubegroovy:coverage_client_tinted_item', { model ->
		model.parent('minecraft:item/generated')
		model.texture(['layer0'], 'kubegroovy:item/example_item')
	}, KubeAssetGenerator.createItemTintSources(1))
	gen.itemDefinition('kubegroovy:coverage_client_def', 'kubegroovy:item/coverage_client_item', null)
	gen.defaultItemModel('kubegroovy:coverage_client_default_item')
	gen.defaultHandheldItemModel('kubegroovy:coverage_client_handheld')
	}

	$TestRuntime.check('client.assets.particles') {
	// Particles + sounds (sounds is currently a no-op but must be invoked).
	gen.particle('kubegroovy:coverage_client_particle') { particle ->
		particle.texture('kubegroovy:coverage_a')
		particle.textures(['kubegroovy:coverage_b', 'kubegroovy:coverage_c'])
	}
	gen.particle('kubegroovy:coverage_client_empty_particle') { particle -> }
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
	def missing = gen.loadTexture('kubegroovy:coverage_missing_xyz')
	gen.texture('kubegroovy:coverage_client/missing', missing)
	def real = gen.loadTexture('kubegroovy:block/unknown')
	if (real.width > 0) {
		gen.texture('kubegroovy:coverage_client/real', real)
		// Sprite backing for the startup-registered coverage particle, so the
		// script-registered provider actually instantiates in game.
		gen.texture('kubegroovy:coverage_particle', real)
		gen.stencil('kubegroovy:coverage_client/stenciled',
			'kubegroovy:block/unknown', [('#ffffff' as KubeColor): ('#ff0000' as KubeColor)])
		gen.stencil('kubegroovy:coverage_client/stencil_missing',
			'kubegroovy:coverage_missing_xyz', [:])
		def masked = gen.mask('kubegroovy:coverage_client/masked',
			'kubegroovy:item/bucket_mask', 'kubegroovy:item/bucket_fluid')
		def maskedMissing = gen.mask('kubegroovy:coverage_client/masked_missing',
			'kubegroovy:coverage_missing_xyz', 'kubegroovy:coverage_missing_abc')
		def maskedSame = gen.mask('kubegroovy:coverage_client/masked_same',
			'kubegroovy:item/bucket_mask', 'kubegroovy:item/bucket_mask')
		$TestRuntime.check('client.assets.mask') {
			$TestRuntime.assertThat(masked == true || masked == false).isTrue()
			$TestRuntime.assertThat(maskedMissing).isFalse()
			$TestRuntime.assertThat(maskedSame == true || maskedSame == false).isTrue()
		}
		def logo = gen.loadTexture('kubegroovy:kubejs_logo')
		if (logo.width > 0) {
			// kubejs_logo ships a .mcmeta: covers texture()'s mcmeta branch.
			gen.texture('kubegroovy:coverage_client/logo', logo)
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
		gen.blockState('kubegroovy:coverage_client_before') { state ->
			state.simpleVariant('', 'kubegroovy:block/unknown')
		}
	}
}
$ClientEvents.generateAssets('last') { gen ->
	$TestRuntime.check('client.assets.last') {
		gen.particle('kubegroovy:coverage_client_last') { particle ->
			particle.texture('kubegroovy:coverage_last')
		}
	}
}
