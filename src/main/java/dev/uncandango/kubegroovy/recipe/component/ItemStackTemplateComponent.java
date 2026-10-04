package dev.uncandango.kubegroovy.recipe.component;

import com.mojang.serialization.Codec;
import dev.uncandango.kubegroovy.plugin.builtin.wrapper.ItemStackTemplateWrapper;
import dev.uncandango.kubegroovy.plugin.builtin.wrapper.ItemWrapper;
import dev.uncandango.kubegroovy.recipe.RecipeScriptContext;
import dev.uncandango.kubegroovy.recipe.filter.RecipeMatchContext;
import dev.uncandango.kubegroovy.recipe.match.ItemMatch;
import dev.uncandango.kubegroovy.recipe.match.ReplacementMatchInfo;
import dev.uncandango.kubegroovy.script.type.TypeInfo;
import dev.uncandango.kubegroovy.util.OpsContainer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStackTemplate;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public record ItemStackTemplateComponent(ResourceKey<RecipeComponentType<?>> type, Codec<ItemStackTemplate> codec) implements RecipeComponent<ItemStackTemplate> {
	public static final ItemStackTemplateComponent ITEM_STACK_TEMPLATE = new ItemStackTemplateComponent(
		RecipeComponentType.builtin("item_stack_template"), ItemStackTemplate.CODEC
	);

	@Override
	public TypeInfo typeInfo() {
		return ItemStackTemplateWrapper.TYPE_INFO;
	}

	@Override
	public boolean hasPriority(RecipeMatchContext cx, @Nullable Object from) {
		return from instanceof ItemStackTemplate || ItemWrapper.isItemStackLike(from);
	}

	@Override
	public boolean matches(RecipeMatchContext cx, ItemStackTemplate value, ReplacementMatchInfo match) {
		return match.match() instanceof ItemMatch m && m.matches(cx, value.create(), match.exact());
	}

	@Override
	public ItemStackTemplate replace(RecipeScriptContext cx, ItemStackTemplate original, ReplacementMatchInfo match, Object with) {
		return matches(cx, original, match) ? wrap(cx, with).withCount(original.count()) : original;
	}

	@Override
	public boolean isEmpty(ItemStackTemplate value) {
		return value.count() <= 0;
	}

	@Override
	public void buildUniqueId(UniqueIdBuilder builder, ItemStackTemplate value) {
		builder.append(value.item().value().kgy$getIdLocation());
	}

	@Override
	public String toString(OpsContainer ops, ItemStackTemplate value) {
		return value.create().kgy$toItemString0(ops.nbt());
	}

	@Override
	public List<ItemStackTemplate> spread(ItemStackTemplate value) {
		var list = new ArrayList<ItemStackTemplate>();
		for (int i = 0; i < value.count(); i++) {
			list.add(value.withCount(1));
		}
		return list;
	}

	@Override
	public String toString() {
		return type.toString();
	}
}