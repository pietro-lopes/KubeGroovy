package dev.uncandango.kubegroovy.plugin.builtin.wrapper;

import dev.uncandango.kubegroovy.script.bridge.Wrapper;
import dev.uncandango.kubegroovy.script.type.TypeInfo;
import net.minecraft.world.item.ItemStackTemplate;
import org.jspecify.annotations.Nullable;

public interface ItemStackTemplateWrapper {
	TypeInfo TYPE_INFO = TypeInfo.of(ItemStackTemplate.class);

	static ItemStackTemplate wrap(@Nullable Object from) {
		from = Wrapper.unwrapped(from);
		return from instanceof ItemStackTemplate template ? template : ItemStackTemplate.fromNonEmptyStack(ItemWrapper.wrap(from));
	}
}
