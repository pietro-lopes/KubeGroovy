package dev.uncandango.kubegroovy.core.mixin;

import dev.uncandango.kubegroovy.script.annotations.RemapForGY;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = Direction.class, priority = 1001)
public abstract class DirectionMixin {
	@Shadow
	@RemapForGY("getX")
	public abstract int getStepX();

	@Shadow
	@RemapForGY("getY")
	public abstract int getStepY();

	@Shadow
	@RemapForGY("getZ")
	public abstract int getStepZ();

	@Shadow
	@RemapForGY("getIndex")
	public abstract int get3DDataValue();

	@Shadow
	@RemapForGY("getHorizontalIndex")
	public abstract int get2DDataValue();

	@Shadow
	@RemapForGY("getYaw")
	public abstract float toYRot();

	@Unique
	@RemapForGY("getPitch")
	public float kgy$getPitch() {
		Object o = this;
		return o == Direction.UP ? 180F : o == Direction.DOWN ? 0F : 90F;
	}
}
