package dev.uncandango.kubegroovy.util;

import com.google.gson.JsonElement;
import dev.uncandango.kubegroovy.script.bridge.Context;

public interface JsonSerializable {
	JsonElement toJson(Context cx);
}