package dev.uncandango.kubegroovy.script.type;

import dev.uncandango.kubegroovy.script.bridge.Context;
import dev.uncandango.kubegroovy.script.bridge.TypeWrapperFactory;
import net.minecraft.util.StringRepresentable;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class EnumTypeInfo extends ClassTypeInfo implements TypeWrapperFactory<Object> {
	static final Map<Class<?>, EnumTypeInfo> CACHE = new IdentityHashMap<>();

	public static String getName(Object e) {
		if (e instanceof StringRepresentable c) {
			String s = c.getSerializedName();

			if (!s.isEmpty()) {
				return s;
			}
		}

		return ((Enum) e).name();
	}

	private List<Object> constants;
	private Map<String, Object> constantMap;

	EnumTypeInfo(Class<?> type) {
		super(type);
	}

	@Override
	public List<Object> enumConstants() {
		if (constants == null) {
			constants = List.of(asClass().getEnumConstants());
		}

		return constants;
	}

	@Override
	public Object wrap(Object from, TypeInfo target) {
		var cx = Context.getCurrent();
		if (from instanceof CharSequence) {
			String s = from.toString();

			if (s.isEmpty()) {
				return null;
			}

			var constants = enumConstants();

			if (constantMap == null) {
				constantMap = new HashMap<>(constants.size());

				for (var entry : constants) {
					var name = getName(entry);
					constantMap.put(name.toLowerCase(Locale.ROOT), entry);
					constantMap.put(name, entry);
				}
			}

			var lookup = constantMap.get(s);

			if (lookup != null) {
				return lookup;
			}

			for (var entry : constants) {
				if (getName(entry).equalsIgnoreCase(s)) {
					return entry;
				}
			}

			throw new IllegalArgumentException("'" + s + "' is not a valid enum constant! Valid values are: " + constants.stream().map(EnumTypeInfo::getName).map(s1 -> "'" + s1 + "'").collect(Collectors.joining(", ")));
		} else if (from instanceof Number) {
			int index = ((Number) from).intValue();

			var constants = enumConstants();

			if (index < 0 || index >= constants.size()) {
				throw new IllegalArgumentException(index + " is not a valid enum index! Valid values are: 0 - " + (constants.size() - 1));
			}

			return constants.get(index);
		}

		return from;
	}
}
