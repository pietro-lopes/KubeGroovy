package dev.uncandango.kubegroovy.utiltest;

import com.google.gson.JsonPrimitive;
import com.mojang.brigadier.StringReader;
import dev.uncandango.kubegroovy.util.ID;
import dev.uncandango.kubegroovy.util.RegExpKGY;
import dev.uncandango.kubegroovy.util.Tristate;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TristateRegExpIdTests {
	@Test
	void tristateWrap() {
		assertEquals(Tristate.DEFAULT, Tristate.wrap(null));
		assertEquals(Tristate.TRUE, Tristate.wrap(Tristate.TRUE));
		assertEquals(Tristate.FALSE, Tristate.wrap(false));
		assertEquals(Tristate.TRUE, Tristate.wrap(true));
		assertEquals(Tristate.TRUE, Tristate.wrap("TRUE"));
		assertEquals(Tristate.FALSE, Tristate.wrap("false"));
		assertEquals(Tristate.DEFAULT, Tristate.wrap("maybe"));
		assertEquals(Tristate.DEFAULT, Tristate.wrap(42));
	}

	@Test
	void tristateTest() {
		assertTrue(Tristate.DEFAULT.test(true));
		assertTrue(Tristate.DEFAULT.test(false));
		assertTrue(Tristate.TRUE.test(true));
		assertFalse(Tristate.TRUE.test(false));
		assertTrue(Tristate.FALSE.test(false));
		assertFalse(Tristate.FALSE.test(true));

		assertTrue(Tristate.TRUE.test(() -> true));
		assertFalse(Tristate.FALSE.test(() -> true));
		assertTrue(Tristate.DEFAULT.test(() -> false));

		assertEquals("true", Tristate.TRUE.getSerializedName());
		assertEquals("false", Tristate.FALSE.getSerializedName());
		assertEquals("default", Tristate.DEFAULT.getSerializedName());
	}

	@Test
	void regExpFlags() {
		assertEquals(Pattern.CASE_INSENSITIVE | Pattern.MULTILINE, RegExpKGY.getFlags("im"));
		assertEquals(0, RegExpKGY.getFlags(""));
		assertEquals(0, RegExpKGY.getFlags("z"));
		assertTrue(RegExpKGY.isValidFlag('i'));
		assertFalse(RegExpKGY.isValidFlag('z'));
	}

	@Test
	void regExpOfStringAndRoundTrip() {
		var pattern = RegExpKGY.ofString("/hello/i");
		assertNotNull(pattern);
		assertEquals("hello", pattern.pattern());
		assertTrue((pattern.flags() & Pattern.CASE_INSENSITIVE) != 0);

		assertNull(RegExpKGY.ofString("hi"));
		assertNull(RegExpKGY.ofString("/"));
		assertNull(RegExpKGY.wrap(null));
		assertNull(RegExpKGY.wrap(42));
		assertSamePattern(pattern, RegExpKGY.wrap(pattern));
		assertNotNull(RegExpKGY.wrap("/abc/"));

		assertEquals("/hello/i", RegExpKGY.toRegExpString(pattern));
		assertEquals("/plain/", RegExpKGY.toRegExpString(Pattern.compile("plain")));
	}

	private static void assertSamePattern(Pattern expected, Pattern actual) {
		assertEquals(expected.pattern(), actual.pattern());
		assertEquals(expected.flags(), actual.flags());
	}

	@Test
	void regExpReadEscapedSlash() {
		var pattern = RegExpKGY.read(new StringReader("/a\\/b/"));
		assertEquals("a/b", pattern.pattern());

		assertThrows(IllegalArgumentException.class, () -> RegExpKGY.read(new StringReader("abc")));
		assertTrue(RegExpKGY.tryRead(new StringReader("/ok/")).result().isPresent());
		assertTrue(RegExpKGY.tryRead(new StringReader("nope")).error().isPresent());
	}

	@Test
	void idStringHelpers() {
		assertEquals("", ID.string(null));
		assertEquals("", ID.string(""));
		assertEquals("minecraft:stone", ID.string("stone"));
		assertEquals("mod:thing", ID.string("mod:thing"));

		assertEquals("", ID.kgyString(null));
		assertEquals("", ID.kgyString(""));
		assertEquals("kubegroovy:thing", ID.kgyString("thing"));
		assertEquals("other:thing", ID.kgyString("other:thing"));

		assertEquals("minecraft", ID.namespace(null));
		assertEquals("minecraft", ID.namespace(""));
		assertEquals("minecraft", ID.namespace("stone"));
		assertEquals("mod", ID.namespace("mod:thing"));

		assertEquals("air", ID.path(null));
		assertEquals("air", ID.path(""));
		assertEquals("stone", ID.path("stone"));
		assertEquals("thing", ID.path("mod:thing"));
	}

	@Test
	void idKeyChecksAndReductions() {
		assertTrue(ID.isKey("stone"));
		assertTrue(ID.isKey(Identifier.parse("minecraft:stone")));
		assertFalse(ID.isKey(42));
		assertFalse(ID.isKey(null));

		assertTrue(ID.isValidKey("stone"));
		assertTrue(ID.isValidKey(Identifier.parse("minecraft:stone")));
		assertFalse(ID.isValidKey("::bad::"));
		assertFalse(ID.isValidKey(42));

		assertEquals("stone", ID.reduce(Identifier.parse("minecraft:stone")));
		assertEquals("mod:thing", ID.reduce(Identifier.parse("mod:thing")));
		assertEquals("thing", ID.reduceKjs(Identifier.parse("kubegroovy:thing")));
		assertEquals("stone", ID.resourcePath(Identifier.parse("minecraft:stone")));
		assertEquals("mod/thing", ID.resourcePath(Identifier.parse("mod:thing")));
	}

	@Test
	void idOf() {
		assertNull(ID.of(null, false));
		assertNull(ID.mc(null));
		assertNull(ID.kgy(null));

		var parsed = Identifier.parse("minecraft:stone");
		assertEquals(parsed, ID.of(parsed, false));
		assertEquals(parsed, ID.mc(parsed));
		assertEquals(Identifier.parse("minecraft:dirt"), ID.mc("minecraft:dirt"));
		assertEquals(Identifier.parse("kubegroovy:thing"), ID.kgy("thing"));
		assertEquals(Identifier.parse("minecraft:stone"), ID.mc(new JsonPrimitive("minecraft:stone")));
	}
}
