package dev.uncandango.kubegroovy.client.model;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperty;
import org.jspecify.annotations.Nullable;

public record KubeGroovyConditionalCallbackProperty(Identifier id) implements ConditionalItemModelProperty {
	public static final MapCodec<KubeGroovyConditionalCallbackProperty> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		Identifier.CODEC.fieldOf("id").forGetter(KubeGroovyConditionalCallbackProperty::id)
	).apply(i, KubeGroovyConditionalCallbackProperty::new));

	@Override
	public boolean get(ItemStack itemStack, @Nullable ClientLevel level, @Nullable LivingEntity owner, int seed, ItemDisplayContext displayContext) {
		var cb = KubeGroovyModelPropertyRegistry.getConditional(id);
		return cb != null && cb.get(itemStack, level, owner, seed, displayContext);
	}

	@Override
	public MapCodec<KubeGroovyConditionalCallbackProperty> type() {
		return MAP_CODEC;
	}
}
