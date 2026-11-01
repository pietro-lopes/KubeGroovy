package dev.uncandango.kubegroovy.gametest;

import com.google.gson.JsonObject;
import dev.uncandango.kubegroovy.error.KubeRuntimeException;
import dev.uncandango.kubegroovy.script.SourceLine;
import dev.uncandango.kubegroovy.script.annotations.ReturnsSelf;
import dev.uncandango.kubegroovy.typings.Info;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.gametest.framework.TestData;
import org.jspecify.annotations.Nullable;

/// Mutable `TestData` description shared by the `test_instance` builders. Defaults match vanilla.
@ReturnsSelf
public class TestDataSpec {
	public static final Identifier DEFAULT_ENVIRONMENT = Identifier.withDefaultNamespace("default");

	@Nullable Identifier structure;
	int maxTicks;
	int setupTicks;
	boolean required;
	Rotation rotation;
	boolean manualOnly;
	int maxAttempts;
	int requiredSuccesses;
	boolean skyAccess;
	int padding;
	Identifier environment;

	public TestDataSpec() {
		this.structure = null;
		this.maxTicks = 0;
		this.setupTicks = 0;
		this.required = true;
		this.rotation = Rotation.NONE;
		this.manualOnly = false;
		this.maxAttempts = 1;
		this.requiredSuccesses = 1;
		this.skyAccess = false;
		this.padding = 0;
		this.environment = DEFAULT_ENVIRONMENT;
	}

	@Info("Sets the structure template the test is placed in, e.g. `minecraft:empty`. Required.")
	public TestDataSpec structure(Identifier id) {
		structure = id;
		return this;
	}

	@Info("Sets how many ticks the test may run before timing out. Required, must be positive.")
	public TestDataSpec maxTicks(int ticks) {
		maxTicks = ticks;
		return this;
	}

	@Info("Sets how many ticks to wait after placing the structure before starting the test. Default 0.")
	public TestDataSpec setupTicks(int ticks) {
		setupTicks = ticks;
		return this;
	}

	@Info("Sets whether a failure of this test fails the whole batch. Default true.")
	public TestDataSpec required(boolean value) {
		required = value;
		return this;
	}

	@Info("Sets the structure rotation, e.g. `clockwise_90`. Default `none`.")
	public TestDataSpec rotation(Rotation value) {
		rotation = value;
		return this;
	}

	@Info("Sets whether the test only runs when selected explicitly. Default false.")
	public TestDataSpec manualOnly(boolean value) {
		manualOnly = value;
		return this;
	}

	@Info("Sets how many times the test may be attempted. Default 1.")
	public TestDataSpec maxAttempts(int value) {
		maxAttempts = value;
		return this;
	}

	@Info("Sets how many attempts must succeed. Default 1.")
	public TestDataSpec requiredSuccesses(int value) {
		requiredSuccesses = value;
		return this;
	}

	@Info("Sets whether the test area is open to the sky. Default false.")
	public TestDataSpec skyAccess(boolean value) {
		skyAccess = value;
		return this;
	}

	@Info("Sets the padding around the test structure, 0..128. Default 0.")
	public TestDataSpec padding(int value) {
		padding = value;
		return this;
	}

	@Info("Sets the `test_environment` the test runs in. Default `minecraft:default`.")
	public TestDataSpec environment(Identifier id) {
		environment = id;
		return this;
	}

	public void validate(Identifier id, SourceLine sourceLine) {
		String error = null;

		if (structure == null) {
			error = "structure is required";
		} else if (maxTicks <= 0) {
			error = "maxTicks must be positive, got " + maxTicks;
		} else if (setupTicks < 0) {
			error = "setupTicks must be non-negative, got " + setupTicks;
		} else if (maxAttempts <= 0) {
			error = "maxAttempts must be positive, got " + maxAttempts;
		} else if (requiredSuccesses <= 0) {
			error = "requiredSuccesses must be positive, got " + requiredSuccesses;
		} else if (padding < 0 || padding > 128) {
			error = "padding must be within 0..128, got " + padding;
		}

		if (error != null) {
			throw new KubeRuntimeException("Invalid test data for '" + id + "': " + error).source(sourceLine);
		}
	}

	@SuppressWarnings("DataFlowIssue")
	public TestData<Holder<TestEnvironmentDefinition<?>>> build(Holder<TestEnvironmentDefinition<?>> environmentHolder) {
		return new TestData<>(environmentHolder, structure, maxTicks, setupTicks, required, rotation, manualOnly, maxAttempts, requiredSuccesses, skyAccess, padding);
	}

	/// Writes the `TestData` fields in the vanilla `test_instance` JSON layout.
	@SuppressWarnings("DataFlowIssue")
	public void writeJson(JsonObject json) {
		json.addProperty("environment", environment.toString());
		json.addProperty("structure", structure.toString());
		json.addProperty("max_ticks", maxTicks);
		json.addProperty("setup_ticks", setupTicks);
		json.addProperty("required", required);
		json.addProperty("rotation", rotation.getSerializedName());
		json.addProperty("manual_only", manualOnly);
		json.addProperty("max_attempts", maxAttempts);
		json.addProperty("required_successes", requiredSuccesses);
		json.addProperty("sky_access", skyAccess);
		json.addProperty("padding", padding);
	}
}
