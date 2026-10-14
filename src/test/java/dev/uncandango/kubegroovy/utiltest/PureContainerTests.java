package dev.uncandango.kubegroovy.utiltest;

import dev.uncandango.kubegroovy.error.KubeRuntimeException;
import dev.uncandango.kubegroovy.util.ErrorStack;
import dev.uncandango.kubegroovy.util.GlobalUnmodifiableMap;
import dev.uncandango.kubegroovy.util.Lazy;
import dev.uncandango.kubegroovy.util.TinyMap;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PureContainerTests {
	@Test
	void tinyMapRoundTrips() {
		var tiny = TinyMap.ofMap(Map.of("a", 1, "b", 2));
		assertFalse(tiny.isEmpty());
		assertEquals(Map.of("a", 1, "b", 2), tiny.toMap());
		assertEquals(1, new TinyMap<>(tiny).toMap().get("a"));

		var fromCollection = new TinyMap<>(List.of(new TinyMap.Entry<>("k", "v")));
		assertEquals("v", fromCollection.toMap().get("k"));

		assertTrue(new TinyMap<String, String>(new TinyMap.Entry[0]).isEmpty());
		assertTrue(TinyMap.ofMap(Map.of()).isEmpty());
	}

	@Test
	void tinyMapCopyClonesBackingArray() {
		var original = new TinyMap<>(new TinyMap.Entry[]{new TinyMap.Entry<>("a", 1)});
		var copy = new TinyMap<>(original);
		assertNotSame(original.entries(), copy.entries());
		assertEquals(original.toMap(), copy.toMap());
	}

	@Test
	void lazyCachesAndForgets() {
		var calls = new AtomicInteger();
		var lazy = Lazy.of(() -> {
			calls.incrementAndGet();
			return "value";
		});

		assertEquals("value", lazy.get());
		assertEquals("value", lazy.get());
		assertEquals(1, calls.get());

		lazy.forget();
		assertEquals("value", lazy.get());
		assertEquals(2, calls.get());
	}

	@Test
	void lazyMapHelpers() {
		var lazyMap = Lazy.map(map -> map.put("a", 1));
		assertEquals(1, lazyMap.get().get("a"));

		var emptyIdentity = Lazy.identityMap(Map::clear);
		assertTrue(emptyIdentity.get().isEmpty());

		var singletonIdentity = Lazy.identityMap(map -> map.put("k", "v"));
		assertEquals("v", singletonIdentity.get().get("k"));

		var multiIdentity = Lazy.identityMap(map -> {
			map.put("a", 1);
			map.put("b", 2);
		});
		assertEquals(2, multiIdentity.get().size());
	}

	@Test
	void lazyExpiryRefetches() throws InterruptedException {
		var calls = new AtomicInteger();
		var lazy = Lazy.of(() -> {
			calls.incrementAndGet();
			return "v";
		}, Duration.ofMillis(5L));

		assertEquals("v", lazy.get());
		Thread.sleep(30L);
		assertEquals("v", lazy.get());
		assertEquals(2, calls.get());
	}

	@Test
	void errorStackPushPopAndFormat() {
		var stack = new ErrorStack();
		assertEquals("", stack.toString());
		assertEquals("", stack.atString());
		assertEquals("", stack.stringAt());

		stack.push("parent");
		stack.setKey("key");
		assertEquals("", stack.toString());

		stack.push("child");
		stack.setKey(3);
		assertEquals("[key][3]", stack.toString());
		assertEquals(" @ [key][3]", stack.atString());
		assertEquals("[key][3] @ ", stack.stringAt());

		stack.pop();
		assertEquals("", stack.toString());

		stack.push("other");
		stack.setKey("x");
		stack.pop();
		stack.pop();
		assertEquals("", stack.toString());
	}

	@Test
	void errorStackNoneIsNoOp() {
		ErrorStack.NONE.push("a");
		ErrorStack.NONE.setKey("b");
		ErrorStack.NONE.setKey(1);
		ErrorStack.NONE.pop();
		assertEquals("", ErrorStack.NONE.toString());
	}

	@Test
	void globalMapDelegatesReadsAndBlocksWrites() {
		var backing = new HashMap<String, Integer>();
		backing.put("a", 1);
		var map = new GlobalUnmodifiableMap<>(backing);

		assertEquals(1, map.size());
		assertFalse(map.isEmpty());
		assertTrue(map.containsKey("a"));
		assertTrue(map.containsValue(1));
		assertEquals(1, map.get("a"));
		assertEquals(backing.keySet(), map.keySet());
		assertEquals(backing.toString(), map.toString());
		assertEquals(backing.hashCode(), map.hashCode());

		assertThrows(KubeRuntimeException.class, () -> map.put("b", 2));
		assertThrows(KubeRuntimeException.class, () -> map.remove("a"));
		assertThrows(KubeRuntimeException.class, () -> map.putAll(Map.of("b", 2)));
		assertThrows(KubeRuntimeException.class, map::clear);
	}

	@Test
	void globalMapEmpty() {
		var map = new GlobalUnmodifiableMap<String, String>(Map.of());
		assertTrue(map.isEmpty());
		assertEquals(0, map.size());
		assertEquals(Map.of(), map.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
	}
}
