import net.minecraft.world.item.ItemStack

StartupEvents.registry('item') { event ->
	event.create('wiki_item')
	event.create('wiki_item_glow')
			.maxStackSize(16).glow(true)
}

StartupEvents.registry('block') { event ->
	event.create('wiki_block')
		.displayName('My Custom Block')
		.soundType('wool')
		.hardness(1)
		.resistance(1)
		.tagBlock(['minecraft:mineable/pickaxe'])
		.requiresTool(true)
}

StartupEvents.registry('creative_mode_tab') { event ->
	event.create('wiki_tab')
		.icon { "minecraft:carrot" } // "minecraft:carrot" as ItemStack
		.content { showRestrictedItems ->
			["minecraft:dirt", "minecraft:grass_block"]
		}
}

StartupEvents.registry('mob_effect') { event ->
	event.create('wiki_effect').color(0x00FF00).beneficial()
}
