$StartupEvents.registry('block') { event ->
	event.create('builder_block')
		.rightClick { $TestRuntime.pass('builder.block.rightClicked') }
}

$StartupEvents.registry('item') { event ->
	event.create('builder_food')
		.food { food -> food.nutrition(6).saturation(0.6) }
}
