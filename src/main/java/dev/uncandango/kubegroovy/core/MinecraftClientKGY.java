package dev.uncandango.kubegroovy.core;

import com.mojang.blaze3d.platform.InputConstants;
import dev.uncandango.kubegroovy.client.ClientProperties;
import dev.uncandango.kubegroovy.client.KubeGroovyKeybinds;
import dev.uncandango.kubegroovy.item.ItemClickedKubeEvent;
import dev.uncandango.kubegroovy.net.FirstClickPayload;
import dev.uncandango.kubegroovy.plugin.builtin.event.ItemEvents;
import dev.uncandango.kubegroovy.plugin.builtin.wrapper.GLFWInputWrapper;
import dev.uncandango.kubegroovy.script.ConsoleGY;
import dev.uncandango.kubegroovy.script.ScriptType;
import dev.uncandango.kubegroovy.typings.Info;
import dev.uncandango.kubegroovy.typings.Param;
import dev.uncandango.kubegroovy.script.annotations.HideFromGY;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.function.Function;

@SuppressWarnings("resource")
@RemapPrefixForGY("kgy$")
public interface MinecraftClientKGY extends MinecraftEnvironmentKGY {
	default Minecraft kgy$self() {
		return (Minecraft) this;
	}

	@Override
	default Component kgy$getName() {
		return Component.literal(kgy$self().name());
	}

	@Override
	default void kgy$tell(Component message) {
		kgy$self().player.kgy$tell(message);
	}

	@Override
	default void kgy$setStatusMessage(Component message) {
		kgy$self().player.kgy$setStatusMessage(message);
	}

	@Override
	@Info(value = "Runs the specified console command client-side with the player's permission level.", params = {
		@Param(name = "command", value = "The console command. Slash at the beginning is optional."),
	})
	default void kgy$runCommand(String command) {
		kgy$self().player.kgy$runCommand(command);
	}

	@Override
	@Info(value = "Runs the specified console command client-side with the player's permission level. The command won't output any logs in chat nor console.", params = {
		@Param(name = "command", value = "The console command. Slash at the beginning is optional."),
	})
	default void kgy$runCommandSilent(String command) {
		kgy$self().player.kgy$runCommandSilent(command);
	}

	@Override
	default void kgy$setActivePostShader(@Nullable Identifier id) {
		kgy$self().player.kgy$setActivePostShader(id);
	}

	@Nullable
	default Screen kgy$getCurrentScreen() {
		return kgy$self().screen;
	}

	default void kgy$setCurrentScreen(Screen gui) {
		kgy$self().setScreen(gui);
	}

	default void kgy$setTitle(String t) {
		ClientProperties.get().windowTitle = t.trim();
		kgy$self().updateTitle();
	}

	default String kgy$getTitle() {
		throw new NoMixinException();
	}

	default String kgy$getCurrentWorldName() {
		var server = kgy$self().getCurrentServer();
		return server == null ? "Singleplayer" : server.name;
	}

	default boolean kgy$isKeyDown(int key) {
		return key != -1 && InputConstants.isKeyDown(kgy$self().getWindow(), key);
	}

	default boolean kgy$isKeyDown(String keyName) {
		return kgy$isKeyDown(GLFWInputWrapper.get(keyName));
	}

	default boolean kgy$isKeyBindDown(String id) {
		var bind = KubeGroovyKeybinds.get(id);
		return bind != null && bind.down;
	}

	default int kgy$getKeyBindPressedTicks(String id) {
		var bind = KubeGroovyKeybinds.get(id);
		return bind == null || !bind.down ? -1 : bind.ticksPressed;
	}

	default boolean kgy$isKeyMappingDown(KeyMapping key) {
		if (key != null && !key.isUnbound() && key.isConflictContextAndModifierActive()) {
			if (key.getKey().getType() == InputConstants.Type.KEYSYM) {
				return kgy$isKeyDown(key.getKey().getValue());
			} else if (key.getKey().getType() == InputConstants.Type.MOUSE && key.getKey().getValue() >= 0) {
				return GLFW.glfwGetMouseButton(kgy$self().getWindow().handle(), key.getKey().getValue()) == GLFW.GLFW_TRUE;
			}
		}

		return false;
	}

	@HideFromGY
	default void kgy$startAttack0() {
		if (ItemEvents.FIRST_LEFT_CLICKED.hasListeners()) {
			var player = kgy$self().player;
			var stack = player.getItemInHand(InteractionHand.MAIN_HAND);
			var key = stack.getItem().kgy$getKey();

			if (ItemEvents.FIRST_LEFT_CLICKED.hasListeners(key)) {
				ItemEvents.FIRST_LEFT_CLICKED.post(ScriptType.CLIENT, key, new ItemClickedKubeEvent(player, InteractionHand.MAIN_HAND, stack));
			}
		}

		ClientPacketDistributor.sendToServer(new FirstClickPayload(0));
	}

	@HideFromGY
	default void kgy$startUseItem0() {
		if (ItemEvents.FIRST_RIGHT_CLICKED.hasListeners()) {
			var player = kgy$self().player;

			for (var hand : InteractionHand.values()) {
				var stack = player.getItemInHand(hand);
				var key = stack.getItem().kgy$getKey();

				if (ItemEvents.FIRST_RIGHT_CLICKED.hasListeners(key)) {
					ItemEvents.FIRST_RIGHT_CLICKED.post(ScriptType.CLIENT, key, new ItemClickedKubeEvent(player, hand, stack));
				}
			}
		}

		ClientPacketDistributor.sendToServer(new FirstClickPayload(1));
	}

	@HideFromGY
	default void kgy$afterResourcesLoaded(boolean reload) {
		if (reload) {
			ConsoleGY.CLIENT.stopCapturingErrors();
		}

		ConsoleGY.CLIENT.info("Client resource reload complete!");
	}

	default Function<Identifier, TextureAtlasSprite> kgy$getBlockTextureAtlas() {
		return (identifier) -> kgy$self().getAtlasManager().getAtlasOrThrow(TextureAtlas.LOCATION_BLOCKS).getSprite(identifier);
	}

	default Function<Identifier, TextureAtlasSprite> kgy$getParticleTextureAtlas() {
		return (identifier) -> kgy$self().getAtlasManager().getAtlasOrThrow(TextureAtlas.LOCATION_PARTICLES).getSprite(identifier);
	}
}
