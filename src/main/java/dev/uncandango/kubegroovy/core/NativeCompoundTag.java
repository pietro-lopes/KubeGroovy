package dev.uncandango.kubegroovy.core;

import dev.uncandango.kubegroovy.plugin.builtin.wrapper.NBTWrapper;
import dev.uncandango.kubegroovy.script.bridge.Context;
import dev.uncandango.kubegroovy.script.bridge.NativeJavaMap;
import dev.uncandango.kubegroovy.script.bridge.Scriptable;
import dev.uncandango.kubegroovy.script.bridge.TypeInfo;
import net.minecraft.nbt.Tag;

import java.util.Map;

/// Custom NativeJavaMap subclass for CompoundTag that properly unwraps
/// Tag values to GY primitives on read, restoring 1.20.1 behavior where
/// reading entity.persistentData.SomeTag returns a GY number/string
/// instead of a wrapped Java IntTag/StringTag object
public class NativeCompoundTag extends NativeJavaMap {
	public NativeCompoundTag(Context cx, Scriptable scope, Object jo, Map<String, Tag> map, TypeInfo type) {
		super(cx, scope, jo, map, type);
	}

	@Override
	public Object get(Context cx, String name, Scriptable start) {
		if (map.containsKey(name)) {
			Object raw = map.get(name);
			Object unwrapped = raw instanceof Tag tag ? NBTWrapper.fromTag(tag) : raw;
			if (unwrapped == null) {
				return null;
			}
			return cx.javaToGY(unwrapped, start, TypeInfo.NONE);
		}
		return super.get(cx, name, start);
	}

	@Override
	public Object get(Context cx, int index, Scriptable start) {
		if (map.containsKey(index)) {
			Object raw = map.get(index);
			Object unwrapped = raw instanceof Tag tag ? NBTWrapper.fromTag(tag) : raw;
			if (unwrapped == null) {
				return null;
			}
			return cx.javaToGY(unwrapped, start, TypeInfo.NONE);
		}
		return super.get(cx, index, start);
	}
}
