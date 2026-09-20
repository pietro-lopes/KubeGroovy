ItemEvents.modification { event ->
	event.modify('minecraft:ender_pearl') { item ->
		item.maxStackSize = 64
		item.rarity = 'UNCOMMON'
	}
}

BlockEvents.modification { event ->
	event.modify('minecraft:stone') { block ->
		block.destroySpeed = 0.1
	}
	TestRuntime.passStartup('modification.block.wiki')
}
