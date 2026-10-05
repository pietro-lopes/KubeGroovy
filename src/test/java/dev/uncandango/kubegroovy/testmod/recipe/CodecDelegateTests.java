package dev.uncandango.kubegroovy.testmod.recipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.testframework.DynamicTest;
import net.neoforged.testframework.annotation.ForEachTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.GameTest;

import static dev.uncandango.kubegroovy.testmod.GameAsserts.assertFired;
import static dev.uncandango.kubegroovy.testmod.GameAsserts.assertVerified;

@ForEachTest(groups = "kubegroovy.recipe.event")
public class CodecDelegateTests {
	private record CustomItem(Ingredient ingredient, int amount) {
		private static final Codec<CustomItem> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Ingredient.CODEC.fieldOf("ingredient").forGetter(CustomItem::ingredient),
			ExtraCodecs.POSITIVE_INT.optionalFieldOf("amount", 1).forGetter(CustomItem::amount)
		).apply(instance, CustomItem::new));
	}

	private record CustomFluid(FluidIngredient ingredient, int amount) {
		private static final Codec<CustomFluid> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			FluidIngredient.CODEC.fieldOf("ingredient").forGetter(CustomFluid::ingredient),
			ExtraCodecs.POSITIVE_INT.optionalFieldOf("amount", 1).forGetter(CustomFluid::amount)
		).apply(instance, CustomFluid::new));
	}

	private static final MapCodec<ItemStackTemplate> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
		BuiltInRegistries.ITEM.holderByNameCodec().fieldOf("item").forGetter(ItemStackTemplate::typeHolder),
		ExtraCodecs.POSITIVE_INT.fieldOf("count").orElse(1).forGetter(ItemStackTemplate::count),
		DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY).forGetter(ItemStackTemplate::components)
	).apply(instance, ItemStackTemplate::new));

	@GameTest
	@EmptyTemplate(floor = true)
	@TestHolder(value = "codec_delegate", description = "JSON components resolve private mod codecs and retain their parent behavior")
	static void codecDelegate(final DynamicTest test) {
		test.onGameTest(helper -> {
			assertFired(helper, "recipes.codec_delegate");
			assertVerified(helper, "recipes.codec_delegate");
			helper.succeed();
		});
	}
}