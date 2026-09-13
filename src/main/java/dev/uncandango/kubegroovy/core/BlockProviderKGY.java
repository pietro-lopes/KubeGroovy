package dev.uncandango.kubegroovy.core;

import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;

import java.util.Map;

@RemapPrefixForGY("kgy$")
public interface BlockProviderKGY extends RegistryObjectKGY<Block> {
	Block kgy$getBlock();

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
		return kgy$getBlock().builtInRegistryHolder();
	}

	@Override
	default ResourceKey<Block> kgy$getKey() {
		return kgy$getBlock().builtInRegistryHolder().key();
	}

	@Override
	default String kgy$getId() {
		return kgy$getBlock().kgy$getId();
	}

	default Map<String, Object> kgy$getTypeData() {
		return kgy$getBlock().kgy$getTypeData();
	}
}
