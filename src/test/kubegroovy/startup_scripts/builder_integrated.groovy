StartupEvents.registry('block') { event ->
	event.create('builder_block' as dev.uncandango.kubegroovy.util.KubeIdentifier)
		.rightClick { TestRuntime.pass('builder.block.rightClicked') }
}

StartupEvents.registry('item') { event ->
	event.create('builder_food' as dev.uncandango.kubegroovy.util.KubeIdentifier)
		.food { food -> food.nutrition(6).saturation(0.6) }
}
