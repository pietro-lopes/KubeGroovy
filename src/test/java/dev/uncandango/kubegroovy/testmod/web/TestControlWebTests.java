package dev.uncandango.kubegroovy.testmod.web;

import dev.uncandango.kubegroovy.testmod.TestRuntime;
import net.minecraft.world.level.GameType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestControlWebTests {
	@Test
	void parseIdsAcceptsDelimitedLists() {
		assertEquals(List.of("server.tick", "server.command"), TestControlWeb.parseIds("server.tick, server.command"));
		assertEquals(List.of("a.b", "c.d"), TestControlWeb.parseIds("a.b\nc.d"));
		assertEquals(List.of("a.b", "c.d"), TestControlWeb.parseIds("a.b; c.d"));
		assertTrue(TestControlWeb.parseIds(null).isEmpty());
		assertTrue(TestControlWeb.parseIds("   ").isEmpty());
	}

	@Test
	void parseIdsAcceptsJsonArray() {
		assertEquals(List.of("a.b", "c.d"), TestControlWeb.parseIds("[\"a.b\", \"c.d\"]"));
		assertEquals(List.of("a.b"), TestControlWeb.parseIds("[\"  a.b  \", \"\"]"));
	}

	@Test
	void stripCommandTrimsAndDropsLeadingSlash() {
		assertEquals("test run testmod:*", TestControlWeb.stripCommand("/test run testmod:*"));
		assertEquals("help", TestControlWeb.stripCommand("  help  "));
		assertEquals("", TestControlWeb.stripCommand(null));
		assertEquals("", TestControlWeb.stripCommand("   "));
	}

	@Test
	void parseGameModeAcceptsNamesAndIds() {
		assertEquals(GameType.SURVIVAL, TestControlWeb.parseGameMode("survival"));
		assertEquals(GameType.CREATIVE, TestControlWeb.parseGameMode("Creative"));
		assertEquals(GameType.ADVENTURE, TestControlWeb.parseGameMode("  ADVENTURE  "));
		assertEquals(GameType.SPECTATOR, TestControlWeb.parseGameMode("spectator"));
		assertEquals(GameType.SURVIVAL, TestControlWeb.parseGameMode("0"));
		assertEquals(GameType.CREATIVE, TestControlWeb.parseGameMode("1"));
		assertEquals(GameType.ADVENTURE, TestControlWeb.parseGameMode("2"));
		assertEquals(GameType.SPECTATOR, TestControlWeb.parseGameMode("3"));
		assertEquals(null, TestControlWeb.parseGameMode(null));
		assertEquals(null, TestControlWeb.parseGameMode(""));
		assertEquals(null, TestControlWeb.parseGameMode("   "));
		assertEquals(null, TestControlWeb.parseGameMode("4"));
		assertEquals(null, TestControlWeb.parseGameMode("godmode"));
	}

	@Test
	void runtimeSnapshotsExposeMarkers() {
		var id = "test.control.snapshot";
		TestRuntime.clear(id);
		TestRuntime.pass(id);
		TestRuntime.pass(id);
		try {
			assertEquals(2, TestRuntime.snapshotCounts().get(id));
			assertEquals(2, TestRuntime.count(id));
			assertTrue(TestRuntime.snapshotFailures().containsKey(id) == false);
		} finally {
			TestRuntime.clear(id);
		}
		assertTrue(TestRuntime.snapshotCounts().containsKey(id) == false);
	}
}
