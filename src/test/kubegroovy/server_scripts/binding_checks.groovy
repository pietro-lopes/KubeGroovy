import dev.uncandango.kubegroovy.script.bridge.Context
import dev.uncandango.kubegroovy.testmod.TestRuntime
import dev.uncandango.kubegroovy.util.registrypredicate.RegistryHolderPredicate
import dev.uncandango.kubegroovy.util.registrypredicate.RegistryPredicate
import net.minecraft.server.MinecraftServer
import net.minecraft.server.TickTask
import net.minecraft.world.item.ItemStack
import net.minecraft.resources.Identifier
import org.assertj.core.api.Assertions

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

// Binding integration checks run once while the server scripts are loaded/ticking.

def stackOf = { value, count = null ->
	def stack = value as ItemStack
	if (count != null) stack.setCount(count as int)
	stack
}

TestRuntime.check('binding.id') {
	TestRuntime.assertThat(ID.namespace('thing')).isEqualTo('minecraft')
	TestRuntime.assertThat(ID.string('air')).isEqualTo('minecraft:air')
	TestRuntime.assertThat(ID.kgyString('x')).isEqualTo('kubegroovy:x')
}

TestRuntime.check('binding.text') {
	TestRuntime.assertThat(Text.ofString('hello').getString()).isEqualTo('hello')
}

TestRuntime.check('binding.text.builders') {
	TestRuntime.assertThat(Text.string('plain').getString()).isEqualTo('plain')
	TestRuntime.assertThat(Text.literal('lit').getString()).isEqualTo('lit')
	TestRuntime.assertThat(Text.string('wrapped').getString()).isEqualTo('wrapped')
	TestRuntime.assertThat(Text.string('a').append(Text.string('b')).getString()).isEqualTo('ab')
	TestRuntime.assertThat(Text.isEmpty(Text.empty())).isTrue()
	TestRuntime.assertThat(Text.translate('kubegroovy.test.missing.key').getString()).isEqualTo('kubegroovy.test.missing.key')
}

TestRuntime.check('binding.text.styled') {
	def styled = ([text: 'hi', bold: true, color: 'red'] as net.minecraft.network.chat.MutableComponent)
	TestRuntime.assertThat(styled.getString()).isEqualTo('hi')
	TestRuntime.assertThat(styled.getStyle().isBold()).isTrue()
	TestRuntime.assertThat(styled.getStyle().getColor() != null).isTrue()
	TestRuntime.assertThat(Text.red(Text.string('x')).getStyle().getColor() != null).isTrue()
}

TestRuntime.check('binding.nbt.compound') {
	def tag = NBT.compoundTag([a: 1, b: 'two'])
	TestRuntime.assertThat(tag.contains('a')).isTrue()
	TestRuntime.assertThat(tag.contains('b')).isTrue()
	TestRuntime.assertThat(tag.size() == 2).isTrue()
	TestRuntime.assertThat(NBT.compoundTag().isEmpty()).isTrue()
}

TestRuntime.check('binding.nbt.typed') {
	TestRuntime.assertThat(NBT.intTag(5).toString()).isEqualTo('5')
	TestRuntime.assertThat(NBT.longTag(7).toString()).isEqualTo('7L')
	TestRuntime.assertThat(NBT.stringTag('hi').toString()).isEqualTo('"hi"')
	TestRuntime.assertThat(NBT.listTag(['a', 'b']).toString()).isEqualTo('["a","b"]')
}

TestRuntime.check('binding.nbt.json') {
	TestRuntime.assertThat(NBT.toJson(NBT.compoundTag([k: 'v'])).toString()).isEqualTo('{"k":"v"}')
}

TestRuntime.check('binding.json.roundtrip') {
	TestRuntime.assertThat(JsonUtils.toString(JsonUtils.fromString('{"msg":"hello"}'))).isEqualTo('{"msg":"hello"}')
	TestRuntime.assertThat(JsonUtils.toObject(JsonUtils.fromString('{"msg":"hi"}')).get('msg') == 'hi').isTrue()
}

TestRuntime.check('binding.json.build') {
	TestRuntime.assertThat(JsonUtils.toString(JsonUtils.of([msg: 'hi']))).isEqualTo('{"msg":"hi"}')
	TestRuntime.assertThat(JsonUtils.toString(JsonUtils.arrayOf(['x', 'y']))).isEqualTo('["x","y"]')
	TestRuntime.assertThat(JsonUtils.toPrettyString(JsonUtils.fromString('{"a":"b"}')).contains('\n')).isTrue()
	def original = JsonUtils.fromString('{"a":"b"}')
	def copy = JsonUtils.copy(original)
	TestRuntime.assertThat(copy !== original).isTrue()
	TestRuntime.assertThat(JsonUtils.toString(copy)).isEqualTo('{"a":"b"}')
}

TestRuntime.check('binding.jsonio') {
	JsonIO.write('kubegroovy/test_binding.json', [hello: 'world', count: 5])
	TestRuntime.assertThat(JsonIO.readString('kubegroovy/test_binding.json')).contains('world')
}

TestRuntime.check('binding.nbtio') {
	NBTIO.write('kubegroovy/test_binding.nbt', [a: 1])
	def tag = NBTIO.read('kubegroovy/test_binding.nbt')
	TestRuntime.assertThat(tag.contains('a')).isTrue()
}

def bindingChecksRun = false
ServerEvents.tick { event ->
	if (bindingChecksRun) return
	bindingChecksRun = true

	TestRuntime.check('binding.item.of') {
		def stack = stackOf('minecraft:diamond')
		TestRuntime.assertThat(stack.id).isEqualTo('minecraft:diamond')
		TestRuntime.assertThat(stack.getCount() == 1).isTrue()
	}
	TestRuntime.check('binding.item.count') {
		TestRuntime.assertThat(stackOf('minecraft:diamond', 4).getCount() == 4).as('count.direct').isTrue()
		TestRuntime.assertThat(stackOf('4x minecraft:diamond').getCount() == 4).as('count.parsed').isTrue()
	}
	TestRuntime.check('binding.item.meta') {
		TestRuntime.assertThat(Item.getEmpty().isEmpty()).as('Item.getEmpty').isTrue()
		TestRuntime.assertThat(Item.isItem(stackOf('minecraft:diamond'))).as('Item.isItem(ItemStack)').isTrue()
		TestRuntime.assertThat(Item.isItem('minecraft:diamond')).as('Item.isItem(String)').isFalse()
		TestRuntime.assertThat(Item.exists('minecraft:diamond' as Identifier)).as('Item.exists(diamond)').isTrue()
		TestRuntime.assertThat(Item.exists('minecraft:totally_not_a_real_item' as Identifier)).as('Item.exists(missing)').isFalse()
	}
	TestRuntime.check('binding.ingredient.match') {
		def diamonds = 'minecraft:diamond' as net.minecraft.world.item.crafting.Ingredient
		TestRuntime.assertThat(diamonds.test(stackOf('minecraft:diamond'))).isTrue()
		TestRuntime.assertThat(diamonds.test(stackOf('minecraft:dirt'))).isFalse()
	}
	TestRuntime.check('binding.ingredient.tag') {
		def planks = '#minecraft:planks' as net.minecraft.world.item.crafting.Ingredient
		TestRuntime.assertThat(planks.test(stackOf('minecraft:oak_planks'))).isTrue()
		TestRuntime.assertThat(planks.test(stackOf('minecraft:diamond'))).isFalse()
	}
	TestRuntime.check('binding.ingredient.compound') {
		def either = ['minecraft:diamond', 'minecraft:dirt'] as net.minecraft.world.item.crafting.Ingredient
		TestRuntime.assertThat(either.test(stackOf('minecraft:diamond'))).isTrue()
		TestRuntime.assertThat(either.test(stackOf('minecraft:dirt'))).isTrue()
		TestRuntime.assertThat(either.test(stackOf('minecraft:stone'))).isFalse()
	}
	TestRuntime.check('binding.ingredient.meta') {
		TestRuntime.assertThat(Ingredient.isIngredient('minecraft:diamond' as net.minecraft.world.item.crafting.Ingredient)).isTrue()
		TestRuntime.assertThat(Ingredient.isIngredient('minecraft:diamond')).isFalse()
		TestRuntime.assertThat(('minecraft:diamond' as net.minecraft.world.item.crafting.Ingredient).first.id).isEqualTo('minecraft:diamond')
	}
	TestRuntime.check('binding.context') {
		def context = Context.getCurrent();
		TestRuntime.assertThat(context).as("context at normal call").isNotNull()
	}
	TestRuntime.check('binding.context_async') {
		def executor = CompletableFuture.delayedExecutor(10, TimeUnit.SECONDS)
		def tasks = [
				CompletableFuture.runAsync({-> TestRuntime.assertThat(dev.uncandango.kubegroovy.testmod.binding.BindingTests.checkContext()).as("context after timeout").isNotNull()} as Runnable, executor),
				CompletableFuture.runAsync({-> TestRuntime.assertThat(dev.uncandango.kubegroovy.testmod.binding.BindingTests.checkContext(Context.getCurrent())).as("context after timeout").isNotNull()} as Runnable, executor)
		].toArray(CompletableFuture[]::new)
		CompletableFuture.allOf(tasks).join();
	}
	TestRuntime.check('binding.context_async2') {
		MinecraftServer server = event.server
		server.executeBlocking([run: {TestRuntime.assertThat("minecraft:diamond" as RegistryPredicate<net.minecraft.world.item.Item>).as("context on submit").isInstanceOf(RegistryHolderPredicate)}] as Runnable)
		def task = server.submitAsync([run: {TestRuntime.assertThat("invalid:item" as RegistryPredicate<net.minecraft.world.item.Item>).as("context on submit").isInstanceOf(RegistryHolderPredicate)}] as Runnable)
		while (!task.isDone()) {
		}
		Assertions.assertThat(task).isCompletedExceptionally()
	}
}
