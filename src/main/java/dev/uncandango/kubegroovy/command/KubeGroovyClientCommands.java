package dev.uncandango.kubegroovy.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.uncandango.kubegroovy.KubeGroovyPaths;
import dev.uncandango.kubegroovy.client.KubeGroovyClient;
import dev.uncandango.kubegroovy.script.data.GeneratedData;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.util.Util;

import java.util.concurrent.CompletableFuture;

public class KubeGroovyClientCommands {
	private static LiteralArgumentBuilder<CommandSourceStack> clientCommands(String name) {
		return Commands.literal(name)
			.then(Commands.literal("reload")
				.then(Commands.literal("client-scripts")
					.requires(source -> true)
					.executes(context -> reloadClient(context.getSource()))
				)
				.then(Commands.literal("textures")
					.requires(source -> true)
					.executes(context -> reloadTextures(context.getSource()))
				)
				.then(Commands.literal("lang")
					.requires(source -> true)
					.executes(context -> reloadLang(context.getSource()))
				)
			)
			.then(Commands.literal("browse")
				.executes(source -> {
					Util.getPlatform().openPath(KubeGroovyPaths.DIRECTORY);
					return 1;
				}));
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(clientCommands("kubegroovy"));
		dispatcher.register(clientCommands("kgy"));
	}

	private static int reloadClient(CommandSourceStack source) {
		KubeGroovyClient.reloadClientScripts();
		source.sendSystemMessage(Component.literal("Done! To reload textures, models and other assets, press F3 + T"));
		return 1;
	}

	private static int reloadTextures(CommandSourceStack source) {
		reloadResources(Minecraft.getInstance().getTextureManager());
		return 1;
	}

	private static int reloadLang(CommandSourceStack source) {
		KubeGroovyClient.reloadClientScripts();
		reloadResources(Minecraft.getInstance().getLanguageManager());
		return 1;
	}

	private static void reloadResources(PreparableReloadListener listener) {
		var mc = Minecraft.getInstance();
		mc.getResourceManager().getResource(GeneratedData.INTERNAL_RELOAD.id());

		var shared = new PreparableReloadListener.SharedState(mc.getResourceManager());
		listener.prepareSharedState(shared);

		PreparableReloadListener.PreparationBarrier barrier = CompletableFuture::completedFuture;

		listener.reload(shared, Util.backgroundExecutor(), barrier, mc)
			.thenAccept(unused -> mc.player.sendSystemMessage(Component.literal("Done! You still may have to reload all assets with F3 + T")));
	}
}
