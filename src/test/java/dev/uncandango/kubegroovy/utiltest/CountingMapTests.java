package dev.uncandango.kubegroovy.utiltest;

import dev.uncandango.kubegroovy.util.CountingMap;
import dev.uncandango.kubegroovy.util.Object2LongEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CountingMapTests {
	@Test
	void setGetAndRemove() {
		var map = new CountingMap();
		assertEquals(0L, map.get("missing"));
		assertEquals(0, map.getSize());

		map.set("a", 5L);
		assertEquals(5L, map.get("a"));
		assertEquals(1, map.getSize());

		map.set("a", 0L);
		assertEquals(0L, map.get("a"));
		assertEquals(0, map.getSize());

		map.set("b", 3L);
		map.set("b", -1L);
		assertEquals(0L, map.get("b"));
		assertEquals(0, map.getSize());
	}

	@Test
	void addAccumulatesAndClears() {
		var map = new CountingMap();
		map.add("x", 2L);
		map.add("x", 3L);
		assertEquals(5L, map.get("x"));

		map.add("y", 7L);
		assertEquals(12L, map.getTotalCount());

		map.clear();
		assertEquals(0, map.getSize());
		assertEquals(0L, map.getTotalCount());
	}

	@Test
	void entriesKeysAndValues() {
		var map = new CountingMap();
		map.set("a", 1L);
		map.set("b", 2L);

		var entries = map.getEntries();
		assertEquals(2, entries.size());
		assertTrue(entries.stream().anyMatch(e -> "a".equals(e.key) && e.value == 1L));
		assertTrue(entries.stream().anyMatch(e -> "b".equals(e.key) && e.value == 2L));

		assertTrue(map.getKeys().containsAll(List.of("a", "b")));
		assertTrue(map.getValues().containsAll(List.of(1L, 2L)));

		var seen = new java.util.ArrayList<String>();
		map.forEach(entry -> seen.add(String.valueOf(entry.key)));
		assertTrue(seen.containsAll(List.of("a", "b")));
	}

	@Test
	void entryCompareOrdersByValueDesc() {
		var high = new Object2LongEntry("a", 10L);
		var low = new Object2LongEntry("b", 1L);
		assertTrue(high.compareTo(low) < 0);
		assertTrue(low.compareTo(high) > 0);
		assertEquals(0, high.compareTo(new Object2LongEntry("a", 10L)));
	}

	@Test
	void entryCompareTieBreaksCaseInsensitive() {
		var a = new Object2LongEntry("apple", 5L);
		var b = new Object2LongEntry("Banana", 5L);
		assertTrue(a.compareTo(b) < 0);
		assertTrue(b.compareTo(a) > 0);
	}
}
