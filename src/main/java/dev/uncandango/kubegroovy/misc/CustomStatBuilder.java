package dev.uncandango.kubegroovy.misc;

import dev.uncandango.kubegroovy.registry.BuilderBase;
import net.minecraft.resources.Identifier;

public class CustomStatBuilder extends BuilderBase<Identifier> {
	public CustomStatBuilder(Identifier i) {
		super(i);
	}

	@Override
	public Identifier createObject() {
		return id;
	}
}
