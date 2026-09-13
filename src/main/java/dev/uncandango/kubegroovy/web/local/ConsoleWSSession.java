package dev.uncandango.kubegroovy.web.local;

import com.google.gson.JsonElement;
import dev.uncandango.kubegroovy.script.ConsoleGY;
import dev.uncandango.kubegroovy.web.KGYHTTPRequest;
import dev.uncandango.kubegroovy.web.KGYWSSession;

public class ConsoleWSSession extends KGYWSSession {
	public final ConsoleGY console;

	public ConsoleWSSession(KGYHTTPRequest request, ConsoleGY console) {
		super(request);
		this.console = console;
	}

	@Override
	public void onEvent(String type, JsonElement payload) {
		switch (type) {
			case "info" -> console.info(payload.getAsString());
			case "warn" -> console.warn(payload.getAsString());
			case "error" -> console.error(payload.getAsString());
		}
	}
}
