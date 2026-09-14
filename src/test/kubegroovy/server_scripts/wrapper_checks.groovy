import dev.uncandango.kubegroovy.color.KubeColor
import dev.uncandango.kubegroovy.util.Tristate
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.MutableComponent
import net.minecraft.resources.Identifier
import net.minecraft.util.valueproviders.IntProvider
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.material.MapColor
import net.minecraft.world.phys.Vec3
import java.time.Duration
import java.util.UUID
import java.util.regex.Pattern

def wrapperChecksRun = false
ServerEvents.tick {
	if (wrapperChecksRun) return
	wrapperChecksRun = true

	TestRuntime.check('wrapper.vec3') {
		def v = [1, 2, 3] as Vec3
		TestRuntime.assertThat(v.x()).isEqualTo(1.0d)
		TestRuntime.assertThat(v.y()).isEqualTo(2.0d)
		TestRuntime.assertThat(v.z()).isEqualTo(3.0d)
	}
	TestRuntime.check('wrapper.blockpos') {
		def p = [4, 5, 6].asType(BlockPos)
		TestRuntime.assertThat(p.toShortString()).isEqualTo('4, 5, 6')
	}
	TestRuntime.check('wrapper.itemstack') {
		def stack = 'minecraft:diamond' as ItemStack
		TestRuntime.assertThat(stack.id).isEqualTo('minecraft:diamond')
	}
	TestRuntime.check('wrapper.component') {
		def component = 'hello' as MutableComponent
		TestRuntime.assertThat(component.getString()).isEqualTo('hello')
	}
	TestRuntime.check('wrapper.nbt') {
		def tag = [a: 1, b: 'two'] as CompoundTag
		TestRuntime.assertThat(tag.contains('a')).isTrue()
		TestRuntime.assertThat(tag.contains('b')).isTrue()
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
}
