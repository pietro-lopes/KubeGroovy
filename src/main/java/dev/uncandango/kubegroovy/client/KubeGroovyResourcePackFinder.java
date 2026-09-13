package dev.uncandango.kubegroovy.client;

import dev.uncandango.kubegroovy.KubeGroovy;
import dev.uncandango.kubegroovy.KubeGroovyPaths;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.RepositorySource;

import java.nio.file.Files;
import java.util.function.Consumer;

public class KubeGroovyResourcePackFinder implements RepositorySource {
	@Override
	public void loadPacks(Consumer<Pack> nameToPackMap) {
		if (KubeGroovyPaths.FIRST_RUN.get()) {
			var blockTextures = KubeGroovyPaths.dir(KubeGroovyPaths.ASSETS.resolve("kubegroovy/textures/block"));
			var itemTextures = KubeGroovyPaths.dir(KubeGroovyPaths.ASSETS.resolve("kubegroovy/textures/item"));

			try (var in = Files.newInputStream(KubeGroovy.thisMod.getModInfo().getOwningFile().getFile().getFilePath().resolve("data", "KubeGroovy", "example_block_texture.png"));
				 var out = Files.newOutputStream(blockTextures.resolve("example_block.png"))) {
				in.transferTo(out);
			} catch (Exception ex) {
				ex.printStackTrace();
			}

			try (var in = Files.newInputStream(KubeGroovy.thisMod.getModInfo().getOwningFile().getFile().getFilePath().resolve("data", "KubeGroovy", "example_item_texture.png"));
				 var out = Files.newOutputStream(itemTextures.resolve("example_item.png"))) {
				in.transferTo(out);
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
	}
}