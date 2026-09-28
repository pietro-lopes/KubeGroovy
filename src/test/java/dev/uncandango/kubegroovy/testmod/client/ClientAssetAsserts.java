package dev.uncandango.kubegroovy.testmod.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.uncandango.kubegroovy.client.ClientAssetPacks;
import dev.uncandango.kubegroovy.client.KubeGroovyKeybinds;
import dev.uncandango.kubegroovy.client.model.KubeGroovyModelPropertyRegistry;
import dev.uncandango.kubegroovy.script.data.GeneratedData;
import dev.uncandango.kubegroovy.script.data.GeneratedDataStage;
import net.minecraft.client.Minecraft;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;

import static dev.uncandango.kubegroovy.testmod.GameAsserts.assertj;
import static org.assertj.core.api.Assertions.assertThat;

/// Java-side effect assertions for the client asset fixtures
/// (`client_coverage_assets.groovy`, `client_coverage_events.groovy` lang
/// handlers, `client_coverage_startup.groovy` registries). Instead of trusting
/// script markers alone, these read back the generated packs, registries, and
/// the mounted resource manager.
///
/// Client-only: isolated in its own class so the dedicated server never loads
/// client classes (same reason as
/// [dev.uncandango.kubegroovy.testmod.web.TestClientStatus]). Only called from
/// `side = CLIENT` holders in [ClientCoverageTests], which never execute on a
/// dedicated server.
public final class ClientAssetAsserts {
	private ClientAssetAsserts() {
	}

	private static GeneratedData generatedAfterMods(String path) {
		var data = ClientAssetPacks.INSTANCE.virtualPacks.get(GeneratedDataStage.AFTER_MODS)
			.getGenerated(Identifier.parse("kubegroovy:" + path));
		assertThat(data).as("generated kubegroovy:" + path).isNotNull();
		return data;
	}

	private static JsonObject generatedJsonAfterMods(String path) {
		return JsonParser.parseString(new String(generatedAfterMods(path).data().get(), StandardCharsets.UTF_8)).getAsJsonObject();
	}

	private static byte[] generatedBytesAfterMods(String path) {
		return generatedAfterMods(path).data().get();
	}

	public static void assertBlockState(final GameTestHelper helper) {
		assertj(helper, () -> {
			var json = generatedJsonAfterMods("blockstates/coverage_client_block.json");
			assertThat(json.has("variants")).as("variants object").isTrue();
			var variants = json.getAsJsonObject("variants");
			assertThat(variants.has("")).as("default variant").isTrue();
			assertThat(variants.has("facing=north")).as("north variant").isTrue();
			assertThat(variants.getAsJsonObject("facing=north").get("model").getAsString())
				.as("north model").isEqualTo("kubegroovy:block/unknown");
			assertThat(variants.has("facing=south")).as("south variant").isTrue();
			assertThat(variants.getAsJsonArray("facing=south")).as("south models").hasSize(2);
		});
	}

	public static void assertModels(final GameTestHelper helper) {
		assertj(helper, () -> {
			var model = generatedJsonAfterMods("models/block/coverage_client_model.json");
			assertThat(model.has("parent")).as("model parent").isTrue();
			assertThat(model.get("parent").getAsString()).as("parent value").isEqualTo("minecraft:block/cube_all");
			assertThat(model.has("elements")).as("elements").isTrue();
			assertThat(model.getAsJsonArray("elements")).as("element count").hasSize(3);
			assertThat(model.has("overrides")).as("overrides").isTrue();
			assertThat(model.getAsJsonArray("overrides")).as("override count").hasSize(2);
			assertThat(model.has("textures")).as("textures").isTrue();
			assertThat(model.getAsJsonObject("textures").has("layer0")).as("layer0 texture").isTrue();
			assertThat(generatedAfterMods("models/item/coverage_client_item.json")).as("item model").isNotNull();
			assertThat(generatedJsonAfterMods("items/coverage_client_tinted_item.json").getAsJsonObject("model").getAsJsonArray("tints"))
				.as("tint sources").hasSize(2);
			assertThat(generatedAfterMods("items/coverage_client_def.json")).as("item definition").isNotNull();
			assertThat(generatedAfterMods("models/item/coverage_client_default_item.json")).as("default item model").isNotNull();
			assertThat(generatedAfterMods("models/item/coverage_client_handheld.json")).as("handheld item model").isNotNull();
		});
	}

	public static void assertParticlesSounds(final GameTestHelper helper) {
		assertj(helper, () -> {
			var particle = generatedJsonAfterMods("particles/coverage_client_particle.json");
			assertThat(particle.has("textures")).as("particle textures").isTrue();
			assertThat(particle.getAsJsonArray("textures")).as("particle texture list")
				.containsExactly(
					JsonParser.parseString("\"kubegroovy:coverage_b\""),
					JsonParser.parseString("\"kubegroovy:coverage_c\""));
			var sounds = generatedJsonAfterMods("sounds.json");
			assertThat(sounds.has("coverage_client")).as("coverage sound").isTrue();
			assertThat(sounds.has("coverage_client_new")).as("overlay sound").isTrue();
		});
	}

	public static void assertTextures(final GameTestHelper helper) {
		assertj(helper, () -> {
			assertThat(generatedBytesAfterMods("textures/coverage_client/real.png")).as("real texture").isNotEmpty();
			assertThat(generatedBytesAfterMods("textures/coverage_client/stenciled.png")).as("stencil texture").isNotEmpty();
			assertThat(generatedBytesAfterMods("textures/coverage_client/masked.png")).as("mask texture").isNotEmpty();
			assertThat(generatedBytesAfterMods("textures/coverage_client/masked_same.png")).as("same-size mask texture").isNotEmpty();
			assertThat(generatedBytesAfterMods("textures/coverage_client/logo.png")).as("logo texture").isNotEmpty();
			assertThat(generatedBytesAfterMods("textures/coverage_client/logo.png.mcmeta")).as("logo mcmeta").isNotEmpty();
			assertThat(generatedBytesAfterMods("textures/coverage_particle.png")).as("particle sprite").isNotEmpty();
			assertThat(ClientAssetPacks.INSTANCE.virtualPacks.get(GeneratedDataStage.AFTER_MODS)
				.getGenerated(Identifier.parse("kubegroovy:textures/coverage_client/missing.png")))
				.as("missing texture absent").isNull();
			assertThat(ClientAssetPacks.INSTANCE.virtualPacks.get(GeneratedDataStage.BEFORE_MODS)
				.getGenerated(Identifier.parse("kubegroovy:blockstates/coverage_client_before.json")))
				.as("before_mods blockstate").isNotNull();
			assertThat(ClientAssetPacks.INSTANCE.virtualPacks.get(GeneratedDataStage.LAST)
				.getGenerated(Identifier.parse("kubegroovy:particles/coverage_client_last.json")))
				.as("last particle").isNotNull();
		});
	}

	private static JsonObject internalLangJson(String namespace, String lang) {
		var data = ClientAssetPacks.INSTANCE.internalAssetPack
			.getGenerated(Identifier.parse(namespace + ":lang/" + lang + ".json"));
		assertThat(data).as("lang file " + namespace + ":lang/" + lang).isNotNull();
		return JsonParser.parseString(new String(data.data().get(), StandardCharsets.UTF_8)).getAsJsonObject();
	}

	public static void assertLang(final GameTestHelper helper) {
		assertj(helper, () -> {
			var enUs = internalLangJson("kubegroovy", "en_us");
			assertThat(enUs.has("coverage.client.key")).as("en_us coverage key present").isTrue();
			assertThat(enUs.get("coverage.client.key").getAsString()).as("en_us coverage value").isEqualTo("Coverage Value");
			var esEs = internalLangJson("kubegroovy", "es_es");
			assertThat(esEs.has("coverage.client.es")).as("es_es coverage key present").isTrue();
			assertThat(esEs.get("coverage.client.es").getAsString()).as("es_es coverage value").isEqualTo("Valor de cobertura");
			var minecraft = internalLangJson("minecraft", "en_us");
			assertThat(minecraft.has("coverage.client.simple")).as("minecraft simple key present").isTrue();
			assertThat(minecraft.get("coverage.client.simple").getAsString()).as("minecraft simple value").isEqualTo("Simple Value");
		});
	}

	public static void assertFilePack(final GameTestHelper helper) {
		assertj(helper, () -> {
			var mc = Minecraft.getInstance();
			assertThat(mc).as("minecraft client").isNotNull();
			var manager = mc.getResourceManager();
			assertThat(manager.getResource(Identifier.parse("kubegroovy:textures/coverage_file_test.png")).isPresent())
				.as("file texture mounted").isTrue();
			var model = readJson(manager, "models/item/coverage_file_item.json");
			assertThat(model.has("parent")).as("file model parent present").isTrue();
			assertThat(model.get("parent").getAsString()).as("file model parent").isEqualTo("minecraft:item/generated");
			assertThat(model.has("textures")).as("file model textures present").isTrue();
			assertThat(model.getAsJsonObject("textures").get("layer0").getAsString())
				.as("file model layer0").isEqualTo("kubegroovy:item/example_item");
			assertThat(readText(manager, "coverage/note.txt")).as("file note").contains("file asset pack note");
			assertThat(manager.getResource(Identifier.parse("kubegroovy:coverage/missing_xyz.txt")).isPresent())
				.as("missing file absent").isFalse();
			assertThat(manager.getNamespaces()).as("namespaces").contains("kubegroovy");
			assertThat(manager.listResources("textures", i -> true).keySet())
				.as("file textures listed").contains(Identifier.parse("kubegroovy:textures/coverage_file_test.png"));
			var lang = internalLangJson("kubegroovy", "en_us");
			assertThat(lang.has("coverage.file.lang")).as("file lang key present").isTrue();
			assertThat(lang.get("coverage.file.lang").getAsString()).as("file lang value").isEqualTo("File Lang");
		});
	}

	private static com.google.gson.JsonObject readJson(net.minecraft.server.packs.resources.ResourceManager manager, String path) {
		return JsonParser.parseString(readTextRaw(manager, path)).getAsJsonObject();
	}

	private static String readText(net.minecraft.server.packs.resources.ResourceManager manager, String path) {
		return readTextRaw(manager, path);
	}

	private static String readTextRaw(net.minecraft.server.packs.resources.ResourceManager manager, String path) {
		var id = Identifier.parse("kubegroovy:" + path);
		var resource = manager.getResource(id);
		assertThat(resource.isPresent()).as("resource present " + id).isTrue();

		try (var in = resource.get().open()) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (Exception ex) {
			throw new AssertionError("Failed reading " + id + ": " + ex);
		}
	}

	public static void assertRegistries(final GameTestHelper helper) {
		assertj(helper, () -> {
			var key = KubeGroovyKeybinds.get("coverage_key_a");
			assertThat(key).as("coverage key registered").isNotNull();
			assertThat(key.mapping).as("coverage key mapping").isNotNull();
			assertThat(KubeGroovyKeybinds.get("coverage_key_unknown_default")).as("unknown-default key registered").isNotNull();
			assertThat(KubeGroovyModelPropertyRegistry.getConditional(Identifier.parse("kubegroovy:coverage_cb")))
				.as("conditional callback").isNotNull();
			assertThat(KubeGroovyModelPropertyRegistry.getConditional(Identifier.parse("kubegroovy:coverage_cb_all")))
				.as("conditional callback all").isNotNull();
		});
	}

	public static void assertResourceManager(final GameTestHelper helper) {
		assertj(helper, () -> {
			var mc = Minecraft.getInstance();
			assertThat(mc).as("minecraft client").isNotNull();
			assertThat(mc.getResourceManager()
				.getResource(Identifier.parse("kubegroovy:blockstates/coverage_client_block.json")).isPresent())
				.as("blockstate mounted in resource manager").isTrue();
			assertThat(mc.getResourceManager()
				.getResource(Identifier.parse("kubegroovy:textures/coverage_client/logo.png")).isPresent())
				.as("logo texture mounted in resource manager").isTrue();
		});
	}
}
