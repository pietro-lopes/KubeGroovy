package dev.uncandango.kubegroovy.plugin.builtin.wrapper;

import dev.uncandango.kubegroovy.KubeGroovy;
import dev.uncandango.kubegroovy.script.KubeGroovyContext;
import dev.uncandango.kubegroovy.script.bridge.Context;
import dev.uncandango.kubegroovy.script.annotations.HideFromGY;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.event.IModBusEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.Collection;
import java.util.LinkedList;
import java.util.function.Consumer;

public interface NativeEventWrapper {
	@HideFromGY
	record Listeners(Collection<Consumer<Event>> listeners) implements Consumer<Event> {
		public record Key(Class<?> eventClass, EventPriority priority) {
			@Override
			public boolean equals(Object o) {
				return o == this || o instanceof Key(Class<?> aClass, EventPriority priority1) && aClass == eventClass && priority1 == priority;
			}
		}

		@Override
		public void accept(Event event) {
			for (var listener : listeners) {
				listener.accept(event);
			}
		}
	}

	static void onEvent(Class<?> eventClass, Consumer<Event> consumer) {
		var cx = Context.getCurrent();
		onEvent(EventPriority.NORMAL, eventClass, consumer);
	}

	static void onEvent(EventPriority priority, Class<?> eventClass, Consumer<Event> consumer) {
		var cx = Context.getCurrent();
		if (!Event.class.isAssignableFrom(eventClass)) {
			throw new IllegalArgumentException("Event class must extend net.neoforged.bus.api.Event!");
		}

		var scriptType = ((KubeGroovyContext) cx).kgyFactory.manager.scriptType;
		var key = new Listeners.Key(eventClass, priority == null ? EventPriority.NORMAL : priority);

		var listeners = scriptType.nativeEventListeners.get(key);

		if (listeners == null) {
			listeners = new Listeners(new LinkedList<>());
			scriptType.nativeEventListeners.put(key, listeners);
			var bus = IModBusEvent.class.isAssignableFrom(eventClass) ? KubeGroovy.modEventBus : NeoForge.EVENT_BUS;
			bus.addListener(priority, false, (Class) eventClass, listeners);
		}

		listeners.listeners.add(consumer);
	}
}
