
$StartupEvents.registry('item') { event ->
	event.create('wiki_item')
	event.create('wiki_item_glow').with {
		maxStackSize(16)
		glow(true)
	}
	event.create('wiki_sword', 'sword').with {
		attackDamageBaseline(4)
		speedBaseline(-2.2)
		disableBlockingForSeconds(1)
	}
	event.create('wiki_pickaxe', 'pickaxe')
	event.create('wiki_axe', 'axe')
	event.create('wiki_shovel', 'shovel')
	event.create('wiki_hoe', 'hoe')
	event.create('wiki_shears', 'shears').with {
		speedBaseline(6)
	}
	event.create('wiki_helmet', 'helmet').with {
		material('diamond')
	}
	event.create('wiki_chestplate', 'chestplate').with {
		material('iron')
	}
	event.create('wiki_leggings', 'leggings').with {
		material('chainmail')
	}
	event.create('wiki_boots', 'boots').with {
		material('gold')
	}
	event.create('wiki_animal_armor', 'animal_armor').with {
		kind('horse')
		material('diamond')
	}
	event.create('wiki_smithing_template', 'smithing_template').with {
		appliesTo('Armor')
		ingredients('Ingots & Crystals')
		appliesToSlotDescription('Add a piece of armor')
		ingredientsSlotDescription('Add ingot or crystal')
		armorIcons()
		ingotAndCrystalIcons()
		swordIcon()
	}
}

$StartupEvents.registry('block') { event ->
	event.create('wiki_block').with {
		displayName('My Custom Block')
		soundType('wool')
		hardness(1)
		resistance(1)
		tagBlock(['minecraft:mineable/pickaxe'])
		requiresTool(true)
	}
	event.create('wiki_detector', 'detector').with {
		detectorId('wiki')
	}
	event.create('wiki_slab', 'slab')
	event.create('wiki_stairs', 'stairs').with {
		hardness(2)
	}
	event.create('wiki_fence', 'fence')
	event.create('wiki_wall', 'wall')
	event.create('wiki_fence_gate', 'fence_gate').with {
		behaviour('birch')
	}
	event.create('wiki_pressure_plate', 'pressure_plate').with {
		behaviour('stone')
		ticksToStayPressed(15)
	}
	event.create('wiki_button', 'button').with {
		behaviour('stone')
		ticksToStayPressed(10)
	}
	event.create('wiki_falling', 'falling').with {
		dustColor(0xFFAA5500)
	}
	event.create('wiki_crop', 'crop').with {
		age(3) { shapesBuilder ->
			shapesBuilder.with {
				beetroot()
			}
		}
		farmersCanPlant()
	}
	event.create('wiki_cardinal', 'cardinal').with {
		box(1, 0, 1, 15, 14, 15)
	}
	event.create('wiki_carpet', 'carpet')
	event.create('wiki_door', 'door').with {
		wooden()
	}
	event.create('wiki_trapdoor', 'trapdoor').with {
		behaviour('iron')
	}
	event.create('wiki_pillar', 'pillar').with {
		box(1, 0, 1, 15, 16, 15)
	}
}

$StartupEvents.registry('fluid') { event ->
	event.create('wiki_fluid').with {
		displayName('Wiki Fluid')
		tint(0x3366FF)
		translucent()
		slopeFindDistance(3)
		levelDecreasePerBlock(2)
		explosionResistance(2)
		tickRate(10)
	}
	event.create('wiki_thin_fluid', 'thin').with {
		noBucket()
	}
	event.create('wiki_thick_fluid', 'thick').with {
		noBlock()
	}
}

$StartupEvents.registry('neoforge:fluid_type') { event ->
	event.create('wiki_fluid_type').with {
		descriptionId('fluid.kubegroovy.wiki_fluid_type')
		density(1200)
		viscosity(1000)
		temperature(300)
		canSwim(false)
		canDrown(false)
	}
}

$StartupEvents.registry('creative_mode_tab') { event ->
	def carrotIcon = { 'minecraft:carrot' }
	def tabContents = { showRestrictedItems ->
		["minecraft:dirt", "minecraft:grass_block"]
	}
	event.create('wiki_tab').with {
		icon(carrotIcon)
		content(tabContents)
	}
}

$StartupEvents.registry('mob_effect') { event ->
	event.create('wiki_effect').with {
		color(0x00FF00)
		beneficial()
	}
}

$StartupEvents.registry('sound_event') { event ->
	event.create('wiki_sound')
}

$StartupEvents.registry('potion') { event ->
	event.create('wiki_potion').with {
		effect('minecraft:luck', 200, 1)
	}
}

$StartupEvents.registry('particle_type') { event ->
	event.create('wiki_particle').with {
		overrideLimiter(true)
		texture('minecraft:generic')
	}
}

$StartupEvents.registry('custom_stat') { event ->
	event.create('wiki_stat')
}

$StartupEvents.registry('point_of_interest_type') { event ->
	event.create('wiki_poi').with {
		block('kubegroovy:wiki_block')
		maxTickets(2)
		validRange(4)
	}
}

$StartupEvents.registry('villager_type') { event ->
	event.create('wiki_villager_type')
}

$StartupEvents.registry('villager_profession') { event ->
	event.create('wiki_profession').with {
		poiType('kubegroovy:wiki_poi')
		requestedItems(['minecraft:emerald'])
		secondaryPoi(['minecraft:composter'])
		workSound('minecraft:block.bell.use')
	}
}

$StartupEvents.registry('painting_variant') { event ->
	event.create('wiki_painting').with {
		size(2, 3)
		assetId('minecraft:painting')
		title('Wiki Painting')
		author('KubeGroovy')
	}
}

$StartupEvents.registry('jukebox_song') { event ->
	event.create('wiki_song').with {
		song('minecraft:music_disc.13', 120)
		description('Wiki Song')
		comparatorOutput(7)
	}
}

$StartupEvents.registry('attribute') { event ->
	event.create('wiki_range_attribute').with {
		range(4.0, 0.0, 10.0)
		syncable(false)
		neutralSentiment()
		attachToPlayers()
	}
	event.create('wiki_boolean_attribute').with {
		bool(true)
		negativeSentiment()
		attachToMonsters()
	}
}