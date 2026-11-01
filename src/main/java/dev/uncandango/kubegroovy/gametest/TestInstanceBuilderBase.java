package dev.uncandango.kubegroovy.gametest;

import dev.uncandango.kubegroovy.error.KubeRuntimeException;
import dev.uncandango.kubegroovy.registry.BuilderBase;
import dev.uncandango.kubegroovy.script.annotations.ReturnsSelf;
import dev.uncandango.kubegroovy.typings.Info;
import dev.uncandango.kubegroovy.util.RegistryAccessContainer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Rotation;

/// Shared `TestData` setters for the `minecraft:test_instance` datapack registry builders.
@ReturnsSelf
public abstract class TestInstanceBuilderBase<T extends GameTestInstance> extends BuilderBase<T> {
	private final TestDataSpec data;

	public TestInstanceBuilderBase(Identifier id) {
		super(id);
		this.data = new TestDataSpec();
	}

	@Info("Sets the structure template the test is placed in, e.g. `minecraft:empty`. Required.")
	public TestInstanceBuilderBase<T> structure(Identifier id) {
		data.structure(id);
		return this;
	}

	@Info("Sets how many ticks the test may run before timing out. Required, must be positive.")
	public TestInstanceBuilderBase<T> maxTicks(int ticks) {
		data.maxTicks(ticks);
		return this;
	}

	@Info("Sets how many ticks to wait after placing the structure before starting the test. Default 0.")
	public TestInstanceBuilderBase<T> setupTicks(int ticks) {
		data.setupTicks(ticks);
		return this;
	}

	@Info("Sets whether a failure of this test fails the whole batch. Default true.")
	public TestInstanceBuilderBase<T> required(boolean value) {
		data.required(value);
		return this;
	}

	@Info("Sets the structure rotation, e.g. `clockwise_90`. Default `none`.")
	public TestInstanceBuilderBase<T> rotation(Rotation value) {
		data.rotation(value);
		return this;
	}

	@Info("Sets whether the test only runs when selected explicitly. Default false.")
	public TestInstanceBuilderBase<T> manualOnly(boolean value) {
		data.manualOnly(value);
		return this;
	}

	@Info("Sets how many times the test may be attempted. Default 1.")
	public TestInstanceBuilderBase<T> maxAttempts(int value) {
		data.maxAttempts(value);
		return this;
	}

	@Info("Sets how many attempts must succeed. Default 1.")
	public TestInstanceBuilderBase<T> requiredSuccesses(int value) {
		data.requiredSuccesses(value);
		return this;
	}

	@Info("Sets whether the test area is open to the sky. Default false.")
	public TestInstanceBuilderBase<T> skyAccess(boolean value) {
		data.skyAccess(value);
		return this;
	}

	@Info("Sets the padding around the test structure, 0..128. Default 0.")
	public TestInstanceBuilderBase<T> padding(int value) {
		data.padding(value);
		return this;
	}

	@Info("Sets the `test_environment` the test runs in. Default `minecraft:default`.")
	public TestInstanceBuilderBase<T> environment(Identifier id) {
		data.environment(id);
		return this;
	}

	/// Validates the spec, throwing with the builder source line on invalid values.
	protected final TestDataSpec validatedData() {
		data.validate(id, sourceLine);
		return data;
	}

	/// The environment registry is usually not loaded yet when server registry builders encode, so
	/// this returns an unbound reference owned by the registry the current `RegistryOps` encode with.
	protected final Holder<TestEnvironmentDefinition<?>> resolveEnvironment() {
		var key = ResourceKey.create(Registries.TEST_ENVIRONMENT, data.environment);
		var owner = RegistryAccessContainer.current.json().owner(Registries.TEST_ENVIRONMENT)
			.orElseThrow(() -> new KubeRuntimeException("Registry '" + Registries.TEST_ENVIRONMENT.identifier() + "' isn't available to resolve environment '" + data.environment + "'!").source(sourceLine));
		return Holder.Reference.createStandAlone(owner, key);
	}
}
