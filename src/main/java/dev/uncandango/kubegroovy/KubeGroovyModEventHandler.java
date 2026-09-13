package dev.uncandango.kubegroovy;

import dev.uncandango.kubegroovy.block.entity.BlockEntityBuilder;
import dev.uncandango.kubegroovy.block.entity.CustomCapabilityAttachment;
import dev.uncandango.kubegroovy.block.entity.KubeBlockEntity;
import dev.uncandango.kubegroovy.event.KubeStartupEvent;
import dev.uncandango.kubegroovy.item.creativetab.CreativeTabCallbackForge;
import dev.uncandango.kubegroovy.item.creativetab.CreativeTabKubeEvent;
import dev.uncandango.kubegroovy.plugin.KubeGroovyPlugin;
import dev.uncandango.kubegroovy.plugin.KubeGroovyPlugins;
import dev.uncandango.kubegroovy.plugin.builtin.event.StartupEvents;
import dev.uncandango.kubegroovy.recipe.RecipesKubeEvent;
import dev.uncandango.kubegroovy.registry.RegistryObjectStorage;
import dev.uncandango.kubegroovy.script.ConsoleGY;
import dev.uncandango.kubegroovy.script.ConsoleLine;
import dev.uncandango.kubegroovy.script.ScriptType;
import dev.uncandango.kubegroovy.script.ScriptsLoadedEvent;
import dev.uncandango.kubegroovy.util.UtilsGY;
import net.minecraft.core.Direction;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.ICapabilityProvider;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import net.neoforged.neoforge.event.ModifyRecipeJsonsEvent;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;

@EventBusSubscriber(modid = KubeGroovy.MOD_ID)
public class KubeGroovyModEventHandler {
	@SubscribeEvent
	public static void modifyDefaultComponents(ModifyDefaultComponentsEvent event) {
		UtilsGY.postItemModificationEvents(event);
	}

	@SubscribeEvent
	public static void modifyRecipeJsons(ModifyRecipeJsonsEvent event) {
		if (!RecipesKubeEvent.INSTANCE.isBound()) {
			KubeGroovy.LOGGER.warn("Recipe event is not bound, is another mod calling ModifyRecipeJsonsEvent?!");
			return;
		}

		RecipesKubeEvent.INSTANCE.get().post(event.getOps(), event.getRecipeJsons());
	}

	@SubscribeEvent(priority = EventPriority.LOW)
	public static void creativeTab(BuildCreativeModeTabContentsEvent event) {
		var tabId = event.getTabKey().identifier();

		if (StartupEvents.MODIFY_CREATIVE_TAB.hasListeners(tabId)) {
			StartupEvents.MODIFY_CREATIVE_TAB.post(ScriptType.STARTUP, tabId, new CreativeTabKubeEvent(event.getTab(), event.hasPermissions(), new CreativeTabCallbackForge(event)));
		}
	}

	@SubscribeEvent(priority = EventPriority.LOW)
	public static void loadComplete(FMLLoadCompleteEvent event) {
		event.enqueueWork(KubeGroovyModEventHandler::loadComplete0);
	}

	private static void loadComplete0() {
		KubeGroovyPlugins.forEachPlugin(KubeGroovyPlugin::afterInit);
		NeoForge.EVENT_BUS.post(new ScriptsLoadedEvent());
		StartupEvents.POST_INIT.post(ScriptType.STARTUP, KubeStartupEvent.BASIC);
		UtilsGY.postBlockModificationEvents();

		if (!ConsoleGY.STARTUP.errors.isEmpty()) {
			var list = new ArrayList<String>();
			list.add("Startup script errors:");

			var lines = ConsoleGY.STARTUP.errors.toArray(ConsoleLine.EMPTY_ARRAY);

			for (int i = 0; i < lines.length; i++) {
				list.add((i + 1) + ") " + lines[i]);
			}

			KubeGroovy.LOGGER.error(String.join("\n", list));

			ConsoleGY.STARTUP.flush(true);

			if (FMLLoader.getCurrent().getDist().isDedicatedServer() || !CommonProperties.get().startupErrorGUI) {
				throw new RuntimeException("There were KubeGroovy startup script syntax errors! See logs/kubegroovy/startup.log for more info");
			}
		}

		ConsoleGY.STARTUP.stopCapturingErrors();
		ConsoleGY.CLIENT.stopCapturingErrors();

		Util.nonCriticalIoPool().execute(() -> {
			try {
				var response = HttpClient.newBuilder()
					.followRedirects(HttpClient.Redirect.ALWAYS)
					.connectTimeout(Duration.ofSeconds(5L))
					.build()
					.send(HttpRequest.newBuilder().uri(URI.create("https://v.kubejs.com/update-check?" + KubeGroovy.QUERY)).GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
				if (response.statusCode() == 200) {
					var body = response.body().trim();

					if (!body.isEmpty()) {
						ConsoleGY.STARTUP.info("Update available: " + body);
					}
				}
			} catch (Exception ignored) {
			}
		});
	}

	private static boolean directionMatches(EnumSet<Direction> directions, @Nullable Object from) {
		return directions.isEmpty() || (from instanceof Direction d && directions.contains(d));
	}

	@SubscribeEvent
	public static void registerCapabilities(RegisterCapabilitiesEvent event) {
		for (var info : RegistryObjectStorage.BLOCK_ENTITY.objects.values().stream()
			.filter(BlockEntityBuilder.class::isInstance)
			.map(b -> ((BlockEntityBuilder) b).info).toList()) {
			var beType = (BlockEntityType<KubeBlockEntity>) info.entityType;
			for (var config : info.energyConfigs) {
				registerDirectional(event, Capabilities.Energy.BLOCK, beType, config.directions(),
					(entity, from) -> entity.energyWrappers.get(config.id()));
			}

			for (var config : info.fluidConfigs) {
				registerDirectional(event, Capabilities.Fluid.BLOCK, beType, config.directions(),
					(entity, from) -> entity.fluidWrappers.get(config.id()));
			}

			for (var config : info.inventoryConfigs) {
				registerDirectional(event, Capabilities.Item.BLOCK, beType, config.directions(),
					(entity, from) -> entity.inventoryWrappers.get(config.id()));
			}

			for (var config : info.customCapConfigs) {
				registerCustomCapability(event, config, beType);
			}
		}
	}

	private static <CAP> void registerDirectional(
		RegisterCapabilitiesEvent event,
		BlockCapability<CAP, Direction> capability,
		BlockEntityType<KubeBlockEntity> beType,
		EnumSet<Direction> directions,
		ICapabilityProvider<KubeBlockEntity, Direction, CAP> provider
	) {
		event.registerBlockEntity(capability, beType, (entity, from) -> {
			if (directionMatches(directions, from)) {
				return provider.getCapability(entity, from);
			}
			return null;
		});
	}

	private static void registerCustomCapability(
		RegisterCapabilitiesEvent event,
		CustomCapabilityAttachment.Config config,
		BlockEntityType<KubeBlockEntity> beType
	) {
		registerDirectional(event, (BlockCapability) config.capability(), beType, config.directions(),
			(entity, from) -> {
				var cap = entity.customCapabilities.get(config.id());
				return cap != null ? cap.getCapability(config.capability()) : null;
			});
	}
}
