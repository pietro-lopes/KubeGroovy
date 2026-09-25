package dev.uncandango.kubegroovy.core.mixin;

import dev.uncandango.kubegroovy.script.bridge.Context;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import dev.uncandango.kubegroovy.script.annotations.SpecialEquality;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static dev.uncandango.kubegroovy.registry.RegistryType.Scanner;

@RemapPrefixForGY("kgy$")
@Mixin(value = ResourceKey.class, priority = 1001)
public abstract class ResourceKeyMixin implements SpecialEquality {
	@Shadow
	@Final
	private Identifier identifier;

	@Unique
	public String kgy$getNamespace() {
		return identifier.getNamespace();
	}

	@Unique
	public String kgy$getPath() {
		return identifier.getPath();
	}

	@Inject(method = "<init>", at = @At(value = "RETURN"))
	private void kgy$getKeyStackTraces(Identifier registryName, Identifier location, CallbackInfo ci) {
		Scanner.scan(registryName, location);
	}

	@Override
	public boolean specialEquals(@Nullable Object o, boolean shallow) {
		var cx = Context.getCurrent();
		return switch (o) {
			case null -> false;
			case ResourceKey<?> _key -> o == this;
			case Identifier id -> identifier.equals(id);
			default -> identifier.toString().equals(o.toString());
		};
	}
}
