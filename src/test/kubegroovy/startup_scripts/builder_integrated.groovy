$StartupEvents.registry('block') { event ->
	event.create('builder_block').with {
		rightClick { $TestRuntime.pass('builder.block.rightClicked') }
	}
}

$StartupEvents.registry('item') { event ->
	event.create('builder_food').with {
		food { foodBuilder ->
			foodBuilder.with {
				nutrition(6)
				saturation(0.6)
			}
		}
	}
}