import net.minecraft.resources.Identifier

ServerEvents.tags('item') { event ->
	TestRuntime.check('tags.item.wiki') {
		event.add('kubegroovy:wiki_tag', 'minecraft:diamond')
		event.add('kubegroovy:wiki_tag', 'minecraft:emerald')
		event.remove('kubegroovy:wiki_tag', 'minecraft:emerald')
		event.add('kubegroovy:wiki_parent', '#kubegroovy:wiki_tag')
	}
}

ServerEvents.tags('block') { event ->
	TestRuntime.check('tags.block.wiki') {
		event.add('minecraft:climbable', 'minecraft:cobblestone')
	}
}
