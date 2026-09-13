package dev.uncandango.kubegroovy.recipe.special;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.uncandango.kubegroovy.recipe.KubeGroovyRecipeSerializers;
import dev.uncandango.kubegroovy.recipe.ingredientaction.IngredientActionHolder;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.crafting.ShapelessRecipe;

import java.util.List;

public class ShapelessKubeGroovyRecipe extends ShapelessRecipe implements KubeGroovyCraftingRecipe {
	public static final MapCodec<ShapelessKubeGroovyRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
		CommonInfo.MAP_CODEC.forGetter(ShapelessKubeGroovyRecipe::commonInfo),
		CraftingBookInfo.MAP_CODEC.forGetter(ShapelessKubeGroovyRecipe::bookInfo),
		ItemStackTemplate.CODEC.fieldOf("result").forGetter(r -> r.result),
		Codec.lazyInitialized(() -> Ingredient.CODEC.listOf(1, ShapedRecipePattern.getMaxHeight() * ShapedRecipePattern.getMaxWidth()))
			.fieldOf("ingredients").forGetter(r -> r.ingredients),
		IngredientActionHolder.LIST_CODEC.optionalFieldOf(INGREDIENT_ACTIONS_KEY, List.of()).forGetter(ShapelessKubeGroovyRecipe::kgy$getIngredientActions),
		Codec.STRING.optionalFieldOf(MODIFY_RESULT_KEY, "").forGetter(ShapelessKubeGroovyRecipe::kgy$getModifyResult)
	).apply(instance, ShapelessKubeGroovyRecipe::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, ShapelessKubeGroovyRecipe> STREAM_CODEC = StreamCodec.composite(
		CommonInfo.STREAM_CODEC, ShapelessKubeGroovyRecipe::commonInfo,
		CraftingBookInfo.STREAM_CODEC, ShapelessKubeGroovyRecipe::bookInfo,
		ItemStackTemplate.STREAM_CODEC, r -> r.result,
		Ingredient.CONTENTS_STREAM_CODEC.apply(ByteBufCodecs.list()), r -> r.ingredients,
		IngredientActionHolder.LIST_STREAM_CODEC, ShapelessKubeGroovyRecipe::kgy$getIngredientActions,
		ByteBufCodecs.STRING_UTF8.cast(), ShapelessKubeGroovyRecipe::kgy$getModifyResult,
		ShapelessKubeGroovyRecipe::new
	);
	public static final RecipeSerializer<ShapelessKubeGroovyRecipe> SERIALIZER = new RecipeSerializer<>(CODEC, STREAM_CODEC);

	private final List<IngredientActionHolder> ingredientActions;
	private final String modifyResult;

	public ShapelessKubeGroovyRecipe(CommonInfo commonInfo, CraftingBookInfo bookInfo, ItemStackTemplate result, List<Ingredient> ingredients, List<IngredientActionHolder> ingredientActions, String modifyResult) {
		super(commonInfo, bookInfo, result, ingredients);
		this.ingredientActions = ingredientActions;
		this.modifyResult = modifyResult;
	}

	@Override
	public RecipeSerializer<ShapelessRecipe> getSerializer() {
		return (RecipeSerializer<ShapelessRecipe>) KubeGroovyRecipeSerializers.SHAPELESS.get();
	}

	@Override
	public List<IngredientActionHolder> kgy$getIngredientActions() {
		return ingredientActions;
	}

	@Override
	public String kgy$getModifyResult() {
		return modifyResult;
	}

	@Override
	public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
		return kgy$getRemainingItems(input);
	}

	@Override
	public ItemStack assemble(CraftingInput input) {
		return kgy$assemble(input, super.assemble(input));
	}

	private CommonInfo commonInfo() {
		return commonInfo;
	}

	private CraftingBookInfo bookInfo() {
		return bookInfo;
	}
}