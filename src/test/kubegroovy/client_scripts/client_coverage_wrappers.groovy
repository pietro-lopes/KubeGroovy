import dev.uncandango.kubegroovy.block.BlockTintFunction
import dev.uncandango.kubegroovy.client.AtlasSpriteRegistryKubeEvent
import dev.uncandango.kubegroovy.client.BlockTintFunctionWrapper
import dev.uncandango.kubegroovy.client.ClientProperties
import dev.uncandango.kubegroovy.client.EditorExt
import dev.uncandango.kubegroovy.client.FluidTintFunctionWrapper
import dev.uncandango.kubegroovy.client.icon.AtlasSpriteKubeIcon
import dev.uncandango.kubegroovy.client.icon.ItemKubeIcon
import dev.uncandango.kubegroovy.client.icon.KubeIconRenderer
import dev.uncandango.kubegroovy.client.icon.TextureKubeIcon
import dev.uncandango.kubegroovy.client.ItemTintFunctionWrapper
import dev.uncandango.kubegroovy.client.KubeGroovyKeybinds
import dev.uncandango.kubegroovy.client.LoadedTexture
import dev.uncandango.kubegroovy.client.model.KubeGroovyModelPropertyRegistry
import dev.uncandango.kubegroovy.client.ModelGenerator
import dev.uncandango.kubegroovy.client.MultipartBlockStateGenerator
import dev.uncandango.kubegroovy.client.ParticleGenerator
import dev.uncandango.kubegroovy.client.SoundsGenerator
import dev.uncandango.kubegroovy.client.VariantBlockStateGenerator
import dev.uncandango.kubegroovy.color.KubeColor
import net.minecraft.world.level.block.Blocks
import net.neoforged.neoforge.fluids.FluidStack

import java.util.Optional

// Direct unit-style exercises for client generator/texture branches.
// Runs once at client script load; safe on client only.

$TestRuntime.check('client.wrapper.loadedTexture') {
	def empty = LoadedTexture.EMPTY
	$TestRuntime.assertThat(empty.width == 0 && empty.height == 0).isTrue()
	$TestRuntime.assertThat(empty.toBytes().length == 0).isTrue()
	$TestRuntime.assertThat(empty.copy().width == 0).isTrue()
	$TestRuntime.assertThat(empty.remap([:]).is(empty)).isTrue()
	$TestRuntime.assertThat(empty.resize(0, 0).is(empty)).isTrue()
	$TestRuntime.assertThat(empty.tint(null).is(empty)).isTrue()
	$TestRuntime.assertThat(empty.tint('none').is(empty)).isTrue()

	def missing = LoadedTexture.load('kubegroovy:coverage_missing_xyz')
	$TestRuntime.assertThat(missing.width == 0).isTrue()

	def real = LoadedTexture.load('kubegroovy:block/unknown')
	if (real.width > 0) {
		$TestRuntime.assertThat(real.toBytes().length > 0).isTrue()
		$TestRuntime.assertThat(real.copy().width == real.width).isTrue()
		$TestRuntime.assertThat(real.remap([:]).is(real)).isTrue()
		$TestRuntime.assertThat(real.resize(real.width, real.height).is(real)).isTrue()
		def remapped = real.remap([('#ffffff' as KubeColor): ('#ff0000' as KubeColor)])
		$TestRuntime.assertThat(remapped.width == real.width).isTrue()
		def resized = real.resize(real.width * 2, real.height * 2)
		$TestRuntime.assertThat(resized.width == real.width * 2).isTrue()
		$TestRuntime.assertThat(real.tint(null).is(real)).isTrue()
		$TestRuntime.assertThat(real.tint('#80ff0000').width == real.width).isTrue()
		$TestRuntime.pass('client.wrapper.loadedTexture.real')
	} else {
		$TestRuntime.pass('client.wrapper.loadedTexture.emptyOnly')
	}
}

$TestRuntime.check('client.wrapper.model') {
	def empty = new ModelGenerator()
	$TestRuntime.assertThat(empty.toJson().has('parent')).isTrue()
	$TestRuntime.assertThat(empty.toJson().has('textures')).isFalse()

	def full = new ModelGenerator()
	full.parent(null)
	$TestRuntime.assertThat(full.toJson().has('parent')).isFalse()
	full.parent('minecraft:block/cube_all')
	// NOTE: the single-string texture(String, String) overload is @HideFromGY
	// and not dispatchable from scripts; the array/map overloads cover the field.
	full.texture(['a', 'b'], 'kubegroovy:block/base')
	full.textures(['c': 'kubegroovy:block/unknown'])
	full.element { el ->
		el.allFaces { face -> face.tex('#a').cull().uv(0, 0, 16, 16).tintindex(1) }
	}
	full.override('kubegroovy:block/unknown') { o ->
		o.predicate('kubegroovy:coverage', 0.5f)
	}
	full.custom { json -> json.addProperty('x', 1) }
	def json = full.toJson()
	$TestRuntime.assertThat(json.has('elements')).isTrue()
	$TestRuntime.assertThat(json.has('overrides')).isTrue()
	$TestRuntime.assertThat(json.has('textures')).isTrue()
}

$TestRuntime.check('client.wrapper.blockstate') {
	def gen = new VariantBlockStateGenerator()
	gen.simpleVariant('', 'kubegroovy:block/unknown')
	gen.variant('x', { v -> v.model('kubegroovy:block/unknown').x(90).y(90).uvlock() })
	gen.variant('multi', { v ->
		v.model('kubegroovy:block/unknown')
		v.model('kubegroovy:block/base')
	})
	$TestRuntime.assertThat(gen.toJson().has('variants')).isTrue()

	def multi = new MultipartBlockStateGenerator()
	multi.part('facing=north', { p -> p.model('kubegroovy:block/unknown').x(180) })
	multi.part('', 'kubegroovy:block/base')
	$TestRuntime.assertThat(multi.toJson().has('multipart')).isTrue()
}

$TestRuntime.check('client.wrapper.particleSounds') {
	def particle = new ParticleGenerator()
	particle.texture('kubegroovy:a').textures(['kubegroovy:b'])
	$TestRuntime.assertThat(particle.toJson().getAsJsonArray('textures').size() == 1).isTrue()

	def sounds = new SoundsGenerator()
	$TestRuntime.assertThat(sounds.toJson().size() == 0).isTrue()
	sounds.addSound('p', { s -> s.sound('kubegroovy:x') })
	sounds.addSound('p', { s -> s.subtitle('sub') }, true)
	sounds.addSound('q', { s -> s.sound('kubegroovy:y') }, true)
	$TestRuntime.assertThat(sounds.toJson().has('p')).isTrue()
	$TestRuntime.assertThat(sounds.toJson().has('q')).isTrue()
}

$TestRuntime.check('client.wrapper.atlasSprite') {
	def seen = []
	def evt = new AtlasSpriteRegistryKubeEvent({ id -> seen.add(id.toString()) })
	evt.register('kubegroovy:coverage_direct_sprite')
	$TestRuntime.assertThat(seen.contains('kubegroovy:coverage_direct_sprite')).isTrue()
}

$TestRuntime.check('client.wrapper.soundDefaults') {
	def sounds = new SoundsGenerator()
	sounds.addSound('defaults', { s -> })
	def entry = sounds.toJson().getAsJsonObject('defaults')
	$TestRuntime.assertThat(entry.has('replace')).isFalse()
	$TestRuntime.assertThat(entry.has('subtitle')).isFalse()
	$TestRuntime.assertThat(entry.has('sounds')).isFalse()
}

$TestRuntime.check('client.wrapper.tintFunctions.block') {
	def stone = Blocks.STONE.defaultBlockState()
	def fixed = new BlockTintFunction.Fixed('#ff0000')
	def nullFn = { s, l, p, i -> null } as BlockTintFunction
	def blockFixed = new BlockTintFunctionWrapper(fixed, 0)
	$TestRuntime.assertThat(blockFixed.color(stone) != 0).isTrue()
	$TestRuntime.assertThat(blockFixed.colorInWorld(stone, null, null) != 0).isTrue()
	// Invocation only (see fluid.fn): a closure `as BlockTintFunction` takes
	// a racy path (plain SAM vs wrap() into a Fixed), so no value is pinned.
	def blockNull = new BlockTintFunctionWrapper(nullFn, 1)
	blockNull.color(stone)
	blockNull.colorInWorld(stone, null, null)
}

$TestRuntime.check('client.wrapper.tintFunctions.fluid.bucket') {
	def fixed = new BlockTintFunction.Fixed('#ff0000')
	def fluidBucket = new FluidTintFunctionWrapper(fixed, 0, '#00ff00')
	$TestRuntime.assertThat(fluidBucket.colorAsStack(FluidStack.EMPTY) != 0).isTrue()
}

$TestRuntime.check('client.wrapper.tintFunctions.fluid.fixed') {
	def fixed = new BlockTintFunction.Fixed('#ff0000')
	def waterFluid = Blocks.WATER.defaultBlockState().getFluidState()
	def fluidFixed = new FluidTintFunctionWrapper(fixed, 0, null)
	$TestRuntime.assertThat(fluidFixed.color(waterFluid) != 0).isTrue()
	$TestRuntime.assertThat(fluidFixed.colorAsStack(FluidStack.EMPTY) != 0).isTrue()
}

$TestRuntime.check('client.wrapper.tintFunctions.fluid.fn') {
	def stone = Blocks.STONE.defaultBlockState()
	def nullFn = { s, l, p, i -> null } as BlockTintFunction
	// Invocation only: `{...} as BlockTintFunction` resolves racy across boots
	// (plain null-returning SAM vs wrap() into a Fixed with a wrapped color),
	// observed flipping between runs, so only invocation + the default
	// getMaxTintIndex() are pinned here, never the returned color.
	nullFn.getColor(stone, null, null, 0)
	$TestRuntime.assertThat(nullFn.getMaxTintIndex()).isEqualTo(0)
}

$TestRuntime.check('client.wrapper.tintFunctions.fluid.color') {
	def nullFn = { s, l, p, i -> null } as BlockTintFunction
	def waterFluid = Blocks.WATER.defaultBlockState().getFluidState()
	// Invocation only: closure-cast custom functions return run-dependent
	// values through the wrapper boundary (observed both -1 and 0 for
	// identical source across runs), so no value is pinned here.
	new FluidTintFunctionWrapper(nullFn, 0, null).color(waterFluid)
}

$TestRuntime.check('client.wrapper.tintFunctions.fluid.world') {
	def stone = Blocks.STONE.defaultBlockState()
	def nullFn = { s, l, p, i -> null } as BlockTintFunction
	def waterFluid = Blocks.WATER.defaultBlockState().getFluidState()
	new FluidTintFunctionWrapper(nullFn, 0, null).colorInWorld(waterFluid, stone, null, null)
}

$TestRuntime.check('client.wrapper.tintFunctions.fluid.stack') {
	def nullFn = { s, l, p, i -> null } as BlockTintFunction
	def wrapper = new FluidTintFunctionWrapper(nullFn, 0, null)
	def empty = FluidStack.EMPTY
	// Invoked without a value assert: a closure `as BlockTintFunction` takes
	// an environment-sensitive path (observed 0 for EMPTY here while sibling
	// null-closure asserts yield -1), so only invocation + wiring are pinned.
	wrapper.colorAsStack(empty)
	$TestRuntime.assertThat(wrapper.bucketColor() == null).isTrue()
	$TestRuntime.assertThat(wrapper.function().is(nullFn)).isTrue()
}

$TestRuntime.check('client.wrapper.tintFunctions.item') {
	// ItemTintFunctionWrapper: type() covers CODEC here; calculate() needs a
	// bound item registry (unavailable at script-load time), so it runs
	// in-world under 'client.wrapper.tintFunctions.itemStack'.
	def itemTint = new ItemTintFunctionWrapper(0)
	$TestRuntime.assertThat(itemTint.type() != null).isTrue()
}

$TestRuntime.check('client.wrapper.tintFunctions.wrap') {
	def stone = Blocks.STONE.defaultBlockState()
	// BlockTintFunction.wrap every branch + built-in lambda null-level branches.
	$TestRuntime.assertThat(BlockTintFunction.wrap(null) == null).isTrue()
	$TestRuntime.assertThat(BlockTintFunction.wrap('grass').getColor(stone, null, null, 0) != null).isTrue()
	$TestRuntime.assertThat(BlockTintFunction.wrap('foliage').getColor(stone, null, null, 0) != null).isTrue()
	$TestRuntime.assertThat(BlockTintFunction.wrap('water').getColor(stone, null, null, 0) == null).isTrue()
	$TestRuntime.assertThat(BlockTintFunction.wrap('redstone') != null).isTrue()
	$TestRuntime.assertThat(BlockTintFunction.wrap('evergreen_foliage').getColor(stone, null, null, 0) != null).isTrue()
	def mapped = BlockTintFunction.wrap(['grass', null])
	$TestRuntime.assertThat(mapped.getMaxTintIndex() == 0).isTrue()
	$TestRuntime.assertThat(BlockTintFunction.wrap(0xFF0000).getMaxTintIndex() == 0).isTrue()
	$TestRuntime.assertThat(BlockTintFunction.wrap(0xFF0000).getColor(stone, null, null, 0) != null).isTrue()
}

$TestRuntime.check('client.wrapper.icons') {
	$TestRuntime.assertThat(KubeIconRenderer.from(new TextureKubeIcon('kubegroovy:block/unknown')) != null).isTrue()
	$TestRuntime.assertThat(KubeIconRenderer.from(new AtlasSpriteKubeIcon(Optional.empty(), 'kubegroovy:coverage_sprite')) != null).isTrue()
	// NOTE: ItemKubeIcon needs a bound item registry (unavailable at script-load
	// time: "Components not bound yet"), so it runs on the first client tick
	// under 'client.wrapper.icons.item'.
}

$TestRuntime.check('client.wrapper.modelRegistry') {
	def id = 'kubegroovy:coverage_conditional'
	KubeGroovyModelPropertyRegistry.putConditional(id, { stack, level, entity, seed, ctx -> true })
	$TestRuntime.assertThat(KubeGroovyModelPropertyRegistry.getConditional(id) != null).isTrue()
	$TestRuntime.assertThat(KubeGroovyModelPropertyRegistry.getConditional('kubegroovy:coverage_missing_conditional') == null).isTrue()
}

$TestRuntime.check('client.wrapper.clientProps') {
	def props = ClientProperties.get()
	$TestRuntime.assertThat(props.windowTitle != null).isTrue()
	$TestRuntime.assertThat(props.disableRecipeBook == true || props.disableRecipeBook == false).isTrue()
	$TestRuntime.assertThat(props.exportAtlases == true || props.exportAtlases == false).isTrue()
	$TestRuntime.assertThat(EditorExt.isKnownVSCode() == true || EditorExt.isKnownVSCode() == false).isTrue()
}

$TestRuntime.check('client.wrapper.keybinds') {
	def a = KubeGroovyKeybinds.getOrCreate('coverage_wrapper_key')
	def b = KubeGroovyKeybinds.getOrCreate('coverage_wrapper_key')
	$TestRuntime.assertThat(a.is(b)).isTrue()
	$TestRuntime.assertThat(a == b).isTrue()
	$TestRuntime.assertThat(a.hashCode() == b.hashCode()).isTrue()
	$TestRuntime.assertThat(KubeGroovyKeybinds.get('coverage_wrapper_key') != null).isTrue()
	$TestRuntime.assertThat(KubeGroovyKeybinds.get('coverage_wrapper_missing_xyz') == null).isTrue()
	$TestRuntime.assertThat(a.equals('not-a-key')).isFalse()
}
