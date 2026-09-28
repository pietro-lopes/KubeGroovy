package dev.uncandango.kubegroovy.testmod.web;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;

/// Client-only snapshot for `GET /api/test/client`. Isolated in its own class so the
/// dedicated server never loads client classes: [TestControlWeb] only touches this class
/// after checking `FMLEnvironment.getDist().isDedicatedServer()`.
public class TestClientStatus {
	public static JsonObject snapshot() {
		var json = new JsonObject();
		try {
			var mc = Minecraft.getInstance();
			if (mc == null) {
				json.addProperty("hasClient", false);
				return json;
			}
			json.addProperty("hasClient", true);
			json.addProperty("hasLevel", mc.level != null);
			json.addProperty("hasPlayer", mc.player != null);
			if (mc.level != null) {
				try {
					json.addProperty("level", mc.level.dimension().identifier().toString());
				} catch (Throwable ignored) {
				}
			}
			if (mc.player != null) {
				try {
					json.addProperty("player", mc.player.getName().getString());
				} catch (Throwable ignored) {
				}
			}
			try {
				json.addProperty("screen", mc.screen == null ? null : mc.screen.getClass().getSimpleName());
			} catch (Throwable ignored) {
			}
		} catch (Throwable ex) {
			json.addProperty("hasClient", false);
		}
		return json;
	}

	/// Stops the Minecraft client. Client-only, see class doc.
	public static void stop() {
		try {
			var mc = Minecraft.getInstance();
			if (mc != null) {
				mc.stop();
			}
		} catch (Throwable ignored) {
		}
	}
}
