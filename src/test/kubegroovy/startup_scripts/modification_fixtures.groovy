import dev.uncandango.kubegroovy.block.state.BlockStatePredicate
import dev.uncandango.kubegroovy.util.registrypredicate.RegistryPredicate

ItemEvents.modification { event ->
	event.modify('minecraft:ender_pearl' as RegistryPredicate) { item ->
		item.maxStackSize = 64
		item.rarity = 'UNCOMMON'
	}
}

BlockEvents.modification { event ->
	event.modify('minecraft:stone' as BlockStatePredicate) { block ->
		block.destroySpeed = 0.1
	}
	TestRuntime.passStartup('modification.block.wiki')
}
