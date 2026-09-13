package dev.uncandango.kubegroovy.web;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.DynamicOps;
import dev.latvian.apps.tinyserver.http.HTTPRequest;
import dev.latvian.apps.tinyserver.http.response.HTTPPayload;
import dev.latvian.apps.tinyserver.http.response.HTTPResponse;
import dev.uncandango.kubegroovy.component.DataComponentWrapper;
import dev.uncandango.kubegroovy.util.RegistryAccessContainer;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.thread.BlockableEventLoop;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class KGYHTTPRequest extends HTTPRequest {
	public final BlockableEventLoop<?> eventLoop;

	public KGYHTTPRequest(BlockableEventLoop<?> eventLoop) {
		this.eventLoop = eventLoop;
	}

	public RegistryAccessContainer registries() {
		return RegistryAccessContainer.current;
	}

	public void runInMainThread(Runnable task) {
		eventLoop.executeBlocking(task);
	}

	public <T> T supplyInMainThread(Supplier<T> task) {
		return CompletableFuture.supplyAsync(task, eventLoop).join();
	}

	public Identifier id(String ns, String path) {
		return Identifier.fromNamespaceAndPath(variable(ns).asString(), variable(path).asString());
	}

	public Identifier id() {
		return id("namespace", "path");
	}

	public DataComponentPatch components(DynamicOps<Tag> ops) throws CommandSyntaxException {
		var str = query("components").asString();
		return str.isEmpty() ? DataComponentPatch.EMPTY : DataComponentWrapper.readPatch(ops, new StringReader("[" + str + "]"));
	}

	@Override
	public HTTPResponse handleResponse(HTTPPayload payload, HTTPResponse response, @Nullable Throwable error) {
		return response.cors();
	}
}
