package dev.uncandango.kubegroovy.groovytest;

import dev.uncandango.kubegroovy.script.groovy.SandboxPaths;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class SandboxPathsTests {
	@TempDir Path directory;
	@Test void relativePathsAndNonexistentTargetsStayInsideRoot() throws Exception {
		var root = Files.createDirectory(directory.resolve("game"));
		assertEquals(root.resolve("new/config.json"), SandboxPaths.verify(Path.of("new/config.json"), root));
		assertEquals(root.resolve("config.json"), SandboxPaths.verify(root.resolve("sub/../config.json"), root));
		assertThrows(IOException.class, () -> SandboxPaths.verify(Path.of("../outside"), root));
		assertThrows(IOException.class, () -> SandboxPaths.verify(directory.resolve("game-other/file"), root));
		assertThrows(IOException.class, () -> SandboxPaths.verify(directory.resolve("outside"), root));
	}
	@Test void modsDirectoryAndDescendantsAreDeniedBeforeTheyExist() throws Exception {
		var root = Files.createDirectory(directory.resolve("game"));
		for (var path : List.of(Path.of("mods"), Path.of("mods/payload.jar"),
			root.resolve("mods/nested/payload.jar"), Path.of("config/../mods/payload.jar"))) {
			assertThrows(IOException.class, () -> SandboxPaths.verify(path, root));
		}
		Files.createDirectory(root.resolve("mods"));
		var existing = Files.createFile(root.resolve("mods/existing.jar"));
		assertThrows(IOException.class, () -> SandboxPaths.verify(existing, root));
		assertThrows(IOException.class, () -> SandboxPaths.verify(Path.of("mods/new/payload.jar"), root));
		assertEquals(root.resolve("mods-backup/payload.jar"), SandboxPaths.verify(Path.of("mods-backup/payload.jar"), root));
		assertEquals(root.resolve("config/mods/file.json"), SandboxPaths.verify(Path.of("config/mods/file.json"), root));
	}
	@Test void aliasesIntoModsAreDenied() throws Exception {
		var root = Files.createDirectory(directory.resolve("game"));
		var mods = Files.createDirectory(root.resolve("mods"));
		Files.createFile(mods.resolve("existing.jar"));
		var link = root.resolve("downloads");
		createDirectoryLink(link, mods);
		assertThrows(IOException.class, () -> SandboxPaths.verify(link, root));
		assertThrows(IOException.class, () -> SandboxPaths.verify(link.resolve("existing.jar"), root));
		assertThrows(IOException.class, () -> SandboxPaths.verify(link.resolve("new/payload.jar"), root));
	}
	@Test void linkedModsDirectoryCannotBeAccessedByItsTarget() throws Exception {
		var root = Files.createDirectory(directory.resolve("game"));
		var target = Files.createDirectory(root.resolve("mod-storage"));
		createDirectoryLink(root.resolve("mods"), target);
		assertThrows(IOException.class, () -> SandboxPaths.verify(target, root));
		assertThrows(IOException.class, () -> SandboxPaths.verify(target.resolve("new/payload.jar"), root));
		assertThrows(IOException.class, () -> SandboxPaths.verify(Path.of("mods/payload.jar"), root));
	}
	@Test void existingLinksAndNonexistentDescendantsCannotEscape() throws Exception {
		var root = Files.createDirectory(directory.resolve("game"));
		var outside = Files.createDirectory(directory.resolve("outside"));
		var link = root.resolve("link");
		createDirectoryLink(link, outside);
		assertThrows(IOException.class, () -> SandboxPaths.verify(link, root));
		assertThrows(IOException.class, () -> SandboxPaths.verify(link.resolve("new/file"), root));
	}
	private static void createDirectoryLink(Path link, Path target) throws Exception {
		try { Files.createSymbolicLink(link, target); }
		catch (IOException | UnsupportedOperationException e) {
			assumeTrue(System.getProperty("os.name").startsWith("Windows"), "Symbolic links unavailable: " + e);
			var junction = new ProcessBuilder("cmd.exe", "/c", "mklink", "/J", link.toString(), target.toString()).redirectErrorStream(true).start();
			assumeTrue(junction.waitFor(10, TimeUnit.SECONDS) && junction.exitValue() == 0, "Junction creation unavailable");
		}
	}
}
