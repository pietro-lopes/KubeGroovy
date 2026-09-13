package dev.uncandango.kubegroovy.script.bridge;

import java.util.ArrayList;
import java.util.List;

public class EnumTypeInfo extends TypeInfo {
	public EnumTypeInfo(Class<?> raw) {
		super(raw);
	}

	public static String getName(Object entry) {
		if (entry instanceof Enum<?> e) {
			return e.name();
		}
		return String.valueOf(entry);
	}

	public List<Object> enumConstants() {
		var raw = asClass();
		var out = new ArrayList<>();
		if (raw != null && raw.isEnum()) {
			for (var c : raw.getEnumConstants()) {
				out.add(c);
			}
		}
		return out;
	}
}
