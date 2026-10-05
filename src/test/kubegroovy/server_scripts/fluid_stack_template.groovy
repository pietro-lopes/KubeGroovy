import dev.uncandango.kubegroovy.recipe.RecipeScriptContext
import dev.uncandango.kubegroovy.recipe.component.FluidStackTemplateComponent
import dev.uncandango.kubegroovy.recipe.match.ReplacementMatchInfo
import dev.uncandango.kubegroovy.recipe.schema.RecipeSchemaStorage
import dev.uncandango.kubegroovy.util.RegistryAccessContainer
import net.minecraft.core.component.DataComponents
import net.minecraft.world.level.material.Fluids
import net.neoforged.neoforge.fluids.FluidStackTemplate
import net.neoforged.neoforge.fluids.FluidStack
import org.assertj.core.api.Assertions

$ServerEvents.recipes { event ->
	$TestRuntime.check('recipes.fluid_template') {
		def component = FluidStackTemplateComponent.FLUID_STACK_TEMPLATE
		def ops = RegistryAccessContainer.of()
		$TestRuntime.assertThat(ops.decode(RecipeSchemaStorage.COMPONENT_CODEC, 'fluid_stack_template').is(component)).isTrue()
		def template = ops.decode(component.codec(), [id: 'minecraft:water', amount: 250, components: ['minecraft:enchantment_glint_override': true]])
		$TestRuntime.assertThat(template.amount()).isEqualTo(250)
		$TestRuntime.assertThat(template.fluid().value()).isEqualTo(Fluids.WATER)
		$TestRuntime.assertThat(template.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)).isTrue()
		def encoded = component.codec().encodeStart(ops.json(), template).getOrThrow()
		$TestRuntime.assertThat(component.codec().parse(ops.json(), encoded).getOrThrow()).isEqualTo(template)
		$TestRuntime.assertThat(ops.decode(component.codec(), 'minecraft:water').amount()).isEqualTo(1000)
		$TestRuntime.assertThat(component.codec().parse(ops.json(), $JsonUtils.fromString('{"id":"minecraft:water","amount":0}')).isError()).isTrue()
		$TestRuntime.assertThat(component.codec().parse(ops.json(), $JsonUtils.fromString('{"id":"minecraft:water"}')).isError()).isTrue()

		def fromString = '250x minecraft:water' as FluidStackTemplate
		$TestRuntime.assertThat(fromString.amount()).isEqualTo(250)
		def fromStack = template.create() as FluidStackTemplate
		$TestRuntime.assertThat(fromStack).isEqualTo(template)
		$TestRuntime.assertThat((template as FluidStackTemplate) === template).isTrue()
		Assertions.assertThatThrownBy(() -> FluidStack.EMPTY as FluidStackTemplate).isInstanceOf(IllegalStateException)

		def contextRecipe = event.shapeless('minecraft:apple', ['minecraft:stone']).id('kubegroovy:fluid_template_context')
		def context = new RecipeScriptContext.Impl(contextRecipe)
		def waterMatch = ReplacementMatchInfo.wrap($Fluid.water(), ReplacementMatchInfo.TYPE_INFO)
		def lavaMatch = ReplacementMatchInfo.wrap($Fluid.lava(), ReplacementMatchInfo.TYPE_INFO)
		$TestRuntime.assertThat(component.matches(context, template, waterMatch)).isTrue()
		$TestRuntime.assertThat(component.matches(context, template, lavaMatch)).isFalse()
		$TestRuntime.assertThat(component.replace(context, template, lavaMatch, 'minecraft:lava') === template).isTrue()
		def replaced = component.replace(context, template, waterMatch, 'minecraft:lava')
		$TestRuntime.assertThat(replaced.fluid().value()).isEqualTo(Fluids.LAVA)
		$TestRuntime.assertThat(replaced.amount()).isEqualTo(250)
		$TestRuntime.assertThat(component.wrap(context, template) === template).isTrue()
	}
}
