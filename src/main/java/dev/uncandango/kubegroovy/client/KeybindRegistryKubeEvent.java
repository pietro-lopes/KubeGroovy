package dev.uncandango.kubegroovy.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.uncandango.kubegroovy.plugin.builtin.wrapper.GLFWInputWrapper;
import dev.uncandango.kubegroovy.script.annotations.HideFromGY;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;

import java.util.ArrayList;
import java.util.List;

public class KeybindRegistryKubeEvent implements ClientKubeEvent {
	private final List<Builder> builders = new ArrayList<>();

	public Builder register(String id) {
		var builder = new Builder(id);
		builders.add(builder);
		return builder;
	}

	public Builder register(String id, String defaultKey) {
		return register(id).defaultKey(defaultKey);
	}

	@HideFromGY
	public List<KubeGroovyKeybinds.KubeKey> build() {
		return builders.stream().map(Builder::create).toList();
	}

	@HideFromGY
	public List<Identifier> categories() {
		var out = new ArrayList<Identifier>();
		for (var b : builders) {
			out.add(b.categoryId);
		}
		return out;
	}

	public static class Builder {
		private final String id;
		private KeyConflictContext keyConflictContext = KeyConflictContext.UNIVERSAL;
		private KeyModifier modifier = KeyModifier.NONE;
		private InputConstants.Type inputType = InputConstants.Type.KEYSYM;
		private int defaultKey = -1;
		private Identifier categoryId;

		private Builder(String id) {
			this.id = id;
			this.categoryId = Identifier.fromNamespaceAndPath("kubegroovy", "KubeGroovy");
		}

		public Builder conflictContext(KeyConflictContext keyConflictContext) {
			this.keyConflictContext = keyConflictContext;
			return this;
		}

		public Builder gui() {
			return conflictContext(KeyConflictContext.GUI);
		}

		public Builder inGame() {
			return conflictContext(KeyConflictContext.IN_GAME);
		}

		public Builder modifier(KeyModifier modifier) {
			this.modifier = modifier;
			return this;
		}

		public Builder inputType(InputConstants.Type inputType) {
			this.inputType = inputType;
			return this;
		}

		public Builder scanCodeInputType() {
			return inputType(InputConstants.Type.SCANCODE);
		}

		public Builder mouseInputType() {
			return inputType(InputConstants.Type.MOUSE);
		}

		public Builder defaultKey(String keyName) {
			this.defaultKey = GLFWInputWrapper.get(keyName);
			return this;
		}

		public Builder category(String category) {
			this.categoryId = Identifier.fromNamespaceAndPath("kubegroovy", category);
			return this;
		}

		@HideFromGY
		public KubeGroovyKeybinds.KubeKey create() {
			var key = KubeGroovyKeybinds.getOrCreate(id);
			key.mapping = new KeyMapping(
				"key.kubegroovy.%s".formatted(id),
				keyConflictContext,
				modifier,
				inputType,
				defaultKey,
				new KeyMapping.Category(categoryId)
			);
			return key;
		}
	}
}
