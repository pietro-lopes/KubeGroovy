package dev.uncandango.kubegroovy.util;

import dev.uncandango.kubegroovy.error.KubeRuntimeException;
import dev.uncandango.kubegroovy.script.SourceLine;
import dev.uncandango.kubegroovy.script.bridge.Context;
import dev.uncandango.kubegroovy.script.type.TypeInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * An unmodifiable Map backing `global` in server and startup scripts.
 * It delegates its operations to the underlying unmodifiable map.
 * Its exceptions will contain a message explaining what went wrong.
 */
public class GlobalUnmodifiableMap<K, V> implements Map<K, V> { // CustomJavaToGyWrapper
	private static final String MESSAGE = "'global' cannot be assigned to in client or server scripts";
	private final Map<K, V> unmodifiableMap;

	public GlobalUnmodifiableMap(Map<K, V> map) {
		this.unmodifiableMap = Collections.unmodifiableMap(map);
	}

	@Override
	public int size() {
		return unmodifiableMap.size();
	}

	@Override
	public boolean isEmpty() {
		return unmodifiableMap.isEmpty();
	}

	@Override
	public boolean containsKey(Object key) {
		return unmodifiableMap.containsKey(key);
	}

	@Override
	public boolean containsValue(Object value) {
		return unmodifiableMap.containsValue(value);
	}

	@Override
	public V get(Object key) {
		return unmodifiableMap.get(key);
	}

	@Override
	public @Nullable V put(K key, V value) {
		var cx = Context.getCurrent();
		throw new KubeRuntimeException(MESSAGE).source(SourceLine.of());
	}

	@Override
	public V remove(Object key) {
		var cx = Context.getCurrent();
		throw new KubeRuntimeException(MESSAGE).source(SourceLine.of());
	}

	@Override
	public void putAll(@NotNull Map<? extends K, ? extends V> m) {
		var cx = Context.getCurrent();
		throw new KubeRuntimeException(MESSAGE).source(SourceLine.of());
	}

	@Override
	public void clear() {
		var cx = Context.getCurrent();
		throw new KubeRuntimeException(MESSAGE).source(SourceLine.of());
	}

	@Override
	public @NotNull Set<K> keySet() {
		return unmodifiableMap.keySet();
	}

	@Override
	public @NotNull Collection<V> values() {
		return unmodifiableMap.values();
	}

	@Override
	public @NotNull Set<Entry<K, V>> entrySet() {
		return unmodifiableMap.entrySet();
	}

	@Override
	public boolean equals(Object obj) {
		return super.equals(obj);
	}

	@Override
	public int hashCode() {
		return unmodifiableMap.hashCode();
	}

	public String toString() {
		return unmodifiableMap.toString();
	}
//
//	@Override
//	public Scriptable convertJavaToGy(Context cx, Scriptable scope, TypeInfo staticType) {
//		return new NativeJavaReadonlyMap(cx, scope, this, this, staticType, MESSAGE);
//	}
}
