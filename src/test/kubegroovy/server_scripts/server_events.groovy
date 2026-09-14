ServerEvents.tick { TestRuntime.pass('server.tick') }
ServerEvents.command('help') { event ->
	TestRuntime.check('server.command') { TestRuntime.assertThat(event.commandName).isEqualTo('help') }
}
