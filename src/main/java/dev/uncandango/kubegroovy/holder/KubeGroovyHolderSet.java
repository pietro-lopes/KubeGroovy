package dev.uncandango.kubegroovy.holder;

import net.minecraft.core.Holder;
import net.neoforged.neoforge.registries.holdersets.ICustomHolderSet;

import java.util.function.Function;

public interface KubeGroovyHolderSet<T> extends ICustomHolderSet<T> {
	String kgy$toIngredientString(Function<Holder<T>, String> elementCodec);
}
