package dev.uncandango.kubegroovy.core;

import dev.uncandango.kubegroovy.block.BlockBuilder;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import org.jspecify.annotations.NullUnmarked;

@RemapPrefixForGY("kgy$")
public interface BlockBuilderProvider {
	@NullUnmarked
	default BlockBuilder kgy$getBlockBuilder() {
		throw new NoMixinException();
	}
}
