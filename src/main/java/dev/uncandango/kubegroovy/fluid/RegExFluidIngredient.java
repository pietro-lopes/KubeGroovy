package dev.uncandango.kubegroovy.fluid;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.uncandango.kubegroovy.util.RegExpKGY;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.FluidIngredientType;

import java.util.regex.Pattern;
import java.util.stream.Stream;

public class RegExFluidIngredient extends FluidIngredient {
	public static final MapCodec<RegExFluidIngredient> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
		RegExpKGY.CODEC.fieldOf("regex").forGetter(i -> i.pattern)
	).apply(instance, RegExFluidIngredient::new));

	public static final StreamCodec<ByteBuf, RegExFluidIngredient> STREAM_CODEC = RegExpKGY.STREAM_CODEC.map(RegExFluidIngredient::new, i -> i.pattern);

	public final Pattern pattern;
	public final String patternString;

	public RegExFluidIngredient(Pattern pattern) {
		this.pattern = pattern;
		this.patternString = RegExpKGY.toRegExpString(pattern);
	}

	@Override
	public FluidIngredientType<?> getType() {
		return KubeGroovyFluidIngredients.REGEX.get();
	}

	@Override
	public boolean test(FluidStack fs) {
		return pattern.matcher(fs.getFluid().kgy$getId()).find();
	}

	@Override
	protected Stream<Holder<Fluid>> generateFluids() {
		return BuiltInRegistries.FLUID.stream().filter(fluid -> pattern.matcher(fluid.kgy$getId()).find()).map(fluid -> Holder.direct(new FluidStack(fluid, FluidType.BUCKET_VOLUME).getFluid()));
	}

	@Override
	public boolean isSimple() {
		return true;
	}

	@Override
	public int hashCode() {
		return patternString.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		return o == this || o instanceof RegExFluidIngredient r && patternString.equals(r.patternString);
	}
}
