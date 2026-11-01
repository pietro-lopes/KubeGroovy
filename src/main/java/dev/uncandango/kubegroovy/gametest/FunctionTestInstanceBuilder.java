package dev.uncandango.kubegroovy.gametest;

import dev.uncandango.kubegroovy.error.KubeRuntimeException;
import dev.uncandango.kubegroovy.script.annotations.ReturnsSelf;
import dev.uncandango.kubegroovy.typings.Info;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

/// `minecraft:function` test instance: runs a `test_function` inside a structure.
@ReturnsSelf
public class FunctionTestInstanceBuilder extends TestInstanceBuilderBase<FunctionGameTestInstance> {
	private @Nullable Identifier function;

	public FunctionTestInstanceBuilder(Identifier id) {
		super(id);
		this.function = null;
	}

	@Info("Sets the `test_function` this instance runs. Defaults to the instance id.")
	public FunctionTestInstanceBuilder function(Identifier id) {
		function = id;
		return this;
	}

	@Override
	public FunctionGameTestInstance createObject() {
		var functionId = function == null ? id : function;

		if (!BuiltInRegistries.TEST_FUNCTION.containsKey(functionId)) {
			throw new KubeRuntimeException("Unknown test function '" + functionId + "' for test instance '" + id + "'!").source(sourceLine);
		}

		var spec = validatedData();
		return new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION, functionId), spec.build(resolveEnvironment()));
	}
}
