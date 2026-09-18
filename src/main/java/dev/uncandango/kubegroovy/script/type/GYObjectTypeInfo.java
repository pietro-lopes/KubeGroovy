package dev.uncandango.kubegroovy.script.type;

import java.util.Collection;
import java.util.List;

// {a: string, b?: number}
public record GYObjectTypeInfo(List<GYOptionalParam> fields) implements TypeInfo {
	public static GYObjectTypeInfo of(GYOptionalParam field) {
		return new GYObjectTypeInfo(List.of(field));
	}

	public static GYObjectTypeInfo of(GYOptionalParam field1, GYOptionalParam field2) {
		return new GYObjectTypeInfo(List.of(field1, field2));
	}

	public static GYObjectTypeInfo of(GYOptionalParam... fields) {
		return new GYObjectTypeInfo(List.of(fields));
	}

	@Override
	public Class<?> asClass() {
		return TypeInfo.class;
	}

	@Override
	public String toString() {
		return TypeStringContext.DEFAULT.toString(this);
	}

	@Override
	public void append(TypeStringContext ctx, StringBuilder sb) {
		sb.append('{');

		boolean first = true;

		for (var field : fields) {
			if (first) {
				first = false;
			} else {
				sb.append(',');
				ctx.appendSpace(sb);
			}

			field.append(ctx, sb);
		}

		sb.append('}');
	}

	@Override
	public void collectContainedComponentClasses(Collection<Class<?>> classes) {
		for (var field : fields) {
			field.type().collectContainedComponentClasses(classes);
		}
	}
}
