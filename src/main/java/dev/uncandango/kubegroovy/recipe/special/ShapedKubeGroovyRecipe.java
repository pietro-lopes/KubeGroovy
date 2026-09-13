package dev.uncandango.kubegroovy.recipe.special;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.uncandango.kubegroovy.codec.KubeGroovyStreamCodecs;
import dev.uncandango.kubegroovy.recipe.KubeGroovyRecipeSerializers;
import dev.uncandango.kubegroovy.recipe.ingredientaction.IngredientActionHolder;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;

import java.util.List;

public class ShapedKubeGroovyRecipe extends ShapedRecipe implements KubeGroovyCraftingRecipe {
	public static final MapCodec<ShapedKubeGroovyRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
		CommonInfo.MAP_CODEC.forGetter(ShapedKubeGroovyRecipe::commonInfo),
		CraftingBookInfo.MAP_CODEC.forGetter(ShapedKubeGroovyRecipe::bookInfo),
		ShapedRecipePattern.MAP_CODEC.forGetter(ShapedKubeGroovyRecipe::pattern),
		ItemStackTemplate.CODEC.fieldOf("result").forGetter(ShapedKubeGroovyRecipe::result),
		Codec.BOOL.optionalFieldOf(MIRROR_KEY, true).forGetter(ShapedKubeGroovyRecipe::kgy$getMirror),
		IngredientActionHolder.LIST_CODEC.optionalFieldOf(INGREDIENT_ACTIONS_KEY, List.of()).forGetter(ShapedKubeGroovyRecipe::kgy$getIngredientActions),
		Codec.STRING.optionalFieldOf(MODIFY_RESULT_KEY, "").forGetter(ShapedKubeGroovyRecipe::kgy$getModifyResult)
	).apply(instance, ShapedKubeGroovyRecipe::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, ShapedKubeGroovyRecipe> STREAM_CODEC = KubeGroovyStreamCodecs.composite(
		CommonInfo.STREAM_CODEC, ShapedKubeGroovyRecipe::commonInfo,
		CraftingBookInfo.STREAM_CODEC, ShapedKubeGroovyRecipe::bookInfo,
		ShapedRecipePattern.STREAM_CODEC, ShapedKubeGroovyRecipe::pattern,
		ItemStackTemplate.STREAM_CODEC, ShapedKubeGroovyRecipe::result,
		ByteBufCodecs.BOOL, ShapedKubeGroovyRecipe::kgy$getMirror,
		IngredientActionHolder.LIST_STREAM_CODEC, ShapedKubeGroovyRecipe::kgy$getIngredientActions,
		ByteBufCodecs.STRING_UTF8.cast(), ShapedKubeGroovyRecipe::kgy$getModifyResult,
		ShapedKubeGroovyRecipe::new
	);
	public static final RecipeSerializer<?> SERIALIZER = new RecipeSerializer<>(CODEC, STREAM_CODEC);

	private final boolean mirror;
	private final List<IngredientActionHolder> ingredientActions;
	private final String modifyResult;

	public ShapedKubeGroovyRecipe(CommonInfo commonInfo, CraftingBookInfo bookInfo, ShapedRecipePattern pattern, ItemStackTemplate result, boolean mirror, List<IngredientActionHolder> ingredientActions, String modifyResult) {
		super(commonInfo, bookInfo, pattern, result);
		this.mirror = mirror;
		this.ingredientActions = ingredientActions;
		this.modifyResult = modifyResult;

		if (!mirror) {
			this.pattern.symmetrical = true;
		}
	}

	@Override
	public RecipeSerializer<ShapedRecipe> getSerializer() {
		return (RecipeSerializer<ShapedRecipe>) KubeGroovyRecipeSerializers.SHAPED.get();
	}

	@Override
	public List<IngredientActionHolder> kgy$getIngredientActions() {
		return ingredientActions;
	}

	@Override
	public String kgy$getModifyResult() {
		return modifyResult;
	}

	public boolean kgy$getMirror() {
		return mirror;
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

	private ShapedRecipePattern pattern() {
		return pattern;
	}

	private ItemStackTemplate result() {
		return result;
	}

	public ItemStack getResultItem() {
		return result.create();
	}
}