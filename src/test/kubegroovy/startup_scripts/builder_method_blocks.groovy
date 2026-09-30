import net.minecraft.world.level.block.state.properties.WoodType

// Base block methods, callback setters, crop shape helpers, and subtype methods.
$StartupEvents.registry('block') { event ->
	def baseBuilder = event.create('coverage_block')
	baseBuilder.with {
		translationKey('block.kubegroovy.coverage_block')
		displayName('Coverage Block')
		formattedDisplayName('Coverage Formatted Block')
		formattedDisplayName()
		texture('kubegroovy:block/coverage_block')
		texture(['side', 'end'], 'kubegroovy:block/coverage_block_side')
		textures([all: 'kubegroovy:block/coverage_block'])
		parentModel('minecraft:block/cube_all')
		modelGenerator { modelBuilder ->
			modelBuilder.with {
				parent('minecraft:block/cube_all')
				textures([all: 'kubegroovy:block/coverage_block'])
			}
		}
		copyPropertiesFrom('minecraft:stone')
		soundType('wood')
		noSoundType()
		woodSoundType()
		stoneSoundType()
		gravelSoundType()
		grassSoundType()
		sandSoundType()
		cropSoundType()
		glassSoundType()
		mapColor('color_red')
		dynamicMapColor { state -> 'color_blue' }
		hardness(2)
		resistance(4)
		unbreakable()
		lightLevel(0.5f)
		opaque(false)
		fullBlock(true)
		requiresTool(false)
		requiresTool()
		renderType('solid')
		color(0) { state -> 0xFF00FF00 }
		color { state -> 0xFFFF0000 }
		item { itemBuilder ->
			itemBuilder.with {
				maxStackSize(16)
				displayName('Coverage Block Item')
				getTranslationKeyGroup()
			}
		}
		box(0, 0, 0, 16, 15, 16)
		box(1, 1, 1, 15, 14, 15, false)
		noCollision()
		notSolid()
		waterlogged()
		drops(null)
		noDrops()
		slipperiness(0.7f)
		speedFactor(0.8f)
		jumpFactor(0.9f)
		randomTick { callback -> }
		noValidSpawns(true)
		suffocating(false)
		viewBlocking(false)
		redstoneConductor(false)
		transparent(true)
		defaultCutout()
		defaultTranslucent()
		instrument('harp')
		tag(['minecraft:mineable/pickaxe'])
		tagBoth(['minecraft:needs_iron_tool'])
		tagBlock(['minecraft:stone_ore_replaceables'])
		tagItem(['minecraft:stone_crafting_materials'])
		defaultState { state -> }
		placementState { context -> }
		canBeReplaced { context -> false }
		entityInside { context -> }
		steppedOn { context -> }
		fallenOn { context -> }
		bounciness(0.5f)
		afterFallenOn { context -> }
		exploded { context -> }
		property($BlockProperties.LIT) // using the binding
		rotateState { context -> }
		mirrorState { context -> }
		rightClick { context -> }
		blockEntity { infoBuilder ->
			infoBuilder.with {
				serverTicking()
			}
		}
	}

	// Exercise the null/no-item path separately so later tag calls remain valid.
	event.create('coverage_no_item').with {
		noItem()
	}

	event.create('coverage_detector', 'detector').with {
		detectorId('coverage_detector_id')
	}
	event.create('coverage_slab', 'slab').with {
		hardness(1)
	}
	event.create('coverage_stairs', 'stairs').with {
		hardness(1)
	}
	event.create('coverage_fence', 'fence').with {
		noCollision()
	}
	event.create('coverage_wall', 'wall').with {
		noCollision()
	}
	event.create('coverage_fence_gate', 'fence_gate').with {
		behaviour(WoodType.BIRCH)
		behaviour('birch')
	}
	event.create('coverage_pressure_plate', 'pressure_plate').with {
		behaviour('stone')
		ticksToStayPressed(15)
	}
	event.create('coverage_button', 'button').with {
		behaviour('stone')
		ticksToStayPressed(10)
	}
	event.create('coverage_falling', 'falling').with {
		dustColor(0xFFAA5500)
	}

	def cropBuilder = event.create('coverage_crop', 'crop')
	cropBuilder.with {
		item { itemBuilder ->
			itemBuilder.with {
				villagerCanPlant(false)
				villagerCanPlant(true)
				getTranslationKeyGroup()
			}
		}
		noSeeds()
		crop('minecraft:carrot')
		crop('minecraft:potato', 2)
		age(3)
		age(3) { shapesBuilder ->
			shapesBuilder.with {
				shape(0, 0, 0, 0, 16, 2, 16)
				wheat()
				carrot()
				beetroot()
				potato()
			}
		}
		farmersCanPlant()
		bonemeal { random -> 1 }
		survive { state, level, pos -> true }
		growTick { random -> 1.0 }
		randomTick(null)
	}
	event.create('coverage_crop_no_item', 'crop').with {
		noItem()
	}

	event.create('coverage_cardinal', 'cardinal').with {
		box(1, 0, 1, 15, 14, 15)
	}
	event.create('coverage_carpet', 'carpet').with {
		noCollision()
	}
	event.create('coverage_door', 'door').with {
		behaviour('oak')
		wooden()
	}
	event.create('coverage_trapdoor', 'trapdoor').with {
		behaviour('iron')
	}
	event.create('coverage_pillar', 'pillar').with {
		box(1, 0, 1, 15, 16, 15)
	}
}