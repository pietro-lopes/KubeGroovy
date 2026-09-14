import net.minecraft.world.item.ItemStack

EntityEvents.spawned('minecraft:zombie') { event ->
	event.entity.setMainHandItem('minecraft:iron_sword' as ItemStack)
	TestRuntime.pass('entity.behavior.zombie')
}

EntityEvents.spawned('minecraft:cow') { event ->
	def cow = event.entity
	cow.setPosition(cow.x, cow.y, cow.z + 5)
	TestRuntime.pass('entity.behavior.cow')
}
