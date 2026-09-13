package dev.uncandango.kubegroovy.client.icon;

import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.uncandango.kubegroovy.KubeGroovy;
import net.minecraft.world.item.ItemStack;

public record ItemKubeIcon(ItemStack item) implements KubeIcon {
	public static final KubeIconType<ItemKubeIcon> TYPE = new KubeIconType<>(KubeGroovy.id("item"), RecordCodecBuilder.mapCodec(instance -> instance.group(
		ItemStack.CODEC.fieldOf("item").forGetter(ItemKubeIcon::item)
	).apply(instance, ItemKubeIcon::new)), ItemStack.STREAM_CODEC.map(ItemKubeIcon::new, ItemKubeIcon::item));

	@Override
	public KubeIconType<?> getType() {
		return TYPE;
	}
}
