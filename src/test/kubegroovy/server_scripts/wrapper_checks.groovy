import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import dev.uncandango.kubegroovy.block.BlockTintFunction
import dev.uncandango.kubegroovy.color.KubeColor
import dev.uncandango.kubegroovy.item.ItemPredicate
import dev.uncandango.kubegroovy.item.ItemTintFunction
import dev.uncandango.kubegroovy.recipe.filter.RecipeFilter
import dev.uncandango.kubegroovy.recipe.match.ReplacementMatch
import dev.uncandango.kubegroovy.recipe.match.ReplacementMatchInfo
import dev.uncandango.kubegroovy.block.state.BlockStatePredicate
import dev.uncandango.kubegroovy.testmod.TestRuntime
import dev.uncandango.kubegroovy.util.KubeIdentifier
import dev.uncandango.kubegroovy.util.SlotFilter
import dev.uncandango.kubegroovy.util.TickDuration
import dev.uncandango.kubegroovy.util.Tristate
import dev.uncandango.kubegroovy.util.registrypredicate.RegistryPredicate
import net.minecraft.core.BlockPos
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
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.TextColor
import net.minecraft.resources.Identifier
import net.minecraft.stats.Stat
import net.minecraft.stats.Stats
import net.minecraft.util.ColorRGBA
import net.minecraft.util.RandomSource
import net.minecraft.util.valueproviders.FloatProvider
import net.minecraft.util.valueproviders.IntProvider
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EntityType
import net.minecraft.world.item.Item
import net.minecraft.world.item.enchantment.ItemEnchantments
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.ToolMaterial
import net.minecraft.world.item.crafting.Ingredient
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockSetType
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleTest
import net.minecraft.world.level.ItemLike
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.level.material.MapColor
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
import org.codehaus.groovy.runtime.typehandling.GroovyCastException

import java.time.Duration
import java.time.temporal.TemporalAmount
import java.util.UUID
import java.util.regex.Pattern

import org.assertj.core.api.Assertions
import dev.uncandango.kubegroovy.error.KubeRuntimeException

import net.minecraft.util.valueproviders.WeightedListInt

def wrapperChecksRun = false
ServerEvents.tick { event ->
	if (wrapperChecksRun) return
	wrapperChecksRun = true

	TestRuntime.check('wrapper.vec3') {
		def v = [1, 2, 3] as Vec3
		TestRuntime.assertThat(v.x()).isEqualTo(1.0d)
		TestRuntime.assertThat(v.y()).isEqualTo(2.0d)
		TestRuntime.assertThat(v.z()).isEqualTo(3.0d)
		def positionMapToInterface = [x: { 1d }, y: { 2d }, z: { 3d }] as Position
		def v2 = positionMapToInterface as Vec3
		TestRuntime.assertThat(v2.x()).isEqualTo(1.0d)
		TestRuntime.assertThat(v2.y()).isEqualTo(2.0d)
		TestRuntime.assertThat(v2.z()).isEqualTo(3.0d)
		Assertions.assertThatThrownBy(() -> [1,2] as Vec3).isInstanceOf(GroovyCastException)
		def v3 = [1,2,3] as BlockPos as Vec3
		TestRuntime.assertThat(v3.x()).isEqualTo(1.5d)
		TestRuntime.assertThat(v3.y()).isEqualTo(2.5d)
		TestRuntime.assertThat(v3.z()).isEqualTo(3.5d)
		def parsedDefaults = ['not-a-number', '2.5', null] as Vec3
		TestRuntime.assertThat(parsedDefaults.x()).isEqualTo(0.0d)
		TestRuntime.assertThat(parsedDefaults.y()).isEqualTo(2.5d)
		TestRuntime.assertThat(parsedDefaults.z()).isEqualTo(0.0d)

		def overworld = ServerLifecycleHooks.getCurrentServer().getLevel("minecraft:overworld" as Identifier)
		def entity = EntityType.CREEPER.create(overworld, EntitySpawnReason.COMMAND)
		TestRuntime.assertThat(entity as Vec3).isEqualTo(entity.position())

		def v4 = entity.getBlock()
		TestRuntime.assertThat(v4 as Vec3).isEqualTo(new Vec3(v4.centerX, v4.centerY, v4.centerZ))

		Assertions.assertThatThrownBy(() -> "strings are invalid" as Vec3).isInstanceOf(GroovyCastException)
		Assertions.assertThatThrownBy(() -> null as Vec3).isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.blockpos') {
		def p = [4, 5, 6].asType(BlockPos)
		TestRuntime.assertThat(p.toShortString()).isEqualTo('4, 5, 6')
		def defaults = [4, 'not-a-number', null] as BlockPos
		TestRuntime.assertThat(defaults.toShortString()).isEqualTo('4, 0, 0')
		Assertions.assertThatThrownBy(() -> [1, 2] as BlockPos).isInstanceOf(GroovyCastException)
		Assertions.assertThatThrownBy(() -> null as BlockPos).isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.itemstack') {
		def stack = 'minecraft:diamond' as ItemStack
		TestRuntime.assertThat(stack.id).isEqualTo('minecraft:diamond')
		def sized = '2x minecraft:diamond' as ItemStack
		TestRuntime.assertThat(sized.getCount()).isEqualTo(2)
		def air = 'air' as ItemStack
		TestRuntime.assertThat(air.isEmpty()).isTrue()
		def json = new JsonPrimitive('minecraft:stick') as ItemStack
		TestRuntime.assertThat(json.id).isEqualTo('minecraft:stick')
		def mapped = [item: 'minecraft:stick', count: 'invalid'] as ItemStack
		TestRuntime.assertThat(mapped.id).isEqualTo('minecraft:stick')
		TestRuntime.assertThat(mapped.getCount()).isEqualTo(1)
		TestRuntime.assertThat(('0x minecraft:diamond' as ItemStack).isEmpty()).isTrue()
	}
	TestRuntime.check('wrapper.component') {
		def component = 'hello' as MutableComponent
		TestRuntime.assertThat(component.getString()).isEqualTo('hello')
		def styled = [text: 'hello', color: '#ff0000', bold: true, extra: ['!']] as MutableComponent
		TestRuntime.assertThat(styled.getString()).isEqualTo('hello!')
		TestRuntime.assertThat(styled.getStyle().isBold()).isTrue()
		TestRuntime.assertThat(styled.getStyle().getColor().getValue()).isEqualTo(0xff0000)
		def empty = '' as MutableComponent
		TestRuntime.assertThat(empty.getString()).isEmpty()
		def json = new JsonPrimitive('json component') as MutableComponent
		TestRuntime.assertThat(json.getString()).isEqualTo('json component')
		def missing = null as MutableComponent
		TestRuntime.assertThat(missing.getString()).isEqualTo('null')
	}
	TestRuntime.check('wrapper.nbt') {
		def tag = [a: 1, b: 'two'] as CompoundTag
		TestRuntime.assertThat(tag.contains('a')).isTrue()
		TestRuntime.assertThat(tag.contains('b')).isTrue()
		def structured = '{nested:{value:"ok"},numbers:[I;1,2,3]}' as CompoundTag
		TestRuntime.assertThat(structured.getCompoundOrEmpty('nested').getStringOr('value', '')).isEqualTo('ok')
		TestRuntime.assertThat(structured.getIntArray('numbers').orElseThrow().length).isEqualTo(3)
		def bytes = [1 as byte, 2 as byte] as Tag
		TestRuntime.assertThat(bytes.getId()).isEqualTo(Tag.TAG_BYTE_ARRAY)
		def booleanTag = true as Tag
		TestRuntime.assertThat(booleanTag.getId()).isEqualTo(Tag.TAG_BYTE)
		def empty = [:] as CompoundTag
		TestRuntime.assertThat(empty.isEmpty()).isTrue()
		Assertions.assertThatThrownBy(() -> 'not a compound' as CompoundTag).isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.color') {
		def color = '#ff0000' as KubeColor
		TestRuntime.assertThat(color.toHexString()).isEqualTo('#FF0000')
		def transparent = 'none' as KubeColor
		TestRuntime.assertThat(transparent.toHexString()).isEqualTo('#00000000')
		def zero = 0 as KubeColor
		TestRuntime.assertThat(zero.toHexString()).isEqualTo('#00000000')
		def unknown = 'not-a-color' as KubeColor
		TestRuntime.assertThat(unknown.toHexString()).isEqualTo('#00000000')
	}
	TestRuntime.check('wrapper.id') {
		def id = 'kubegroovy:test' as Identifier
		TestRuntime.assertThat(id.toString()).isEqualTo('kubegroovy:test')
		def defaultNamespace = 'stone' as Identifier
		TestRuntime.assertThat(defaultNamespace.toString()).isEqualTo('minecraft:stone')
		def json = new JsonPrimitive('minecraft:dirt') as Identifier
		TestRuntime.assertThat(json.toString()).isEqualTo('minecraft:dirt')
		Assertions.assertThatThrownBy(() -> 'bad id!' as Identifier).isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.uuid') {
		def uuid = '12345678-1234-1234-1234-123456789abc' as UUID
		TestRuntime.assertThat(uuid.toString()).isEqualTo('12345678-1234-1234-1234-123456789abc')
		def compact = '12345678123412341234123456789abc' as UUID
		TestRuntime.assertThat(compact.toString()).isEqualTo('12345678-1234-1234-1234-123456789abc')
		Assertions.assertThatThrownBy(() -> 'too-short' as UUID).isInstanceOf(GroovyCastException)
		Assertions.assertThatThrownBy(() -> '12345678-1234-1234-1234-xxxxxxxxxxxx' as UUID).isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.tristate') {
		def tristate = 'true' as Tristate
		TestRuntime.assertThat(tristate.getSerializedName()).isEqualTo('true')
		TestRuntime.assertThat((false as Tristate).getSerializedName()).isEqualTo('false')
		TestRuntime.assertThat(('unrecognised' as Tristate).getSerializedName()).isEqualTo('default')
		TestRuntime.assertThat((null as Tristate).getSerializedName()).isEqualTo('default')
	}
	TestRuntime.check('wrapper.duration') {
		def duration = '20t' as Duration
		TestRuntime.assertThat(duration.toMillis() == 1000).isTrue()
		TestRuntime.assertThat((1500 as Duration).toMillis()).isEqualTo(1500L)
		TestRuntime.assertThat(('2m 5s' as Duration).toMillis()).isEqualTo(125000L)
		Assertions.assertThatThrownBy(() -> 'malformed' as Duration).isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.regexp') {
		def pattern = '/foo/i' as Pattern
		TestRuntime.assertThat(pattern.pattern()).isEqualTo('foo')
		TestRuntime.assertThat((pattern.flags() & Pattern.CASE_INSENSITIVE) != 0).isTrue()
		def escaped = '/foo\\/bar/s' as Pattern
		TestRuntime.assertThat(escaped.matcher('foo/bar').find()).isTrue()
		def noPattern = 'plain text' as Pattern
		TestRuntime.assertThat(noPattern).isNull()
	}
	TestRuntime.check('wrapper.int_provider') {
		def constant = 3 as IntProvider
		TestRuntime.assertThat(constant.minInclusive() == 3 && constant.maxInclusive() == 3).isTrue()
		def range = [2, 8] as IntProvider
		TestRuntime.assertThat(range.minInclusive() == 2 && range.maxInclusive() == 8).isTrue()
		def parsed = ['7'] as IntProvider
		TestRuntime.assertThat(parsed.minInclusive() == 7 && parsed.maxInclusive() == 7).isTrue()
		def bounds = [min_inclusive: 9, max_inclusive: 4] as IntProvider
		TestRuntime.assertThat(bounds.minInclusive() == 4 && bounds.maxInclusive() == 9).isTrue()
		def clamped = [clamped: 3, min: 2, max: 4] as IntProvider
		TestRuntime.assertThat(clamped.minInclusive() == 3 && clamped.maxInclusive() == 3).isTrue()
		def clampedNormal = [clamped_normal: 3, mean: 2, deviation: 1, min: 1, max: 5] as IntProvider
		TestRuntime.assertThat(clampedNormal.minInclusive()).isEqualTo(1)
		TestRuntime.assertThat(clampedNormal.maxInclusive()).isEqualTo(5)
		TestRuntime.assertThat(clamped.minInclusive() == 3 && clamped.maxInclusive() == 3).isTrue()
		def weightedList = [type: "weighted_list", distribution: [[data: [type: "constant", value: 9], weight: 1],[data: [type: "constant", value: 3], weight: 2]]] as IntProvider
		if (weightedList instanceof WeightedListInt wl) {
			TestRuntime.assertThat(wl.minInclusive()).isEqualTo(3)
			TestRuntime.assertThat(wl.maxInclusive()).isEqualTo(9)
		}
		Assertions.assertThatThrownBy(() -> [] as IntProvider).isInstanceOf(GroovyCastException)
		Assertions.assertThatThrownBy(() -> [1,2,3] as IntProvider).isInstanceOf(GroovyCastException)
		Assertions.assertThatThrownBy(() -> null as IntProvider).isInstanceOf(GroovyCastException)
		Assertions.assertThatThrownBy(() -> [something: 2, invalid: 3] as IntProvider).isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.map_color') {
		def color = 'color_red' as MapColor
		TestRuntime.assertThat(color.col).isEqualTo(10040115)
		TestRuntime.assertThat(('STONE' as MapColor).col).isEqualTo(MapColor.STONE.col)
		TestRuntime.assertThat(('#ff0000' as MapColor)).isNotSameAs(MapColor.NONE)
		Assertions.assertThatThrownBy(() -> '' as MapColor).isInstanceOf(GroovyCastException)
		Assertions.assertThatThrownBy(() -> 'not-a-map-color' as MapColor).isInstanceOf(GroovyCastException)
	}

	TestRuntime.check('wrapper.registry_predicate') {
		def predicate = 'minecraft:diamond' as RegistryPredicate
		TestRuntime.assertThat(predicate.test(Items.DIAMOND.builtInRegistryHolder())).isTrue()
		TestRuntime.assertThat(predicate.test(Items.STICK.builtInRegistryHolder())).isFalse()
		def all = '*' as RegistryPredicate
		TestRuntime.assertThat(all.test(Items.STICK.builtInRegistryHolder())).isTrue()
		def none = '-' as RegistryPredicate
		TestRuntime.assertThat(none.test(Items.DIAMOND.builtInRegistryHolder())).isFalse()
		def namespace = '@minecraft' as RegistryPredicate
		TestRuntime.assertThat(namespace.test(Items.STICK.builtInRegistryHolder())).isTrue()
		def regex = '/diamond/' as RegistryPredicate
		TestRuntime.assertThat(regex.test(Items.DIAMOND.builtInRegistryHolder())).isTrue()
		def any = ['minecraft:stick', 'minecraft:diamond'] as RegistryPredicate
		TestRuntime.assertThat(any.test(Items.DIAMOND.builtInRegistryHolder())).isTrue()
		TestRuntime.assertThat(([] as RegistryPredicate).test(Items.DIAMOND.builtInRegistryHolder())).isFalse()
	}
	TestRuntime.check('wrapper.json_object') {
		def object = [name: 'wrapper', count: 2] as JsonObject
		TestRuntime.assertThat(object.get('name').getAsString()).isEqualTo('wrapper')
		TestRuntime.assertThat(object.get('count').getAsInt()).isEqualTo(2)
		def parsed = '{"nested":{"ok":true},"number":2}' as JsonObject
		TestRuntime.assertThat(parsed.getAsJsonObject('nested').get('ok').getAsBoolean()).isTrue()
		TestRuntime.assertThat(parsed.get('number').getAsInt()).isEqualTo(2)
		def empty = [:] as JsonObject
		TestRuntime.assertThat(empty.isEmpty()).isTrue()
		Assertions.assertThatThrownBy(() -> 'not json' as JsonObject).isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.json_array') {
		def array = ['wrapper', 2, true] as JsonArray
		TestRuntime.assertThat(array.size()).isEqualTo(3)
		TestRuntime.assertThat(array.get(1).getAsInt()).isEqualTo(2)
		def parsed = '[1,null,{"ok":false}]' as JsonArray
		TestRuntime.assertThat(parsed.get(0).getAsInt()).isEqualTo(1)
		TestRuntime.assertThat(parsed.get(1).isJsonNull()).isTrue()
		TestRuntime.assertThat(parsed.get(2).getAsJsonObject().get('ok').getAsBoolean()).isFalse()
		def empty = [] as JsonArray
		TestRuntime.assertThat(empty.isEmpty()).isTrue()
		Assertions.assertThatThrownBy(() -> 'not json' as JsonArray).isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.json_element') {
		def element = [wrapped: true] as JsonElement
		TestRuntime.assertThat(element.isJsonObject()).isTrue()
		TestRuntime.assertThat(element.getAsJsonObject().get('wrapped').getAsBoolean()).isTrue()
		def primitive = 'text' as JsonElement
		TestRuntime.assertThat(primitive.isJsonPrimitive()).isTrue()
		TestRuntime.assertThat(primitive.getAsString()).isEqualTo('text')
		def nullElement = null as JsonElement
		TestRuntime.assertThat(nullElement.isJsonNull()).isTrue()
	}
	TestRuntime.check('wrapper.json_primitive') {
		def primitive = 42 as JsonPrimitive
		TestRuntime.assertThat(primitive.getAsInt()).isEqualTo(42)
		TestRuntime.assertThat((true as JsonPrimitive).getAsBoolean()).isTrue()
		TestRuntime.assertThat(('text' as JsonPrimitive).getAsString()).isEqualTo('text')
		def object = [nested: true] as JsonPrimitive
		TestRuntime.assertThat(object).isNull()
	}
	TestRuntime.check('wrapper.temporal_amount') {
		def amount = '1s 10t' as TemporalAmount
		TestRuntime.assertThat(amount.toString()).isEqualTo('30 ticks')
		TestRuntime.assertThat((1000 as TemporalAmount).toString()).isEqualTo('PT1S')
		Assertions.assertThatThrownBy(() -> '' as TemporalAmount).isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.tick_duration') {
		def ticks = '1s 10t' as TickDuration
		TestRuntime.assertThat(ticks.ticks()).isEqualTo(30L)
		TestRuntime.assertThat((null as TickDuration).ticks()).isEqualTo(0L)
		TestRuntime.assertThat((new JsonPrimitive(4) as TickDuration).ticks()).isEqualTo(4L)
		TestRuntime.assertThat((100 as TickDuration).ticks()).isEqualTo(100L)
	}
	TestRuntime.check('wrapper.kube_identifier') {
		def id = 'wrapper_test' as KubeIdentifier
		TestRuntime.assertThat(id.wrapped().toString()).isEqualTo('kubegroovy:wrapper_test')
		def explicit = 'minecraft:stone' as KubeIdentifier
		TestRuntime.assertThat(explicit.wrapped().toString()).isEqualTo('minecraft:stone')
		Assertions.assertThatThrownBy(() -> 'not an id!' as KubeIdentifier).isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.nbt_collection') {
		def collection = [1, 2, 3] as CollectionTag
		TestRuntime.assertThat(collection.size()).isEqualTo(3)
		TestRuntime.assertThat(collection.get(1).asInt().orElseThrow()).isEqualTo(2)
		def parsed = '[I;4,5]' as CollectionTag
		TestRuntime.assertThat(parsed.size()).isEqualTo(2)
		TestRuntime.assertThat(parsed.get(0).asInt().orElseThrow()).isEqualTo(4)
		def mixed = [1, 'two'] as CollectionTag
		TestRuntime.assertThat(mixed.isEmpty()).isTrue()
		TestRuntime.assertThat(([] as CollectionTag).isEmpty()).isTrue()
	}
	TestRuntime.check('wrapper.nbt_list') {
		def list = ['one', 'two'] as ListTag
		TestRuntime.assertThat(list.getStringOr(1, '')).isEqualTo('two')
		def parsed = '[]' as ListTag
		TestRuntime.assertThat(parsed.isEmpty()).isTrue()
		def mixed = ['one', 2] as ListTag
		TestRuntime.assertThat(mixed.isEmpty()).isTrue()
	}
	TestRuntime.check('wrapper.nbt_tag') {
		def tag = [value: 7] as Tag
		TestRuntime.assertThat(tag.getId()).isEqualTo(Tag.TAG_COMPOUND)
		TestRuntime.assertThat(tag.asCompound().orElseThrow().getIntOr('value', 0)).isEqualTo(7)
		TestRuntime.assertThat((new JsonPrimitive(8) as Tag).getId()).isEqualTo(Tag.TAG_INT)
		TestRuntime.assertThat((null as Tag)).isNull()
	}
	TestRuntime.check('wrapper.data_component_type') {
		def type = 'minecraft:custom_name' as DataComponentType
		TestRuntime.assertThat(type).isSameAs(DataComponents.CUSTOM_NAME)
		def json = new JsonPrimitive('minecraft:custom_name') as DataComponentType
		TestRuntime.assertThat(json).isSameAs(DataComponents.CUSTOM_NAME)
		Assertions.assertThatThrownBy(() -> 'minecraft:not_a_component' as DataComponentType).isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.data_component_map') {
		def components = [(DataComponents.CUSTOM_NAME): ('wrapped' as MutableComponent)] as DataComponentMap
		def name = components.get(DataComponents.CUSTOM_NAME)
		TestRuntime.assertThat(name.getString()).isEqualTo('wrapped')
		def empty = [:] as DataComponentMap
		TestRuntime.assertThat(empty.isEmpty()).isTrue()
		Assertions.assertThatThrownBy(() -> '[minecraft:custom_name=' as DataComponentMap).isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.data_component_patch') {
		def patch = [(DataComponents.CUSTOM_NAME): ('patched' as MutableComponent)] as DataComponentPatch
		def name = patch.getPatch(DataComponents.CUSTOM_NAME).orElseThrow()
		TestRuntime.assertThat(name.getString()).isEqualTo('patched')
		def removed = [(DataComponents.CUSTOM_NAME): null] as DataComponentPatch
		TestRuntime.assertThat(removed.entrySet().iterator().next().getValue().isEmpty()).isTrue()
		TestRuntime.assertThat(([:] as DataComponentPatch).isEmpty()).isTrue()
	}
	TestRuntime.check('wrapper.vec3i') {
		def vec = [7, 8, 9] as net.minecraft.core.Vec3i
		TestRuntime.assertThat(vec.getX()).isEqualTo(7)
		TestRuntime.assertThat(vec.getY()).isEqualTo(8)
		TestRuntime.assertThat(vec.getZ()).isEqualTo(9)
		def defaults = [7, 'not-a-number', null] as net.minecraft.core.Vec3i
		TestRuntime.assertThat(defaults.getX()).isEqualTo(7)
		TestRuntime.assertThat(defaults.getY()).isEqualTo(0)
		TestRuntime.assertThat(defaults.getZ()).isEqualTo(0)
		Assertions.assertThatThrownBy(() -> [1, 2] as net.minecraft.core.Vec3i).isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.item') {
		def item = 'minecraft:diamond' as Item
		TestRuntime.assertThat(item).isSameAs(Items.DIAMOND)
		TestRuntime.assertThat(('air' as Item)).isSameAs(Items.AIR)
		TestRuntime.assertThat((null as Item)).isSameAs(Items.AIR)
		Assertions.assertThatThrownBy(() -> 'minecraft:not_an_item' as Item).isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.item_like') {
		def item = 'minecraft:diamond' as ItemLike
		TestRuntime.assertThat(item.asItem()).isSameAs(Items.DIAMOND)
		TestRuntime.assertThat(('air' as ItemLike).asItem()).isSameAs(Items.AIR)
		TestRuntime.assertThat((null as ItemLike).asItem()).isSameAs(Items.AIR)
		Assertions.assertThatThrownBy(() -> 'minecraft:not_an_item' as ItemLike).isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.item_enchantments') {
		def sharpness = event.getServer().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(Enchantments.SHARPNESS).orElseThrow()
		def enchantments = [(sharpness): 3] as ItemEnchantments
		TestRuntime.assertThat(enchantments.size()).isEqualTo(1)
		TestRuntime.assertThat(enchantments.entrySet().iterator().next().getIntValue()).isEqualTo(3)
		TestRuntime.assertThat(([:] as ItemEnchantments).isEmpty()).isTrue()
		TestRuntime.assertThat((42 as ItemEnchantments).isEmpty()).isTrue()
	}
	TestRuntime.check('wrapper.aabb') {
		def dimensions = [2.0d, 4.0d, 6.0d] as double[]
		def box = dimensions as AABB
		TestRuntime.assertThat(box.getXsize()).isEqualTo(2.0d)
		TestRuntime.assertThat(box.getYsize()).isEqualTo(4.0d)
		TestRuntime.assertThat(box.getZsize()).isEqualTo(6.0d)
		def bounds = [1.0d, 2.0d, 3.0d, 4.0d, 6.0d, 8.0d] as double[]
		def boundsBox = bounds as AABB
		TestRuntime.assertThat(boundsBox.minX).isEqualTo(1.0d)
		TestRuntime.assertThat(boundsBox.maxZ).isEqualTo(8.0d)
		def empty = [] as double[]
		TestRuntime.assertThat((empty as AABB).getXsize()).isEqualTo(0.0d)
		TestRuntime.assertThat((null as AABB).getYsize()).isEqualTo(0.0d)
		Assertions.assertThatThrownBy(() -> ([1.0d, 2.0d] as double[]) as AABB).isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.float_provider') {
		def range = [1.5f, 3.5f] as FloatProvider
		TestRuntime.assertThat(range.min()).isEqualTo(1.5f)
		TestRuntime.assertThat(range.max()).isEqualTo(3.5f)
		def constant = 2 as FloatProvider
		TestRuntime.assertThat(constant.min()).isEqualTo(2.0f)
		def mapped = [min: 4.5f, max: 1.5f] as FloatProvider
		TestRuntime.assertThat(mapped.min()).isEqualTo(1.5f)
		TestRuntime.assertThat(mapped.max()).isEqualTo(4.5f)
		Assertions.assertThatThrownBy(() -> [] as FloatProvider).isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.number_provider') {
		def constant = 4 as NumberProvider
		if (constant instanceof UniformGenerator uniGen) {
			TestRuntime.assertThat(uniGen.min().getFloat(null)).isEqualTo(4.0f)
			TestRuntime.assertThat(uniGen.max().getFloat(null)).isEqualTo(4.0f)
		}
		def constantList = [4] as NumberProvider
		if (constantList instanceof UniformGenerator uniGen) {
			TestRuntime.assertThat(uniGen.min().getFloat(null)).isEqualTo(4.0f)
			TestRuntime.assertThat(uniGen.max().getFloat(null)).isEqualTo(4.0f)
		}
		def range = [2, 6] as NumberProvider
		if (range instanceof UniformGenerator uniGen) {
			TestRuntime.assertThat(uniGen.min().getFloat(null)).isEqualTo(2.0f)
			TestRuntime.assertThat(uniGen.max().getFloat(null)).isEqualTo(6.0f)
		}
		def value = [value: 4] as NumberProvider
		if (value instanceof UniformGenerator uniGen) {
			TestRuntime.assertThat(uniGen.min().getFloat(null)).isEqualTo(4.0f)
			TestRuntime.assertThat(uniGen.max().getFloat(null)).isEqualTo(4.0f)
		}
		def reversed = [min: 8, max: 2] as NumberProvider
		if (reversed instanceof UniformGenerator uniGen) {
			TestRuntime.assertThat(uniGen.min().getFloat(null)).isEqualTo(8.0f)
			TestRuntime.assertThat(uniGen.max().getFloat(null)).isEqualTo(2.0f)
		}

		Assertions.assertThatThrownBy(() -> [] as NumberProvider).isInstanceOf(GroovyCastException)
		Assertions.assertThatThrownBy(() -> [1,2,3,4] as NumberProvider).isInstanceOf(GroovyCastException)
		Assertions.assertThatThrownBy(() -> null as NumberProvider).isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.block_set_type') {
		def type = 'oak' as BlockSetType
		TestRuntime.assertThat(type).isSameAs(BlockSetType.OAK)
		TestRuntime.assertThat(('OAK' as BlockSetType)).isSameAs(BlockSetType.OAK)
		TestRuntime.assertThat((null as BlockSetType)).isNull()
		Assertions.assertThatThrownBy(() -> 'not-a-set-type' as BlockSetType).isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.block_state') {
		def state = 'minecraft:stone' as BlockState
		TestRuntime.assertThat(state.is(Blocks.STONE)).isTrue()
		def fromBlock = Blocks.DIRT as BlockState
		TestRuntime.assertThat(fromBlock.is(Blocks.DIRT)).isTrue()
		Assertions.assertThatThrownBy(() -> 'minecraft:not_a_block' as BlockState).isInstanceOf(GroovyCastException)
		Assertions.assertThatThrownBy(() -> null as BlockState).isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.item_ability') {
		def ability = 'axe_strip' as ItemAbility
		TestRuntime.assertThat(ability.name()).isEqualTo('axe_strip')
		TestRuntime.assertThat(('not_an_ability' as ItemAbility).name()).isEqualTo('not_an_ability')
		TestRuntime.assertThat((null as ItemAbility)).isNull()
	}
	TestRuntime.check('wrapper.color_rgba') {
		def color = '#336699' as ColorRGBA
		TestRuntime.assertThat(color.rgba()).isEqualTo(-13408615)
		def alpha = '#80112233' as ColorRGBA
		TestRuntime.assertThat(alpha.rgba() & 0xFFFFFF).isEqualTo(0x112233)
		TestRuntime.assertThat(('none' as ColorRGBA).rgba()).isEqualTo(0)
		TestRuntime.assertThat(('not-a-color' as ColorRGBA).rgba()).isEqualTo(0)
	}
	TestRuntime.check('wrapper.ingredient') {
		def ingredient = 'minecraft:diamond' as Ingredient
		TestRuntime.assertThat(ingredient.test(new ItemStack(Items.DIAMOND))).isTrue()
		TestRuntime.assertThat(ingredient.test(new ItemStack(Items.STICK))).isFalse()
		def regex = '/diamond/' as Ingredient
		TestRuntime.assertThat(regex.test(new ItemStack(Items.DIAMOND))).isTrue()
		def compound = ['minecraft:diamond', 'minecraft:stick'] as Ingredient
		TestRuntime.assertThat(compound.test(new ItemStack(Items.DIAMOND))).isTrue()
		TestRuntime.assertThat(compound.test(new ItemStack(Items.STICK))).isTrue()
		Assertions.assertThatThrownBy(() -> [] as Ingredient).isInstanceOf(GroovyCastException)
		Assertions.assertThatThrownBy(() -> '-' as Ingredient).isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.item_predicate') {
		def predicate = 'minecraft:diamond' as ItemPredicate
		TestRuntime.assertThat(predicate.test(new ItemStack(Items.DIAMOND))).isTrue()
		TestRuntime.assertThat(predicate.test(new ItemStack(Items.STICK))).isFalse()
		TestRuntime.assertThat(('*' as ItemPredicate).test(new ItemStack(Items.STICK))).isTrue()
		TestRuntime.assertThat(('' as ItemPredicate).test(new ItemStack(Items.DIAMOND))).isFalse()
		TestRuntime.assertThat((null as ItemPredicate).test(new ItemStack(Items.DIAMOND))).isFalse()
	}
	TestRuntime.check('wrapper.sized_ingredient') {
		def ingredient = '3x minecraft:diamond' as SizedIngredient
		TestRuntime.assertThat(ingredient.count()).isEqualTo(3)
		TestRuntime.assertThat(ingredient.test(new ItemStack(Items.DIAMOND, 3))).isTrue()
		def fromStack = new ItemStack(Items.STICK, 2) as SizedIngredient
		TestRuntime.assertThat(fromStack.count()).isEqualTo(2)
		TestRuntime.assertThat(fromStack.test(new ItemStack(Items.STICK, 2))).isTrue()
		def defaultCount = 'minecraft:diamond' as SizedIngredient
		TestRuntime.assertThat(defaultCount.count()).isEqualTo(1)
		Assertions.assertThatThrownBy(() -> '0x minecraft:diamond' as SizedIngredient).isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.block_state_predicate') {
		def predicate = [or: ['minecraft:stone', 'minecraft:dirt']] as BlockStatePredicate
		TestRuntime.assertThat(predicate.testBlock(Blocks.STONE)).isTrue()
		TestRuntime.assertThat(predicate.testBlock(Blocks.OAK_LOG)).isFalse()
		def none = '-' as BlockStatePredicate
		TestRuntime.assertThat(none.testBlock(Blocks.STONE)).isFalse()
		def regex = '/stone/' as BlockStatePredicate
		TestRuntime.assertThat(regex.testBlock(Blocks.STONE)).isTrue()
		def inverted = [not: 'minecraft:stone'] as BlockStatePredicate
		TestRuntime.assertThat(inverted.testBlock(Blocks.STONE)).isFalse()
		TestRuntime.assertThat(inverted.testBlock(Blocks.DIRT)).isTrue()
		TestRuntime.assertThat(([:] as BlockStatePredicate).testBlock(Blocks.DIRT)).isTrue()
		TestRuntime.assertThat(([] as BlockStatePredicate).testBlock(Blocks.DIRT)).isFalse()
	}
	TestRuntime.check('wrapper.rule_test') {
		def rule = Blocks.STONE as RuleTest
		TestRuntime.assertThat(rule.test(Blocks.STONE.defaultBlockState(), RandomSource.create())).isTrue()
		TestRuntime.assertThat(rule.test(Blocks.DIRT.defaultBlockState(), RandomSource.create())).isFalse()
		def always = [:] as RuleTest
		TestRuntime.assertThat(always.test(Blocks.DIRT.defaultBlockState(), RandomSource.create())).isTrue()
		def encoded = '{predicate_type:"minecraft:block_match",block:"minecraft:stone"}' as RuleTest
		TestRuntime.assertThat(encoded.test(Blocks.STONE.defaultBlockState(), RandomSource.create())).isTrue()
		Assertions.assertThatThrownBy(() -> 'not-a-rule-test' as RuleTest).isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.fluid_stack') {
		def stack = '250x minecraft:water' as FluidStack
		TestRuntime.assertThat(stack.getFluid()).isSameAs(Fluids.WATER)
		TestRuntime.assertThat(stack.getAmount()).isEqualTo(250)
		def bucket = '1b x minecraft:water' as FluidStack
		TestRuntime.assertThat(bucket.getAmount()).isEqualTo(1000)
		TestRuntime.assertThat(('empty' as FluidStack).isEmpty()).isTrue()
		Assertions.assertThatThrownBy(() -> '0x minecraft:water' as FluidStack).isInstanceOf(GroovyCastException)
		Assertions.assertThatThrownBy(() -> 'minecraft:not_a_fluid' as FluidStack).isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.fluid_ingredient') {
		def ingredient = 'minecraft:water' as FluidIngredient
		TestRuntime.assertThat(ingredient.test(new FluidStack(Fluids.WATER, 250))).isTrue()
		TestRuntime.assertThat(ingredient.test(new FluidStack(Fluids.LAVA, 250))).isFalse()
		def fromStack = new FluidStack(Fluids.WATER, 1) as FluidIngredient
		TestRuntime.assertThat(fromStack.test(new FluidStack(Fluids.WATER, 2))).isTrue()
		def namespace = '@minecraft' as FluidIngredient
		TestRuntime.assertThat(namespace.test(new FluidStack(Fluids.LAVA, 1))).isTrue()
		def regex = '/water/' as FluidIngredient
		TestRuntime.assertThat(regex.test(new FluidStack(Fluids.WATER, 1))).isTrue()
		Assertions.assertThatThrownBy(() -> 'empty' as FluidIngredient).isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.sized_fluid_ingredient') {
		def ingredient = '500x minecraft:water' as SizedFluidIngredient
		TestRuntime.assertThat(ingredient.amount()).isEqualTo(500)
		TestRuntime.assertThat(ingredient.test(new FluidStack(Fluids.WATER, 500))).isTrue()
		def fromStack = new FluidStack(Fluids.LAVA, 333) as SizedFluidIngredient
		TestRuntime.assertThat(fromStack.amount()).isEqualTo(333)
		TestRuntime.assertThat(fromStack.test(new FluidStack(Fluids.LAVA, 333))).isTrue()
		def bucket = '1b x minecraft:water' as SizedFluidIngredient
		TestRuntime.assertThat(bucket.amount()).isEqualTo(1000)
		Assertions.assertThatThrownBy(() -> '0x minecraft:water' as SizedFluidIngredient).isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.recipe_filter') {
		def filter = '*' as RecipeFilter
		TestRuntime.assertThat(filter.test(null)).isTrue()
		TestRuntime.assertThat(('-' as RecipeFilter).test(null)).isFalse()
		TestRuntime.assertThat((null as RecipeFilter).test(null)).isTrue()
		TestRuntime.assertThat(([] as RecipeFilter).test(null)).isFalse()
		TestRuntime.assertThat((['-', '*'] as RecipeFilter).test(null)).isTrue()
		Assertions.assertThatThrownBy(() -> [unknown: 'value'] as RecipeFilter).isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.slot_filter') {
		def filter = 2 as SlotFilter
		TestRuntime.assertThat(filter.checkFilter(2, ItemStack.EMPTY)).isTrue()
		TestRuntime.assertThat(filter.checkFilter(1, ItemStack.EMPTY)).isFalse()
		def byItem = 'minecraft:diamond' as SlotFilter
		TestRuntime.assertThat(byItem.checkFilter(0, new ItemStack(Items.DIAMOND))).isTrue()
		TestRuntime.assertThat(byItem.checkFilter(0, new ItemStack(Items.STICK))).isFalse()
	}
	TestRuntime.check('wrapper.tool_material') {
		def material = 'diamond' as ToolMaterial
		TestRuntime.assertThat(material).isSameAs(ToolMaterial.DIAMOND)
		TestRuntime.assertThat(('unknown_material' as ToolMaterial)).isSameAs(ToolMaterial.IRON)
		TestRuntime.assertThat((null as ToolMaterial)).isSameAs(ToolMaterial.IRON)
	}
	TestRuntime.check('wrapper.damage_source') {
		def source = 'minecraft:generic' as DamageSource
		TestRuntime.assertThat(source.getMsgId()).isEqualTo('generic')
		def overworld = ServerLifecycleHooks.getCurrentServer().getLevel("minecraft:overworld" as Identifier)
		def mob = EntityType.CREEPER.create(overworld, EntitySpawnReason.COMMAND)
		def mobSource = mob as DamageSource
		TestRuntime.assertThat(mobSource.getEntity()).isSameAs(mob)
		Assertions.assertThatThrownBy(() -> null as DamageSource).isInstanceOf(GroovyCastException)
		Assertions.assertThatThrownBy(() -> 'minecraft:not_a_damage_type' as DamageSource).isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.entity_selector') {
		def selector = '@e[type=minecraft:pig,limit=1]' as net.minecraft.commands.arguments.selector.EntitySelector
		TestRuntime.assertThat(selector.getMaxResults()).isEqualTo(1)
		TestRuntime.assertThat(selector.includesEntities()).isTrue()
		def self = '@s' as net.minecraft.commands.arguments.selector.EntitySelector
		TestRuntime.assertThat(self.getMaxResults()).isEqualTo(1)
		Assertions.assertThatThrownBy(() -> '' as net.minecraft.commands.arguments.selector.EntitySelector).isInstanceOf(GroovyCastException)
		Assertions.assertThatThrownBy(() -> '@e[invalid' as net.minecraft.commands.arguments.selector.EntitySelector).isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.replacement_match') {
		def match = 'minecraft:diamond' as ReplacementMatch
		TestRuntime.assertThat(match.matches(null, new ItemStack(Items.DIAMOND), false)).isTrue()
		TestRuntime.assertThat(match.matches(null, new ItemStack(Items.STICK), false)).isFalse()
		def compound = ['minecraft:diamond', 'minecraft:stick'] as ReplacementMatch
		TestRuntime.assertThat(compound.matches(null, new ItemStack(Items.STICK), false)).isTrue()
		TestRuntime.assertThat((null as ReplacementMatch).toString()).isEqualTo('NONE')
		Assertions.assertThatThrownBy(() -> '' as ReplacementMatch).isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.replacement_match_info') {
		def info = 'minecraft:diamond' as ReplacementMatchInfo
		TestRuntime.assertThat(info.exact()).isFalse()
		TestRuntime.assertThat(info.match().matches(null, new ItemStack(Items.DIAMOND), false)).isTrue()
		def none = null as ReplacementMatchInfo
		TestRuntime.assertThat(none).isSameAs(ReplacementMatchInfo.NONE)
	}
	TestRuntime.check('wrapper.stat') {
		def stat = Stats.JUMP as Stat
		TestRuntime.assertThat(stat.getValue().toString()).isEqualTo('minecraft:jump')
		Assertions.assertThatThrownBy(() -> 'minecraft:not_a_stat' as Stat).isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.sound_type') {
		def sound = 'wool' as SoundType
		TestRuntime.assertThat(sound).isSameAs(SoundType.WOOL)
		TestRuntime.assertThat(('empty' as SoundType)).isSameAs(SoundType.EMPTY)
		TestRuntime.assertThat((new JsonPrimitive('wool') as SoundType)).isSameAs(SoundType.WOOL)
		Assertions.assertThatThrownBy(() -> 'not-a-sound-type' as SoundType).isInstanceOf(GroovyCastException)
		Assertions.assertThatThrownBy(() -> null as SoundType).isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.particle_options') {
		def particle = 'minecraft:flame' as ParticleOptions
		TestRuntime.assertThat(particle.getType()).isSameAs(ParticleTypes.FLAME)
		def json = new JsonPrimitive('minecraft:flame') as ParticleOptions
		TestRuntime.assertThat(json.getType()).isSameAs(ParticleTypes.FLAME)
		def missing = null as ParticleOptions
		TestRuntime.assertThat(missing.getType()).isSameAs(ParticleTypes.DUST)
		Assertions.assertThatThrownBy(() -> 'minecraft:not_a_particle' as ParticleOptions).isInstanceOf(GroovyCastException)
	}
	TestRuntime.check('wrapper.item_tint') {
		def fixed = '#112233' as ItemTintFunction
		TestRuntime.assertThat(fixed.getColor(ItemStack.EMPTY, 0).toHexString()).isEqualTo('#112233')
		def mapped = ['#010203', null, '#A0B0C0'] as ItemTintFunction
		TestRuntime.assertThat(mapped.getMaxTintIndex()).isEqualTo(2)
		TestRuntime.assertThat(mapped.getColor(ItemStack.EMPTY, 2).toHexString()).isEqualTo('#A0B0C0')
		TestRuntime.assertThat(mapped.getColor(ItemStack.EMPTY, 1)).isNull()
		TestRuntime.assertThat(('map' as ItemTintFunction).getColor(ItemStack.EMPTY, 0)).isNull()
		TestRuntime.assertThat((null as ItemTintFunction)).isNull()
	}
	TestRuntime.check('wrapper.block_tint') {
		def fixed = '#445566' as BlockTintFunction
		def state = Blocks.STONE.defaultBlockState()
		TestRuntime.assertThat(fixed.getColor(state, null, null, 0).toHexString()).isEqualTo('#445566')
		def mapped = ['#010203', null, '#A0B0C0'] as BlockTintFunction
		TestRuntime.assertThat(mapped.getMaxTintIndex()).isEqualTo(2)
		TestRuntime.assertThat(mapped.getColor(state, null, null, 2).toHexString()).isEqualTo('#A0B0C0')
		TestRuntime.assertThat(mapped.getColor(state, null, null, 1)).isNull()
		TestRuntime.assertThat(('water' as BlockTintFunction).getColor(state, null, null, 0)).isNull()
		TestRuntime.assertThat((null as BlockTintFunction)).isNull()
	}
	TestRuntime.check('wrapper.text_color') {
		def color = '#123456' as TextColor
		TestRuntime.assertThat(color.getValue()).isEqualTo(0x123456)
		TestRuntime.assertThat(('none' as TextColor).getValue()).isEqualTo(0)
		TestRuntime.assertThat(('not-a-color' as TextColor).getValue()).isEqualTo(0)
	}
	TestRuntime.check('wrapper.click_event') {
		def shorthand = 'command:/say wrapper' as ClickEvent
		TestRuntime.assertThat(shorthand instanceof ClickEvent.RunCommand).isTrue()
		TestRuntime.assertThat(shorthand.command()).isEqualTo('/say wrapper')
		def structured = [action: 'copy_to_clipboard', value: 'wrapper'] as ClickEvent
		TestRuntime.assertThat(structured instanceof ClickEvent.CopyToClipboard).isTrue()
		TestRuntime.assertThat(structured.value()).isEqualTo('wrapper')
		def json = '{"action":"copy_to_clipboard","value":"json wrapper"}' as ClickEvent
		TestRuntime.assertThat(json instanceof ClickEvent.CopyToClipboard).isTrue()
		TestRuntime.assertThat(json.value()).isEqualTo('json wrapper')
		def url = 'https://example.com' as ClickEvent
		TestRuntime.assertThat(url instanceof ClickEvent.OpenUrl).isTrue()
		TestRuntime.assertThat(url.uri().toString()).isEqualTo('https://example.com')
		def copy = 'copy:copied text' as ClickEvent
		TestRuntime.assertThat(copy instanceof ClickEvent.CopyToClipboard).isTrue()
		TestRuntime.assertThat(copy.value()).isEqualTo('copied text')
		def page = 'page:2' as ClickEvent
		TestRuntime.assertThat(page instanceof ClickEvent.ChangePage).isTrue()
		TestRuntime.assertThat(page.page()).isEqualTo(2)
		def file = 'file:wrapper.txt' as ClickEvent
		TestRuntime.assertThat(file instanceof ClickEvent.OpenFile).isTrue()
		TestRuntime.assertThat(file.file().getPath().endsWith('wrapper.txt')).isTrue()
		TestRuntime.assertThat((null as ClickEvent)).isNull()
		Assertions.assertThatThrownBy(() -> 'page:not-a-number' as ClickEvent).isInstanceOf(GroovyCastException)
	}
}
