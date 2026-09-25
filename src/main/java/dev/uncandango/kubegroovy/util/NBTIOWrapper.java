package dev.uncandango.kubegroovy.util;

import dev.uncandango.kubegroovy.plugin.builtin.wrapper.MiscWrappers;
import dev.uncandango.kubegroovy.plugin.builtin.wrapper.NBTWrapper;
import dev.uncandango.kubegroovy.script.bridge.Context;
import dev.uncandango.kubegroovy.script.bridge.ContextFactory;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public interface NBTIOWrapper {
	@Nullable
	static CompoundTag read(Path path) throws IOException {
		if (!Files.isRegularFile(path)) {
			return null;
		}

		return NbtIo.readCompressed(Files.newInputStream(path), NbtAccounter.unlimitedHeap());
	}

	/** Script-safe overload: accepts a game-relative string and validates it. */
	static CompoundTag read(Object path) throws IOException {
		var cx = Context.getCurrent();
		return read(MiscWrappers.wrapPath(path));
	}

	static void write(Path path, CompoundTag nbt) throws IOException {
		if (nbt == null) {
			Files.deleteIfExists(path);
			return;
		}

		NbtIo.writeCompressed(nbt, Files.newOutputStream(path));
	}

	/** Script-safe overload: converts Groovy maps and validates the path. */
	static void write(Object path, Object nbt) throws IOException {
		write(MiscWrappers.wrapPath(path), NBTWrapper.wrapCompound(nbt));
	}
}
