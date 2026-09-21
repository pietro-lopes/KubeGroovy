ServerEvents.tick { TestRuntime.pass('server.tick') }
ServerEvents.command('help') { event ->
	TestRuntime.check('server.command') { TestRuntime.assertThat(event.commandName).isEqualTo('help') }
}

// Painting variants and jukebox songs are datapack-backed registries. Keep their
// canonical examples in wiki_builders.groovy and register the same objects during
// the server registry pass so the generated registry data is available to tests.
ServerEvents.registry('painting_variant') { event ->
	event.create('wiki_painting')
		.size(2, 3).assetId('minecraft:painting')
		.title('Wiki Painting').author('KubeGroovy')
}

ServerEvents.registry('jukebox_song') { event ->
	event.create('wiki_song')
		.song('minecraft:music_disc.13', 120)
		.description('Wiki Song').comparatorOutput(7)
}
