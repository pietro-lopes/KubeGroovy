package dev.uncandango.kubegroovy.utiltest;

import dev.uncandango.kubegroovy.util.ListGY;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ListGYTests {
	@Test
	void ofListReturnsSameInstance() {
		var list = List.of("a", "b");
		assertSame(list, ListGY.of(list));
	}

	@Test
	void ofIterableCopiesElements() {
		Iterable<String> iterable = () -> List.of("x", "y").iterator();
		assertEquals(List.of("x", "y"), ListGY.of(iterable));
	}

	@Test
	void ofArrayHandlesPrimitiveAndObjectArrays() {
		assertEquals(List.of("a", "b"), ListGY.of(new String[]{"a", "b"}));
		assertEquals(List.of(1, 2, 3), ListGY.of(new int[]{1, 2, 3}));
		assertEquals(List.of((byte) 1, (byte) 2), ListGY.of(new byte[]{1, 2}));
		assertEquals(List.of((short) 1), ListGY.of(new short[]{1}));
		assertEquals(List.of(4L), ListGY.of(new long[]{4L}));
		assertEquals(List.of(1.5f), ListGY.of(new float[]{1.5f}));
		assertEquals(List.of(2.5d), ListGY.of(new double[]{2.5d}));
		assertEquals(List.of('z'), ListGY.of(new char[]{'z'}));
		assertEquals(List.of(true, false), ListGY.of(new boolean[]{true, false}));
	}

	@Test
	void ofNullAndUnknownReturnsNull() {
		assertNull(ListGY.of((Object) null));
		assertNull(ListGY.of(42));
		assertNull(ListGY.ofArray(null));
		assertNull(ListGY.ofArray("not an array"));
	}

	@Test
	void orSelfWrapsSingleValues() {
		assertEquals(List.of("a"), ListGY.orSelf("a"));
		assertEquals(List.of(1, 2), ListGY.orSelf(new int[]{1, 2}));
		assertTrue(ListGY.orSelf(null).isEmpty());

		var list = new ArrayList<>(List.of("k"));
		assertSame(list, ListGY.orSelf(list));
	}

	@Test
	void ofSetHandlesSetsCollectionsAndArrays() {
		var set = Set.of("a");
		assertSame(set, ListGY.ofSet(set));
		assertEquals(new LinkedHashSet<>(List.of("a", "b")), ListGY.ofSet(List.of("a", "b", "a")));
		assertEquals(Set.of("x"), ListGY.ofSet(new String[]{"x"}));
		assertNull(ListGY.ofSet(null));
		assertNull(ListGY.ofSet(42));
	}
}
