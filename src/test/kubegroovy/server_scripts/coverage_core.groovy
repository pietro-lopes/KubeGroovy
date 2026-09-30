$ServerEvents.basicPublicCommand('coverage_core_mutations') { event ->
	$TestRuntime.check('coverage.core.mutations') {
		def player = event.player
		$TestRuntime.assertThat(player.isPlayer()).isTrue()
		$TestRuntime.assertThat(player.isLiving()).isTrue()
		$TestRuntime.assertThat(player.isServerPlayer()).isTrue()
		player.setSelectedSlot(99)
		$TestRuntime.assertThat(player.getSelectedSlot()).isEqualTo(8)
		player.setSelectedSlot(-1)
		$TestRuntime.assertThat(player.getSelectedSlot()).isEqualTo(0)
		player.setSelectedSlot(2)
		player.setFoodLevel(12)
		player.setSaturation(3.0f)
		player.setXp(10)
		player.addXP(7)
		player.setMaxHealth(30.0f)
		player.setDefaultMovementSpeed(0.2d)
		player.setMovementSpeedAddition(0.1d)
		$TestRuntime.assertThat(player.getTotalMovementSpeed()).isBetween(0.299d, 0.301d)
		// Each setter replaces the same modifier instead of stacking modifiers.
		player.setDefaultMovementSpeedMultiplier(0.5d)
		$TestRuntime.assertThat(player.getTotalMovementSpeed()).isBetween(0.299d, 0.301d)
		player.setTotalMovementSpeedMultiplier(1.0d)
		player.setRotation(90.0f, -60.0f)
		$TestRuntime.assertThat(player.getFacing().toString()).isEqualTo('up')
		player.setMotionX(0.25d)
		player.setMotionY(0.5d)
		player.setMotionZ(-0.25d)
		player.getForgePersistentData().putInt('coverage_core_mutations', 7)

		def original = $Item.of('minecraft:diamond')
		def named = original.withCount(3).withCustomName($Text.string('Core coverage diamond'))
		$TestRuntime.assertThat(original.getCount()).isEqualTo(1)
		$TestRuntime.assertThat(original.getHoverName().getString()).isNotEqualTo('Core coverage diamond')
		$TestRuntime.assertThat(original.withCount(0).isEmpty()).isTrue()
		player.setMainHandItem(named)
		def offhand = $Item.of('minecraft:stick').withCustomName($Text.string('Temporary name'))
		offhand.resetComponents()
		player.setOffHandItem(offhand)
		player.setHeadArmorItem($Item.of('minecraft:iron_helmet'))
		player.setMouseItem($Item.of('2x minecraft:emerald'))
	}
}

$EntityEvents.spawned('minecraft:sheep') { event ->
	$TestRuntime.check('coverage.core.entity') {
		def sheep = event.entity
		$TestRuntime.assertThat(sheep.type).isEqualTo('minecraft:sheep')
		$TestRuntime.assertThat(sheep.isAnimal()).isTrue()
		sheep.setX(sheep.x + 4.0d)
	}
}

$ServerEvents.basicPublicCommand('coverage_inventory') { event ->
	$TestRuntime.check('coverage.core.inventory') {
		def inventory = event.player.inventory
		event.player.addTag('kubegroovy_coverage_inventory_target')
		$TestRuntime.pass('coverage.core.inventory.started')
		$TestRuntime.assertThat(inventory.getSlots()).isGreaterThan(0)
		$TestRuntime.assertThat(inventory.isEmpty()).isTrue()

		def offered = '3x minecraft:diamond'
		def simulatedRemainder = inventory.insertItem(0, offered, true)
		$TestRuntime.assertThat(simulatedRemainder.isEmpty()).isTrue()
		$TestRuntime.assertThat(inventory.getStackInSlot(0).isEmpty()).isTrue()

		def remainder = inventory.insertItem(0, offered, false)
		$TestRuntime.assertThat(remainder.isEmpty()).isTrue()
		$TestRuntime.assertThat(inventory.find()).isEqualTo(0)
		$TestRuntime.assertThat(inventory.count()).isEqualTo(3)
		$TestRuntime.assertThat(inventory.countNonEmpty()).isEqualTo(1)
		$TestRuntime.assertThat(inventory.getAllItems().size()).isEqualTo(1)

		def extracted = inventory.extractItem(0, 1, false)
		$TestRuntime.assertThat(extracted.getCount()).isEqualTo(1)
		$TestRuntime.assertThat(inventory.getStackInSlot(0).getCount()).isEqualTo(2)
	}
}
