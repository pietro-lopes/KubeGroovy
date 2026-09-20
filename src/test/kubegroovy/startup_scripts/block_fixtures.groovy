
StartupEvents.registry('block') { event ->
	event.create('test_detector', 'detector').detectorId('test')
	event.create('test_ticker').blockEntity { be -> be.serverTicking() }
}

BlockEvents.modification { TestRuntime.passStartup('block.modification') }
