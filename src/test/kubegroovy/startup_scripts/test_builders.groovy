import net.minecraft.world.level.block.Blocks

// Script-defined game test bodies. The generated `test_instance` is manual-only so the
// headless suite never schedules it; RegistryBuilderTests runs the bodies directly.
$StartupEvents.registry('test_function') { event ->
	event.create('script_test_place')
		.test { helper ->
			helper.setBlock(1, 1, 1, Blocks.STONE)
			helper.assertBlockPresent(Blocks.STONE, 1, 1, 1)
		}
		.instance { spec ->
			spec.structure('minecraft:empty').maxTicks(40).manualOnly(true)
		}
	event.create('script_test_throws')
		.test { helper -> throw new IllegalStateException('script test boom') }
}
