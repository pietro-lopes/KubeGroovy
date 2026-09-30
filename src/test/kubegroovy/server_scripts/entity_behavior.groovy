import net.minecraft.world.entity.LivingEntity

$EntityEvents.spawned('minecraft:zombie') { event ->
	if (event.entity instanceof LivingEntity le) {
		le.setMainHandItem('minecraft:iron_sword')
	}
	$TestRuntime.pass('entity.behavior.zombie')
}

$EntityEvents.spawned('minecraft:cow') { event ->
	def cow = event.entity
	cow.setPosition(cow.x, cow.y, cow.z + 5)
	$TestRuntime.pass('entity.behavior.cow')
}
