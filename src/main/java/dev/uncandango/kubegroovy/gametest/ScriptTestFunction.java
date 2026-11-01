package dev.uncandango.kubegroovy.gametest;

import dev.uncandango.kubegroovy.error.KubeRuntimeException;
import dev.uncandango.kubegroovy.script.ConsoleGY;
import dev.uncandango.kubegroovy.script.SourceLine;
import net.minecraft.gametest.framework.GameTestException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.function.Consumer;

/// Script-defined `test_function` body. Game-test assertion failures pass through untouched;
/// any other script error is logged with its source line and reported as a test failure.
public record ScriptTestFunction(Identifier id, SourceLine sourceLine, Consumer<GameTestHelper> body) implements Consumer<GameTestHelper> {
	@Override
	public void accept(GameTestHelper helper) {
		try {
			body.accept(helper);
		} catch (GameTestException ex) {
			throw ex;
		} catch (Throwable ex) {
			var message = "Error in test function '" + id + "'";
			ConsoleGY.SERVER.error(message, new KubeRuntimeException(message, ex).source(sourceLine));
			throw helper.assertionException(Component.literal(message + ": " + ex));
		}
	}

	@Override
	public String toString() {
		return "ScriptTestFunction[" + id + "]";
	}
}
