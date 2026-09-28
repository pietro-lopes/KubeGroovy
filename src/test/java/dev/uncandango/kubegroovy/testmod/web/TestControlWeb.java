package dev.uncandango.kubegroovy.testmod.web;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.latvian.apps.tinyserver.http.response.HTTPResponse;
import dev.latvian.apps.tinyserver.http.response.HTTPStatus;
import dev.uncandango.kubegroovy.KubeGroovy;
import dev.uncandango.kubegroovy.testmod.KubeGroovyGameTests;
import dev.uncandango.kubegroovy.testmod.TestRuntime;
import dev.uncandango.kubegroovy.web.JsonContent;
import dev.uncandango.kubegroovy.web.KGYHTTPRequest;
import dev.uncandango.kubegroovy.web.LocalWebServerAPIRegistry;
import dev.uncandango.kubegroovy.web.LocalWebServerRegistry;
import net.minecraft.world.level.GameType;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/// Test-only HTTP control plane for driving client (and server) game tests from the
/// local web server. Read endpoints are public, mutating endpoints require auth – see
/// [dev.uncandango.kubegroovy.testmod.TestRuntimePlugin].
///
/// Flow: launch `runGametestClient` (auto-joins `Test World` via quick-play), then
/// - `POST /api/test/command` with body `test run testmod:*` to start the suite on the
///   integrated server,
/// - poll `GET /api/test/markers` until the markers you care about appear,
/// - `POST /api/test/clear` to reset between runs.
///
/// Only loaded from `src/test`, so production jars never expose these endpoints.
public class TestControlWeb {
	public static void registerAPIs(LocalWebServerAPIRegistry registry) {
		registry.register(KubeGroovy.id("test_control"), 1);
	}

	public static void register(LocalWebServerRegistry registry) {
		registry.get("/api/test/status", TestControlWeb::getStatus);
		registry.get("/api/test/markers", TestControlWeb::getMarkers);
		registry.get("/api/test/markers/{id}", TestControlWeb::getMarker);
		registry.get("/api/test/client", TestControlWeb::getClient);
		registry.get("/api/test/results", TestControlWeb::getResults);
	}

	public static void registerWithAuth(LocalWebServerRegistry registry) {
		registry.post("/api/test/command", TestControlWeb::postCommand);
		registry.post("/api/test/clear", TestControlWeb::postClear);
		registry.post("/api/test/pass/{id}", TestControlWeb::postPass);
		registry.post("/api/test/gamemode", TestControlWeb::postGamemode);
		registry.post("/api/test/stop", TestControlWeb::postStop);
	}

	private static HTTPResponse getStatus(KGYHTTPRequest req) {
		try {
			return HTTPResponse.ok().content(JsonContent.any(() -> req.supplyInMainThread(TestControlWeb::snapshotStatus)));
		} catch (Throwable ex) {
			return HTTPResponse.ok().content(JsonContent.any(TestControlWeb::snapshotStatus));
		}
	}

	static JsonObject snapshotStatus() {
		var json = new JsonObject();
		json.addProperty("dist", FMLEnvironment.getDist().isClient() ? "client" : "dedicated");
		var server = ServerLifecycleHooks.getCurrentServer();
		json.addProperty("hasServer", server != null);
		if (server != null) {
			try {
				json.addProperty("players", server.getPlayerList().getPlayerCount());
			} catch (Throwable ignored) {
			}
			try {
				int levels = 0;
				for (var ignored : server.getAllLevels()) {
					levels++;
				}
				json.addProperty("levels", levels);
			} catch (Throwable ignored) {
			}
			try {
				json.addProperty("tick", server.getTickCount());
			} catch (Throwable ignored) {
			}
		}
		return json;
	}

	private static HTTPResponse getMarkers(KGYHTTPRequest req) {
		return HTTPResponse.ok().content(JsonContent.object(json -> {
			var markers = new JsonObject();
			for (var entry : TestRuntime.snapshotCounts().entrySet()) {
				markers.addProperty(entry.getKey(), entry.getValue());
			}
			json.add("markers", markers);
			var failures = new JsonObject();
			for (var entry : TestRuntime.snapshotFailures().entrySet()) {
				failures.addProperty(entry.getKey(), entry.getValue());
			}
			json.add("failures", failures);
			var startup = new com.google.gson.JsonArray();
			for (var id : TestRuntime.snapshotStartup()) {
				startup.add(id);
			}
			json.add("startup", startup);
		}));
	}

	private static HTTPResponse getMarker(KGYHTTPRequest req) {
		var id = req.variable("id").asString("");
		if (id.isEmpty()) {
			return HTTPStatus.BAD_REQUEST;
		}
		return HTTPResponse.ok().content(JsonContent.object(json -> {
			json.addProperty("id", id);
			json.addProperty("count", TestRuntime.count(id));
			json.addProperty("passed", TestRuntime.passed(id));
			var failure = TestRuntime.snapshotFailures().get(id);
			if (failure != null) {
				json.addProperty("failure", failure);
			}
		}));
	}

	private static HTTPResponse getClient(KGYHTTPRequest req) {
		if (FMLEnvironment.getDist().isDedicatedServer()) {
			return HTTPResponse.ok().content(JsonContent.object(json -> json.addProperty("hasClient", false)));
		}
		try {
			return HTTPResponse.ok().content(JsonContent.any(() -> req.supplyInMainThread(TestClientStatus::snapshot)));
		} catch (Throwable ex) {
			return HTTPResponse.ok().content(JsonContent.object(json -> json.addProperty("hasClient", false)));
		}
	}

	/// Framework verdict for the last (or running) `test run` invocation: per-holder
	/// result plus passed/failed/pending counts. `{present: false}` when the testmod
	/// framework isn't loaded, so callers can tell "no suite" apart from "suite green".
	private static HTTPResponse getResults(KGYHTTPRequest req) {
		return HTTPResponse.ok().content(JsonContent.any(TestControlWeb::snapshotResults));
	}

	static JsonObject snapshotResults() {
		var json = new JsonObject();
		var framework = KubeGroovyGameTests.framework();
		if (framework == null) {
			json.addProperty("present", false);
			return json;
		}
		json.addProperty("present", true);
		json.addProperty("framework", framework.id().toString());
		var tests = new com.google.gson.JsonArray();
		var failed = new com.google.gson.JsonArray();
		int passed = 0;
		int failedCount = 0;
		int pending = 0;
		try {
			for (var test : framework.tests().all()) {
				try {
					var id = test.id();
					var status = framework.tests().getStatus(id);
					var result = status == null || status.result() == null
						? "NOT_PROCESSED"
						: status.result().name();
					var entry = new JsonObject();
					entry.addProperty("id", id);
					entry.addProperty("result", result);
					try {
						var groups = new com.google.gson.JsonArray();
						for (var group : test.groups()) {
							groups.add(group);
						}
						entry.add("groups", groups);
					} catch (Throwable ignored) {
					}
					String message = "";
					try {
						message = status == null ? "" : status.message();
					} catch (Throwable ignored) {
					}
					if (message != null && !message.isEmpty()) {
						entry.addProperty("message", message);
					}
					tests.add(entry);
					if ("PASSED".equals(result)) {
						passed++;
					} else if ("FAILED".equals(result)) {
						failedCount++;
						failed.add(entry);
					} else {
						pending++;
					}
				} catch (Throwable ignored) {
				}
			}
		} catch (Throwable ignored) {
		}
		var summary = new JsonObject();
		summary.addProperty("total", passed + failedCount + pending);
		summary.addProperty("passed", passed);
		summary.addProperty("failed", failedCount);
		summary.addProperty("pending", pending);
		json.add("summary", summary);
		json.add("failed", failed);
		json.add("tests", tests);
		return json;
	}

	private static HTTPResponse postCommand(KGYHTTPRequest req) {
		var command = stripCommand(readBody(req));
		if (command.isEmpty()) {
			return HTTPStatus.BAD_REQUEST;
		}
		int result;
		try {
			result = req.supplyInMainThread(() -> runCommandBlocking(command));
		} catch (Throwable ex) {
			return HTTPResponse.ok().content(JsonContent.object(json -> {
				json.addProperty("ok", false);
				json.addProperty("error", String.valueOf(ex.getMessage()));
			}));
		}
		if (result < 0) {
			return HTTPStatus.SERVICE_UNAVAILABLE;
		}
		int code = result;
		return HTTPResponse.ok().content(JsonContent.object(json -> {
			json.addProperty("ok", true);
			json.addProperty("command", command);
			json.addProperty("result", code);
		}));
	}

	/// Runs {@code command} as the server console. Returns 0 on success,
	/// or -1 when no server (title screen / not joined yet) is available.
	static int runCommandBlocking(String command) {
		var server = ServerLifecycleHooks.getCurrentServer();
		if (server == null) {
			return -1;
		}
		if (server.isSameThread()) {
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
			return 0;
		}
		var future = new CompletableFuture<Integer>();
		server.execute(() -> {
			try {
				server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
				future.complete(0);
			} catch (Throwable ex) {
				future.completeExceptionally(ex);
			}
		});
		return future.join();
	}

	private static HTTPResponse postClear(KGYHTTPRequest req) {
		var ids = parseIds(readBody(req));
		var query = req.query("id").asString("");
		if (!query.isEmpty()) {
			ids.add(query);
		}
		if (ids.isEmpty()) {
			return HTTPStatus.BAD_REQUEST;
		}
		TestRuntime.clear(ids.toArray(new String[0]));
		return HTTPResponse.ok().content(JsonContent.object(json -> {
			json.addProperty("ok", true);
			var cleared = new com.google.gson.JsonArray();
			for (var id : ids) {
				cleared.add(id);
			}
			json.add("cleared", cleared);
		}));
	}

	private static HTTPResponse postPass(KGYHTTPRequest req) {
		var id = req.variable("id").asString("");
		if (id.isEmpty()) {
			return HTTPStatus.BAD_REQUEST;
		}
		TestRuntime.pass(id);
		return HTTPResponse.ok().content(JsonContent.object(json -> {
			json.addProperty("ok", true);
			json.addProperty("id", id);
			json.addProperty("count", TestRuntime.count(id));
		}));
	}

	/// Sets every (or the targeted) player's game mode. Body is the mode name
	/// (`survival`, `creative`, `adventure`, `spectator`, also accepted as `0`-`3`);
	/// `?target=` picks one player by name, otherwise all online players change.
	/// Useful before a suite run so the real player can't die into global script
	/// handlers (e.g. `spectator` for a clean `test run testmod:*`).
	private static HTTPResponse postGamemode(KGYHTTPRequest req) {
		var mode = parseGameMode(readBody(req));
		if (mode == null) {
			return HTTPStatus.BAD_REQUEST;
		}
		var target = req.query("target").asString("");
		List<String> updated;
		try {
			updated = req.supplyInMainThread(() -> applyGameMode(mode, target));
		} catch (Throwable ex) {
			return HTTPResponse.ok().content(JsonContent.object(json -> {
				json.addProperty("ok", false);
				json.addProperty("error", String.valueOf(ex.getMessage()));
			}));
		}
		if (updated == null) {
			return HTTPStatus.SERVICE_UNAVAILABLE;
		}
		if (updated.isEmpty()) {
			return HTTPStatus.NOT_FOUND;
		}
		return HTTPResponse.ok().content(JsonContent.object(json -> {
			json.addProperty("ok", true);
			json.addProperty("mode", mode.name());
			var names = new com.google.gson.JsonArray();
			for (var name : updated) {
				names.add(name);
			}
			json.add("updated", names);
		}));
	}

	/// Null when no server is available (title screen / not joined yet).
	static List<String> applyGameMode(GameType mode, String target) {
		var server = ServerLifecycleHooks.getCurrentServer();
		if (server == null) {
			return null;
		}
		String want = target == null ? "" : target;
		var updated = new ArrayList<String>();
		Runnable apply = () -> {
			for (var player : server.getPlayerList().getPlayers()) {
				String name;
				try {
					name = player.getName().getString();
				} catch (Throwable ignored) {
					continue;
				}
				if (!want.isEmpty() && !name.equalsIgnoreCase(want)) {
					continue;
				}
				try {
					player.setGameMode(mode);
					updated.add(name);
				} catch (Throwable ignored) {
				}
			}
		};
		if (server.isSameThread()) {
			apply.run();
		} else {
			var done = new CompletableFuture<Void>();
			server.execute(() -> {
				try {
					apply.run();
					done.complete(null);
				} catch (Throwable ex) {
					done.completeExceptionally(ex);
				}
			});
			done.join();
		}
		return updated;
	}

	/// Parses a game mode full name (`survival`, `creative`, `adventure`, `spectator`,
	/// case-insensitive) or vanilla id (`0`-`3`). Null when unrecognized.
	static GameType parseGameMode(String raw) {
		if (raw == null) {
			return null;
		}
		switch (raw.trim().toLowerCase(java.util.Locale.ROOT)) {
			case "survival", "0" -> {
				return GameType.SURVIVAL;
			}
			case "creative", "1" -> {
				return GameType.CREATIVE;
			}
			case "adventure", "2" -> {
				return GameType.ADVENTURE;
			}
			case "spectator", "3" -> {
				return GameType.SPECTATOR;
			}
			default -> {
				return null;
			}
		}
	}

	/// Schedules a graceful shutdown shortly after responding, so the `{ok: true}`
	/// reply always flushes: stops the Minecraft client when present, otherwise halts
	/// the dedicated server. Lets an external harness close `runGametestClient` when done.
	private static HTTPResponse postStop(KGYHTTPRequest req) {
		boolean client = FMLEnvironment.getDist().isClient();
		var stopper = new Thread(() -> {
			try {
				Thread.sleep(500L);
			} catch (InterruptedException ignored) {
			}
			try {
				if (client) {
					req.runInMainThread(TestClientStatus::stop);
				} else {
					var server = ServerLifecycleHooks.getCurrentServer();
					if (server != null) {
						server.halt(false);
					} else {
						System.exit(0);
					}
				}
			} catch (Throwable ignored) {
			}
		}, "kgy-test-stop");
		stopper.setDaemon(true);
		stopper.start();
		return HTTPResponse.ok().content(JsonContent.object(json -> {
			json.addProperty("ok", true);
			json.addProperty("stopping", client ? "client" : "server");
		}));
	}

	static String readBody(KGYHTTPRequest req) {
		try {
			var body = req.mainBody();
			if (body == null) {
				return "";
			}
			var text = body.text();
			return text == null ? "" : text;
		} catch (Exception ex) {
			return "";
		}
	}

	/// Normalizes a posted command: trims, drops one leading slash (`/test ...` -> `test ...`).
	static String stripCommand(String raw) {
		if (raw == null) {
			return "";
		}
		var command = raw.trim();
		if (command.startsWith("/")) {
			command = command.substring(1).trim();
		}
		return command;
	}

	/// Parses posted marker ids. Accepts a JSON array (`["a.b", "c.d"]`) or a
	/// comma/whitespace/semicolon-separated list. Never returns null.
	static List<String> parseIds(String body) {
		var ids = new ArrayList<String>();
		if (body == null || body.isBlank()) {
			return ids;
		}
		var text = body.trim();
		if (text.startsWith("[")) {
			try {
				for (var element : JsonParser.parseString(text).getAsJsonArray()) {
					var id = element.getAsString().trim();
					if (!id.isEmpty()) {
						ids.add(id);
					}
				}
				return ids;
			} catch (RuntimeException ignored) {
			}
		}
		for (var part : text.split("[,\\s;]+")) {
			var id = part.trim();
			if (!id.isEmpty()) {
				ids.add(id);
			}
		}
		return ids;
	}
}
