package dev.uncandango.kubegroovy;

import com.google.common.base.Stopwatch;
import dev.uncandango.kubegroovy.block.entity.KubeGroovyAttachmentTypes;
import dev.uncandango.kubegroovy.client.ClientScriptManager;
import dev.uncandango.kubegroovy.event.KubeStartupEvent;
import dev.uncandango.kubegroovy.fluid.KubeGroovyFluidIngredients;
import dev.uncandango.kubegroovy.gui.KubeGroovyMenus;
import dev.uncandango.kubegroovy.holder.KubeGroovyHolderSets;
import dev.uncandango.kubegroovy.ingredient.KubeGroovyIngredients;
import dev.uncandango.kubegroovy.item.creativetab.KubeGroovyCreativeTabs;
import dev.uncandango.kubegroovy.level.ruletest.KubeGroovyRuleTests;
import dev.uncandango.kubegroovy.plugin.KubeGroovyPlugin;
import dev.uncandango.kubegroovy.plugin.KubeGroovyPlugins;
import dev.uncandango.kubegroovy.plugin.builtin.event.StartupEvents;
import dev.uncandango.kubegroovy.recipe.KubeGroovyRecipeSerializers;
import dev.uncandango.kubegroovy.registry.RegistryType;
import dev.uncandango.kubegroovy.script.KubeGroovyBackgroundThread;
import dev.uncandango.kubegroovy.script.PlatformWrapper;
import dev.uncandango.kubegroovy.script.ScriptManager;
import dev.uncandango.kubegroovy.script.ScriptType;
import dev.uncandango.kubegroovy.script.data.KubeFileResourcePack;
import dev.uncandango.kubegroovy.util.RecordDefaults;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.language.IModInfo;
import org.jspecify.annotations.NullUnmarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;

@Mod(KubeGroovy.MOD_ID)
@NullUnmarked
public class KubeGroovy {
	public static final String MOD_ID = "kubegroovy";
	public static final String MOD_NAME = "KubeGroovy";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);
	public static final int MC_VERSION_NUMBER = 2601;
	public static final String MC_VERSION_STRING = "26.1";
	public static String QUERY;
	public static String VERSION = "0";
	public static String DISPLAY_NAME = "KubeGroovy";

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	public static IEventBus modEventBus;
	public static ModContainer thisMod;

	public static KubeGroovyCommon PROXY = new KubeGroovyCommon();

	private static ScriptManager startupScriptManager, clientScriptManager;

	public static ScriptManager getStartupScriptManager() {
		return startupScriptManager;
	}

	public static ScriptManager getClientScriptManager() {
		return clientScriptManager;
	}

	public KubeGroovy(IEventBus bus, Dist dist, ModContainer mod) throws Throwable {
		modEventBus = bus;
		thisMod = mod;
		VERSION = mod.getModInfo().getVersion().toString();
		DISPLAY_NAME = "KubeGroovy " + VERSION;
		QUERY = "source=KubeGroovy&mc=" + MC_VERSION_NUMBER + "&loader=neoforge&v=" + URLEncoder.encode(mod.getModInfo().getVersion().toString(), StandardCharsets.UTF_8);

		if (Files.notExists(KubeGroovyPaths.README)) {
			try {
				Files.writeString(KubeGroovyPaths.README, """
					Find out more info on the website: https://kubejs.com/
					
					Directory information:
					
					assets - Acts as a resource pack, you can put any client resources in here, like textures, models, etc. Example: assets/kubegroovy/textures/item/test_item.png
					data - Acts as a datapack, you can put any server resources in here, like loot tables, functions, etc. Example: data/kubegroovy/loot_tables/blocks/test_block.json
					
					startup_scripts - Scripts that get loaded once during game startup - Used for adding items and other things that can only happen while the game is loading (Can be reloaded with /kubegroovy reload_startup_scripts, but it may not work!)
					server_scripts - Scripts that get loaded every time server resources reload - Used for modifying recipes, tags, loot tables, and handling server events (Can be reloaded with /reload)
					client_scripts - Scripts that get loaded every time client resources reload - Used for JEI events, tooltips and other client side things (Can be reloaded with F3+T)
					
					config - KubeGroovy config storage. This is also the only directory that scripts can access other than world directory
					exported - Data dumps like texture atlases end up here
					
					You can find type-specific logs in logs/kubegroovy/ directory
					""".trim()
				);
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}

		RecordDefaults.init();

		boolean datagen = PlatformWrapper.isGeneratingData();

		if (!datagen) {
			new KubeGroovyBackgroundThread().start();
			ScriptType.STARTUP.console.startCapturingErrors();
			ScriptType.CLIENT.console.startCapturingErrors();
		}

		LOGGER.info("Loading vanilla registries...");
		RegistryType.Scanner.init();

		var pluginTimer = Stopwatch.createStarted();
		LOGGER.info("Looking for KubeGroovy plugins...");
		var allMods = new ArrayList<>(ModList.get().getMods().stream().map(IModInfo::getOwningFile).map(IModFileInfo::getFile).toList());
		var thisModFile = mod.getModInfo().getOwningFile().getFile();
		allMods.remove(thisModFile);
		allMods.addFirst(thisModFile);
		KubeGroovyPlugins.load(allMods, dist == Dist.CLIENT);
		LOGGER.info("Done in {}", pluginTimer.stop());

		startupScriptManager = new StartupScriptManager();

		KubeGroovyPlugins.forEachPlugin(KubeGroovyPlugin::init);

		if (!datagen) {
			startupScriptManager.reload();
		}

		if (dist.isClient()) {
			clientScriptManager = new ClientScriptManager();

			if (!datagen) {
				clientScriptManager.reload();
			}
		}

		KubeGroovyPlugins.forEachPlugin(KubeGroovyPlugin::initStartup);

		if (dist.isClient()) {
			KubeFileResourcePack.scanForInvalidFiles("kubegroovy/assets/", KubeGroovyPaths.ASSETS);
		}

		KubeFileResourcePack.scanForInvalidFiles("kubegroovy/data/", KubeGroovyPaths.DATA);

		if (dist.isClient() || !CommonProperties.get().serverOnly) {
			// See NeoForgeRegistriesSetup.VANILLA_SYNC_REGISTRIES
			NeoForgeMod.enableMilkFluid();
			// KubeGroovyComponents.REGISTRY.register(bus);
			KubeGroovyRecipeSerializers.REGISTRY.register(bus);
			KubeGroovyMenus.REGISTRY.register(bus);
			KubeGroovyIngredients.REGISTRY.register(bus);
			KubeGroovyFluidIngredients.REGISTRY.register(bus);
			KubeGroovyHolderSets.REGISTRY.register(bus);
		}

		KubeGroovyCreativeTabs.REGISTRY.register(bus);
		KubeGroovyRuleTests.REGISTRY.register(bus);
		KubeGroovyAttachmentTypes.REGISTRY.register(bus);

		StartupEvents.INIT.post(ScriptType.STARTUP, KubeStartupEvent.BASIC);
		// KubeGroovyRegistries.chunkGenerators().register(new Identifier(KubeGroovy.MOD_ID, "flat"), () -> KGYFlatLevelSource.CODEC);
	}
}