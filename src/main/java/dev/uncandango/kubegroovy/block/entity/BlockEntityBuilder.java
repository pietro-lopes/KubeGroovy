package dev.uncandango.kubegroovy.block.entity;

import dev.uncandango.kubegroovy.registry.BuilderBase;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class BlockEntityBuilder extends BuilderBase<BlockEntityType<?>> {
	protected BlockEntityInfo info;

	public BlockEntityBuilder(Identifier i, BlockEntityInfo info) {
		super(i);
		this.info = info;
	}

	public final BlockEntityInfo kgy$getInfo() {
		return info;
	}

	@Override
	public BlockEntityType<?> createObject() {
		var block = info.blockBuilder.get();
		if (block == null) {
			block = info.blockBuilder.createTransformedObject();
		}
		info.entityType = new BlockEntityType<>(info::createBlockEntity, block);
		return info.entityType;
	}
}
