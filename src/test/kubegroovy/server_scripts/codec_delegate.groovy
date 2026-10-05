import dev.uncandango.kubegroovy.recipe.RecipeScriptContext
import dev.uncandango.kubegroovy.recipe.component.RecipeComponentWithCodec
import dev.uncandango.kubegroovy.recipe.match.ReplacementMatchInfo
import dev.uncandango.kubegroovy.recipe.schema.RecipeSchemaStorage
import dev.uncandango.kubegroovy.util.RegistryAccessContainer
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.Items
import net.neoforged.neoforge.common.crafting.SizedIngredient
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient

$ServerEvents.recipes { event ->
	$TestRuntime.check('recipes.codec_delegate') {
		def ops = RegistryAccessContainer.of()
		def component = ops.decode(RecipeSchemaStorage.COMPONENT_CODEC, 'testmod:delegated_result')
		$TestRuntime.assertThat(component instanceof RecipeComponentWithCodec).isTrue()
		def value = ops.decode(component.codec(), [item: 'minecraft:apple', count: 120, components: ['minecraft:enchantment_glint_override': true]])
		$TestRuntime.assertThat(value.count()).isEqualTo(120)
		$TestRuntime.assertThat(value.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)).isTrue()
		def encodedValue = component.codec().encodeStart(ops.json(), value).getOrThrow()
		$TestRuntime.assertThat(encodedValue.getAsJsonObject().has('item')).isTrue()
		$TestRuntime.assertThat(encodedValue.getAsJsonObject().has('id')).isFalse()
		$TestRuntime.assertThat(component.codec().parse(ops.json(), encodedValue).getOrThrow()).isEqualTo(value)
		$TestRuntime.assertThat(ops.decode(component.codec(), [item: 'minecraft:apple']).count()).isEqualTo(1)
		$TestRuntime.assertThat(ops.decode(component.codec(), [item: 'minecraft:apple', count: 'bad']).count()).isEqualTo(1)

		def definition = RecipeSchemaStorage.COMPONENT_CODEC.encodeStart(ops.json(), component).getOrThrow()
		$TestRuntime.assertThat(definition.getAsJsonObject().get('type').getAsString()).isEqualTo('with_codec')
		def reloaded = RecipeSchemaStorage.COMPONENT_CODEC.parse(ops.json(), definition).getOrThrow()
		$TestRuntime.assertThat(reloaded.codec().parse(ops.json(), encodedValue).getOrThrow()).isEqualTo(value)
		def listDefinition = $JsonUtils.fromString('{"type":"list"}').getAsJsonObject()
		listDefinition.add('component', definition)
		def list = RecipeSchemaStorage.COMPONENT_CODEC.parse(ops.json(), listDefinition).getOrThrow()
		$TestRuntime.assertThat(ops.decode(list.codec(), [[item: 'minecraft:apple', count: 2]]).first().count()).isEqualTo(2)

		def recipe = event.shapeless('minecraft:apple', ['minecraft:stone']).id('kubegroovy:codec_delegate_context')
		def context = new RecipeScriptContext.Impl(recipe)
		def itemComponent = ops.decode(RecipeSchemaStorage.COMPONENT_CODEC, 'testmod:converted_item')
		def itemValue = ops.decode(itemComponent.codec(), [ingredient: 'minecraft:apple', amount: 3])
		$TestRuntime.assertThat(itemValue instanceof SizedIngredient).isTrue()
		$TestRuntime.assertThat(itemValue.count()).isEqualTo(3)
		$TestRuntime.assertThat(itemComponent.spread(itemValue).size()).isEqualTo(3)
		$TestRuntime.assertThat(itemComponent.wrap(context, '2x minecraft:apple').count()).isEqualTo(2)
		def itemEncoded = itemComponent.codec().encodeStart(ops.json(), itemValue).getOrThrow()
		$TestRuntime.assertThat(itemEncoded.getAsJsonObject().get('amount').getAsInt()).isEqualTo(3)
		$TestRuntime.assertThat(ops.decode(itemComponent.codec(), [ingredient: 'minecraft:apple']).count()).isEqualTo(1)
		def itemDefinition = RecipeSchemaStorage.COMPONENT_CODEC.encodeStart(ops.json(), itemComponent).getOrThrow()
		def itemReloaded = RecipeSchemaStorage.COMPONENT_CODEC.parse(ops.json(), itemDefinition).getOrThrow()
		$TestRuntime.assertThat(itemReloaded.codec().parse(ops.json(), itemEncoded).getOrThrow().count()).isEqualTo(3)
		def fluidComponent = ops.decode(RecipeSchemaStorage.COMPONENT_CODEC, 'testmod:converted_fluid')
		def fluidValue = ops.decode(fluidComponent.codec(), [ingredient: 'minecraft:water', amount: 250])
		$TestRuntime.assertThat(fluidValue instanceof SizedFluidIngredient).isTrue()
		$TestRuntime.assertThat(fluidValue.amount()).isEqualTo(250)
		def fluidEncoded = fluidComponent.codec().encodeStart(ops.json(), fluidValue).getOrThrow()
		$TestRuntime.assertThat(fluidComponent.codec().parse(ops.json(), fluidEncoded).getOrThrow().amount()).isEqualTo(250)
		$TestRuntime.assertThat(ops.decode(fluidComponent.codec(), [ingredient: 'minecraft:water']).amount()).isEqualTo(1)
		def wrapped = component.wrap(context, '3x minecraft:apple')
		$TestRuntime.assertThat(wrapped.count()).isEqualTo(3)
		def match = ReplacementMatchInfo.wrap(Items.APPLE.defaultInstance, ReplacementMatchInfo.TYPE_INFO)
		def replaced = component.replace(context, wrapped, match, 'minecraft:golden_apple')
		$TestRuntime.assertThat(replaced.item().value()).isEqualTo(Items.GOLDEN_APPLE)
		$TestRuntime.assertThat(replaced.count()).isEqualTo(3)
	}
}