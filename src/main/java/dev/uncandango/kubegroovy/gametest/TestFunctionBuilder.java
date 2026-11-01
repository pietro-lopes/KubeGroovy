package dev.uncandango.kubegroovy.gametest;

import com.google.gson.JsonObject;
import dev.uncandango.kubegroovy.error.KubeRuntimeException;
import dev.uncandango.kubegroovy.generator.KubeDataGenerator;
import dev.uncandango.kubegroovy.registry.BuilderBase;
import dev.uncandango.kubegroovy.script.annotations.ReturnsSelf;
import dev.uncandango.kubegroovy.typings.Info;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/// Builder for the static `minecraft:test_function` registry. The body is fixed for the game
/// session because the registry is frozen after startup.
@ReturnsSelf
public class TestFunctionBuilder extends BuilderBase<Consumer<GameTestHelper>> {
	private @Nullable Consumer<GameTestHelper> body;
	private @Nullable TestDataSpec instance;

	public TestFunctionBuilder(Identifier id) {
		super(id);
		this.body = null;
		this.instance = null;
	}

	@Info("""
		Sets the test body. Throw through `GameTestHelper` assertions to fail the test and call
		`helper.succeed()` (or one of the `succeedWhen...` helpers) to pass it.
		""")
	public TestFunctionBuilder test(Consumer<GameTestHelper> callback) {
		body = callback;
		return this;
	}

	@Info("""
		Also generates a `minecraft:function` `test_instance` with the same id that runs this
		function. `structure` and `maxTicks` are required.
		""")
	public TestFunctionBuilder instance(Consumer<TestDataSpec> callback) {
		var spec = new TestDataSpec();
		callback.accept(spec);
		instance = spec;
		return this;
	}

	@Override
	public Consumer<GameTestHelper> createObject() {
		if (body == null) {
			throw new KubeRuntimeException("Test function '" + id + "' has no test body, call test { helper -> ... }").source(sourceLine);
		}

		return new ScriptTestFunction(id, sourceLine, body);
	}

	@Override
	public void generateData(KubeDataGenerator generator) {
		if (instance == null) {
			return;
		}

		instance.validate(id, sourceLine);
		var json = new JsonObject();
		json.addProperty("type", "minecraft:function");
		json.addProperty("function", id.toString());
		instance.writeJson(json);
		generator.json(Identifier.fromNamespaceAndPath(id.getNamespace(), "test_instance/" + id.getPath()), json);
	}
}
