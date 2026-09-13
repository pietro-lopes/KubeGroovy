package dev.uncandango.kubegroovy.item.creativetab;

import dev.uncandango.kubegroovy.registry.BuilderBase;
import dev.uncandango.kubegroovy.script.annotations.ReturnsSelf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import org.jspecify.annotations.Nullable;

@ReturnsSelf
public class CreativeTabBuilder extends BuilderBase<CreativeModeTab> {
	public transient CreativeTabIconSupplier icon;
	public transient @Nullable CreativeTabContentSupplier content;

	public CreativeTabBuilder(Identifier i) {
		super(i);
		this.icon = CreativeTabIconSupplier.DEFAULT;
		this.content = null;
	}

	@Override
	public CreativeModeTab createObject() {
		return CreativeModeTab.builder()
			.title(displayName == null ? Component.translatable(getBuilderTranslationKey()) : displayName)
			.icon(new CreativeTabIconSupplier.Wrapper(icon))
			.displayItems(new CreativeTabContentSupplier.Wrapper(content))
			.build();
	}

	public CreativeTabBuilder icon(CreativeTabIconSupplier icon) {
		this.icon = icon;
		return this;
	}

	public CreativeTabBuilder content(CreativeTabContentSupplier content) {
		this.content = content;
		return this;
	}
}
