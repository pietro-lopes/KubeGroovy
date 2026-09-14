import net.minecraft.resources.Identifier

ServerEvents.tags('item') { event ->
	TestRuntime.check('tags.item.wiki') {
		event.add('kubegroovy:wiki_tag' as Identifier, 'minecraft:diamond' as Identifier)
		event.add('kubegroovy:wiki_tag' as Identifier, 'minecraft:emerald' as Identifier)
		event.remove('kubegroovy:wiki_tag' as Identifier, 'minecraft:emerald' as Identifier)
		event.add('kubegroovy:wiki_parent' as Identifier, '#kubegroovy:wiki_tag')
	}
}

ServerEvents.tags('block') { event ->
	TestRuntime.check('tags.block.wiki') {
		event.add('minecraft:climbable' as Identifier, 'minecraft:cobblestone' as Identifier)
	}
}
