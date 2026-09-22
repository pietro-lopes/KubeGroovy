import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.WoodType

// Base block methods, callback setters, crop shape helpers, and subtype methods.
StartupEvents.registry('block') { event ->
	def base = event.create('coverage_block')
	base.translationKey('block.kubegroovy.coverage_block')
		.displayName('Coverage Block').formattedDisplayName('Coverage Formatted Block').formattedDisplayName()
		.texture('kubegroovy:block/coverage_block')
		.texture(['side', 'end'], 'kubegroovy:block/coverage_block_side')
		.textures([all: 'kubegroovy:block/coverage_block'])
		.parentModel('minecraft:block/cube_all')
		.modelGenerator { model -> model.parent('minecraft:block/cube_all') }
		.copyPropertiesFrom('minecraft:stone')
		.soundType('wood').noSoundType().woodSoundType().stoneSoundType().gravelSoundType()
		.grassSoundType().sandSoundType().cropSoundType().glassSoundType()
		.mapColor('color_red').dynamicMapColor { state -> 'color_blue' }
		.hardness(2).resistance(4).unbreakable().lightLevel(0.5f)
		.opaque(false).fullBlock(true).requiresTool(false).requiresTool()
		.renderType('solid').color(0) { state -> 0xFF00FF00 }.color { state -> 0xFFFF0000 }
		.item { item -> item.maxStackSize(16).displayName('Coverage Block Item').getTranslationKeyGroup() }
		.box(0, 0, 0, 16, 15, 16).box(1, 1, 1, 15, 14, 15, false)
		.noCollision().notSolid().waterlogged()
		.drops(null).noDrops()
		.slipperiness(0.7f).speedFactor(0.8f).jumpFactor(0.9f)
		.randomTick { callback -> }
		.noValidSpawns(true).suffocating(false).viewBlocking(false).redstoneConductor(false)
		.transparent(true).defaultCutout().defaultTranslucent().instrument('harp')
		.tag(['minecraft:mineable/pickaxe']).tagBoth(['minecraft:needs_iron_tool'])
		.tagBlock(['minecraft:stone_ore_replaceables']).tagItem(['minecraft:stone_crafting_materials'])
		.defaultState { state -> }
		.placementState { context -> }
		.canBeReplaced { context -> false }
		.entityInside { context -> }
		.steppedOn { context -> }
		.fallenOn { context -> }
		.bounciness(0.5f).afterFallenOn { context -> }
		.exploded { context -> }
		.property(BlockStateProperties.LIT)
		.rotateState { context -> }
		.mirrorState { context -> }
		.rightClick { context -> }
		.blockEntity { info -> info.serverTicking() }

	// Exercise the null/no-item path separately so later tag calls remain valid.
	event.create('coverage_no_item').noItem()

	event.create('coverage_detector', 'detector').detectorId('coverage_detector_id')
	event.create('coverage_slab', 'slab').hardness(1)
	event.create('coverage_stairs', 'stairs').hardness(1)
	event.create('coverage_fence', 'fence').noCollision()
	event.create('coverage_wall', 'wall').noCollision()
	event.create('coverage_fence_gate', 'fence_gate').behaviour(WoodType.BIRCH).behaviour('birch')
	event.create('coverage_pressure_plate', 'pressure_plate').behaviour('stone').ticksToStayPressed(15)
	event.create('coverage_button', 'button').behaviour('stone').ticksToStayPressed(10)
	event.create('coverage_falling', 'falling').dustColor(0xFFAA5500)

	def crop = event.create('coverage_crop', 'crop')
	crop.item { item -> item.villagerCanPlant(false).villagerCanPlant(true).getTranslationKeyGroup() }
	crop.noSeeds().crop('minecraft:carrot').crop('minecraft:potato', 2)
	crop.age(3).age(3) { shapes -> shapes.shape(0, 0, 0, 0, 16, 2, 16).wheat().carrot().beetroot().potato() }
	crop.farmersCanPlant()
		.bonemeal { random -> 1 }
		.survive { state, level, pos -> true }
		.growTick { random -> 1.0 }
		.randomTick(null)
	event.create('coverage_crop_no_item', 'crop').noItem()

	event.create('coverage_cardinal', 'cardinal').box(1, 0, 1, 15, 14, 15)
	event.create('coverage_carpet', 'carpet').noCollision()
	event.create('coverage_door', 'door').behaviour('oak').wooden()
	event.create('coverage_trapdoor', 'trapdoor').behaviour('iron')
	event.create('coverage_pillar', 'pillar').box(1, 0, 1, 15, 16, 15)
}
