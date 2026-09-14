BlockEvents.broken { event -> if (event.block.id == 'minecraft:dirt') TestRuntime.pass('block.break.dirt') }
BlockEvents.drops { event -> if (event.block.id == 'minecraft:dirt') TestRuntime.pass('block.drops.dirt') }
BlockEvents.placed { TestRuntime.pass('block.placed') }
BlockEvents.rightClicked { TestRuntime.pass('block.rightClicked') }
BlockEvents.leftClicked { TestRuntime.pass('block.leftClicked') }
BlockEvents.startedFalling { TestRuntime.pass('block.startedFalling') }
BlockEvents.stoppedFalling { TestRuntime.pass('block.stoppedFalling') }
BlockEvents.farmlandTrampled { TestRuntime.pass('block.farmlandTrampled') }
BlockEvents.randomTick('minecraft:dirt') { TestRuntime.pass('block.randomTick.dirt') }
BlockEvents.detectorChanged('test') { TestRuntime.pass('block.detector.changed') }
BlockEvents.detectorPowered('test') { TestRuntime.pass('block.detector.powered') }
BlockEvents.detectorUnpowered('test') { TestRuntime.pass('block.detector.unpowered') }
BlockEvents.blockEntityTick('kubegroovy:test_ticker') { TestRuntime.pass('block.blockEntityTick') }
BlockEvents.picked('minecraft:stone') { TestRuntime.pass('block.picked') }
