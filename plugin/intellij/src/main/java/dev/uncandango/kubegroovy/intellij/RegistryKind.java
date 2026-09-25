package dev.uncandango.kubegroovy.intellij;

import java.util.List;

record RegistryKind(String key, String elementType) {
	static final List<RegistryKind> PROTOTYPE_KINDS = List.of(
		new RegistryKind("item", "net.minecraft.world.item.Item"),
		new RegistryKind("block", "net.minecraft.world.level.block.Block"),
		new RegistryKind("fluid", "net.minecraft.world.level.material.Fluid")
	);

	String shortElementType() {
		var lastDot = elementType.lastIndexOf('.');
		return lastDot < 0 ? elementType : elementType.substring(lastDot + 1);
	}

	static RegistryKind forKey(String key) {
		return PROTOTYPE_KINDS.stream().filter(kind -> kind.key().equals(key)).findFirst().orElse(null);
	}

	static RegistryKind forElementType(String elementType) {
		return PROTOTYPE_KINDS.stream().filter(kind -> kind.elementType().equals(elementType)).findFirst().orElse(null);
	}
}
