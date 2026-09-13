package dev.uncandango.kubegroovy.plugin.builtin.event;

import dev.uncandango.kubegroovy.client.KeybindRegistryKubeEvent;
import dev.uncandango.kubegroovy.client.KubeGroovyKeybinds;
import dev.uncandango.kubegroovy.event.EventGroup;
import dev.uncandango.kubegroovy.event.EventHandler;
import dev.uncandango.kubegroovy.event.TargetedEventHandler;

/// Client-side keybind events.
/// Use [#REGISTRY] at startup to register new keys, then [#PRESSED]/[#RELEASED]/[#TICK] to handle them.
public interface KeyBindEvents {
	EventGroup GROUP = EventGroup.of("KeyBindEvents");

	EventHandler REGISTRY = GROUP.startup("registry", () -> KeybindRegistryKubeEvent.class);
	TargetedEventHandler<KubeGroovyKeybinds.KubeKey> PRESSED = GROUP.client("pressed", () -> KubeGroovyKeybinds.KeyEvent.class).requiredTarget(KubeGroovyKeybinds.TARGET);
	TargetedEventHandler<KubeGroovyKeybinds.KubeKey> RELEASED = GROUP.client("released", () -> KubeGroovyKeybinds.TickingKeyEvent.class).requiredTarget(KubeGroovyKeybinds.TARGET);
	TargetedEventHandler<KubeGroovyKeybinds.KubeKey> TICK = GROUP.client("tick", () -> KubeGroovyKeybinds.TickingKeyEvent.class).requiredTarget(KubeGroovyKeybinds.TARGET);
}
