import net.minecraft.resources.Identifier

$ServerEvents.tags('item') { event ->
	$TestRuntime.check('tags.item.wiki') {
		event.add('kubegroovy:wiki_tag', 'minecraft:diamond')
		event.add('kubegroovy:wiki_tag', 'minecraft:emerald')
		event.remove('kubegroovy:wiki_tag', 'minecraft:emerald')
		event.add('kubegroovy:wiki_parent', '#kubegroovy:wiki_tag')
	}

	event.add('kubegroovy:icon_export/item/nested_child', 'minecraft:stone')
	event.add('kubegroovy:icon_export/item/nested_parent', '#kubegroovy:icon_export/item/nested_child')
	event.add('kubegroovy:icon_export/item/nested_parent', 'minecraft:apple')
	event.add('kubegroovy:icon_export/item/fallback', 'minecraft:diamond')
	event.add('kubegroovy:icon_export/item/fallback', 'minecraft:stone')
	event.add('kubegroovy:icon_export/item/empty', 'minecraft:stone')
	event.remove('kubegroovy:icon_export/item/empty', 'minecraft:stone')
}

$ServerEvents.tags('block') { event ->
	$TestRuntime.check('tags.block.wiki') {
		event.add('minecraft:climbable', 'minecraft:cobblestone')
	}

	event.add('kubegroovy:icon_export/block/fallback', 'minecraft:air')
	event.add('kubegroovy:icon_export/block/fallback', 'minecraft:coal_ore')
}

$ServerEvents.tags('fluid') { event ->
	event.add('kubegroovy:icon_export/fluid/fixture', 'minecraft:water')
	event.add('kubegroovy:icon_export/fluid/fixture', 'minecraft:lava')
}
