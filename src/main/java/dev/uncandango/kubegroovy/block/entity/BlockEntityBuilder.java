package dev.uncandango.kubegroovy.block.entity;

import dev.uncandango.kubegroovy.registry.BuilderBase;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class BlockEntityBuilder extends BuilderBase<BlockEntityType<?>> {
	public BlockEntityInfo info;

	public BlockEntityBuilder(Identifier i, BlockEntityInfo info) {
		super(i);
		this.info = info;
	}

	@Override
	public BlockEntityType<?> createObject() {
		info.entityType = new BlockEntityType<>(info::createBlockEntity, info.blockBuilder.get());
		return info.entityType;
	}
}
