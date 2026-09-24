$ItemEvents.dropped { event ->
	$TestRuntime.check('item.dropped') { $TestRuntime.assertThat(event.item.id).isEqualTo('minecraft:diamond') }
}
$ItemEvents.rightClicked { $TestRuntime.pass('item.rightClicked') }
$ItemEvents.entityInteracted { $TestRuntime.pass('item.entityInteracted') }
$ItemEvents.canPickUp { event ->
	$TestRuntime.check('item.canPickUp') { $TestRuntime.assertThat(event.item.id).isEqualTo('minecraft:diamond') }
}
$ItemEvents.pickedUp { $TestRuntime.pass('item.pickedUp') }
