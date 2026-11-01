package dev.uncandango.kubegroovy.gametest;

import dev.uncandango.kubegroovy.script.annotations.ReturnsSelf;
import net.minecraft.gametest.framework.BlockBasedTestInstance;
import net.minecraft.resources.Identifier;

/// `minecraft:block_based` test instance: driven by test blocks inside the structure.
@ReturnsSelf
public class BlockBasedTestInstanceBuilder extends TestInstanceBuilderBase<BlockBasedTestInstance> {
	public BlockBasedTestInstanceBuilder(Identifier id) {
		super(id);
	}

	@Override
	public BlockBasedTestInstance createObject() {
		return new BlockBasedTestInstance(validatedData().build(resolveEnvironment()));
	}
}
