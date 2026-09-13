package dev.uncandango.kubegroovy.server.tag;

import dev.uncandango.kubegroovy.DevProperties;
import dev.uncandango.kubegroovy.script.ConsoleGY;
import net.minecraft.resources.Identifier;

public record TagWrapper(TagKubeEvent event, Identifier id) {
	TagEventBuilder builder() {
		return event.getOrCreateBuilder(id);
	}

	@Override
	public String toString() {
		return "<%s / #%s>".formatted(event.getType(), id);
	}

	public TagWrapper add(Object... filters) {
		var filter = TagEventFilter.unwrap(event, filters);

		if (filter.add(this)) {
			if (ConsoleGY.SERVER.shouldPrintDebug()) {
				ConsoleGY.SERVER.debug("+ %s // %s".formatted(this, filter));
			}
		} else if (DevProperties.get().logSkippedTags) {
			ConsoleGY.SERVER.warn("+ %s // %s [No matches found!]".formatted(this, filter));
		}

		return this;
	}

	public TagWrapper remove(Object... filters) {
		var filter = TagEventFilter.unwrap(event, filters);

		if (filter.remove(this)) {
			if (ConsoleGY.SERVER.shouldPrintDebug()) {
				ConsoleGY.SERVER.debug("- %s // %s".formatted(this, filter));
			}
		} else if (DevProperties.get().logSkippedTags) {
			ConsoleGY.SERVER.warn("- %s // %s [No matches found!]".formatted(this, filter));
		}

		return this;
	}

	public TagWrapper removeAll() {
		ConsoleGY.SERVER.info("TagWrapper.removeAll has been renamed to replace to better represent what the method does!");
		return replace();
	}

	public TagWrapper replace() {
		if (ConsoleGY.SERVER.shouldPrintDebug()) {
			ConsoleGY.SERVER.debug("- %s // (all)".formatted(this));
		}

		if (!builder().isEmpty()) {
			builder().replace();
		} else if (DevProperties.get().logSkippedTags) {
			ConsoleGY.SERVER.warn("- %s // (all) [No matches found!]".formatted(this));
		}

		return this;
	}
}
