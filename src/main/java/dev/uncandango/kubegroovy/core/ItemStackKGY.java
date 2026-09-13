package dev.uncandango.kubegroovy.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import dev.uncandango.kubegroovy.codec.KubeGroovyCodecs;
import dev.uncandango.kubegroovy.component.DataComponentWrapper;
import dev.uncandango.kubegroovy.core.component.ItemComponentFunctions;
import dev.uncandango.kubegroovy.level.LevelBlock;
import dev.uncandango.kubegroovy.plugin.builtin.wrapper.IngredientWrapper;
import dev.uncandango.kubegroovy.plugin.builtin.wrapper.ItemWrapper;
import dev.uncandango.kubegroovy.recipe.RecipeScriptContext;
import dev.uncandango.kubegroovy.recipe.filter.RecipeMatchContext;
import dev.uncandango.kubegroovy.recipe.match.ItemMatch;
import dev.uncandango.kubegroovy.recipe.match.Replaceable;
import dev.uncandango.kubegroovy.util.Cast;
import dev.uncandango.kubegroovy.util.ID;
import dev.uncandango.kubegroovy.util.RegistryAccessContainer;
import dev.uncandango.kubegroovy.util.WithCodec;
import dev.uncandango.kubegroovy.web.RelativeURL;
import dev.uncandango.kubegroovy.script.bridge.Context;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import dev.uncandango.kubegroovy.script.annotations.ReturnsSelf;
import dev.uncandango.kubegroovy.script.annotations.SpecialEquality;
import dev.uncandango.kubegroovy.script.annotations.ToStringGY;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

@SuppressWarnings("unused")
@RemapPrefixForGY("kgy$")
public interface ItemStackKGY extends
	SpecialEquality,
	WithCodec,
	IngredientSupplierKGY,
	ToStringGY,
	Replaceable,
	ItemComponentFunctions,
	MutableDataComponentHolderKGY,
	ItemMatch,
	RegistryObjectKGY<Item> {
	default ItemStack kgy$self() {
		return (ItemStack) this;
	}

	@Override
	default boolean specialEquals(Context cx, @Nullable Object o, boolean shallow) {
		return switch (o) {
			case CharSequence cs -> kgy$getId().equals(ID.string(cs.toString()));
			case Identifier id -> kgy$getIdLocation().equals(id);
			case ItemStack s -> kgy$equalsIgnoringCount(s);
			case null, default -> KubeGroovyCodecs.filter(ItemWrapper.wrapResult(cx, o), this::kgy$equalsIgnoringCount);
		};
	}

	default boolean kgy$equalsIgnoringCount(ItemStack stack) {
		var self = kgy$self();

		if (self == stack) {
			return true;
		} else if (self.isEmpty()) {
			return stack.isEmpty();
		}

		return ItemStack.isSameItemSameComponents(self, stack);
	}

	@Override
	default ResourceKey<Registry<Item>> kgy$getRegistryId() {
		return Registries.ITEM;
	}

	@Override
	default Registry<Item> kgy$getRegistry() {
		return BuiltInRegistries.ITEM;
	}

	@Override
	default Identifier kgy$getIdLocation() {
		return kgy$self().getItem().kgy$getIdLocation();
	}

	@Override
	default Holder<Item> kgy$asHolder() {
		return kgy$self().getItem().kgy$asHolder();
	}

	@Override
	default ResourceKey<Item> kgy$getKey() {
		return kgy$self().getItem().kgy$getKey();
	}

	@Override
	default String kgy$getId() {
		return kgy$self().getItem().kgy$getId();
	}

	@Override
	default String kgy$getMod() {
		return kgy$self().getItem().kgy$getMod();
	}

	@Nullable
	default Block kgy$getBlock() {
		return kgy$self().getItem() instanceof BlockItem bi ? bi.getBlock() : null;
	}

	@ReturnsSelf(copy = true)
	default ItemStack kgy$withCount(int c) {
		if (c <= 0 || kgy$self().isEmpty()) {
			return ItemStack.EMPTY;
		}

		var is = kgy$self().copy();
		is.setCount(c);
		return is;
	}

	@Override
	default String kgy$getComponentString(Context cx) {
		return DataComponentWrapper.patchToString(new StringBuilder(), RegistryAccessContainer.of(cx).nbt(), kgy$self().getComponentsPatch()).toString();
	}

	@ReturnsSelf(copy = true)
	default ItemStack kgy$withCustomName(@Nullable Component name) {
		var is = kgy$self().copy();
		is.kgy$setCustomName(name);
		return is;
	}

	default ItemEnchantments kgy$getEnchantments() {
		return EnchantmentHelper.getEnchantmentsForCrafting(kgy$self());
	}

	default boolean kgy$hasEnchantment(Holder<Enchantment> enchantment, int level) {
		var e = kgy$getEnchantments();
		return e != null && e.getLevel(enchantment) >= level;
	}

	@ReturnsSelf
	default ItemStack kgy$enchant(Holder<Enchantment> enchantment, int level) {
		var is = kgy$self();
		is.enchant(enchantment, level);
		return is;
	}

	@ReturnsSelf(copy = true)
	default ItemStack kgy$enchant(ItemEnchantments enchantments) {
		var is = kgy$self().copy();

		EnchantmentHelper.updateEnchantments(is, mutable -> {
			for (var entry : enchantments.entrySet()) {
				mutable.upgrade(entry.getKey(), entry.getIntValue());
			}
		});

		return is;
	}

	default boolean kgy$areItemsEqual(ItemStack other) {
		return kgy$self().getItem() == other.getItem();
	}

	default boolean kgy$areComponentsEqual(ItemStack other) {
		return ItemStack.isSameItemSameComponents(kgy$self(), other);
	}

	default float kgy$getHarvestSpeed(@Nullable LevelBlock block) {
		return kgy$self().getDestroySpeed(block == null ? Blocks.AIR.defaultBlockState() : block.getBlockState());
	}

	default float kgy$getHarvestSpeed() {
		return kgy$getHarvestSpeed(null);
	}

	default Map<String, Object> kgy$getTypeData() {
		return kgy$self().getItem().kgy$getTypeData();
	}

	@Override
	default String toStringGY(Context cx) {
		return kgy$toItemString0(RegistryAccessContainer.of(cx).nbt());
	}

	default String kgy$toItemString(Context cx) {
		return kgy$toItemString0(RegistryAccessContainer.of(cx).nbt());
	}

	default String kgy$toItemString0(@Nullable DynamicOps<Tag> dynamicOps) {
		var is = kgy$self();
		var count = is.getCount();

		if (count <= 0) {
			return "minecraft:air";
		}

		var builder = new StringBuilder();
		builder.append('\'');

		if (count > 1) {
			builder.append(count);
			builder.append("x ");
		}

		builder.append(kgy$getId());

		if (!is.isComponentsPatchEmpty()) {
			DataComponentWrapper.patchToString(builder, dynamicOps, is.getComponentsPatch());
		}

		builder.append('\'');
		return builder.toString();
	}

	@Override
	default Ingredient kgy$asIngredient() {
		var p = kgy$self().getComponentsPatch();
		if (p.isEmpty()) {
			return Ingredient.of(HolderSet.direct(kgy$asHolder()));
		}
		var map = DataComponentMap.builder();
		for (var entry : p.entrySet()) {
			if (entry.getValue().isPresent()) {
				map.set(entry.getKey(), Cast.to(entry.getValue().get()));
			}
		}
		return IngredientWrapper.withData(HolderSet.direct(kgy$asHolder()), map.build());
	}

	@Override
	default Codec<ItemStack> getCodec(Context cx) {
		return ItemStack.CODEC;
	}

	@ReturnsSelf(copy = true)
	default ItemStack kgy$withLore(Component[] lines) {
		var is = kgy$self().copy();
		is.set(DataComponents.LORE, new ItemLore(List.of(lines)));
		return is;
	}

	@ReturnsSelf(copy = true)
	default ItemStack kgy$withLore(Component[] lines, Component[] styledLines) {
		var is = kgy$self().copy();
		is.set(DataComponents.LORE, new ItemLore(List.of(lines), List.of(styledLines)));
		return is;
	}

	@Override
	default Object replaceThisWith(RecipeScriptContext cx, Object with) {
		var t = kgy$self();
		var r = ItemWrapper.wrap(cx.cx(), with);

		if (!ItemStack.isSameItemSameComponents(t, r)) {
			r.setCount(t.getCount());
			return r;
		}

		return this;
	}

	@Override
	default boolean matches(RecipeMatchContext cx, ItemStack s, boolean exact) {
		return kgy$self().getItem() == s.getItem();
	}

	@Override
	default boolean matches(RecipeMatchContext cx, Ingredient in, boolean exact) {
		return in.test(kgy$self());
	}

	@Override
	default boolean matches(RecipeMatchContext cx, ItemLike itemLike, boolean exact) {
		return kgy$self().getItem() == itemLike.asItem();
	}

	default RelativeURL kgy$getWebIconURL(DynamicOps<Tag> ops, int size) {
		var url = "/img/" + size + "/item/" + ID.url(kgy$getIdLocation());
		var c = DataComponentWrapper.patchToString(new StringBuilder(), ops, DataComponentWrapper.visualPatch(kgy$self().getComponentsPatch())).toString();
		return new RelativeURL(url, c.equals("[]") ? Map.of() : Map.of("components", c.substring(1, c.length() - 1)));
	}
}
