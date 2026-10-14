package dev.uncandango.kubegroovy.script.groovy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/// Filesystem confinement shared by coercions and approved I/O helpers.
public final class SandboxPaths {
	private SandboxPaths() {}

	public static Path verify(Path path, Path root) throws IOException {
		var absoluteRoot = root.toAbsolutePath().normalize();
		var absolute = (path.isAbsolute() ? path : absoluteRoot.resolve(path)).normalize();
		if (!absolute.startsWith(absoluteRoot)) throw new IOException("Path is outside Minecraft directory: " + path);
		var mods = absoluteRoot.resolve("mods");
		if (absolute.startsWith(mods)) throw new IOException("Path is inside Minecraft mods directory: " + path);
		var realRoot = absoluteRoot.toRealPath();
		var real = resolveRealPath(absolute);
		if (!real.startsWith(realRoot)) throw new IOException("Path escapes Minecraft directory through a link: " + path);
		// Deny aliases into mods, including when mods itself is a linked directory.
		if (real.startsWith(realRoot.resolve("mods")) || real.startsWith(resolveRealPath(mods))) {
			throw new IOException("Path resolves inside Minecraft mods directory: " + path);
		}
		return absolute;
	}

	private static Path resolveRealPath(Path absolute) throws IOException {
		var ancestor = absolute;
		while (!Files.exists(ancestor, LinkOption.NOFOLLOW_LINKS)) {
			ancestor = ancestor.getParent();
			if (ancestor == null) throw new IOException("No existing ancestor for path: " + absolute);
		}
		return ancestor.toRealPath().resolve(ancestor.relativize(absolute)).normalize();
	}
}
