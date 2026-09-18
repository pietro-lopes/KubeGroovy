import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import dev.uncandango.kubegroovy.block.BlockTintFunction
import dev.uncandango.kubegroovy.client.icon.KubeIcon
import dev.uncandango.kubegroovy.client.icon.TextureKubeIcon
import dev.uncandango.kubegroovy.color.KubeColor
import dev.uncandango.kubegroovy.core.PlayerSelector
import dev.uncandango.kubegroovy.holder.HolderWrapper
import dev.uncandango.kubegroovy.item.ItemPredicate
import dev.uncandango.kubegroovy.item.ItemTintFunction
import dev.uncandango.kubegroovy.recipe.filter.RecipeFilter
import dev.uncandango.kubegroovy.recipe.match.ReplacementMatch
import dev.uncandango.kubegroovy.recipe.match.ReplacementMatchInfo
import dev.uncandango.kubegroovy.block.state.BlockStatePredicate
import dev.uncandango.kubegroovy.script.bridge.BaseFunction
import dev.uncandango.kubegroovy.testmod.TestRuntime
import dev.uncandango.kubegroovy.util.KubeIdentifier
import dev.uncandango.kubegroovy.util.SlotFilter
import dev.uncandango.kubegroovy.util.TickDuration
import dev.uncandango.kubegroovy.util.Tristate
import dev.uncandango.kubegroovy.util.registrypredicate.RegistryPredicate
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderSet
import net.minecraft.core.Position
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.registries.Registries
import net.minecraft.core.component.DataComponentMap
import net.minecraft.core.component.DataComponentPatch
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CollectionTag
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.TextColor
import net.minecraft.resources.Identifier
import net.minecraft.stats.Stat
import net.minecraft.stats.Stats
import net.minecraft.util.ColorRGBA
import net.minecraft.util.RandomSource
import net.minecraft.util.Unit
import net.minecraft.util.valueproviders.FloatProvider
import net.minecraft.util.valueproviders.IntProvider
import net.minecraft.world.LockCode
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.item.Item
import net.minecraft.world.item.enchantment.ItemEnchantments
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.ToolMaterial
import net.minecraft.world.item.component.BlockItemStateProperties
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.component.CustomModelData
import net.minecraft.world.item.component.Fireworks
import net.minecraft.world.item.component.ItemAttributeModifiers
import net.minecraft.world.item.component.ItemLore
import net.minecraft.world.item.crafting.Ingredient
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.item.equipment.ArmorMaterial
import net.minecraft.world.item.equipment.ArmorType
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockSetType
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleTest
import net.minecraft.world.level.ItemLike
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.level.material.MapColor
import net.minecraft.world.level.storage.loot.LootContext.EntityTarget
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.common.ItemAbility
import net.neoforged.neoforge.common.crafting.SizedIngredient
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.fluids.crafting.FluidIngredient
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient
import net.neoforged.neoforge.server.ServerLifecycleHooks
import net.neoforged.neoforge.transfer.fluid.FluidResource
import net.neoforged.neoforge.transfer.item.ItemResource
import org.assertj.core.api.Assertions
import org.codehaus.groovy.runtime.typehandling.GroovyCastException

import java.io.File
import java.nio.file.Path
import java.time.Duration
import java.time.temporal.TemporalAmount
import java.util.UUID
import java.util.regex.Pattern

import net.minecraft.util.valueproviders.WeightedListInt

def wrapperChecksRun = false
ServerEvents.tick { event ->
	if (wrapperChecksRun) return
	wrapperChecksRun = true

	TestRuntime.check('wrapper.vec3') {
		def v = [1, 2, 3] as Vec3
		TestRuntime.assertThat(v.x()).as("vec3 x").isEqualTo(1.0d)
		TestRuntime.assertThat(v.y()).as("vec3 y").isEqualTo(2.0d)
		TestRuntime.assertThat(v.z()).as("vec3 z").isEqualTo(3.0d)
		def positionMapToInterface = [x: { 1d }, y: { 2d }, z: { 3d }] as Position
		def v2 = positionMapToInterface as Vec3
		TestRuntime.assertThat(v2.x()).as("vec3 from position x").isEqualTo(1.0d)
		TestRuntime.assertThat(v2.y()).as("vec3 from position y").isEqualTo(2.0d)
		TestRuntime.assertThat(v2.z()).as("vec3 from position z").isEqualTo(3.0d)
		Assertions.assertThatThrownBy(() -> [1, 2] as Vec3).as("two elements should not cast to Vec3").isInstanceOf(GroovyCastException)
		def v3 = [1, 2, 3] as BlockPos as Vec3
		TestRuntime.assertThat(v3.x()).as("blockpos vec3 x").isEqualTo(1.5d)
		TestRuntime.assertThat(v3.y()).as("blockpos vec3 y").isEqualTo(2.5d)
		TestRuntime.assertThat(v3.z()).as("blockpos vec3 z").isEqualTo(3.5d)
		def parsedDefaults = ['not-a-number', '2.5', null] as Vec3
		TestRuntime.assertThat(parsedDefaults.x()).as("unparsable vec3 x defaults to 0").isEqualTo(0.0d)
		TestRuntime.assertThat(parsedDefaults.y()).as("parsable vec3 y").isEqualTo(2.5d)
		TestRuntime.assertThat(parsedDefaults.z()).as("null vec3 z defaults to 0").isEqualTo(0.0d)

		def overworld = ServerLifecycleHooks.getCurrentServer().getLevel("minecraft:overworld" as Identifier)
		def entity = EntityType.CREEPER.create(overworld, EntitySpawnReason.COMMAND)
		TestRuntime.assertThat(entity as Vec3).as("entity position").isEqualTo(entity.position())

		def v4 = entity.getBlock()
		TestRuntime.assertThat(v4 as Vec3).as("block center vec3").isEqualTo(new Vec3(v4.centerX, v4.centerY, v4.centerZ))

		Assertions.assertThatThrownBy(() -> "strings are invalid" as Vec3).as("string should not cast to Vec3").isInstanceOf(GroovyCastException)
		Assertions.assertThatThrownBy(() -> null as Vec3).as("null should not cast to Vec3").isInstanceOf(GroovyCastException)
		Assertions.assertThatThrownBy(() -> 42 as Vec3).as("number should not cast to Vec3").isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.blockpos') {
		def p = [4, 5, 6].asType(BlockPos)
		TestRuntime.assertThat(p.toShortString()).as("blockpos short string").isEqualTo('4, 5, 6')
		def defaults = [4, 'not-a-number', null] as BlockPos
		TestRuntime.assertThat(defaults.toShortString()).as("blockpos unparsable defaults").isEqualTo('4, 0, 0')
		def fromPos = ([x: { 4d }, y: { 5d }, z: { 6d }] as Position) as BlockPos
		TestRuntime.assertThat(fromPos.toShortString()).as("blockpos from position").isEqualTo('4, 5, 6')
		Assertions.assertThatThrownBy(() -> [1, 2] as BlockPos).as("two elements should not cast to BlockPos").isInstanceOf(GroovyCastException)
		Assertions.assertThatThrownBy(() -> null as BlockPos).as("null should not cast to BlockPos").isInstanceOf(GroovyCastException)
		Assertions.assertThatThrownBy(() -> 42 as BlockPos).as("number should not cast to BlockPos").isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.itemstack') {
		def stack = 'minecraft:diamond' as ItemStack
		TestRuntime.assertThat(stack.id).as("itemstack id").isEqualTo('minecraft:diamond')
		def sized = '2x minecraft:diamond' as ItemStack
		TestRuntime.assertThat(sized.getCount()).as("sized itemstack count").isEqualTo(2)
		def air = 'air' as ItemStack
		TestRuntime.assertThat(air.isEmpty()).as("air itemstack is empty").isTrue()
		def json = new JsonPrimitive('minecraft:stick') as ItemStack
		TestRuntime.assertThat(json.id).as("json itemstack id").isEqualTo('minecraft:stick')
		def mapped = [item: 'minecraft:stick', count: 'invalid'] as ItemStack
		TestRuntime.assertThat(mapped.id).as("mapped itemstack id").isEqualTo('minecraft:stick')
		TestRuntime.assertThat(mapped.getCount()).as("invalid count defaults to 1").isEqualTo(1)
		TestRuntime.assertThat(('0x minecraft:diamond' as ItemStack).isEmpty()).as("zero count itemstack is empty").isTrue()
		TestRuntime.assertThat((null as ItemStack).isEmpty()).as("null itemstack is empty").isTrue()
		TestRuntime.assertThat((Items.DIAMOND as ItemStack).id).as("item to itemstack id").isEqualTo('minecraft:diamond')
		TestRuntime.assertThat(('minecraft:diamond' as Identifier as ItemStack).id).as("identifier to itemstack id").isEqualTo('minecraft:diamond')
		Assertions.assertThatThrownBy(() -> ('minecraft:diamond' as Ingredient) as ItemStack).as("ingredient should not cast to ItemStack").isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.component') {
		def component = 'hello' as MutableComponent
		TestRuntime.assertThat(component.getString()).as("plain component text").isEqualTo('hello')
		def styled = [text: 'hello', color: '#ff0000', bold: true, extra: ['!']] as MutableComponent
		TestRuntime.assertThat(styled.getString()).as("styled component text").isEqualTo('hello!')
		TestRuntime.assertThat(styled.getStyle().isBold()).as("styled component is bold").isTrue()
		TestRuntime.assertThat(styled.getStyle().getColor().getValue()).as("styled component color").isEqualTo(0xff0000)
		def empty = '' as MutableComponent
		TestRuntime.assertThat(empty.getString().isEmpty()).as("empty component text is empty").isTrue()
		def json = new JsonPrimitive('json component') as MutableComponent
		TestRuntime.assertThat(json.getString()).as("json component text").isEqualTo('json component')
		def missing = null as MutableComponent
		TestRuntime.assertThat(missing.getString()).as("null component text").isEqualTo('null')
		def fromEnum = java.util.concurrent.TimeUnit.SECONDS as MutableComponent
		TestRuntime.assertThat(fromEnum.getString()).as("enum component text").isEqualTo('SECONDS')
		def fromList = ['a', 'b'] as MutableComponent
		TestRuntime.assertThat(fromList.getString()).as("list component text").isEqualTo('ab')
		def fromNumber = 42 as MutableComponent
		TestRuntime.assertThat(fromNumber.getString()).as("number component text").isEqualTo('42')
	}
	TestRuntime.check('wrapper.nbt') {
		def tag = [a: 1, b: 'two'] as CompoundTag
		TestRuntime.assertThat(tag.contains('a')).as("compound contains a").isTrue()
		TestRuntime.assertThat(tag.contains('b')).as("compound contains b").isTrue()
		def structured = '{nested:{value:"ok"},numbers:[I;1,2,3]}' as CompoundTag
		TestRuntime.assertThat(structured.getCompoundOrEmpty('nested').getStringOr('value', '')).as("nested compound value").isEqualTo('ok')
		TestRuntime.assertThat(structured.getIntArray('numbers').orElseThrow().length).as("int array length").isEqualTo(3)
		def bytes = [1 as byte, 2 as byte] as Tag
		TestRuntime.assertThat(bytes.getId()).as("byte array tag id").isEqualTo(Tag.TAG_BYTE_ARRAY)
		def booleanTag = true as Tag
		TestRuntime.assertThat(booleanTag.getId()).as("boolean tag id").isEqualTo(Tag.TAG_BYTE)
		def stringTag = 'hi' as Tag
		TestRuntime.assertThat(stringTag.getId()).as("string tag id").isEqualTo(Tag.TAG_STRING)
		def intTag = 5 as Tag
		TestRuntime.assertThat(intTag.getId()).as("int tag id").isEqualTo(Tag.TAG_INT)
		def longTag = 7L as Tag
		TestRuntime.assertThat(longTag.getId()).as("long tag id").isEqualTo(Tag.TAG_LONG)
		def doubleTag = 1.5d as Tag
		TestRuntime.assertThat(doubleTag.getId()).as("double tag id").isEqualTo(Tag.TAG_DOUBLE)
		def charTag = ('a' as char) as Tag
		TestRuntime.assertThat(charTag.getId()).as("char tag id").isEqualTo(Tag.TAG_STRING)
		def listTag = [1, 2, 3] as Tag
		TestRuntime.assertThat(listTag.getId()).as("int list tag id").isEqualTo(Tag.TAG_INT_ARRAY)
		def empty = [:] as CompoundTag
		TestRuntime.assertThat(empty.isEmpty()).as("empty compound is empty").isTrue()
		TestRuntime.assertThat(null as Tag).as("null tag").isNull()
		Assertions.assertThatThrownBy(() -> 'not a compound' as CompoundTag).as("string should not cast to CompoundTag").isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.color') {
		def color = '#ff0000' as KubeColor
		TestRuntime.assertThat(color.toHexString()).as("hex color").isEqualTo('#FF0000')
		def transparent = 'none' as KubeColor
		TestRuntime.assertThat(transparent.toHexString()).as("none color is transparent").isEqualTo('#00000000')
		def zero = 0 as KubeColor
		TestRuntime.assertThat(zero.toHexString()).as("zero color is transparent").isEqualTo('#00000000')
		def nonZero = 0xFF0000 as KubeColor
		TestRuntime.assertThat(nonZero.toHexString()).as("integer color").isEqualTo('#FF0000')
		def named = 'red' as KubeColor
		TestRuntime.assertThat(named).as("named color").isNotNull()
		def unknown = 'not-a-color' as KubeColor
		TestRuntime.assertThat(unknown.toHexString()).as("unknown color is transparent").isEqualTo('#00000000')
		TestRuntime.assertThat((null as KubeColor).toHexString()).as("null color is transparent").isEqualTo('#00000000')
	}
	TestRuntime.check('wrapper.id') {
		def id = 'kubegroovy:test' as Identifier
		TestRuntime.assertThat(id.toString()).as("explicit identifier").isEqualTo('kubegroovy:test')
		def defaultNamespace = 'stone' as Identifier
		TestRuntime.assertThat(defaultNamespace.toString()).as("default namespace identifier").isEqualTo('minecraft:stone')
		def json = new JsonPrimitive('minecraft:dirt') as Identifier
		TestRuntime.assertThat(json.toString()).as("json identifier").isEqualTo('minecraft:dirt')
		TestRuntime.assertThat(null as Identifier).as("null identifier").isNull()
		Assertions.assertThatThrownBy(() -> 'bad id!' as Identifier).as("bad id should not cast to Identifier").isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.uuid') {
		def uuid = '12345678-1234-1234-1234-123456789abc' as UUID
		TestRuntime.assertThat(uuid.toString()).as("dashed uuid").isEqualTo('12345678-1234-1234-1234-123456789abc')
		def compact = '12345678123412341234123456789abc' as UUID
		TestRuntime.assertThat(compact.toString()).as("compact uuid").isEqualTo('12345678-1234-1234-1234-123456789abc')
		TestRuntime.assertThat(null as UUID).as("null uuid").isNull()
		Assertions.assertThatThrownBy(() -> 'too-short' as UUID).as("short string should not cast to UUID").isInstanceOf(GroovyCastException)
		Assertions.assertThatThrownBy(() -> '12345678-1234-1234-1234-xxxxxxxxxxxx' as UUID).as("invalid uuid should not cast to UUID").isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.tristate') {
		def tristate = 'true' as Tristate
		TestRuntime.assertThat(tristate.getSerializedName()).as("true tristate").isEqualTo('true')
		TestRuntime.assertThat((false as Tristate).getSerializedName()).as("false tristate").isEqualTo('false')
		TestRuntime.assertThat((true as Tristate).getSerializedName()).as("boolean true tristate").isEqualTo('true')
		TestRuntime.assertThat(('unrecognised' as Tristate).getSerializedName()).as("unknown tristate defaults").isEqualTo('default')
		TestRuntime.assertThat((null as Tristate).getSerializedName()).as("null tristate defaults").isEqualTo('default')
	}
	TestRuntime.check('wrapper.duration') {
		def duration = '20t' as Duration
		TestRuntime.assertThat(duration.toMillis()).as("tick duration millis").isEqualTo(1000L)
		TestRuntime.assertThat((1500 as Duration).toMillis()).as("millis duration").isEqualTo(1500L)
		TestRuntime.assertThat(('2m 5s' as Duration).toMillis()).as("combined duration millis").isEqualTo(125000L)
		Assertions.assertThatThrownBy(() -> 'malformed' as Duration).as("malformed string should not cast to Duration").isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.regexp') {
		def pattern = '/foo/i' as Pattern
		TestRuntime.assertThat(pattern.pattern()).as("regex source").isEqualTo('foo')
		TestRuntime.assertThat((pattern.flags() & Pattern.CASE_INSENSITIVE) != 0).as("case-insensitive flag is set").isTrue()
		def escaped = '/foo\\/bar/s' as Pattern
		TestRuntime.assertThat(escaped.matcher('foo/bar').find()).as("escaped regex matches").isTrue()
		def noPattern = 'plain text' as Pattern
		TestRuntime.assertThat(noPattern).as("plain text is not a pattern").isNull()
		TestRuntime.assertThat(null as Pattern).as("null pattern").isNull()
	}
	TestRuntime.check('wrapper.int_provider') {
		def constant = 3 as IntProvider
		TestRuntime.assertThat(constant.minInclusive()).as("constant min").isEqualTo(3)
		TestRuntime.assertThat(constant.maxInclusive()).as("constant max").isEqualTo(3)
		def range = [2, 8] as IntProvider
		TestRuntime.assertThat(range.minInclusive()).as("range min").isEqualTo(2)
		TestRuntime.assertThat(range.maxInclusive()).as("range max").isEqualTo(8)
		def parsed = ['7'] as IntProvider
		TestRuntime.assertThat(parsed.minInclusive()).as("parsed min").isEqualTo(7)
		TestRuntime.assertThat(parsed.maxInclusive()).as("parsed max").isEqualTo(7)
		def bounds = [min_inclusive: 9, max_inclusive: 4] as IntProvider
		TestRuntime.assertThat(bounds.minInclusive()).as("swapped bounds min").isEqualTo(4)
		TestRuntime.assertThat(bounds.maxInclusive()).as("swapped bounds max").isEqualTo(9)
		def clamped = [clamped: 3, min: 2, max: 4] as IntProvider
		TestRuntime.assertThat(clamped.minInclusive()).as("clamped min").isEqualTo(3)
		TestRuntime.assertThat(clamped.maxInclusive()).as("clamped max").isEqualTo(3)
		def clampedNormal = [clamped_normal: 3, mean: 2, deviation: 1, min: 1, max: 5] as IntProvider
		TestRuntime.assertThat(clampedNormal.minInclusive()).as("clamped normal min").isEqualTo(1)
		TestRuntime.assertThat(clampedNormal.maxInclusive()).as("clamped normal max").isEqualTo(5)
		TestRuntime.assertThat(clamped.minInclusive()).as("clamped min again").isEqualTo(3)
		TestRuntime.assertThat(clamped.maxInclusive()).as("clamped max again").isEqualTo(3)
		def weightedList = [type: "weighted_list", distribution: [[data: [type: "constant", value: 9], weight: 1], [data: [type: "constant", value: 3], weight: 2]]] as IntProvider
		if (weightedList instanceof WeightedListInt wl) {
			TestRuntime.assertThat(wl.minInclusive()).as("weighted list min").isEqualTo(3)
			TestRuntime.assertThat(wl.maxInclusive()).as("weighted list max").isEqualTo(9)
		}
		def minMax = [min: 1, max: 5] as IntProvider
		TestRuntime.assertThat(minMax.minInclusive()).as("minmax min").isEqualTo(1)
		TestRuntime.assertThat(minMax.maxInclusive()).as("minmax max").isEqualTo(5)
		def withValue = [value: 4] as IntProvider
		TestRuntime.assertThat(withValue.minInclusive()).as("value min").isEqualTo(4)
		TestRuntime.assertThat(withValue.maxInclusive()).as("value max").isEqualTo(4)
		def withBounds = [bounds: [2, 6]] as IntProvider
		TestRuntime.assertThat(withBounds.minInclusive()).as("bounds min").isEqualTo(2)
		TestRuntime.assertThat(withBounds.maxInclusive()).as("bounds max").isEqualTo(6)
		Assertions.assertThatThrownBy(() -> [] as IntProvider).as("empty list should not cast to IntProvider").isInstanceOf(GroovyCastException)
		Assertions.assertThatThrownBy(() -> [1, 2, 3] as IntProvider).as("three elements should not cast to IntProvider").isInstanceOf(GroovyCastException)
		Assertions.assertThatThrownBy(() -> null as IntProvider).as("null should not cast to IntProvider").isInstanceOf(GroovyCastException)
		Assertions.assertThatThrownBy(() -> [something: 2, invalid: 3] as IntProvider).as("unknown keys should not cast to IntProvider").isInstanceOf(GroovyCastException)
		Assertions.assertThatThrownBy(() -> [bounds: [1]] as IntProvider).as("single bound should not cast to IntProvider").isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.map_color') {
		def color = 'color_red' as MapColor
		TestRuntime.assertThat(color.col).as("map color value").isEqualTo(10040115)
		TestRuntime.assertThat(('STONE' as MapColor).col).as("uppercase map color").isEqualTo(MapColor.STONE.col)
		TestRuntime.assertThat(('#ff0000' as MapColor).is(MapColor.NONE)).as("hex map color is not none").isFalse()
		TestRuntime.assertThat(0xFF0000 as MapColor).as("integer map color").isNotNull()
		TestRuntime.assertThat(net.minecraft.world.item.DyeColor.RED as MapColor).as("dye map color").isNotNull()
		Assertions.assertThatThrownBy(() -> '' as MapColor).as("empty string should not cast to MapColor").isInstanceOf(GroovyCastException)
		Assertions.assertThatThrownBy(() -> 'not-a-map-color' as MapColor).as("unknown name should not cast to MapColor").isInstanceOf(GroovyCastException)
		Assertions.assertThatThrownBy(() -> null as MapColor).as("null should not cast to MapColor").isInstanceOf(GroovyCastException)
	}

	TestRuntime.check('wrapper.registry_predicate') {
		def predicate = 'minecraft:diamond' as RegistryPredicate
		TestRuntime.assertThat(predicate.test(Items.DIAMOND.builtInRegistryHolder())).as("diamond predicate matches diamond").isTrue()
		TestRuntime.assertThat(predicate.test(Items.STICK.builtInRegistryHolder())).as("diamond predicate rejects stick").isFalse()
		def all = '*' as RegistryPredicate
		TestRuntime.assertThat(all.test(Items.STICK.builtInRegistryHolder())).as("wildcard predicate matches stick").isTrue()
		def none = '-' as RegistryPredicate
		TestRuntime.assertThat(none.test(Items.DIAMOND.builtInRegistryHolder())).as("none predicate rejects diamond").isFalse()
		def namespace = '@minecraft' as RegistryPredicate
		TestRuntime.assertThat(namespace.test(Items.STICK.builtInRegistryHolder())).as("namespace predicate matches stick").isTrue()
		def regex = '/diamond/' as RegistryPredicate
		TestRuntime.assertThat(regex.test(Items.DIAMOND.builtInRegistryHolder())).as("regex predicate matches diamond").isTrue()
		def any = ['minecraft:stick', 'minecraft:diamond'] as RegistryPredicate
		TestRuntime.assertThat(any.test(Items.DIAMOND.builtInRegistryHolder())).as("list predicate matches diamond").isTrue()
		TestRuntime.assertThat(([] as RegistryPredicate).test(Items.DIAMOND.builtInRegistryHolder())).as("empty predicate rejects diamond").isFalse()
		TestRuntime.assertThat((null as RegistryPredicate).test(Items.DIAMOND.builtInRegistryHolder())).as("null predicate rejects diamond").isFalse()
		TestRuntime.assertThat((('/diamond/' as Pattern) as RegistryPredicate).test(Items.DIAMOND.builtInRegistryHolder())).as("pattern predicate matches diamond").isTrue()
		TestRuntime.assertThat((new JsonPrimitive('minecraft:diamond') as RegistryPredicate).test(Items.DIAMOND.builtInRegistryHolder())).as("json predicate matches diamond").isTrue()
		TestRuntime.assertThat(((new BaseFunction({ holder -> true })) as RegistryPredicate).test(Items.DIAMOND.builtInRegistryHolder())).as("function predicate matches diamond").isTrue()
		TestRuntime.assertThat(Items.DIAMOND as RegistryPredicate).as("item predicate").isNotNull()
	}
	TestRuntime.check('wrapper.json_object') {
		def object = [name: 'wrapper', count: 2] as JsonObject
		TestRuntime.assertThat(object.get('name').getAsString()).as("object name field").isEqualTo('wrapper')
		TestRuntime.assertThat(object.get('count').getAsInt()).as("object count field").isEqualTo(2)
		def parsed = '{"nested":{"ok":true},"number":2}' as JsonObject
		TestRuntime.assertThat(parsed.getAsJsonObject('nested').get('ok').getAsBoolean()).as("nested object field").isTrue()
		TestRuntime.assertThat(parsed.get('number').getAsInt()).as("parsed number field").isEqualTo(2)
		def empty = [:] as JsonObject
		TestRuntime.assertThat(empty.isEmpty()).as("empty object is empty").isTrue()
		TestRuntime.assertThat(null as JsonObject).as("null object").isNull()
		Assertions.assertThatThrownBy(() -> 'not json' as JsonObject).as("string should not cast to JsonObject").isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.json_array') {
		def array = ['wrapper', 2, true] as JsonArray
		TestRuntime.assertThat(array.size()).as("array size").isEqualTo(3)
		TestRuntime.assertThat(array.get(1).getAsInt()).as("array int element").isEqualTo(2)
		def parsed = '[1,null,{"ok":false}]' as JsonArray
		TestRuntime.assertThat(parsed.get(0).getAsInt()).as("parsed int element").isEqualTo(1)
		TestRuntime.assertThat(parsed.get(1).isJsonNull()).as("parsed null element").isTrue()
		TestRuntime.assertThat(parsed.get(2).getAsJsonObject().get('ok').getAsBoolean()).as("parsed object element").isFalse()
		def empty = [] as JsonArray
		TestRuntime.assertThat(empty.isEmpty()).as("empty array is empty").isTrue()
		TestRuntime.assertThat(null as JsonArray).as("null array").isNull()
		Assertions.assertThatThrownBy(() -> 'not json' as JsonArray).as("string should not cast to JsonArray").isInstanceOf(GroovyCastException)
		Assertions.assertThatThrownBy(() -> 42 as JsonArray).as("number should not cast to JsonArray").isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.json_element') {
		def element = [wrapped: true] as JsonElement
		TestRuntime.assertThat(element.isJsonObject()).as("map element is object").isTrue()
		TestRuntime.assertThat(element.getAsJsonObject().get('wrapped').getAsBoolean()).as("wrapped element field").isTrue()
		def primitive = 'text' as JsonElement
		TestRuntime.assertThat(primitive.isJsonPrimitive()).as("text element is primitive").isTrue()
		TestRuntime.assertThat(primitive.getAsString()).as("text element value").isEqualTo('text')
		def boolElement = true as JsonElement
		TestRuntime.assertThat(boolElement.isJsonPrimitive()).as("boolean element is primitive").isTrue()
		TestRuntime.assertThat(boolElement.getAsBoolean()).as("boolean element value").isTrue()
		def numElement = 42 as JsonElement
		TestRuntime.assertThat(numElement.isJsonPrimitive()).as("number element is primitive").isTrue()
		TestRuntime.assertThat(numElement.getAsInt()).as("number element value").isEqualTo(42)
		def listElement = [1, 2] as JsonElement
		TestRuntime.assertThat(listElement.isJsonArray()).as("list element is array").isTrue()
		TestRuntime.assertThat(listElement.getAsJsonArray().size()).as("list element size").isEqualTo(2)
		def nullElement = null as JsonElement
		TestRuntime.assertThat(nullElement.isJsonNull()).as("null element is json null").isTrue()
	}
	TestRuntime.check('wrapper.json_primitive') {
		def primitive = 42 as JsonPrimitive
		TestRuntime.assertThat(primitive.getAsInt()).as("int primitive value").isEqualTo(42)
		TestRuntime.assertThat((true as JsonPrimitive).getAsBoolean()).as("boolean primitive value").isTrue()
		TestRuntime.assertThat(('text' as JsonPrimitive).getAsString()).as("text primitive value").isEqualTo('text')
		def object = [nested: true] as JsonPrimitive
		TestRuntime.assertThat(object).as("map is not a primitive").isNull()
		TestRuntime.assertThat(null as JsonPrimitive).as("null primitive").isNull()
	}
	TestRuntime.check('wrapper.temporal_amount') {
		def amount = '1s 10t' as TemporalAmount
		TestRuntime.assertThat(amount.toString()).as("combined temporal amount").isEqualTo('30 ticks')
		TestRuntime.assertThat((1000 as TemporalAmount).toString()).as("millis temporal amount").isEqualTo('PT1S')
		TestRuntime.assertThat('5ms' as TemporalAmount).as("millis amount").isNotNull()
		TestRuntime.assertThat('7ns' as TemporalAmount).as("nanos amount").isNotNull()
		TestRuntime.assertThat('2m' as TemporalAmount).as("minutes amount").isNotNull()
		TestRuntime.assertThat('3h' as TemporalAmount).as("hours amount").isNotNull()
		Assertions.assertThatThrownBy(() -> '' as TemporalAmount).as("empty string should not cast to TemporalAmount").isInstanceOf(GroovyCastException)
		Assertions.assertThatThrownBy(() -> null as TemporalAmount).as("null should not cast to TemporalAmount").isInstanceOf(GroovyCastException)
		Assertions.assertThatThrownBy(() -> true as TemporalAmount).as("boolean should not cast to TemporalAmount").isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.tick_duration') {
		def ticks = '1s 10t' as TickDuration
		TestRuntime.assertThat(ticks.ticks()).as("combined tick duration").isEqualTo(30L)
		TestRuntime.assertThat((null as TickDuration).ticks()).as("null tick duration").isEqualTo(0L)
		TestRuntime.assertThat((new JsonPrimitive(4) as TickDuration).ticks()).as("json tick duration").isEqualTo(4L)
		TestRuntime.assertThat((100 as TickDuration).ticks()).as("number tick duration").isEqualTo(100L)
	}
	TestRuntime.check('wrapper.kube_identifier') {
		def id = 'wrapper_test' as KubeIdentifier
		TestRuntime.assertThat(id.wrapped().toString()).as("default namespace identifier").isEqualTo('kubegroovy:wrapper_test')
		def explicit = 'minecraft:stone' as KubeIdentifier
		TestRuntime.assertThat(explicit.wrapped().toString()).as("explicit identifier").isEqualTo('minecraft:stone')
		Assertions.assertThatThrownBy(() -> 'not an id!' as KubeIdentifier).as("bad id should not cast to KubeIdentifier").isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.nbt_collection') {
		def collection = [1, 2, 3] as CollectionTag
		TestRuntime.assertThat(collection.size()).as("collection size").isEqualTo(3)
		TestRuntime.assertThat(collection.get(1).asInt().orElseThrow()).as("collection int element").isEqualTo(2)
		def parsed = '[I;4,5]' as CollectionTag
		TestRuntime.assertThat(parsed.size()).as("parsed collection size").isEqualTo(2)
		TestRuntime.assertThat(parsed.get(0).asInt().orElseThrow()).as("parsed collection element").isEqualTo(4)
		def mixed = [1, 'two'] as CollectionTag
		TestRuntime.assertThat(mixed.isEmpty()).as("mixed collection is empty").isTrue()
		TestRuntime.assertThat(([] as CollectionTag).isEmpty()).as("empty collection is empty").isTrue()
		TestRuntime.assertThat(null as CollectionTag).as("null collection").isNull()
		def fromJsonArray = (new JsonArray() as CollectionTag)
		TestRuntime.assertThat(fromJsonArray.isEmpty()).as("json collection is empty").isTrue()
	}
	TestRuntime.check('wrapper.nbt_list') {
		def list = ['one', 'two'] as ListTag
		TestRuntime.assertThat(list.getStringOr(1, '')).as("string list element").isEqualTo('two')
		def parsed = '[]' as ListTag
		TestRuntime.assertThat(parsed.isEmpty()).as("parsed list is empty").isTrue()
		def mixed = ['one', 2] as ListTag
		TestRuntime.assertThat(mixed.isEmpty()).as("mixed list is empty").isTrue()
		TestRuntime.assertThat(null as ListTag).as("null list").isNull()
	}
	TestRuntime.check('wrapper.nbt_tag') {
		def tag = [value: 7] as Tag
		TestRuntime.assertThat(tag.getId()).as("compound tag id").isEqualTo(Tag.TAG_COMPOUND)
		TestRuntime.assertThat(tag.asCompound().orElseThrow().getIntOr('value', 0)).as("compound tag value").isEqualTo(7)
		TestRuntime.assertThat((new JsonPrimitive(8) as Tag).getId()).as("json tag id").isEqualTo(Tag.TAG_INT)
		TestRuntime.assertThat(null as Tag).as("null tag").isNull()
		TestRuntime.assertThat(('hi' as Tag).getId()).as("string tag id").isEqualTo(Tag.TAG_STRING)
		TestRuntime.assertThat((true as Tag).getId()).as("boolean tag id").isEqualTo(Tag.TAG_BYTE)
		TestRuntime.assertThat((5 as Tag).getId()).as("int tag id").isEqualTo(Tag.TAG_INT)
		TestRuntime.assertThat(([1, 2] as Tag).getId()).as("int array tag id").isEqualTo(Tag.TAG_INT_ARRAY)
	}
	TestRuntime.check('wrapper.data_component_type') {
		def type = 'minecraft:custom_name' as DataComponentType
		TestRuntime.assertThat(type.is(DataComponents.CUSTOM_NAME)).as("custom name component type").isTrue()
		def json = new JsonPrimitive('minecraft:custom_name') as DataComponentType
		TestRuntime.assertThat(json.is(DataComponents.CUSTOM_NAME)).as("json component type").isTrue()
		Assertions.assertThatThrownBy(() -> 'minecraft:not_a_component' as DataComponentType).as("unknown name should not cast to DataComponentType").isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.data_component_map') {
		def components = [(DataComponents.CUSTOM_NAME): ('wrapped' as MutableComponent)] as DataComponentMap
		def name = components.get(DataComponents.CUSTOM_NAME)
		TestRuntime.assertThat(name.getString()).as("component map name").isEqualTo('wrapped')
		def empty = [:] as DataComponentMap
		TestRuntime.assertThat(empty.isEmpty()).as("empty component map is empty").isTrue()
		TestRuntime.assertThat((null as DataComponentMap).isEmpty()).as("null component map is empty").isTrue()
		TestRuntime.assertThat(('[]' as DataComponentMap).isEmpty()).as("bracket component map is empty").isTrue()
		Assertions.assertThatThrownBy(() -> '[minecraft:custom_name=' as DataComponentMap).as("malformed string should not cast to DataComponentMap").isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.data_component_patch') {
		def patch = [(DataComponents.CUSTOM_NAME): ('patched' as MutableComponent)] as DataComponentPatch
		def name = patch.getPatch(DataComponents.CUSTOM_NAME).orElseThrow()
		TestRuntime.assertThat(name.getString()).as("component patch name").isEqualTo('patched')
		def removed = [(DataComponents.CUSTOM_NAME): null] as DataComponentPatch
		TestRuntime.assertThat(removed.entrySet().iterator().next().getValue().isEmpty()).as("removed component patch is empty").isTrue()
		TestRuntime.assertThat(([:] as DataComponentPatch).isEmpty()).as("empty patch is empty").isTrue()
		TestRuntime.assertThat((null as DataComponentPatch).isEmpty()).as("null patch is empty").isTrue()
		TestRuntime.assertThat(('[]' as DataComponentPatch).isEmpty()).as("bracket patch is empty").isTrue()
	}
	TestRuntime.check('wrapper.vec3i') {
		def vec = [7, 8, 9] as net.minecraft.core.Vec3i
		TestRuntime.assertThat(vec.getX()).as("vec3i x").isEqualTo(7)
		TestRuntime.assertThat(vec.getY()).as("vec3i y").isEqualTo(8)
		TestRuntime.assertThat(vec.getZ()).as("vec3i z").isEqualTo(9)
		def defaults = [7, 'not-a-number', null] as net.minecraft.core.Vec3i
		TestRuntime.assertThat(defaults.getX()).as("defaulted vec3i x").isEqualTo(7)
		TestRuntime.assertThat(defaults.getY()).as("defaulted vec3i y").isEqualTo(0)
		TestRuntime.assertThat(defaults.getZ()).as("defaulted vec3i z").isEqualTo(0)
		Assertions.assertThatThrownBy(() -> [1, 2] as net.minecraft.core.Vec3i).as("two elements should not cast to Vec3i").isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.item') {
		def item = 'minecraft:diamond' as Item
		TestRuntime.assertThat(item.is(Items.DIAMOND)).as("diamond item").isTrue()
		TestRuntime.assertThat(('air' as Item).is(Items.AIR)).as("air item").isTrue()
		TestRuntime.assertThat((null as Item).is(Items.AIR)).as("null item is air").isTrue()
		TestRuntime.assertThat(([item: 'minecraft:diamond'] as Item).is(Items.DIAMOND)).as("mapped item").isTrue()
		Assertions.assertThatThrownBy(() -> 'minecraft:not_an_item' as Item).as("unknown id should not cast to Item").isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.item_like') {
		def item = 'minecraft:diamond' as ItemLike
		TestRuntime.assertThat(item.asItem().is(Items.DIAMOND)).as("diamond itemlike").isTrue()
		TestRuntime.assertThat(('air' as ItemLike).asItem().is(Items.AIR)).as("air itemlike").isTrue()
		TestRuntime.assertThat((null as ItemLike).asItem().is(Items.AIR)).as("null itemlike is air").isTrue()
		Assertions.assertThatThrownBy(() -> 'minecraft:not_an_item' as ItemLike).as("unknown id should not cast to ItemLike").isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.item_enchantments') {
		def sharpness = event.getServer().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(Enchantments.SHARPNESS).orElseThrow()
		def enchantments = [(sharpness): 3] as ItemEnchantments
		TestRuntime.assertThat(enchantments.size()).as("enchantments size").isEqualTo(1)
		TestRuntime.assertThat(enchantments.entrySet().iterator().next().getIntValue()).as("enchantment level").isEqualTo(3)
		TestRuntime.assertThat(([:] as ItemEnchantments).isEmpty()).as("empty enchantments are empty").isTrue()
		TestRuntime.assertThat((42 as ItemEnchantments).isEmpty()).as("number enchantments are empty").isTrue()
		TestRuntime.assertThat((null as ItemEnchantments).isEmpty()).as("null enchantments are empty").isTrue()
	}
	TestRuntime.check('wrapper.aabb') {
		def dimensions = [2.0d, 4.0d, 6.0d] as double[]
		def box = dimensions as AABB
		TestRuntime.assertThat(box.getXsize()).as("box x size").isEqualTo(2.0d)
		TestRuntime.assertThat(box.getYsize()).as("box y size").isEqualTo(4.0d)
		TestRuntime.assertThat(box.getZsize()).as("box z size").isEqualTo(6.0d)
		def bounds = [1.0d, 2.0d, 3.0d, 4.0d, 6.0d, 8.0d] as double[]
		def boundsBox = bounds as AABB
		TestRuntime.assertThat(boundsBox.minX).as("bounds min x").isEqualTo(1.0d)
		TestRuntime.assertThat(boundsBox.maxZ).as("bounds max z").isEqualTo(8.0d)
		def empty = [] as double[]
		TestRuntime.assertThat((empty as AABB).getXsize()).as("empty box x size").isEqualTo(0.0d)
		TestRuntime.assertThat((null as AABB).getYsize()).as("null box y size").isEqualTo(0.0d)
		def fromBlockPos = ([1, 2, 3] as BlockPos) as AABB
		TestRuntime.assertThat(fromBlockPos.getXsize()).as("blockpos box x size").isEqualTo(1.0d)
		Assertions.assertThatThrownBy(() -> ([1.0d, 2.0d] as double[]) as AABB).as("two elements should not cast to AABB").isInstanceOf(GroovyCastException)
		Assertions.assertThatThrownBy(() -> 'not-a-box' as AABB).as("string should not cast to AABB").isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.float_provider') {
		def range = [1.5f, 3.5f] as FloatProvider
		TestRuntime.assertThat(range.min()).as("range min").isEqualTo(1.5f)
		TestRuntime.assertThat(range.max()).as("range max").isEqualTo(3.5f)
		def constant = 2 as FloatProvider
		TestRuntime.assertThat(constant.min()).as("constant min").isEqualTo(2.0f)
		def mapped = [min: 4.5f, max: 1.5f] as FloatProvider
		TestRuntime.assertThat(mapped.min()).as("swapped min").isEqualTo(1.5f)
		TestRuntime.assertThat(mapped.max()).as("swapped max").isEqualTo(4.5f)
		def single = [2.5f] as FloatProvider
		TestRuntime.assertThat(single.min()).as("single min").isEqualTo(2.5f)
		def fromValue = [value: 3.0f] as FloatProvider
		TestRuntime.assertThat(fromValue.min()).as("value min").isEqualTo(3.0f)
		Assertions.assertThatThrownBy(() -> [] as FloatProvider).as("empty list should not cast to FloatProvider").isInstanceOf(GroovyCastException)
		Assertions.assertThatThrownBy(() -> [1.0f, 2.0f, 3.0f] as FloatProvider).as("three elements should not cast to FloatProvider").isInstanceOf(GroovyCastException)
		Assertions.assertThatThrownBy(() -> null as FloatProvider).as("null should not cast to FloatProvider").isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.number_provider') {
		def constant = 4 as NumberProvider
		if (constant instanceof UniformGenerator uniGen) {
			TestRuntime.assertThat(uniGen.min().getFloat(null)).as("constant min").isEqualTo(4.0f)
			TestRuntime.assertThat(uniGen.max().getFloat(null)).as("constant max").isEqualTo(4.0f)
		}
		def constantList = [4] as NumberProvider
		if (constantList instanceof UniformGenerator uniGen) {
			TestRuntime.assertThat(uniGen.min().getFloat(null)).as("list constant min").isEqualTo(4.0f)
			TestRuntime.assertThat(uniGen.max().getFloat(null)).as("list constant max").isEqualTo(4.0f)
		}
		def range = [2, 6] as NumberProvider
		if (range instanceof UniformGenerator uniGen) {
			TestRuntime.assertThat(uniGen.min().getFloat(null)).as("range min").isEqualTo(2.0f)
			TestRuntime.assertThat(uniGen.max().getFloat(null)).as("range max").isEqualTo(6.0f)
		}
		def value = [value: 4] as NumberProvider
		if (value instanceof UniformGenerator uniGen) {
			TestRuntime.assertThat(uniGen.min().getFloat(null)).as("value min").isEqualTo(4.0f)
			TestRuntime.assertThat(uniGen.max().getFloat(null)).as("value max").isEqualTo(4.0f)
		}
		def reversed = [min: 8, max: 2] as NumberProvider
		if (reversed instanceof UniformGenerator uniGen) {
			TestRuntime.assertThat(uniGen.min().getFloat(null)).as("reversed min").isEqualTo(8.0f)
			TestRuntime.assertThat(uniGen.max().getFloat(null)).as("reversed max").isEqualTo(2.0f)
		}

		Assertions.assertThatThrownBy(() -> [] as NumberProvider).as("empty list should not cast to NumberProvider").isInstanceOf(GroovyCastException)
		Assertions.assertThatThrownBy(() -> [1, 2, 3, 4] as NumberProvider).as("four elements should not cast to NumberProvider").isInstanceOf(GroovyCastException)
		Assertions.assertThatThrownBy(() -> null as NumberProvider).as("null should not cast to NumberProvider").isInstanceOf(GroovyCastException)
		def binomial = [n: 5, p: 0.5f] as NumberProvider
		TestRuntime.assertThat(binomial).as("binomial provider").isNotNull()
		Assertions.assertThatThrownBy(() -> [invalid: 1] as NumberProvider).as("unknown keys should not cast to NumberProvider").isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.block_set_type') {
		def type = 'oak' as BlockSetType
		TestRuntime.assertThat(type.is(BlockSetType.OAK)).as("oak block set type").isTrue()
		TestRuntime.assertThat(('OAK' as BlockSetType).is(BlockSetType.OAK)).as("uppercase block set type").isTrue()
		TestRuntime.assertThat(null as BlockSetType).as("null block set type").isNull()
		Assertions.assertThatThrownBy(() -> 'not-a-set-type' as BlockSetType).as("unknown name should not cast to BlockSetType").isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.block_state') {
		def state = 'minecraft:stone' as BlockState
		TestRuntime.assertThat(state.is(Blocks.STONE)).as("stone block state").isTrue()
		def fromBlock = Blocks.DIRT as BlockState
		TestRuntime.assertThat(fromBlock.is(Blocks.DIRT)).as("dirt block state").isTrue()
		Assertions.assertThatThrownBy(() -> 'minecraft:not_a_block' as BlockState).as("unknown id should not cast to BlockState").isInstanceOf(GroovyCastException)
		Assertions.assertThatThrownBy(() -> null as BlockState).as("null should not cast to BlockState").isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.item_ability') {
		def ability = 'axe_strip' as ItemAbility
		TestRuntime.assertThat(ability.name()).as("axe strip ability").isEqualTo('axe_strip')
		TestRuntime.assertThat(('not_an_ability' as ItemAbility).name()).as("unknown ability keeps name").isEqualTo('not_an_ability')
		TestRuntime.assertThat((42 as ItemAbility).name()).as("number ability keeps name").isEqualTo('42')
		TestRuntime.assertThat(null as ItemAbility).as("null ability").isNull()
	}
	TestRuntime.check('wrapper.color_rgba') {
		def color = '#336699' as ColorRGBA
		TestRuntime.assertThat(color.rgba()).as("hex color rgba").isEqualTo(-13408615)
		def alpha = '#80112233' as ColorRGBA
		TestRuntime.assertThat((alpha.rgba() & 0xFFFFFF)).as("alpha color rgb").isEqualTo(0x112233)
		TestRuntime.assertThat(('none' as ColorRGBA).rgba()).as("none color rgba").isEqualTo(0)
		TestRuntime.assertThat(('not-a-color' as ColorRGBA).rgba()).as("unknown color rgba").isEqualTo(0)
		TestRuntime.assertThat((0xFF0000 as ColorRGBA).rgba() != 0).as("integer color rgba is set").isTrue()
	}
	TestRuntime.check('wrapper.ingredient') {
		def ingredient = 'minecraft:diamond' as Ingredient
		TestRuntime.assertThat(ingredient.test(new ItemStack(Items.DIAMOND))).as("diamond ingredient matches diamond").isTrue()
		TestRuntime.assertThat(ingredient.test(new ItemStack(Items.STICK))).as("diamond ingredient rejects stick").isFalse()
		def regex = '/diamond/' as Ingredient
		TestRuntime.assertThat(regex.test(new ItemStack(Items.DIAMOND))).as("regex ingredient matches diamond").isTrue()
		def compound = ['minecraft:diamond', 'minecraft:stick'] as Ingredient
		TestRuntime.assertThat(compound.test(new ItemStack(Items.DIAMOND))).as("compound ingredient matches diamond").isTrue()
		TestRuntime.assertThat(compound.test(new ItemStack(Items.STICK))).as("compound ingredient matches stick").isTrue()
		TestRuntime.assertThat('#minecraft:planks' as Ingredient).as("planks tag ingredient").isNotNull()
		TestRuntime.assertThat(('#minecraft:planks' as Ingredient).test(new ItemStack(Items.OAK_PLANKS))).as("tag ingredient matches planks").isTrue()
		TestRuntime.assertThat(('@kubegroovy' as Ingredient).test('kubegroovy:builder_food' as ItemStack)).as("namespace ingredient matches builder food").isTrue()
		// Known limitation: the '*' wildcard builds an any-holder set that still
		// contains air, which vanilla Ingredient rejects. Covered here as a
		// throw until wildcard-air exclusion is implemented.
		Assertions.assertThatThrownBy(() -> '*' as Ingredient).as("wildcard ingredient currently throws").isInstanceOf(GroovyCastException)
		TestRuntime.assertThat((new JsonPrimitive('minecraft:diamond') as Ingredient).test(new ItemStack(Items.DIAMOND))).as("json ingredient matches diamond").isTrue()
		TestRuntime.assertThat((('/diamond/' as Pattern) as Ingredient).test(new ItemStack(Items.DIAMOND))).as("pattern ingredient matches diamond").isTrue()
		Assertions.assertThatThrownBy(() -> [] as Ingredient).as("empty list should not cast to Ingredient").isInstanceOf(GroovyCastException)
		Assertions.assertThatThrownBy(() -> '-' as Ingredient).as("dash should not cast to Ingredient").isInstanceOf(GroovyCastException)
		Assertions.assertThatThrownBy(() -> null as Ingredient).as("null should not cast to Ingredient").isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.item_predicate') {
		def predicate = 'minecraft:diamond' as ItemPredicate
		TestRuntime.assertThat(predicate.test(new ItemStack(Items.DIAMOND))).as("diamond predicate matches diamond").isTrue()
		TestRuntime.assertThat(predicate.test(new ItemStack(Items.STICK))).as("diamond predicate rejects stick").isFalse()
		TestRuntime.assertThat(('*' as ItemPredicate).test(new ItemStack(Items.STICK))).as("wildcard predicate matches stick").isTrue()
		TestRuntime.assertThat(('' as ItemPredicate).test(new ItemStack(Items.DIAMOND))).as("empty predicate rejects diamond").isFalse()
		TestRuntime.assertThat((null as ItemPredicate).test(new ItemStack(Items.DIAMOND))).as("null predicate rejects diamond").isFalse()
		TestRuntime.assertThat((('minecraft:diamond' as Ingredient) as ItemPredicate).test(new ItemStack(Items.DIAMOND))).as("ingredient predicate matches diamond").isTrue()
		TestRuntime.assertThat(((new BaseFunction({ stack -> true })) as ItemPredicate).test(new ItemStack(Items.STICK))).as("function predicate matches stick").isTrue()
	}
	TestRuntime.check('wrapper.sized_ingredient') {
		def ingredient = '3x minecraft:diamond' as SizedIngredient
		TestRuntime.assertThat(ingredient.count()).as("sized ingredient count").isEqualTo(3)
		TestRuntime.assertThat(ingredient.test(new ItemStack(Items.DIAMOND, 3))).as("sized ingredient matches stack").isTrue()
		def fromStack = new ItemStack(Items.STICK, 2) as SizedIngredient
		TestRuntime.assertThat(fromStack.count()).as("stack sized count").isEqualTo(2)
		TestRuntime.assertThat(fromStack.test(new ItemStack(Items.STICK, 2))).as("stack sized matches").isTrue()
		def defaultCount = 'minecraft:diamond' as SizedIngredient
		TestRuntime.assertThat(defaultCount.count()).as("default sized count").isEqualTo(1)
		def fromIngredient = ('minecraft:diamond' as Ingredient) as SizedIngredient
		TestRuntime.assertThat(fromIngredient.count()).as("ingredient sized count").isEqualTo(1)
		def fromItem = Items.DIAMOND as SizedIngredient
		TestRuntime.assertThat(fromItem.count()).as("item sized count").isEqualTo(1)
		Assertions.assertThatThrownBy(() -> '0x minecraft:diamond' as SizedIngredient).as("zero count should not cast to SizedIngredient").isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.block_state_predicate') {
		def predicate = [or: ['minecraft:stone', 'minecraft:dirt']] as BlockStatePredicate
		TestRuntime.assertThat(predicate.testBlock(Blocks.STONE)).as("or predicate matches stone").isTrue()
		TestRuntime.assertThat(predicate.testBlock(Blocks.OAK_LOG)).as("or predicate rejects log").isFalse()
		def none = '-' as BlockStatePredicate
		TestRuntime.assertThat(none.testBlock(Blocks.STONE)).as("none predicate rejects stone").isFalse()
		def regex = '/stone/' as BlockStatePredicate
		TestRuntime.assertThat(regex.testBlock(Blocks.STONE)).as("regex predicate matches stone").isTrue()
		def inverted = [not: 'minecraft:stone'] as BlockStatePredicate
		TestRuntime.assertThat(!inverted.testBlock(Blocks.STONE)).as("inverted predicate rejects stone").isTrue()
		TestRuntime.assertThat(inverted.testBlock(Blocks.DIRT)).as("inverted predicate matches dirt").isTrue()
		def tag = '#minecraft:mineable/pickaxe' as BlockStatePredicate
		TestRuntime.assertThat(tag.testBlock(Blocks.STONE)).as("tag predicate matches stone").isTrue()
		TestRuntime.assertThat(tag.testBlock(Blocks.DIRT)).as("tag predicate rejects dirt").isFalse()
		TestRuntime.assertThat(([:] as BlockStatePredicate).testBlock(Blocks.DIRT)).as("empty predicate matches dirt").isTrue()
		TestRuntime.assertThat(([] as BlockStatePredicate).testBlock(Blocks.DIRT)).as("empty list predicate rejects dirt").isFalse()
		TestRuntime.assertThat((null as BlockStatePredicate).testBlock(Blocks.DIRT)).as("null predicate matches dirt").isTrue()
		TestRuntime.assertThat(('minecraft:stone' as BlockStatePredicate).testBlock(Blocks.STONE)).as("id predicate matches stone").isTrue()
		TestRuntime.assertThat((Blocks.STONE as BlockStatePredicate).testBlock(Blocks.STONE)).as("block predicate matches stone").isTrue()
		TestRuntime.assertThat((('minecraft:stone' as BlockState) as BlockStatePredicate).testBlock(Blocks.STONE)).as("state predicate matches stone").isTrue()
	}
	TestRuntime.check('wrapper.rule_test') {
		def rule = Blocks.STONE as RuleTest
		TestRuntime.assertThat(rule.test(Blocks.STONE.defaultBlockState(), RandomSource.create())).as("stone rule matches stone").isTrue()
		TestRuntime.assertThat(rule.test(Blocks.DIRT.defaultBlockState(), RandomSource.create())).as("stone rule rejects dirt").isFalse()
		def always = [:] as RuleTest
		TestRuntime.assertThat(always.test(Blocks.DIRT.defaultBlockState(), RandomSource.create())).as("empty rule matches dirt").isTrue()
		def encoded = '{predicate_type:"minecraft:block_match",block:"minecraft:stone"}' as RuleTest
		TestRuntime.assertThat(encoded.test(Blocks.STONE.defaultBlockState(), RandomSource.create())).as("encoded rule matches stone").isTrue()
		def fromPredicate = ('minecraft:stone' as BlockStatePredicate) as RuleTest
		TestRuntime.assertThat(fromPredicate.test(Blocks.STONE.defaultBlockState(), RandomSource.create())).as("predicate rule matches stone").isTrue()
		Assertions.assertThatThrownBy(() -> 'not-a-rule-test' as RuleTest).as("string should not cast to RuleTest").isInstanceOf(GroovyCastException)
		def nullRule = null as RuleTest
		TestRuntime.assertThat(nullRule.test(Blocks.DIRT.defaultBlockState(), RandomSource.create())).as("null rule matches dirt").isTrue()
	}
	TestRuntime.check('wrapper.fluid_stack') {
		def stack = '250x minecraft:water' as FluidStack
		TestRuntime.assertThat(stack.getFluid().is(Fluids.WATER)).as("water stack fluid").isTrue()
		TestRuntime.assertThat(stack.getAmount()).as("water stack amount").isEqualTo(250)
		def bucket = '1b x minecraft:water' as FluidStack
		TestRuntime.assertThat(bucket.getAmount()).as("bucket amount").isEqualTo(1000)
		TestRuntime.assertThat(('empty' as FluidStack).isEmpty()).as("empty stack is empty").isTrue()
		TestRuntime.assertThat((null as FluidStack).isEmpty()).as("null stack is empty").isTrue()
		TestRuntime.assertThat((Fluids.WATER as FluidStack).getFluid().is(Fluids.WATER)).as("fluid to stack").isTrue()
		TestRuntime.assertThat((Fluids.EMPTY as FluidStack).isEmpty()).as("empty fluid stack is empty").isTrue()
		Assertions.assertThatThrownBy(() -> ('minecraft:water' as FluidIngredient) as FluidStack).as("fluid ingredient should not cast to FluidStack").isInstanceOf(GroovyCastException)
		Assertions.assertThatThrownBy(() -> '0x minecraft:water' as FluidStack).as("zero amount should not cast to FluidStack").isInstanceOf(GroovyCastException)
		Assertions.assertThatThrownBy(() -> 'minecraft:not_a_fluid' as FluidStack).as("unknown id should not cast to FluidStack").isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.fluid_ingredient') {
		def ingredient = 'minecraft:water' as FluidIngredient
		TestRuntime.assertThat(ingredient.test(new FluidStack(Fluids.WATER, 250))).as("water ingredient matches water").isTrue()
		TestRuntime.assertThat(ingredient.test(new FluidStack(Fluids.LAVA, 250))).as("water ingredient rejects lava").isFalse()
		def fromStack = new FluidStack(Fluids.WATER, 1) as FluidIngredient
		TestRuntime.assertThat(fromStack.test(new FluidStack(Fluids.WATER, 2))).as("stack ingredient matches water").isTrue()
		def namespace = '@minecraft' as FluidIngredient
		TestRuntime.assertThat(namespace.test(new FluidStack(Fluids.LAVA, 1))).as("namespace ingredient matches lava").isTrue()
		def regex = '/water/' as FluidIngredient
		TestRuntime.assertThat(regex.test(new FluidStack(Fluids.WATER, 1))).as("regex ingredient matches water").isTrue()
		def fromFluid = Fluids.WATER as FluidIngredient
		TestRuntime.assertThat(fromFluid.test(new FluidStack(Fluids.WATER, 1))).as("fluid ingredient matches water").isTrue()
		def fromSized = ('500x minecraft:water' as SizedFluidIngredient) as FluidIngredient
		TestRuntime.assertThat(fromSized.test(new FluidStack(Fluids.WATER, 1))).as("sized ingredient matches water").isTrue()
		Assertions.assertThatThrownBy(() -> 'empty' as FluidIngredient).as("empty should not cast to FluidIngredient").isInstanceOf(GroovyCastException)
		Assertions.assertThatThrownBy(() -> null as FluidIngredient).as("null should not cast to FluidIngredient").isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.sized_fluid_ingredient') {
		def ingredient = '500x minecraft:water' as SizedFluidIngredient
		TestRuntime.assertThat(ingredient.amount()).as("sized fluid amount").isEqualTo(500)
		TestRuntime.assertThat(ingredient.test(new FluidStack(Fluids.WATER, 500))).as("sized fluid matches").isTrue()
		def fromStack = new FluidStack(Fluids.LAVA, 333) as SizedFluidIngredient
		TestRuntime.assertThat(fromStack.amount()).as("stack sized amount").isEqualTo(333)
		TestRuntime.assertThat(fromStack.test(new FluidStack(Fluids.LAVA, 333))).as("stack sized matches").isTrue()
		def bucket = '1b x minecraft:water' as SizedFluidIngredient
		TestRuntime.assertThat(bucket.amount()).as("bucket sized amount").isEqualTo(1000)
		def fromIngredient = ('minecraft:water' as FluidIngredient) as SizedFluidIngredient
		TestRuntime.assertThat(fromIngredient.amount()).as("ingredient sized amount").isEqualTo(1000)
		def fromFluid = Fluids.LAVA as SizedFluidIngredient
		TestRuntime.assertThat(fromFluid.amount()).as("fluid sized amount").isEqualTo(1000)
		Assertions.assertThatThrownBy(() -> '0x minecraft:water' as SizedFluidIngredient).as("zero amount should not cast to SizedFluidIngredient").isInstanceOf(GroovyCastException)
		Assertions.assertThatThrownBy(() -> null as SizedFluidIngredient).as("null should not cast to SizedFluidIngredient").isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.recipe_filter') {
		def filter = '*' as RecipeFilter
		TestRuntime.assertThat(filter.test(null)).as("wildcard filter matches").isTrue()
		TestRuntime.assertThat(('-' as RecipeFilter).test(null)).as("none filter rejects").isFalse()
		TestRuntime.assertThat((null as RecipeFilter).test(null)).as("null filter matches").isTrue()
		TestRuntime.assertThat(([] as RecipeFilter).test(null)).as("empty filter rejects").isFalse()
		TestRuntime.assertThat((['-', '*'] as RecipeFilter).test(null)).as("compound filter matches").isTrue()
		TestRuntime.assertThat([id: 'minecraft:stone'] as RecipeFilter).as("id filter").isNotNull()
		TestRuntime.assertThat([type: 'minecraft:crafting_shaped'] as RecipeFilter).as("type filter").isNotNull()
		TestRuntime.assertThat(((new BaseFunction({ recipe -> true })) as RecipeFilter).test(null)).as("function filter matches").isTrue()
		Assertions.assertThatThrownBy(() -> [unknown: 'value'] as RecipeFilter).as("unknown keys should not cast to RecipeFilter").isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.slot_filter') {
		def filter = 2 as SlotFilter
		TestRuntime.assertThat(filter.checkFilter(2, ItemStack.EMPTY)).as("index filter matches slot").isTrue()
		TestRuntime.assertThat(filter.checkFilter(1, ItemStack.EMPTY)).as("index filter rejects slot").isFalse()
		def byItem = 'minecraft:diamond' as SlotFilter
		TestRuntime.assertThat(byItem.checkFilter(0, new ItemStack(Items.DIAMOND))).as("item filter matches diamond").isTrue()
		TestRuntime.assertThat(byItem.checkFilter(0, new ItemStack(Items.STICK))).as("item filter rejects stick").isFalse()
		def byRegex = '/diamond/' as SlotFilter
		TestRuntime.assertThat(byRegex.checkFilter(0, new ItemStack(Items.DIAMOND))).as("regex filter matches diamond").isTrue()
		TestRuntime.assertThat(byRegex.checkFilter(0, new ItemStack(Items.STICK))).as("regex filter rejects stick").isFalse()
		def byMap = [index: 1] as SlotFilter
		TestRuntime.assertThat(byMap.checkFilter(1, ItemStack.EMPTY)).as("map filter matches slot").isTrue()
		TestRuntime.assertThat(byMap.checkFilter(0, ItemStack.EMPTY)).as("map filter rejects slot").isFalse()
	}
	TestRuntime.check('wrapper.tool_material') {
		def material = 'diamond' as ToolMaterial
		TestRuntime.assertThat(material.is(ToolMaterial.DIAMOND)).as("diamond tool material").isTrue()
		TestRuntime.assertThat(('unknown_material' as ToolMaterial).is(ToolMaterial.IRON)).as("unknown tool material defaults to iron").isTrue()
		TestRuntime.assertThat((null as ToolMaterial).is(ToolMaterial.IRON)).as("null tool material defaults to iron").isTrue()
	}
	TestRuntime.check('wrapper.damage_source') {
		def source = 'minecraft:generic' as DamageSource
		TestRuntime.assertThat(source.getMsgId()).as("generic damage id").isEqualTo('generic')
		def overworld = ServerLifecycleHooks.getCurrentServer().getLevel("minecraft:overworld" as Identifier)
		def mob = EntityType.CREEPER.create(overworld, EntitySpawnReason.COMMAND)
		def mobSource = mob as DamageSource
		TestRuntime.assertThat(mobSource.getEntity().is(mob)).as("mob damage source entity").isTrue()
		Assertions.assertThatThrownBy(() -> null as DamageSource).as("null should not cast to DamageSource").isInstanceOf(GroovyCastException)
		Assertions.assertThatThrownBy(() -> 'minecraft:not_a_damage_type' as DamageSource).as("unknown id should not cast to DamageSource").isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.entity_selector') {
		def selector = '@e[type=minecraft:pig,limit=1]' as net.minecraft.commands.arguments.selector.EntitySelector
		TestRuntime.assertThat(selector.getMaxResults()).as("selector max results").isEqualTo(1)
		TestRuntime.assertThat(selector.includesEntities()).as("selector includes entities").isTrue()
		def self = '@s' as net.minecraft.commands.arguments.selector.EntitySelector
		TestRuntime.assertThat(self.getMaxResults()).as("self selector max results").isEqualTo(1)
		Assertions.assertThatThrownBy(() -> '' as net.minecraft.commands.arguments.selector.EntitySelector).as("empty string should not cast to EntitySelector").isInstanceOf(GroovyCastException)
		Assertions.assertThatThrownBy(() -> '@e[invalid' as net.minecraft.commands.arguments.selector.EntitySelector).as("broken selector should not cast to EntitySelector").isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.replacement_match') {
		def match = 'minecraft:diamond' as ReplacementMatch
		TestRuntime.assertThat(match.matches(null, new ItemStack(Items.DIAMOND), false)).as("diamond match matches diamond").isTrue()
		TestRuntime.assertThat(match.matches(null, new ItemStack(Items.STICK), false)).as("diamond match rejects stick").isFalse()
		def compound = ['minecraft:diamond', 'minecraft:stick'] as ReplacementMatch
		TestRuntime.assertThat(compound.matches(null, new ItemStack(Items.STICK), false)).as("compound match matches stick").isTrue()
		TestRuntime.assertThat((null as ReplacementMatch).toString()).as("null match").isEqualTo('NONE')
		Assertions.assertThatThrownBy(() -> '' as ReplacementMatch).as("empty string should not cast to ReplacementMatch").isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.replacement_match_info') {
		def info = 'minecraft:diamond' as ReplacementMatchInfo
		TestRuntime.assertThat(info.exact()).as("id match is not exact").isFalse()
		TestRuntime.assertThat(info.match().matches(null, new ItemStack(Items.DIAMOND), false)).as("info match matches diamond").isTrue()
		def none = null as ReplacementMatchInfo
		TestRuntime.assertThat(none.is(ReplacementMatchInfo.NONE)).as("null info is none").isTrue()
	}
	TestRuntime.check('wrapper.stat') {
		def stat = Stats.JUMP as Stat
		TestRuntime.assertThat(stat.getValue().toString()).as("jump stat id").isEqualTo('minecraft:jump')
		Assertions.assertThatThrownBy(() -> 'minecraft:not_a_stat' as Stat).as("unknown id should not cast to Stat").isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.sound_type') {
		def sound = 'wool' as SoundType
		TestRuntime.assertThat(sound.is(SoundType.WOOL)).as("wool sound type").isTrue()
		TestRuntime.assertThat(('empty' as SoundType).is(SoundType.EMPTY)).as("empty sound type").isTrue()
		TestRuntime.assertThat((new JsonPrimitive('wool') as SoundType).is(SoundType.WOOL)).as("json sound type").isTrue()
		Assertions.assertThatThrownBy(() -> 'not-a-sound-type' as SoundType).as("unknown name should not cast to SoundType").isInstanceOf(GroovyCastException)
		Assertions.assertThatThrownBy(() -> null as SoundType).as("null should not cast to SoundType").isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.particle_options') {
		def particle = 'minecraft:flame' as ParticleOptions
		TestRuntime.assertThat(particle.getType().is(ParticleTypes.FLAME)).as("flame particle type").isTrue()
		def json = new JsonPrimitive('minecraft:flame') as ParticleOptions
		TestRuntime.assertThat(json.getType().is(ParticleTypes.FLAME)).as("json particle type").isTrue()
		def missing = null as ParticleOptions
		TestRuntime.assertThat(missing.getType().is(ParticleTypes.DUST)).as("null particle defaults to dust").isTrue()
		Assertions.assertThatThrownBy(() -> 'minecraft:not_a_particle' as ParticleOptions).as("unknown id should not cast to ParticleOptions").isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.item_tint') {
		def fixed = '#112233' as ItemTintFunction
		TestRuntime.assertThat(fixed.getColor(ItemStack.EMPTY, 0).toHexString()).as("fixed tint color").isEqualTo('#112233')
		def mapped = ['#010203', null, '#A0B0C0'] as ItemTintFunction
		TestRuntime.assertThat(mapped.getMaxTintIndex()).as("mapped tint max index").isEqualTo(2)
		TestRuntime.assertThat(mapped.getColor(ItemStack.EMPTY, 2).toHexString()).as("mapped tint color").isEqualTo('#A0B0C0')
		TestRuntime.assertThat(mapped.getColor(ItemStack.EMPTY, 1)).as("mapped tint hole").isNull()
		TestRuntime.assertThat(('map' as ItemTintFunction).getColor(ItemStack.EMPTY, 0)).as("map tint color").isNull()
		TestRuntime.assertThat(null as ItemTintFunction).as("null tint").isNull()
	}
	TestRuntime.check('wrapper.block_tint') {
		def fixed = '#445566' as BlockTintFunction
		def state = Blocks.STONE.defaultBlockState()
		TestRuntime.assertThat(fixed.getColor(state, null, null, 0).toHexString()).as("fixed block tint color").isEqualTo('#445566')
		def mapped = ['#010203', null, '#A0B0C0'] as BlockTintFunction
		TestRuntime.assertThat(mapped.getMaxTintIndex()).as("mapped block tint max index").isEqualTo(2)
		TestRuntime.assertThat(mapped.getColor(state, null, null, 2).toHexString()).as("mapped block tint color").isEqualTo('#A0B0C0')
		TestRuntime.assertThat(mapped.getColor(state, null, null, 1)).as("mapped block tint hole").isNull()
		TestRuntime.assertThat(('water' as BlockTintFunction).getColor(state, null, null, 0)).as("water block tint color").isNull()
		TestRuntime.assertThat(null as BlockTintFunction).as("null block tint").isNull()
	}
	TestRuntime.check('wrapper.text_color') {
		def color = '#123456' as TextColor
		TestRuntime.assertThat(color.getValue()).as("hex text color").isEqualTo(0x123456)
		TestRuntime.assertThat(('none' as TextColor).getValue()).as("none text color").isEqualTo(0)
		TestRuntime.assertThat(('not-a-color' as TextColor).getValue()).as("unknown text color").isEqualTo(0)
	}
	TestRuntime.check('wrapper.click_event') {
		def shorthand = 'command:/say wrapper' as ClickEvent
		TestRuntime.assertThat(shorthand instanceof ClickEvent.RunCommand).as("command shorthand type").isTrue()
		TestRuntime.assertThat(shorthand.command()).as("command shorthand value").isEqualTo('/say wrapper')
		def structured = [action: 'copy_to_clipboard', value: 'wrapper'] as ClickEvent
		TestRuntime.assertThat(structured instanceof ClickEvent.CopyToClipboard).as("structured copy type").isTrue()
		TestRuntime.assertThat(structured.value()).as("structured copy value").isEqualTo('wrapper')
		def json = '{"action":"copy_to_clipboard","value":"json wrapper"}' as ClickEvent
		TestRuntime.assertThat(json instanceof ClickEvent.CopyToClipboard).as("json copy type").isTrue()
		TestRuntime.assertThat(json.value()).as("json copy value").isEqualTo('json wrapper')
		def url = 'https://example.com' as ClickEvent
		TestRuntime.assertThat(url instanceof ClickEvent.OpenUrl).as("url type").isTrue()
		TestRuntime.assertThat(url.uri().toString()).as("url value").isEqualTo('https://example.com')
		def copy = 'copy:copied text' as ClickEvent
		TestRuntime.assertThat(copy instanceof ClickEvent.CopyToClipboard).as("copy shorthand type").isTrue()
		TestRuntime.assertThat(copy.value()).as("copy shorthand value").isEqualTo('copied text')
		def page = 'page:2' as ClickEvent
		TestRuntime.assertThat(page instanceof ClickEvent.ChangePage).as("page shorthand type").isTrue()
		TestRuntime.assertThat(page.page()).as("page shorthand value").isEqualTo(2)
		def file = 'file:wrapper.txt' as ClickEvent
		TestRuntime.assertThat(file instanceof ClickEvent.OpenFile).as("file shorthand type").isTrue()
		TestRuntime.assertThat(file.file().getPath().endsWith('wrapper.txt')).as("file shorthand value").isTrue()
		TestRuntime.assertThat(null as ClickEvent).as("null click event").isNull()
		Assertions.assertThatThrownBy(() -> 'page:not-a-number' as ClickEvent).as("bad page should not cast to ClickEvent").isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.path') {
		def p = 'kubegroovy/test_wrapper.txt' as Path
		TestRuntime.assertThat(p).as("sandboxed path").isNotNull()
		TestRuntime.assertThat(p.toString().replace('\\', '/').endsWith('kubegroovy/test_wrapper.txt')).as("sandboxed path value").isTrue()
		TestRuntime.assertThat(null as Path).as("null path").isNull()
		TestRuntime.assertThat('' as Path).as("empty path").isNull()
		Assertions.assertThatThrownBy(() -> '../outside_wrapper.txt' as Path).as("escaping path should not cast to Path").isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.file') {
		def f = 'kubegroovy/test_wrapper.txt' as File
		TestRuntime.assertThat(f).as("sandboxed file").isNotNull()
		TestRuntime.assertThat(f.getPath().replace('\\', '/').endsWith('kubegroovy/test_wrapper.txt')).as("sandboxed file value").isTrue()
		TestRuntime.assertThat(null as File).as("null file").isNull()
		TestRuntime.assertThat('' as File).as("empty file").isNull()
		Assertions.assertThatThrownBy(() -> '../outside_wrapper.txt' as File).as("escaping path should not cast to File").isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.mob_category') {
		TestRuntime.assertThat(('monster' as MobCategory).is(MobCategory.MONSTER)).as("lowercase mob category").isTrue()
		TestRuntime.assertThat(('MONSTER' as MobCategory).is(MobCategory.MONSTER)).as("uppercase mob category").isTrue()
		TestRuntime.assertThat('' as MobCategory).as("empty mob category").isNull()
		TestRuntime.assertThat(null as MobCategory).as("null mob category").isNull()
		Assertions.assertThatThrownBy(() -> 'not_a_category' as MobCategory).as("unknown name should not cast to MobCategory").isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.armor_type') {
		TestRuntime.assertThat(('chestplate' as ArmorType).is(ArmorType.CHESTPLATE)).as("lowercase armor type").isTrue()
		TestRuntime.assertThat(('CHESTPLATE' as ArmorType).is(ArmorType.CHESTPLATE)).as("uppercase armor type").isTrue()
		TestRuntime.assertThat('' as ArmorType).as("empty armor type").isNull()
		TestRuntime.assertThat(null as ArmorType).as("null armor type").isNull()
		Assertions.assertThatThrownBy(() -> 'not_an_armor_type' as ArmorType).as("unknown name should not cast to ArmorType").isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.entity_target') {
		TestRuntime.assertThat(('this' as EntityTarget).is(EntityTarget.THIS)).as("lowercase entity target").isTrue()
		TestRuntime.assertThat(('THIS' as EntityTarget).is(EntityTarget.THIS)).as("uppercase entity target").isTrue()
		TestRuntime.assertThat('' as EntityTarget).as("empty entity target").isNull()
		TestRuntime.assertThat(null as EntityTarget).as("null entity target").isNull()
		Assertions.assertThatThrownBy(() -> 'not_a_target' as EntityTarget).as("unknown name should not cast to EntityTarget").isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.armor_material') {
		TestRuntime.assertThat(null as ArmorMaterial).as("null armor material").isNull()
		TestRuntime.assertThat('diamond' as ArmorMaterial).as("unknown armor material").isNull()
		TestRuntime.assertThat(42 as ArmorMaterial).as("number armor material").isNull()
	}
	TestRuntime.check('wrapper.player_selector') {
		def uuid = UUID.fromString('12345678-1234-1234-1234-123456789abc')
		TestRuntime.assertThat(uuid as PlayerSelector).as("uuid player selector").isNotNull()
		TestRuntime.assertThat('12345678-1234-1234-1234-123456789abc' as PlayerSelector).as("string player selector").isNotNull()
		TestRuntime.assertThat(new BaseFunction({ server -> null }) as PlayerSelector).as("function player selector").isNotNull()
		Assertions.assertThatThrownBy(() -> null as PlayerSelector).as("null should not cast to PlayerSelector").isInstanceOf(GroovyCastException)
		Assertions.assertThatThrownBy(() -> '' as PlayerSelector).as("empty string should not cast to PlayerSelector").isInstanceOf(GroovyCastException)
		Assertions.assertThatThrownBy(() -> '   ' as PlayerSelector).as("blank string should not cast to PlayerSelector").isInstanceOf(GroovyCastException)
		Assertions.assertThatThrownBy(() -> 'steve' as PlayerSelector).as("name should not cast to PlayerSelector").isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.component_alias') {
		def m = 'hello alias' as MutableComponent
		def c = m as Component
		TestRuntime.assertThat(c.is(m)).as("component alias preserves instance").isTrue()
		TestRuntime.assertThat(c.getString()).as("component alias text").isEqualTo('hello alias')
		TestRuntime.assertThat(null as Component).as("null component").isNull()
		Assertions.assertThatThrownBy(() -> 'hello' as Component).as("string should not cast to Component").isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.item_lore') {
		def empty = [] as ItemLore
		TestRuntime.assertThat(empty.lines().isEmpty()).as("empty lore is empty").isTrue()
		def lore = ['hello' as MutableComponent, 'world' as MutableComponent] as ItemLore
		TestRuntime.assertThat(lore.lines()).as("lore lines").hasSize(2)
		TestRuntime.assertThat(lore.lines().get(0).getString()).as("first lore line").isEqualTo('hello')
		TestRuntime.assertThat(null as ItemLore).as("null lore").isNull()
	}
	TestRuntime.check('wrapper.item_attribute_modifiers') {
		def empty = [] as ItemAttributeModifiers
		TestRuntime.assertThat(empty.modifiers().isEmpty()).as("empty modifiers are empty").isTrue()
		TestRuntime.assertThat(null as ItemAttributeModifiers).as("null modifiers").isNull()
	}
	TestRuntime.check('wrapper.fireworks') {
		TestRuntime.assertThat(null as Fireworks).as("null fireworks").isNull()
		def withFlight = [flight_duration: 2, explosions: []] as Fireworks
		TestRuntime.assertThat(withFlight).as("flight fireworks").isNotNull()
		TestRuntime.assertThat(withFlight.flightDuration()).as("flight duration").isEqualTo(2)
		Assertions.assertThatThrownBy(() -> 42 as Fireworks).as("number should not cast to Fireworks").isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.kube_icon') {
		TestRuntime.assertThat(null as KubeIcon).as("null icon").isNull()
		def tex = [type: 'kubegroovy:texture', texture: 'minecraft:block/stone'] as KubeIcon
		TestRuntime.assertThat(tex instanceof TextureKubeIcon).as("texture icon type").isTrue()
		TestRuntime.assertThat(tex.texture().toString()).as("texture icon value").isEqualTo('minecraft:block/stone')
		Assertions.assertThatThrownBy(() -> [:] as KubeIcon).as("empty map should not cast to KubeIcon").isInstanceOf(GroovyCastException)
		Assertions.assertThatThrownBy(() -> 42 as KubeIcon).as("number should not cast to KubeIcon").isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.unit') {
		TestRuntime.assertThat(('anything' as Unit).is(Unit.INSTANCE)).as("string unit is instance").isTrue()
		TestRuntime.assertThat((42 as Unit).is(Unit.INSTANCE)).as("number unit is instance").isTrue()
		TestRuntime.assertThat(null as Unit).as("null unit").isNull()
	}
	TestRuntime.check('wrapper.custom_data') {
		def data = ([a: 1] as CompoundTag) as CustomData
		TestRuntime.assertThat(data.copyTag().contains('a')).as("custom data key").isTrue()
		def fromString = ('{b:2}' as CompoundTag) as CustomData
		TestRuntime.assertThat(fromString.copyTag().contains('b')).as("parsed custom data key").isTrue()
		TestRuntime.assertThat(null as CustomData).as("null custom data").isNull()
	}
	TestRuntime.check('wrapper.custom_model_data') {
		def cmd = 5 as CustomModelData
		TestRuntime.assertThat(cmd.colors()).as("custom model data colors").isEqualTo([5])
		TestRuntime.assertThat(null as CustomModelData).as("null custom model data").isNull()
	}
	TestRuntime.check('wrapper.lock_code') {
		def lock = 'minecraft:stick' as LockCode
		TestRuntime.assertThat(lock).as("stick lock code").isNotNull()
		TestRuntime.assertThat(lock.unlocksWith(new ItemStack(Items.STICK))).as("lock opens with stick").isTrue()
		TestRuntime.assertThat(lock.unlocksWith(new ItemStack(Items.DIAMOND))).as("lock rejects diamond").isFalse()
		TestRuntime.assertThat((Items.STICK as LockCode).unlocksWith(new ItemStack(Items.STICK))).as("item lock opens with stick").isTrue()
		TestRuntime.assertThat(null as LockCode).as("null lock code").isNull()
		Assertions.assertThatThrownBy(() -> 'minecraft:not_an_item' as LockCode).as("unknown id should not cast to LockCode").isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.block_state_properties') {
		def empty = [:] as BlockItemStateProperties
		TestRuntime.assertThat(empty.properties().isEmpty()).as("empty state properties are empty").isTrue()
		def withProp = [facing: 'north'] as BlockItemStateProperties
		TestRuntime.assertThat(withProp.properties().get('facing')).as("facing property").isEqualTo('north')
		TestRuntime.assertThat(null as BlockItemStateProperties).as("null state properties").isNull()
	}
	TestRuntime.check('wrapper.item_resource') {
		def res = ('minecraft:diamond' as ItemStack) as ItemResource
		TestRuntime.assertThat(res).as("diamond item resource").isNotNull()
		TestRuntime.assertThat(res.isEmpty()).as("diamond resource is not empty").isFalse()
		TestRuntime.assertThat(null as ItemResource).as("null item resource").isNull()
	}
	TestRuntime.check('wrapper.fluid_resource') {
		def res = ('minecraft:water' as FluidStack) as FluidResource
		TestRuntime.assertThat(res).as("water fluid resource").isNotNull()
		TestRuntime.assertThat(null as FluidResource).as("null fluid resource").isNull()
	}
	TestRuntime.check('wrapper.holder_set') {
		Assertions.assertThatThrownBy(() -> 'minecraft:diamond' as HolderSet<Item>).as("raw id should not cast to HolderSet").isInstanceOf(GroovyCastException)
		def itemRegistry = event.getServer().registryAccess().lookupOrThrow(Registries.ITEM)
		def emptySet = HolderWrapper.wrapSimpleSet(itemRegistry, '')
		TestRuntime.assertThat(emptySet.size()).as("empty holder set size").isEqualTo(0)
		def tagSet = HolderWrapper.wrapSimpleSet(itemRegistry, '#minecraft:planks')
		TestRuntime.assertThat(tagSet.size() > 0).as("tag holder set is not empty").isTrue()
		def nsSet = HolderWrapper.wrapSimpleSet(itemRegistry, '@minecraft')
		TestRuntime.assertThat(nsSet.size() > 0).as("namespace holder set is not empty").isTrue()
		def regexSet = HolderWrapper.wrapSimpleSet(itemRegistry, '/diamond/')
		TestRuntime.assertThat(regexSet.size() > 0).as("regex holder set is not empty").isTrue()
		TestRuntime.assertThat(HolderWrapper.wrapSimpleSet(itemRegistry, 42)).as("number holder set").isNull()
	}
}
