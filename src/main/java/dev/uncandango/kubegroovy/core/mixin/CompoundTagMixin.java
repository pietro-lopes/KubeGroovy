package dev.uncandango.kubegroovy.core.mixin;

import dev.uncandango.kubegroovy.core.NativeCompoundTag;
import dev.uncandango.kubegroovy.script.bridge.Context;
import dev.uncandango.kubegroovy.script.bridge.Scriptable;
import dev.uncandango.kubegroovy.script.type.TypeInfo;
import dev.uncandango.kubegroovy.script.annotations.CustomJavaToGyWrapper;
import dev.uncandango.kubegroovy.script.annotations.HideFromGY;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.Map;

@Mixin(CompoundTag.class)
public abstract class CompoundTagMixin implements CustomJavaToGyWrapper {
	@Unique
	private static final TypeInfo KGY$MAP_TYPE = TypeInfo.RAW_MAP.withParams(TypeInfo.STRING, TypeInfo.of(Tag.class));

	@Shadow
	@Final
	public Map<String, Tag> tags;

	@Override
	public Scriptable convertJavaToGy(Context cx, Scriptable scope, TypeInfo target) {
		return new NativeCompoundTag(cx, scope, this, tags, KGY$MAP_TYPE);
	}

	@Shadow
	@HideFromGY
	public abstract void putByteArray(String key, byte[] value);

	@Shadow
	@HideFromGY
	public abstract void putIntArray(String key, int[] value);

	@Shadow
	@HideFromGY
	public abstract void putLongArray(String key, long[] value);
}
