import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.Tag
import net.minecraft.world.item.ItemStack

def coverageWrappersRan = false
$ServerEvents.tick { event ->
	if (coverageWrappersRan) return
	coverageWrappersRan = true

	$TestRuntime.check('coverage.wrappers') {
		def joined = $Text.joinSep($Text.string(', '), [$Text.string('one'), $Text.string('two')])
		$TestRuntime.assertThat(joined.getString()).isEqualTo('one, two')
		$TestRuntime.assertThat($Text.translateWithFallback('kubegroovy.coverage.missing', 'coverage fallback').getString()).isEqualTo('coverage fallback')

		def original = 'minecraft:diamond' as ItemStack
		def counted = $Item.of(original, 3)
		def components = [(DataComponents.CUSTOM_NAME): $Text.string('Coverage Diamond')]
		def named = $Item.of(counted, components)
		$TestRuntime.assertThat(original.getCount()).isEqualTo(1)
		$TestRuntime.assertThat(counted.getCount()).isEqualTo(3)
		$TestRuntime.assertThat(named.getHoverName().getString()).isEqualTo('Coverage Diamond')

		def byteArray = $NBT.byteArrayTag([1, 2])
		def intArray = $NBT.intArrayTag([3, 4])
		def longArray = $NBT.longArrayTag([5L, 6L])
		$TestRuntime.assertThat(byteArray.getId()).isEqualTo(Tag.TAG_BYTE_ARRAY)
		$TestRuntime.assertThat(intArray.getId()).isEqualTo(Tag.TAG_INT_ARRAY)
		$TestRuntime.assertThat(longArray.getId()).isEqualTo(Tag.TAG_LONG_ARRAY)
		["minecraft:diamond"].each(aaa -> {
			def exists = $Item.exists(aaa)
			$TestRuntime.assertThat(exists).isTrue()
		})
	}
}
