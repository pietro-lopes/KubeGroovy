package dev.uncandango.kubegroovy.server;

import dev.uncandango.kubegroovy.util.ScheduledEvents;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jspecify.annotations.Nullable;

public class ScheduledServerEvent extends ScheduledEvents.ScheduledEvent {
	public static final ScheduledEvents EVENTS = new ScheduledEvents(ScheduledServerEvent::new);

	@Nullable
	public MinecraftServer getServer() {
		return ServerLifecycleHooks.getCurrentServer();
	}
}