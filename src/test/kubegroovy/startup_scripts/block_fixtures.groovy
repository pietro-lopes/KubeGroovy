import net.minecraft.core.Registry
import net.minecraft.world.level.block.Block

$StartupEvents.registry('block') { event ->
	event.create('test_detector', 'detector').with {
		detectorId('test')
	}
	event.create('test_ticker').with {
		blockEntity { beBuilder ->
			beBuilder.with {
				serverTicking()
			}
		}
	}
}

$BlockEvents.modification { $TestRuntime.passStartup('block.modification') }