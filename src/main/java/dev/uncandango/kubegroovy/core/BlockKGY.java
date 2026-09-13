package dev.uncandango.kubegroovy.core;

import dev.uncandango.kubegroovy.block.BlockBuilder;
import dev.uncandango.kubegroovy.plugin.builtin.wrapper.BlockWrapper;
import dev.uncandango.kubegroovy.recipe.RecipeScriptContext;
import dev.uncandango.kubegroovy.recipe.match.Replaceable;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

@RemapPrefixForGY("kgy$")
public interface BlockKGY extends BlockBuilderProvider, BlockBehaviourKGY, Replaceable {
	@Override
	default Block kgy$getBlock() {
		return (Block) this;
	}

	default void kgy$setBlockBuilder(BlockBuilder b) {
		throw new NoMixinException();
	}

	default void kgy$setDestroySpeed(float v) {
		for (var state : kgy$getBlockStates()) {
			state.kgy$setDestroySpeed(v);
		}
	}

	default void kgy$setLightEmission(int v) {
		for (var state : kgy$getBlockStates()) {
			state.kgy$setLightEmission(v);
		}
	}

	default void kgy$setRequiresTool(boolean v) {
		for (var state : kgy$getBlockStates()) {
			state.kgy$setRequiresTool(v);
		}
	}

	default List<BlockState> kgy$getBlockStates() {
		return kgy$getBlock().getStateDefinition().getPossibleStates();
	}

	@Override
	default Object replaceThisWith(RecipeScriptContext cx, Object with) {
		return with instanceof Block block ? block : with instanceof BlockState state ? state.getBlock() : cx.cx().gyToJava(with, BlockWrapper.TYPE_INFO);
	}
}
