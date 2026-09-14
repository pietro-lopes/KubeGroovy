LevelEvents.tick { TestRuntime.pass('level.tick') }
LevelEvents.beforeExplosion { event ->
	TestRuntime.check('level.beforeExplosion') { TestRuntime.assertThat(event.size).isGreaterThan(0.0d) }
}
LevelEvents.afterExplosion { event ->
	TestRuntime.check('level.afterExplosion') { TestRuntime.assertThat(event.affectedBlocks).isNotEmpty() }
}
