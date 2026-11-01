import net.minecraft.world.level.block.Rotation

$ServerEvents.tick { $TestRuntime.pass('server.tick') }
$ServerEvents.command('help') { event ->
	$TestRuntime.check('server.command') { $TestRuntime.assertThat(event.commandName).isEqualTo('help') }
}

// Painting variants and jukebox songs are datapack-backed registries. Keep their
// canonical examples in wiki_builders.groovy and register the same objects during
// the server registry pass so the generated registry data is available to tests.
$ServerEvents.registry('painting_variant') { event ->
	event.create('wiki_painting')
		.size(2, 3).assetId('minecraft:painting')
		.title('Wiki Painting').author('KubeGroovy')
	event.create('coverage_painting')
		.size(2, 2).assetId('minecraft:painting')
		.title('Coverage Painting').author('KubeGroovy')
}

$ServerEvents.registry('jukebox_song') { event ->
	event.create('wiki_song')
		.song('minecraft:music_disc.13', 120)
		.description('Wiki Song').comparatorOutput(7)
	event.create('coverage_song')
		.song('minecraft:music_disc.13', 120)
		.description('Coverage Song').comparatorOutput(7)
}

// Game test instances for the script test functions in test_builders.groovy. All are
// manual-only: the headless server runs every non-manual test instance it finds.
$ServerEvents.registry('test_instance') { event ->
	event.create('script_test_place_rotated')
		.function('kubegroovy:script_test_place')
		.structure('minecraft:empty').maxTicks(30).setupTicks(2)
		.rotation(Rotation.CLOCKWISE_90).environment('minecraft:default')
		.required(false).manualOnly(true).maxAttempts(3).requiredSuccesses(2)
		.skyAccess(true).padding(4)
	event.create('script_test_block_based', 'block_based')
		.structure('minecraft:empty').maxTicks(10).manualOnly(true)
}
