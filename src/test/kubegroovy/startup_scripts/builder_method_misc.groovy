import dev.uncandango.kubegroovy.fluid.FluidBuilder
import dev.uncandango.kubegroovy.util.KubeIdentifier
import net.minecraft.core.Holder
import net.minecraft.core.particles.ParticleType
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.particles.SimpleParticleType
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.common.SoundActions

// Fluids, fluid types, sounds, effects, potions, particles, POIs and remaining registries.
$StartupEvents.registry('fluid') { event ->
	def fluid = event.create('coverage_fluid')
	fluid.translationKey('fluid.kubegroovy.coverage_fluid').displayName('Coverage Fluid')
		.tag(['minecraft:water']).tint(0x3366FF)
		.tintFunction { state, level, pos, index -> 0xFF3366FF }
		.bucketColor(0xFF2244AA)
		.stillTexture('kubegroovy:block/coverage_fluid_still')
		.flowingTexture('kubegroovy:block/coverage_fluid_flow')
		.renderType('cutout').translucent()
		.slopeFindDistance(3).levelDecreasePerBlock(2).explosionResistance(2).tickRate(10)
		.type { type ->
			type.stillTexture('kubegroovy:block/coverage_type_still')
				.flowingTexture('kubegroovy:block/coverage_type_flow')
				.actualStillTexture('kubegroovy:block/generated/coverage_still')
				.actualFlowingTexture('kubegroovy:block/generated/coverage_flow')
				.screenOverlayTexture('kubegroovy:misc/coverage_screen')
				.blockOverlayTexture('kubegroovy:misc/coverage_block')
				.textureTint(0xFF00AAFF).tint(0xFF22AA44)
				.tintFunction { state, level, pos, index -> 0xFF22AA44 }
				.descriptionId('fluid.kubegroovy.coverage_fluid')
				.motionScale(0.01).canPushEntity(false).canSwim(false).canDrown(false)
				.fallDistanceModifier(0.5f).canExtinguish(true).canConvertToSource(false)
				.supportsBoating(true).pathType(null).adjacentPathType(null)
				.sound("bucket_fill", "item.bucket.fill")
				.canHydrate(true).lightLevel(2).density(1200).temperature(300).viscosity(1000)
				.rarity('rare')
				.addDripstoneDripping(0.25f, "dripping_water", "cauldron", "item.bucket.fill")
				.renderType('translucent')
		}
	event.create('icon_test_tinted_fluid')
		.stillTexture('minecraft:block/white_concrete')
		.tint(0xFF2244AA)
	event.create('coverage_no_bucket', 'thin').noBucket()
	event.create('coverage_no_block', 'thick').noBlock()
}

$StartupEvents.registry('neoforge:fluid_type') { event ->
	event.create('coverage_standalone_fluid_type')
		.descriptionId('fluid.kubegroovy.coverage_standalone')
		.actualStillTexture('kubegroovy:block/generated/standalone_still')
		.actualFlowingTexture('kubegroovy:block/generated/standalone_flow')
		.textureTint(0xFFAA00AA)
		.canPushEntity(true).canSwim(true).canDrown(true).fallDistanceModifier(1.0f)
		.canExtinguish(false).canConvertToSource(true).supportsBoating(false)
		.pathType(null).adjacentPathType(null).canHydrate(false)
		.lightLevel(0).density(1000).temperature(295).viscosity(800).rarity('common')
		.renderType('solid')
}

$StartupEvents.registry('sound_event') { event ->
	event.create('coverage_sound').sounds { sound ->
		sound.replace(false).replace().subtitle('sound.kubegroovy.coverage')
			.sound('kubegroovy:coverage').sounds('kubegroovy:coverage_a', 'kubegroovy:coverage_b')
			.sound('kubegroovy:coverage_complex') { instance ->
				instance.volume(0.5f).pitch(1.0f).weight(2).stream().stream(false)
					.attenuationDistance(32).preload().preload(false).asReferenceToEvent()
			}
	}
}

$StartupEvents.registry('mob_effect') { event ->
	def effect = event.create('coverage_effect')
	effect.getTranslationKeyGroup()
	effect
		.modifyAttribute('minecraft:generic.movement_speed', 'kubegroovy:coverage_speed', 0.1, "add_value")
		.category('harmful').harmful().beneficial()
		.effectTick { entity, level -> }
		.color(0x00FF00).instant(false).instant()
}

$StartupEvents.registry('potion') { event ->
	def potion = event.create('coverage_potion')
	def luck = "luck" as Holder<MobEffect>
	def instance = new MobEffectInstance("luck", 20)
	potion.addEffect(instance)
		.effect(luck).effect(luck, 20).effect(luck, 20, 1)
		.effect(luck, 20, 1, true, false)
		.effect(luck, 20, 1, true, false, true)
		.effect(luck, 20, 1, true, true, true, null)
}

$StartupEvents.registry('particle_type') { event ->
	def angryVillager = ("angry_villager" as Holder<ParticleType<?>>).value()
	event.create('coverage_particle')
		.overrideLimiter(true)
		.codec(angryVillager.codec())
		.streamCodec(angryVillager.streamCodec())
		.textures(['kubegroovy:coverage_a', 'kubegroovy:coverage_b'])
		.texture('kubegroovy:coverage_particle')
}

$StartupEvents.registry('custom_stat') { event -> event.create('coverage_stat') }

$StartupEvents.registry('point_of_interest_type') { event ->
	def poi = event.create('coverage_poi')
	poi.blocks(["stone"])
		.block('minecraft:stone').maxTickets(2).validRange(4)
}

$StartupEvents.registry('villager_type') { event -> event.create('coverage_villager_type') }

$StartupEvents.registry('villager_profession') { event ->
	event.create('coverage_profession')
		.poiType('kubegroovy:coverage_poi').poiTypeTag('minecraft:acquirable_job_site')
		.requestedItems(['minecraft:emerald']).secondaryPoi(['minecraft:composter'])
		.workSound('minecraft:block.bell.use')
}

$StartupEvents.registry('creative_mode_tab') { event ->
	event.create('coverage_tab').displayName('Coverage Tab')
		.icon { 'minecraft:carrot' }
		.content { showRestrictedItems -> ['minecraft:dirt', 'minecraft:stone'] }
}

$StartupEvents.registry('jukebox_song') { event ->
	event.create('coverage_song').song('minecraft:music_disc.13', 120)
		.description('Coverage Song').comparatorOutput(7)
}

$StartupEvents.registry('attribute') { event ->
	event.create('coverage_range_attribute' as KubeIdentifier).range(4.0, 0.0, 10.0).syncable(false)
		.sentiment('positive').negativeSentiment().neutralSentiment()
		.attachTo { entityType -> true }.attachToPlayers().attachToMonsters().attachToCategory('monster')
	event.create('coverage_boolean_attribute').bool(true)
}
