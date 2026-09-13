package dev.uncandango.kubegroovy.core;

import dev.uncandango.kubegroovy.KubeGroovy;
import dev.uncandango.kubegroovy.KubeGroovyPaths;
import dev.uncandango.kubegroovy.client.ClientProperties;
import dev.uncandango.kubegroovy.script.data.GeneratedData;
import net.minecraft.server.packs.resources.IoSupplier;

import javax.imageio.ImageIO;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.function.Supplier;

public interface WindowKGY {
	record KGYScaledIconProvider(BufferedImage original, int target) implements Supplier<byte[]> {
		@Override
		public byte[] get() {
			try {
				var out = new ByteArrayOutputStream();

				if (original.getWidth() == target && original.getHeight() == target) {
					ImageIO.write(original, "png", out);
				} else {
					var img = new BufferedImage(target, target, BufferedImage.TYPE_INT_ARGB);
					var g = img.createGraphics();

					if (ClientProperties.get().blurScaledPackIcon) {
						g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
					} else {
						g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
					}

					g.drawImage(original, 0, 0, target, target, null);
					g.dispose();
					ImageIO.write(img, "png", out);
				}

				return out.toByteArray();
			} catch (Exception ex) {
				throw new IllegalStateException(original.toString(), ex);
			}
		}
	}

	default List<IoSupplier<InputStream>> kgy$loadIcons(List<IoSupplier<InputStream>> original) throws IOException {
		if (Files.exists(KubeGroovyPaths.PACKICON)) {
			try (var in = Files.newInputStream(KubeGroovyPaths.PACKICON)) {
				var img = ImageIO.read(in);

				return List.of(
					new GeneratedData(KubeGroovy.id("icon_16x.png"), new KGYScaledIconProvider(img, 16)),
					new GeneratedData(KubeGroovy.id("icon_24x.png"), new KGYScaledIconProvider(img, 24)),
					new GeneratedData(KubeGroovy.id("icon_32x.png"), new KGYScaledIconProvider(img, 32)),
					new GeneratedData(KubeGroovy.id("icon_48x.png"), new KGYScaledIconProvider(img, 48)),
					new GeneratedData(KubeGroovy.id("icon_128.png"), new KGYScaledIconProvider(img, 128)),
					new GeneratedData(KubeGroovy.id("icon_256x.png"), new KGYScaledIconProvider(img, 256))
				);
			}
		}

		return original;
	}
}
