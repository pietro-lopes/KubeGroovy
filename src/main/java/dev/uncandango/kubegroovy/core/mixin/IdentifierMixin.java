package dev.uncandango.kubegroovy.core.mixin;

import dev.uncandango.kubegroovy.script.bridge.Context;
import dev.uncandango.kubegroovy.script.annotations.SpecialEquality;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = Identifier.class, priority = 1001)
public abstract class IdentifierMixin implements SpecialEquality {
	@Override
	public boolean specialEquals(@Nullable Object o, boolean shallow) {
		var cx = Context.getCurrent();
		return switch (o) {
			case Identifier _id -> equals(o);
			case null, default -> toString().equals(String.valueOf(o));
		};
	}
}
