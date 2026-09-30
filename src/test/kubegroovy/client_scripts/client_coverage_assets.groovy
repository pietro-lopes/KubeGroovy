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
			state.with {
				simpleVariant('', 'kubegroovy:block/unknown')
				variant('facing=north', { vBuilder ->
					vBuilder.with {
						model('kubegroovy:block/unknown').with {
							x(90)
							y(180)
							uvlock()
						}
					}
				})
				variant('facing=south', { vBuilder ->
					vBuilder.with {
						model('kubegroovy:block/unknown')
						model('kubegroovy:block/base').with {
							x(0)
							y(90)
						}
					}
				})
			}
		}

		// Multipart: consumer part (with + without when), direct model part, malformed when.
		gen.multipartState('kubegroovy:coverage_client_multipart') { multipartBuilder ->
			multipartBuilder.with {
				part('facing=north,open=true', { partBuilder ->
					partBuilder.with {
						model('kubegroovy:block/unknown').with {
							x(90)
						}
					}
				})
				part('', 'kubegroovy:block/base')
				part('badsegment', { partBuilder ->
					partBuilder.with {
						model('kubegroovy:block/unknown')
					}
				})
				part('a=1,,=2,x=', { partBuilder ->
					partBuilder.with {
						model('kubegroovy:block/unknown').with {
							y(270)
							uvlock()
						}
					}
				})
				part('facing=east', { partBuilder ->
					partBuilder.with {
						model('kubegroovy:block/unknown')
						model('kubegroovy:block/base').with {
							y(90)
						}
					}
				})
			}
		}
	}

	$TestRuntime.check('client.assets.models') {
		// Block + item models: every ModelGenerator branch.
		gen.blockModel('kubegroovy:coverage_client_model') { modelBuilder ->
			modelBuilder.with {
				parent(null)
				parent('minecraft:block/cube_all')
				texture(['layer0', 'particle'], 'kubegroovy:block/unknown')
				textures(['layer0': 'kubegroovy:block/unknown', 'side': 'kubegroovy:block/base'])
				element { elBuilder ->
					elBuilder.with {
						allFaces { faceBuilder ->
							faceBuilder.with {
								tex('#layer0').cull().uv(0, 0, 16, 16).tintindex(0)
							}
						}
					}
				}
				element { elBuilder ->
					elBuilder.with {
						faces([Direction.UP, Direction.DOWN], { faceBuilder ->
							faceBuilder.with {
								tex('#side').cull(Direction.UP)
							}
						})
					}
				}
				element { elBuilder ->
					elBuilder.with {
						// Default face: covers Face.toJson null-cull/null-uv/no-tint branches.
						allFaces { face -> }
					}
				}
				override('kubegroovy:block/unknown') { oBuilder ->
					oBuilder.with {
						predicate('kubegroovy:coverage', 1.0f)
					}
				}
				override('kubegroovy:block/base') { o -> }
				custom { json -> json.addProperty('coverage', true) }
			}
		}
		gen.blockModel('kubegroovy:coverage_client_empty_model') { model -> }

		gen.itemModel('kubegroovy:coverage_client_item') { modelBuilder ->
			modelBuilder.with {
				parent('minecraft:item/generated')
				texture(['layer0'], 'kubegroovy:item/example_item')
			}
		}
		gen.itemModel('kubegroovy:coverage_client_tinted_item', { modelBuilder ->
			modelBuilder.with {
				parent('minecraft:item/generated')
				texture(['layer0'], 'kubegroovy:item/example_item')
			}
		}, KubeAssetGenerator.createItemTintSources(1))
		gen.itemDefinition('kubegroovy:coverage_client_def', 'kubegroovy:item/coverage_client_item', null)
		gen.defaultItemModel('kubegroovy:coverage_client_default_item')
		gen.defaultHandheldItemModel('kubegroovy:coverage_client_handheld')
	}

	$TestRuntime.check('client.assets.particles') {
		// Particles + sounds (sounds is currently a no-op but must be invoked).
		gen.particle('kubegroovy:coverage_client_particle') { particleBuilder ->
			particleBuilder.with {
				texture('kubegroovy:coverage_a')
				textures(['kubegroovy:coverage_b', 'kubegroovy:coverage_c'])
			}
		}
		gen.particle('kubegroovy:coverage_client_empty_particle') { particle -> }
		gen.sounds('kubegroovy') { soundsBuilder ->
			soundsBuilder.with {
				addSound('coverage_client', { sBuilder ->
					sBuilder.with {
						replace(false).replace().subtitle('sound.kubegroovy.coverage_client')
						.sound('kubegroovy:coverage').sounds('kubegroovy:a', 'kubegroovy:b')
						.sound('kubegroovy:complex') { instBuilder ->
							instBuilder.with {
								volume(0.5f).pitch(1.5f).weight(2).stream().stream(false)
								.attenuationDistance(32).preload().preload(false).asReferenceToEvent()
							}
						}
					}
				})
				addSound('coverage_client', { sBuilder ->
					sBuilder.with {
						subtitle('overlay')
					}
				}, true)
				addSound('coverage_client_new', { sBuilder ->
					sBuilder.with {
						sound('kubegroovy:new')
					}
				}, true)
			}
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
			state.with {
				simpleVariant('', 'kubegroovy:block/unknown')
			}
		}
	}
}
$ClientEvents.generateAssets('last') { gen ->
	$TestRuntime.check('client.assets.last') {
		gen.particle('kubegroovy:coverage_client_last') { particleBuilder ->
			particleBuilder.with {
				texture('kubegroovy:coverage_last')
			}
		}
	}
}