package dev.uncandango.kubegroovy.client;

import com.mojang.serialization.DynamicOps;
import dev.uncandango.kubegroovy.CommonProperties;
import dev.uncandango.kubegroovy.KubeGroovy;
import dev.uncandango.kubegroovy.block.BlockBuilder;
import dev.uncandango.kubegroovy.block.BlockTintFunction;
import dev.uncandango.kubegroovy.client.model.KubeGroovyConditionalCallbackProperty;
import dev.uncandango.kubegroovy.command.KubeGroovyClientCommands;
import dev.uncandango.kubegroovy.fluid.FluidBuilder;
import dev.uncandango.kubegroovy.fluid.FluidTypeBuilder;
import dev.uncandango.kubegroovy.gui.KubeGroovyMenus;
import dev.uncandango.kubegroovy.gui.KubeGroovyScreen;
import dev.uncandango.kubegroovy.item.DynamicItemTooltipsKubeEvent;
import dev.uncandango.kubegroovy.item.ItemModelPropertiesKubeEvent;
import dev.uncandango.kubegroovy.item.ModifyItemTooltipsKubeEvent;
import dev.uncandango.kubegroovy.plugin.builtin.event.ClientEvents;
import dev.uncandango.kubegroovy.plugin.builtin.event.ItemEvents;
import dev.uncandango.kubegroovy.plugin.builtin.event.KeyBindEvents;
import dev.uncandango.kubegroovy.registry.RegistryObjectStorage;
import dev.uncandango.kubegroovy.script.ConsoleGY;
import dev.uncandango.kubegroovy.script.PlatformWrapper;
import dev.uncandango.kubegroovy.script.ScriptType;
import dev.uncandango.kubegroovy.text.action.DynamicTextAction;
import dev.uncandango.kubegroovy.text.tooltip.ItemTooltipData;
import dev.uncandango.kubegroovy.text.tooltip.TooltipRequirements;
import dev.uncandango.kubegroovy.util.StackTraceCollector;
import dev.uncandango.kubegroovy.util.Tristate;
import dev.uncandango.kubegroovy.web.LocalWebServer;
import dev.uncandango.kubegroovy.web.WebServerProperties;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterConditionalItemModelPropertyEvent;
import net.neoforged.neoforge.client.event.RegisterFluidModelsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@EventBusSubscriber(modid = KubeGroovy.MOD_ID, value = Dist.CLIENT)
public class KubeGroovyClientEventHandler {
	public static final Pattern COMPONENT_ERROR = ConsoleGY.methodPattern(KubeGroovyClientEventHandler.class, "onItemTooltip");
	private static final List<String> lastComponentError = List.of();

	@SubscribeEvent(priority = EventPriority.LOW)
	public static void setupClient(FMLClientSetupEvent event) {
		KubeGroovy.PROXY = new KubeGroovyClient();
		event.enqueueWork(KubeGroovyClientEventHandler::setupClient0);
	}

	@SubscribeEvent
	public static void addClientPacks(AddPackFindersEvent event) {
		if (event.getPackType() == PackType.CLIENT_RESOURCES) {
			event.addRepositorySource(new KubeGroovyResourcePackFinder());
		}
	}

	@SubscribeEvent
	public static void registerConditionalItemModelProperties(RegisterConditionalItemModelPropertyEvent event) {
		event.register(Identifier.fromNamespaceAndPath("kubegroovy", "callback"),
			KubeGroovyConditionalCallbackProperty.MAP_CODEC);
	}

	private static void setupClient0() {
		if (!PlatformWrapper.isGeneratingData() && Minecraft.getInstance() != null && WebServerProperties.get().enabled) {
			LocalWebServer.start(Minecraft.getInstance(), true);
		}

		ItemEvents.MODEL_PROPERTIES.post(ScriptType.STARTUP, new ItemModelPropertiesKubeEvent());

		var list = new ArrayList<ItemTooltipData>();
		ItemEvents.MODIFY_TOOLTIPS.post(ScriptType.CLIENT, new ModifyItemTooltipsKubeEvent(list::add));
		KubeGroovyClient.clientItemTooltips = List.copyOf(list);
	}

	@SubscribeEvent
	public static void blockTintSources(RegisterColorHandlersEvent.BlockTintSources event) {
		for (var builder : RegistryObjectStorage.BLOCK) {
			if (builder instanceof BlockBuilder b && b.tint != null) {
				var tintSources = new ArrayList<@Nullable BlockTintSource>();

				if (b.tint instanceof BlockTintFunction.Mapped mapped) {
					for (int i = 0; i <= mapped.getMaxTintIndex(); i++) {
						var func = mapped.map.get(i);
						tintSources.add(func == null ? null : new BlockTintFunctionWrapper(func, i));
					}
				} else {
					tintSources.add(new BlockTintFunctionWrapper(b.tint, 0));
				}

				event.register(tintSources, b.get());
			}
		}
	}

	@SubscribeEvent
	public static void itemTintSources(RegisterColorHandlersEvent.ItemTintSources event) {
		event.register(Identifier.fromNamespaceAndPath("kubegroovy", "tint"), ItemTintFunctionWrapper.CODEC);
	}

	@SubscribeEvent
	public static void registerMenuScreens(RegisterMenuScreensEvent event) {
		event.register(KubeGroovyMenus.MENU.get(), KubeGroovyScreen::new);
		ClientEvents.MENU_SCREEN_REGISTRY.post(ScriptType.STARTUP, new MenuScreenRegistryKubeEvent(event));
	}

	@SubscribeEvent
	public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
		ClientEvents.ENTITY_RENDERER_REGISTRY.post(ScriptType.STARTUP, new EntityRendererRegistryKubeEvent(event));
		ClientEvents.BLOCK_ENTITY_RENDERER_REGISTRY.post(ScriptType.STARTUP, new BlockEntityRendererRegistryKubeEvent(event));
	}

	@SubscribeEvent
	public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
		var mainCategory = new KeyMapping.Category(Identifier.fromNamespaceAndPath("kubegroovy", "KubeGroovy"));
		event.registerCategory(mainCategory);

		var kubeEvent = new KeybindRegistryKubeEvent();
		KeyBindEvents.REGISTRY.post(kubeEvent);

		for (var catId : kubeEvent.categories()) {
			event.registerCategory(new KeyMapping.Category(catId));
		}

		for (var bind : kubeEvent.build()) {
			event.register(bind.mapping);
		}

		KubeGroovyKeybinds.triggerReload();
	}

	@SubscribeEvent
	public static void registerCoreShaders(RegisterRenderPipelinesEvent event) {
		/*var shaderId = Identifier.withDefaultNamespace("kubegroovy/rendertype_highlight");
		HighlightRenderer.HIGHLIGHT_PIPELINE_BLOCK = RenderPipeline.builder()
			.withLocation(shaderId)
			.withVertexShader(shaderId)
			.withFragmentShader(shaderId)
			.withVertexFormat(DefaultVertexFormat.BLOCK, VertexFormat.Mode.QUADS)
			.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
			.withCull(true)
			.withoutBlend()
			.withColorWrite(true, true)
			.withDepthWrite(true)
			.build();
		event.registerPipeline(HighlightRenderer.HIGHLIGHT_PIPELINE_BLOCK);
		*/
	}

	@SubscribeEvent
	public static void onRegisterFluidModels(RegisterFluidModelsEvent event) {
		for (var builder : RegistryObjectStorage.FLUID) {
			if (builder instanceof FluidBuilder b) {
				var type = b.fluidType;
				event.register(new FluidModel.Unbaked(
					new Material(type.actualStillTexture),
					new Material(type.actualFlowingTexture),
					type.blockOverlayTexture != null ? new Material(type.blockOverlayTexture) : null,
					type.tint != null ? new FluidTintFunctionWrapper(type.tint, 0, b.bucketColor) : null,
					null
				), b.get(), b.flowingFluid.get());
			}
		}
	}

	@SubscribeEvent
	public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
		for (var builder : RegistryObjectStorage.FLUID_TYPE) {
			if (builder instanceof FluidTypeBuilder b) {
				event.registerFluidType(new IClientFluidTypeExtensions() {
					@Override
					@Nullable
					public Identifier getRenderOverlayTexture(Minecraft mc) {
						return b.screenOverlayTexture;
					}
				}, b.get());
			}
		}
	}


	@SubscribeEvent
	public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
		if (ClientEvents.PARTICLE_PROVIDER_REGISTRY.hasListeners()) {
			ClientEvents.PARTICLE_PROVIDER_REGISTRY.post(new ParticleProviderRegistryKubeEvent(event));
		}
	}

	@SubscribeEvent
	public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
		KubeGroovyClientCommands.register(event.getDispatcher());
		// TODO: custom client commands...?
	}

	private static <T> List<String> appendComponentValue(DynamicOps<Tag> ops, MutableComponent line, DataComponentType<T> type, @Nullable T value) {
		if (value == null) {
			line.append(Component.literal("null").kgy$red());
			return List.of();
		} else if (value instanceof Component c) {
			line.append(Component.empty().kgy$gold().append(c));
		}

		try {
			var tag = type.codecOrThrow().encodeStart(ops, value).getOrThrow();
			line.append(NbtUtils.toPrettyComponent(tag));
			return List.of();
		} catch (Throwable ex) {
			line.append(Component.literal(String.valueOf(value)).kgy$red());
			var lines = new ArrayList<String>();
			ex.printStackTrace(new StackTraceCollector(lines, COMPONENT_ERROR, ConsoleGY.ERROR_REDUCE));
			return lines;
		}
	}

	public static boolean testRequirements(Minecraft mc, DynamicItemTooltipsKubeEvent event, TooltipRequirements r) {
		if (!r.advanced().test(event.advanced)) {
			return false;
		}

		if (!r.creative().test(event.creative)) {
			return false;
		}

		if (!r.shift().test(event.shift)) {
			return false;
		}

		if (!r.ctrl().test(event.ctrl)) {
			return false;
		}

		if (!r.alt().test(event.alt)) {
			return false;
		}

		if (!r.stages().isEmpty()) {
			var stages = mc.player.kgy$getStages();

			for (var entry : r.stages().entrySet()) {
				if (entry.getValue() != Tristate.DEFAULT && !entry.getValue().test(stages.has(entry.getKey()))) {
					return false;
				}
			}
		}

		return true;
	}

	private static void handleItemTooltips(Minecraft mc, ItemTooltipData tooltip, DynamicItemTooltipsKubeEvent event) {
		if ((tooltip.filter().isEmpty() || tooltip.filter().get().test(event.item))
			&& (tooltip.requirements().isEmpty() || testRequirements(mc, event, tooltip.requirements().get()))) {
			for (var action : tooltip.actions()) {
				if (action instanceof DynamicTextAction(String id)) {
					try {
						ItemEvents.DYNAMIC_TOOLTIPS.post(ScriptType.CLIENT, id, event);
					} catch (Exception ex) {
						ConsoleGY.CLIENT.error("Item " + event.item.kgy$getId() + " dynamic tooltip error", ex);
					}
				} else {
					action.apply(event.lines);
				}
			}
		}
	}

	@SubscribeEvent(priority = EventPriority.LOW)
	public static void onItemTooltip(ItemTooltipEvent event) {
		var stack = event.getItemStack();

		if (stack.isEmpty()) {
			return;
		}

		var mc = Minecraft.getInstance();
		var lines = event.getToolTip();
		var flags = event.getFlags();
		var sessionData = KubeSessionData.of(mc);

		var dynamicEvent = new DynamicItemTooltipsKubeEvent(stack, flags, lines, sessionData == null);

		for (var tooltip : KubeGroovyClient.clientItemTooltips) {
			handleItemTooltips(mc, tooltip, dynamicEvent);
		}

		if (sessionData != null) {
			for (var tooltip : sessionData.itemTooltips) {
				handleItemTooltips(mc, tooltip, dynamicEvent);
			}
		}
	}

	@SubscribeEvent
	public static void loggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
		ScheduledClientEvent.EVENTS.clear();
		ClientEvents.LOGGED_IN.post(ScriptType.CLIENT, new ClientPlayerKubeEvent(event.getPlayer()));
	}

	@SubscribeEvent
	public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
		ClientEvents.LOGGED_OUT.post(ScriptType.CLIENT, new ClientPlayerKubeEvent(event.getPlayer()));
	}

	@SubscribeEvent
	public static void clientTick(ClientTickEvent.Pre event) {
		var mc = Minecraft.getInstance();

		KubeGroovyKeybinds.triggerKeyEvents(mc);
	}

	public static @Nullable Screen setScreen(@Nullable Screen screen) {
		if (screen instanceof TitleScreen && !ConsoleGY.STARTUP.errors.isEmpty() && CommonProperties.get().startupErrorGUI) {
			return new KubeGroovyErrorScreen(screen, ConsoleGY.STARTUP, false);
		}

		if (screen instanceof TitleScreen && !ConsoleGY.CLIENT.errors.isEmpty() && CommonProperties.get().startupErrorGUI) {
			return new KubeGroovyErrorScreen(screen, ConsoleGY.CLIENT, false);
		}

		return screen;
	}

	@SubscribeEvent
	public static void guiPostInit(ScreenEvent.Init.Post event) {
		var screen = event.getScreen();

		if (ClientProperties.get().disableRecipeBook && screen instanceof RecipeUpdateListener) {
			var iterator = screen.children().iterator();

			while (iterator.hasNext()) {
				var listener = iterator.next();

				if (listener instanceof ImageButton button && button.sprites.enabled().equals(KubeGroovyClient.RECIPE_BUTTON_TEXTURE)) {
					screen.renderables.remove(listener);
					screen.narratables.remove(listener);
					iterator.remove();
					return;
				}
			}
		}
	}

	/*private void postAtlasStitch(TextureAtlas atlas) {
		if (!ClientProperties.get().getExportAtlases()) {
			return;
		}

		GL11.glBindTexture(GL11.GL_TEXTURE_2D, atlas.getId());
		var w = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
		var h = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);

		if (w <= 0 || h <= 0) {
			return;
		}

		var image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		var pixels = new int[w * h];

		var result = BufferUtils.createIntBuffer(w * h);
		GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, result);
		result.get(pixels);

		image.setRGB(0, 0, w, h, pixels, 0, w);

		var path = KubeGroovyPaths.EXPORT.resolve(atlas.identifier().getNamespace() + "/" + atlas.identifier().getPath());

		if (!Files.exists(path.getParent())) {
			try {
				Files.createDirectories(path.getParent());
			} catch (Exception ex) {
				ex.printStackTrace();
				return;
			}
		}

		if (!Files.exists(path)) {
			try {
				Files.createFile(path);
			} catch (Exception ex) {
				ex.printStackTrace();
				return;
			}
		}

		try (var stream = Files.newOutputStream(path)) {
			ImageIO.write(image, "PNG", stream);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}*/

	@SubscribeEvent(priority = EventPriority.LOW)
	public static void openScreenEvent(ScreenEvent.Opening event) {
		var s = KubeGroovyClientEventHandler.setScreen(event.getScreen());

		if (s != null && event.getScreen() != s) {
			event.setNewScreen(s);
		}
	}

	@SubscribeEvent
	public static void tagsUpdated(TagsUpdatedEvent event) {
		if (event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.CLIENT_PACKET_RECEIVED
			&& Minecraft.getInstance().screen instanceof KubeGroovyErrorScreen screen
			&& screen.scriptType == ScriptType.SERVER) {
			Minecraft.getInstance().kgy$runCommand("kubegroovy errors server");
		}
	}
}