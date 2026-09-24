import net.minecraft.world.item.ItemStack

EntityEvents.spawned('minecraft:sheep') { event ->
	TestRuntime.check('coverage.core.entity') {
		def sheep = event.entity
		TestRuntime.assertThat(sheep.type).isEqualTo('minecraft:sheep')
		TestRuntime.assertThat(sheep.isAnimal()).isTrue()
		sheep.setX(sheep.x + 4.0d)
	}
}

ServerEvents.basicPublicCommand('coverage_inventory') { event ->
	TestRuntime.check('coverage.core.inventory') {
		def inventory = event.player.inventory
		event.player.addTag('kubegroovy_coverage_inventory_target')
		TestRuntime.pass('coverage.core.inventory.started')
		TestRuntime.assertThat(inventory.getSlots()).isGreaterThan(0d)
		TestRuntime.assertThat(inventory.isEmpty()).isTrue()

		def offered = '3x minecraft:diamond' as ItemStack
		def simulatedRemainder = inventory.insertItem(0, offered, true)
		TestRuntime.assertThat(simulatedRemainder.isEmpty()).isTrue()
		TestRuntime.assertThat(inventory.getStackInSlot(0).isEmpty()).isTrue()

		def remainder = inventory.insertItem(0, offered, false)
		TestRuntime.assertThat(remainder.isEmpty()).isTrue()
		TestRuntime.assertThat(inventory.find()).isEqualTo(0)
		TestRuntime.assertThat(inventory.count()).isEqualTo(3)
		TestRuntime.assertThat(inventory.countNonEmpty()).isEqualTo(1)
		TestRuntime.assertThat(inventory.getAllItems().size()).isEqualTo(1)

		def extracted = inventory.extractItem(0, 1, false)
		TestRuntime.assertThat(extracted.getCount()).isEqualTo(1)
		TestRuntime.assertThat(inventory.getStackInSlot(0).getCount()).isEqualTo(2)
	}
}
