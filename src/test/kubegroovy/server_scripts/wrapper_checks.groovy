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

def wrapperChecksRun = false
ServerEvents.tick { event ->
	if (wrapperChecksRun) return
	wrapperChecksRun = true

	TestRuntime.check('wrapper.vec3') {
		def v = [1, 2, 3] as Vec3
		TestRuntime.assertThat(v as Vec3).isEqualTo(v)
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
	}
	TestRuntime.check('wrapper.itemstack') {
		def stack = 'minecraft:diamond' as ItemStack
		TestRuntime.assertThat(stack.id).isEqualTo('minecraft:diamond')
		def sized = '2x minecraft:diamond' as ItemStack
		TestRuntime.assertThat(sized.getCount()).isEqualTo(2)
	}
	TestRuntime.check('wrapper.component') {
		def component = 'hello' as MutableComponent
		TestRuntime.assertThat(component.getString()).isEqualTo('hello')
		def styled = [text: 'hello', color: '#ff0000', bold: true, extra: ['!']] as MutableComponent
		TestRuntime.assertThat(styled.getString()).isEqualTo('hello!')
		TestRuntime.assertThat(styled.getStyle().isBold()).isTrue()
		TestRuntime.assertThat(styled.getStyle().getColor().getValue()).isEqualTo(0xff0000)
	}
	TestRuntime.check('wrapper.nbt') {
		def tag = [a: 1, b: 'two'] as CompoundTag
		TestRuntime.assertThat(tag.contains('a')).isTrue()
		TestRuntime.assertThat(tag.contains('b')).isTrue()
		def structured = '{nested:{value:"ok"},numbers:[I;1,2,3]}' as CompoundTag
		TestRuntime.assertThat(structured.getCompoundOrEmpty('nested').getStringOr('value', '')).isEqualTo('ok')
		TestRuntime.assertThat(structured.getIntArray('numbers').orElseThrow().length).isEqualTo(3)
	}
	TestRuntime.check('wrapper.color') {
		def color = '#ff0000' as KubeColor
		TestRuntime.assertThat(color.toHexString()).isEqualTo('#FF0000')
	}
	TestRuntime.check('wrapper.id') {
		def id = 'kubegroovy:test' as Identifier
		TestRuntime.assertThat(id.toString()).isEqualTo('kubegroovy:test')
	}
	TestRuntime.check('wrapper.uuid') {
		def uuid = '12345678-1234-1234-1234-123456789abc' as UUID
		TestRuntime.assertThat(uuid.toString()).isEqualTo('12345678-1234-1234-1234-123456789abc')
		def compact = '12345678123412341234123456789abc' as UUID
		TestRuntime.assertThat(compact.toString()).isEqualTo('12345678-1234-1234-1234-123456789abc')
	}
	TestRuntime.check('wrapper.tristate') {
		def tristate = 'true' as Tristate
		TestRuntime.assertThat(tristate.getSerializedName()).isEqualTo('true')
	}
	TestRuntime.check('wrapper.duration') {
		def duration = '20t' as Duration
		TestRuntime.assertThat(duration.toMillis() == 1000).isTrue()
	}
	TestRuntime.check('wrapper.regexp') {
		def pattern = '/foo/i' as Pattern
		TestRuntime.assertThat(pattern.pattern()).isEqualTo('foo')
	}
	TestRuntime.check('wrapper.int_provider') {
		def constant = 3 as IntProvider
		TestRuntime.assertThat(constant.minInclusive() == 3 && constant.maxInclusive() == 3).isTrue()
		def range = [2, 8] as IntProvider
		TestRuntime.assertThat(range.minInclusive() == 2 && range.maxInclusive() == 8).isTrue()
	}
	TestRuntime.check('wrapper.map_color') {
		def color = 'color_red' as MapColor
		TestRuntime.assertThat(color.col).isEqualTo(10040115)
	}

	TestRuntime.check('wrapper.registry_predicate') {
		def predicate = 'minecraft:diamond' as RegistryPredicate
		TestRuntime.assertThat(predicate.test(Items.DIAMOND.builtInRegistryHolder())).isTrue()
		TestRuntime.assertThat(predicate.test(Items.STICK.builtInRegistryHolder())).isFalse()
	}
	TestRuntime.check('wrapper.json_object') {
		def object = [name: 'wrapper', count: 2] as JsonObject
		TestRuntime.assertThat(object.get('name').getAsString()).isEqualTo('wrapper')
		TestRuntime.assertThat(object.get('count').getAsInt()).isEqualTo(2)
	}
	TestRuntime.check('wrapper.json_array') {
		def array = ['wrapper', 2, true] as JsonArray
		TestRuntime.assertThat(array.size()).isEqualTo(3)
		TestRuntime.assertThat(array.get(1).getAsInt()).isEqualTo(2)
	}
	TestRuntime.check('wrapper.json_element') {
		def element = [wrapped: true] as JsonElement
		TestRuntime.assertThat(element.isJsonObject()).isTrue()
		TestRuntime.assertThat(element.getAsJsonObject().get('wrapped').getAsBoolean()).isTrue()
	}
	TestRuntime.check('wrapper.json_primitive') {
		def primitive = 42 as JsonPrimitive
		TestRuntime.assertThat(primitive.getAsInt()).isEqualTo(42)
	}
	TestRuntime.check('wrapper.temporal_amount') {
		def amount = '1s 10t' as TemporalAmount
		TestRuntime.assertThat(amount.toString()).isEqualTo('30 ticks')
	}
	TestRuntime.check('wrapper.tick_duration') {
		def ticks = '1s 10t' as TickDuration
		TestRuntime.assertThat(ticks.ticks()).isEqualTo(30L)
	}
	TestRuntime.check('wrapper.kube_identifier') {
		def id = 'wrapper_test' as KubeIdentifier
		TestRuntime.assertThat(id.wrapped().toString()).isEqualTo('kubegroovy:wrapper_test')
	}
	TestRuntime.check('wrapper.nbt_collection') {
		def collection = [1, 2, 3] as CollectionTag
		TestRuntime.assertThat(collection.size()).isEqualTo(3)
		TestRuntime.assertThat(collection.get(1).asInt().orElseThrow()).isEqualTo(2)
	}
	TestRuntime.check('wrapper.nbt_list') {
		def list = ['one', 'two'] as ListTag
		TestRuntime.assertThat(list.getStringOr(1, '')).isEqualTo('two')
	}
	TestRuntime.check('wrapper.nbt_tag') {
		def tag = [value: 7] as Tag
		TestRuntime.assertThat(tag.getId()).isEqualTo(Tag.TAG_COMPOUND)
		TestRuntime.assertThat(tag.asCompound().orElseThrow().getIntOr('value', 0)).isEqualTo(7)
	}
	TestRuntime.check('wrapper.data_component_type') {
		def type = 'minecraft:custom_name' as DataComponentType
		TestRuntime.assertThat(type).isSameAs(DataComponents.CUSTOM_NAME)
	}
	TestRuntime.check('wrapper.data_component_map') {
		def components = [(DataComponents.CUSTOM_NAME): ('wrapped' as MutableComponent)] as DataComponentMap
		def name = components.get(DataComponents.CUSTOM_NAME)
		TestRuntime.assertThat(name.getString()).isEqualTo('wrapped')
	}
	TestRuntime.check('wrapper.data_component_patch') {
		def patch = [(DataComponents.CUSTOM_NAME): ('patched' as MutableComponent)] as DataComponentPatch
		def name = patch.getPatch(DataComponents.CUSTOM_NAME).orElseThrow()
		TestRuntime.assertThat(name.getString()).isEqualTo('patched')
	}
	TestRuntime.check('wrapper.vec3i') {
		def vec = [7, 8, 9] as net.minecraft.core.Vec3i
		TestRuntime.assertThat(vec.getX()).isEqualTo(7)
		TestRuntime.assertThat(vec.getY()).isEqualTo(8)
		TestRuntime.assertThat(vec.getZ()).isEqualTo(9)
	}
	TestRuntime.check('wrapper.item') {
		def item = 'minecraft:diamond' as Item
		TestRuntime.assertThat(item).isSameAs(Items.DIAMOND)
	}
	TestRuntime.check('wrapper.item_like') {
		def item = 'minecraft:diamond' as ItemLike
		TestRuntime.assertThat(item.asItem()).isSameAs(Items.DIAMOND)
	}
	TestRuntime.check('wrapper.item_enchantments') {
		def sharpness = event.getServer().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(Enchantments.SHARPNESS).orElseThrow()
		def enchantments = [(sharpness): 3] as ItemEnchantments
		TestRuntime.assertThat(enchantments.size()).isEqualTo(1)
		TestRuntime.assertThat(enchantments.entrySet().iterator().next().getIntValue()).isEqualTo(3)
	}
	TestRuntime.check('wrapper.aabb') {
		def dimensions = [2.0d, 4.0d, 6.0d] as double[]
		def box = dimensions as AABB
		TestRuntime.assertThat(box.getXsize()).isEqualTo(2.0d)
		TestRuntime.assertThat(box.getYsize()).isEqualTo(4.0d)
		TestRuntime.assertThat(box.getZsize()).isEqualTo(6.0d)
	}
	TestRuntime.check('wrapper.float_provider') {
		def range = [1.5f, 3.5f] as FloatProvider
		TestRuntime.assertThat(range.min()).isEqualTo(1.5f)
		TestRuntime.assertThat(range.max()).isEqualTo(3.5f)
	}
	TestRuntime.check('wrapper.number_provider') {
		def range = [2, 6] as NumberProvider
		if (range instanceof UniformGenerator uniGen) {
			TestRuntime.assertThat(uniGen.min().getFloat(null)).isEqualTo(2.0f)
			TestRuntime.assertThat(uniGen.max().getFloat(null)).isEqualTo(6.0f)
		}
	}
	TestRuntime.check('wrapper.block_set_type') {
		def type = 'oak' as BlockSetType
		TestRuntime.assertThat(type).isSameAs(BlockSetType.OAK)
	}
	TestRuntime.check('wrapper.block_state') {
		def state = 'minecraft:stone' as BlockState
		TestRuntime.assertThat(state.is(Blocks.STONE)).isTrue()
	}
	TestRuntime.check('wrapper.item_ability') {
		def ability = 'axe_strip' as ItemAbility
		TestRuntime.assertThat(ability.name()).isEqualTo('axe_strip')
	}
	TestRuntime.check('wrapper.color_rgba') {
		def color = '#336699' as ColorRGBA
		TestRuntime.assertThat(color.rgba()).isEqualTo(-13408615)
	}
	TestRuntime.check('wrapper.ingredient') {
		def ingredient = 'minecraft:diamond' as Ingredient
		TestRuntime.assertThat(ingredient.test(new ItemStack(Items.DIAMOND))).isTrue()
		TestRuntime.assertThat(ingredient.test(new ItemStack(Items.STICK))).isFalse()
	}
	TestRuntime.check('wrapper.item_predicate') {
		def predicate = 'minecraft:diamond' as ItemPredicate
		TestRuntime.assertThat(predicate.test(new ItemStack(Items.DIAMOND))).isTrue()
		TestRuntime.assertThat(predicate.test(new ItemStack(Items.STICK))).isFalse()
	}
	TestRuntime.check('wrapper.sized_ingredient') {
		def ingredient = '3x minecraft:diamond' as SizedIngredient
		TestRuntime.assertThat(ingredient.count()).isEqualTo(3)
		TestRuntime.assertThat(ingredient.test(new ItemStack(Items.DIAMOND, 3))).isTrue()
	}
	TestRuntime.check('wrapper.block_state_predicate') {
		def predicate = [or: ['minecraft:stone', 'minecraft:dirt']] as BlockStatePredicate
		TestRuntime.assertThat(predicate.testBlock(Blocks.STONE)).isTrue()
		TestRuntime.assertThat(predicate.testBlock(Blocks.OAK_LOG)).isFalse()
	}
	TestRuntime.check('wrapper.rule_test') {
		def predicate = 'minecraft:stone' as BlockStatePredicate
		def rule = predicate as RuleTest
		TestRuntime.assertThat(rule.test(Blocks.STONE.defaultBlockState(), RandomSource.create())).isTrue()
		TestRuntime.assertThat(rule.test(Blocks.DIRT.defaultBlockState(), RandomSource.create())).isFalse()
	}
	TestRuntime.check('wrapper.fluid_stack') {
		def stack = '250x minecraft:water' as FluidStack
		TestRuntime.assertThat(stack.getFluid()).isSameAs(Fluids.WATER)
		TestRuntime.assertThat(stack.getAmount()).isEqualTo(250)
	}
	TestRuntime.check('wrapper.fluid_ingredient') {
		def ingredient = 'minecraft:water' as FluidIngredient
		TestRuntime.assertThat(ingredient.test(new FluidStack(Fluids.WATER, 250))).isTrue()
		TestRuntime.assertThat(ingredient.test(new FluidStack(Fluids.LAVA, 250))).isFalse()
	}
	TestRuntime.check('wrapper.sized_fluid_ingredient') {
		def ingredient = '500x minecraft:water' as SizedFluidIngredient
		TestRuntime.assertThat(ingredient.amount()).isEqualTo(500)
		TestRuntime.assertThat(ingredient.test(new FluidStack(Fluids.WATER, 500))).isTrue()
	}
	TestRuntime.check('wrapper.recipe_filter') {
		def filter = '*' as RecipeFilter
		TestRuntime.assertThat(filter.test(null)).isTrue()
	}
	TestRuntime.check('wrapper.slot_filter') {
		def filter = 2 as SlotFilter
		TestRuntime.assertThat(filter.checkFilter(2, ItemStack.EMPTY)).isTrue()
		TestRuntime.assertThat(filter.checkFilter(1, ItemStack.EMPTY)).isFalse()
	}
	TestRuntime.check('wrapper.tool_material') {
		def material = 'diamond' as ToolMaterial
		TestRuntime.assertThat(material).isSameAs(ToolMaterial.DIAMOND)
	}
	TestRuntime.check('wrapper.damage_source') {
		def source = 'minecraft:generic' as DamageSource
		TestRuntime.assertThat(source.getMsgId()).isEqualTo('generic')
	}
	TestRuntime.check('wrapper.entity_selector') {
		def selector = '@e[type=minecraft:pig,limit=1]' as net.minecraft.commands.arguments.selector.EntitySelector
		TestRuntime.assertThat(selector.getMaxResults()).isEqualTo(1)
		TestRuntime.assertThat(selector.includesEntities()).isTrue()
	}
	TestRuntime.check('wrapper.replacement_match') {
		def match = 'minecraft:diamond' as ReplacementMatch
		TestRuntime.assertThat(match.matches(null, new ItemStack(Items.DIAMOND), false)).isTrue()
		TestRuntime.assertThat(match.matches(null, new ItemStack(Items.STICK), false)).isFalse()
	}
	TestRuntime.check('wrapper.replacement_match_info') {
		def info = 'minecraft:diamond' as ReplacementMatchInfo
		TestRuntime.assertThat(info.exact()).isFalse()
		TestRuntime.assertThat(info.match().matches(null, new ItemStack(Items.DIAMOND), false)).isTrue()
	}
	TestRuntime.check('wrapper.stat') {
		def stat = Stats.PLAY_TIME as Stat
		TestRuntime.assertThat(stat.getValue().toString()).isEqualTo('minecraft:play_time')
	}
	TestRuntime.check('wrapper.sound_type') {
		def sound = 'wool' as SoundType
		TestRuntime.assertThat(sound).isSameAs(SoundType.WOOL)
	}
	TestRuntime.check('wrapper.particle_options') {
		def particle = 'minecraft:flame' as ParticleOptions
		TestRuntime.assertThat(particle.getType()).isSameAs(ParticleTypes.FLAME)
	}
	TestRuntime.check('wrapper.item_tint') {
		def fixed = '#112233' as ItemTintFunction
		TestRuntime.assertThat(fixed.getColor(ItemStack.EMPTY, 0).toHexString()).isEqualTo('#112233')
		def mapped = ['#010203', null, '#A0B0C0'] as ItemTintFunction
		TestRuntime.assertThat(mapped.getMaxTintIndex()).isEqualTo(2)
		TestRuntime.assertThat(mapped.getColor(ItemStack.EMPTY, 2).toHexString()).isEqualTo('#A0B0C0')
	}
	TestRuntime.check('wrapper.block_tint') {
		def fixed = '#445566' as BlockTintFunction
		def state = Blocks.STONE.defaultBlockState()
		TestRuntime.assertThat(fixed.getColor(state, null, null, 0).toHexString()).isEqualTo('#445566')
		def mapped = ['#010203', null, '#A0B0C0'] as BlockTintFunction
		TestRuntime.assertThat(mapped.getMaxTintIndex()).isEqualTo(2)
		TestRuntime.assertThat(mapped.getColor(state, null, null, 2).toHexString()).isEqualTo('#A0B0C0')
	}
	TestRuntime.check('wrapper.text_color') {
		def color = '#123456' as TextColor
		TestRuntime.assertThat(color.getValue()).isEqualTo(0x123456)
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
	}
}
