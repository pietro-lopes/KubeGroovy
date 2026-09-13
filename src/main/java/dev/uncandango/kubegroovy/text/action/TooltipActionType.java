package dev.uncandango.kubegroovy.text.action;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record TooltipActionType<T extends TextAction>(int type, StreamCodec<? super RegistryFriendlyByteBuf, ? extends T> streamCodec) {
}
