package dev.uncandango.kubegroovy.script.bridge;

import java.util.List;

public class GYObjectTypeInfo extends TypeInfo {
	public final List<GYOptionalParam> fields;

	public GYObjectTypeInfo(List<GYOptionalParam> fields) {
		super(java.util.Map.class);
		this.fields = List.copyOf(fields);
	}
}
