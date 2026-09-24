// Event fixtures exercising TestRuntime's typed assertions.
$BlockEvents.broken { event ->
	$TestRuntime.check('block.broken.assert') {
		$TestRuntime.assertThat(event.block).hasId('minecraft:dirt')
		$TestRuntime.assertThat(event).hasPlayer()
	}
}

$BlockEvents.placed { event ->
	$TestRuntime.check('block.placed.assert') {
		$TestRuntime.assertThat(event.block).hasId('minecraft:oak_planks')
	}
}

$EntityEvents.death { event ->
	$TestRuntime.check('entity.death.assert') {
		$TestRuntime.assertThat(event).hasEntityType('minecraft:pig').hasNoPlayer()
		$TestRuntime.assertThat(event.source).isNotNull()
	}
}
