package dev.uncandango.kubegroovy.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.uncandango.kubegroovy.KubeGroovy;
import dev.uncandango.kubegroovy.plugin.builtin.wrapper.GLFWInputWrapper;
import dev.uncandango.kubegroovy.script.annotations.HideFromGY;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class KeybindRegistryKubeEvent implements ClientKubeEvent {
	private final List<Builder> builders = new ArrayList<>();
	@HideFromGY
	public final transient Map<Identifier, KeyMapping.Category> categories;
	@HideFromGY
	public final transient KeyMapping.Category mainCategory;

	public KeybindRegistryKubeEvent() {
		categories = new LinkedHashMap<>();
		mainCategory = categories.computeIfAbsent(KubeGroovy.id("kubegroovy"), KeyMapping.Category::new);
	}

	public Builder register(String id) {
		var builder = new Builder(this, id);
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
			out.add(b.category.id());
		}
		return out.stream().distinct().toList();
	}

	public static class Builder {
		private final KeybindRegistryKubeEvent event;
		private final String id;
		private KeyConflictContext keyConflictContext = KeyConflictContext.UNIVERSAL;
		private KeyModifier modifier = KeyModifier.NONE;
		private InputConstants.Type inputType = InputConstants.Type.KEYSYM;
		private int defaultKey = -1;
		private KeyMapping.Category category;

		private Builder(KeybindRegistryKubeEvent event, String id) {
			this.event = event;
			this.id = id;
			this.category = event.mainCategory;
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
			this.category = event.categories.computeIfAbsent(KubeGroovy.id(category), KeyMapping.Category::new);
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
				category
			);
			return key;
		}
	}
}
