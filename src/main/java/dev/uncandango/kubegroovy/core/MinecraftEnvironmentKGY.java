package dev.uncandango.kubegroovy.core;

import dev.uncandango.kubegroovy.util.ScheduledEvents;
import dev.uncandango.kubegroovy.util.TickDuration;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;

import java.time.temporal.TemporalAmount;

@RemapPrefixForGY("kgy$")
public interface MinecraftEnvironmentKGY extends MessageSenderKGY {
	default ScheduledEvents kgy$getScheduledEvents() {
		throw new NoMixinException();
	}

	default ScheduledEvents.ScheduledEvent kgy$schedule(TemporalAmount timer, ScheduledEvents.Callback callback) {
		return kgy$getScheduledEvents().schedule(timer, false, callback);
	}

	default ScheduledEvents.ScheduledEvent kgy$scheduleInTicks(TickDuration ticks, ScheduledEvents.Callback callback) {
		return kgy$getScheduledEvents().schedule(ticks, false, callback);
	}

	default ScheduledEvents.ScheduledEvent kgy$scheduleRepeating(TemporalAmount timer, ScheduledEvents.Callback callback) {
		return kgy$getScheduledEvents().schedule(timer, true, callback);
	}

	default ScheduledEvents.ScheduledEvent kgy$scheduleRepeatingInTicks(TickDuration ticks, ScheduledEvents.Callback callback) {
		return kgy$getScheduledEvents().schedule(ticks, true, callback);
	}
}
