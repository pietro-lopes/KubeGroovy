package dev.uncandango.kubegroovy.core;

import dev.uncandango.kubegroovy.block.RandomTickKubeEvent;
import dev.uncandango.kubegroovy.plugin.builtin.event.BlockEvents;
import dev.uncandango.kubegroovy.plugin.builtin.wrapper.BlockWrapper;
import dev.uncandango.kubegroovy.recipe.RecipeScriptContext;
import dev.uncandango.kubegroovy.recipe.match.Replaceable;
import dev.uncandango.kubegroovy.script.ScriptType;
import dev.uncandango.kubegroovy.util.Cast;
import dev.uncandango.kubegroovy.util.ID;
import dev.uncandango.kubegroovy.web.RelativeURL;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

@RemapPrefixForGY("kgy$")
public interface BlockStateKGY extends RegistryObjectKGY<Block>, Replaceable {
	@Override
	default ResourceKey<Registry<Block>> kgy$getRegistryId() {
		return Registries.BLOCK;
	}

	@Override
	default Registry<Block> kgy$getRegistry() {
		return BuiltInRegistries.BLOCK;
	}

	@Override
	default Holder<Block> kgy$asHolder() {
		return ((BlockBehaviour.BlockStateBase) this).getBlock().kgy$asHolder();
	}

	@Override
	default ResourceKey<Block> kgy$getKey() {
		return ((BlockBehaviour.BlockStateBase) this).getBlock().kgy$getKey();
	}

	@Override
	default String kgy$getId() {
		return ((BlockBehaviour.BlockStateBase) this).getBlock().kgy$getId();
	}

	default void kgy$setDestroySpeed(float v) {
		throw new NoMixinException();
	}

	default void kgy$setRequiresTool(boolean v) {
		throw new NoMixinException();
	}

	default void kgy$setLightEmission(int v) {
		throw new NoMixinException();
	}

	default boolean kgy$randomTickOverride(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (BlockEvents.RANDOM_TICK.hasListeners(state.kgy$getKey())) {
			return BlockEvents.RANDOM_TICK.post(ScriptType.SERVER, state.kgy$getKey(), new RandomTickKubeEvent(level, pos, state, random)).interruptFalse();
		}

		return false;
	}

	@Override
	default Object replaceThisWith(RecipeScriptContext cx, Object with) {
		return with instanceof BlockState state ? state : with instanceof Block block ? block.defaultBlockState() : cx.cx().gyToJava(with, BlockWrapper.STATE_TYPE_INFO);
	}

	default RelativeURL kgy$getWebIconURL(int size) {
		return new RelativeURL("/img/" + size + "/block/" + ID.url(kgy$getIdLocation()));
	}

	default String kgy$toString() {
		var state = (BlockState) this;
		var sb = new StringBuilder();
		sb.append(state.getBlock().kgy$getId());
		boolean first = true;

		for (var prop : state.getProperties()) {
			var value = state.getValue(prop);

			if (!value.equals(state.getBlock().defaultBlockState().getValue(prop))) {
				if (first) {
					sb.append('[');
					first = false;
				} else {
					sb.append(',');
				}

				sb.append(prop.getName());
				sb.append('=');
				sb.append(prop.getName(Cast.to(value)));
			}
		}

		if (!first) {
			sb.append(']');
		}

		return sb.toString();
	}
}
