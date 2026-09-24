ServerEvents.basicPublicCommand('coverage_public_echo') { event ->
	TestRuntime.check('coverage.command.public') {
		TestRuntime.assertThat(event.input).isEqualTo('trimmed input')
		TestRuntime.assertThat(event.player).isNotNull()
		event.respond(Text.string("echo:${event.input}"))
	}
}
