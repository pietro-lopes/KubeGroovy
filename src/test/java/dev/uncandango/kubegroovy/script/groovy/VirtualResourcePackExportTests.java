package dev.uncandango.kubegroovy.groovytest;

import dev.uncandango.kubegroovy.script.ScriptType;
import dev.uncandango.kubegroovy.script.data.GeneratedDataStage;
import dev.uncandango.kubegroovy.script.data.VirtualResourcePack;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VirtualResourcePackExportTests {
	@TempDir Path directory;

	private static VirtualResourcePack pack(String path) {
		var pack = new VirtualResourcePack(ScriptType.SERVER, PackType.SERVER_DATA, GeneratedDataStage.LAST, () -> null);
		pack.text(Identifier.fromNamespaceAndPath("a", path), "marker");
		return pack;
	}

	@Test void traversalCannotCreateOrOverwriteOutsideExportRoot() throws Exception {
		var root = directory.resolve("export");
		var outside = directory.resolve("outside-marker.txt");
		assertThrows(IOException.class, () -> pack("../../../outside-marker.txt").export(root));
		assertFalse(Files.exists(outside));
		assertFalse(Files.exists(root));
		Files.writeString(outside, "original");
		assertThrows(IOException.class, () -> pack("../../../outside-marker.txt").export(root));
		assertEquals("original", Files.readString(outside));
	}

	@Test void normalExportCreatesDirectoriesAndOverwritesExistingFiles() throws Exception {
		var root = directory.resolve("export");
		var pack = pack("nested/file.txt");
		pack.export(root);
		var target = root.resolve("data/a/nested/file.txt");
		assertEquals("marker", Files.readString(target));
		Files.writeString(target, "longer existing content");
		pack.export(root);
		assertEquals("marker", Files.readString(target));
	}

	@Test void zipExportsRemainConfinedAndSupported() throws Exception {
		var uri = URI.create("jar:" + directory.resolve("export.zip").toUri());
		try (var zip = FileSystems.newFileSystem(uri, Map.of("create", "true"))) {
			pack("nested/file.txt").export(zip.getPath("."));
			assertEquals("marker", Files.readString(zip.getPath("data/a/nested/file.txt")));
			assertThrows(IOException.class, () -> pack("../../../../outside-marker.txt").export(zip.getPath("export")));
		}
	}
}
