import dev.uncandango.kubegroovy.util.KubeIdentifier

StartupEvents.registry('block') { event ->
	event.create('test_detector' as KubeIdentifier, 'detector' as KubeIdentifier).detectorId('test')
	event.create('test_ticker' as KubeIdentifier).blockEntity { be -> be.serverTicking() }
}

BlockEvents.modification { TestRuntime.passStartup('block.modification') }
