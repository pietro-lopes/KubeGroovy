import dev.uncandango.kubegroovy.util.KubeIdentifier
import dev.uncandango.kubegroovy.color.KubeColor
import net.minecraft.world.level.block.SoundType
import net.minecraft.network.chat.MutableComponent
import net.minecraft.world.item.Items
import net.minecraft.world.item.crafting.Ingredient

StartupEvents.registry('item') { event ->
	event.create('wiki_item' as KubeIdentifier)
	event.create('wiki_item_glow' as KubeIdentifier).maxStackSize(16).glow(true)
}

StartupEvents.registry('block') { event ->
	event.create('wiki_block' as KubeIdentifier)
		.displayName('My Custom Block' as MutableComponent)
		.soundType('wool' as SoundType)
		.hardness(1)
		.resistance(1)
		.tagBlock(['minecraft:mineable/pickaxe' as net.minecraft.resources.Identifier] as net.minecraft.resources.Identifier[])
		.requiresTool(true)
}

StartupEvents.registry('creative_mode_tab') { event ->
	def content = Ingredient.of(Items.DIRT, Items.GRASS_BLOCK)
	event.create('wiki_tab' as KubeIdentifier)
		.icon { Items.DIRT.defaultInstance }
		.content { content }
}

StartupEvents.registry('mob_effect') { event ->
	event.create('wiki_effect' as KubeIdentifier).color(0x00FF00 as KubeColor).beneficial()
}
