package dev.uncandango.kubegroovy.plugin.builtin.wrapper;

import dev.uncandango.kubegroovy.KubeGroovy;
import dev.uncandango.kubegroovy.script.KubeGroovyContext;
import dev.uncandango.kubegroovy.script.bridge.Context;
import dev.uncandango.kubegroovy.script.annotations.HideFromGY;
import dev.uncandango.kubegroovy.util.Cast;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.event.IModBusEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.Collection;
import java.util.LinkedList;
import java.util.function.Consumer;

public interface NativeEventWrapper {
	@HideFromGY
	record Listeners<T extends Event>(Collection<Consumer<T>> listeners) implements Consumer<T> {
		public record Key<T extends Event>(Class<T> eventClass, EventPriority priority) {
			@Override
			public boolean equals(Object o) {
				return o == this || o instanceof Key(Class<?> aClass, EventPriority priority1) && aClass == eventClass && priority1 == priority;
			}
		}

		@Override
		public void accept(T event) {
			for (var listener : listeners) {
				listener.accept(event);
			}
		}
	}

	static <T extends Event> void onEvent(Class<T> eventClass, Consumer<T> consumer) {
		var cx = Context.getCurrent();
		onEvent(EventPriority.NORMAL, eventClass, consumer);
	}

	static <T extends Event> void onEvent(EventPriority priority, Class<T> eventClass, Consumer<T> consumer) {
		var cx = Context.getCurrent();
		if (!Event.class.isAssignableFrom(eventClass)) {
			throw new IllegalArgumentException("Event class must extend net.neoforged.bus.api.Event!");
		}

		var scriptType = ((KubeGroovyContext) cx).kgyFactory.manager.scriptType;
		Listeners.Key<T> key = new Listeners.Key<>(eventClass, priority == null ? EventPriority.NORMAL : priority);

		Listeners<T> listeners = Cast.to(scriptType.nativeEventListeners.get(key));

		if (listeners == null) {
			listeners = new Listeners<>(new LinkedList<>());
			scriptType.nativeEventListeners.put(key, listeners);
			var bus = IModBusEvent.class.isAssignableFrom(eventClass) ? KubeGroovy.modEventBus : NeoForge.EVENT_BUS;
			bus.addListener(priority, false, eventClass, listeners);
		}

		listeners.listeners.add(consumer);
	}
}
