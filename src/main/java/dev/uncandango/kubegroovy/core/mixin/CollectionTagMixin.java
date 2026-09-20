//package dev.uncandango.kubegroovy.core.mixin;
//
//import dev.uncandango.kubegroovy.script.bridge.Context;
//import dev.uncandango.kubegroovy.script.bridge.NativeJavaList;
//import dev.uncandango.kubegroovy.script.bridge.Scriptable;
//import dev.uncandango.kubegroovy.script.type.TypeInfo;
//import dev.uncandango.kubegroovy.script.annotations.CustomJavaToGyWrapper;
//import net.minecraft.nbt.CollectionTag;
//import net.minecraft.nbt.Tag;
//import org.spongepowered.asm.mixin.Mixin;
//
//import java.util.List;
//
//@Mixin(CollectionTag.class)
//public interface CollectionTagMixin extends CustomJavaToGyWrapper {
//	@Override
//	default Scriptable convertJavaToGy(Context cx, Scriptable scope, TypeInfo target) {
//		return new NativeJavaList(cx, scope, this, (List) this, TypeInfo.RAW_LIST.withParams(TypeInfo.of(Tag.class)));
//	}
//}
