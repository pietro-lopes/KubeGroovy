package dev.uncandango.kubegroovy.item;

import dev.uncandango.kubegroovy.script.bridge.Context;
import dev.uncandango.kubegroovy.script.type.TypeInfo;
import net.minecraft.core.Holder;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.Map;

public class ItemEnchantmentsWrapper {
	public static final TypeInfo MAP_TYPE = TypeInfo.RAW_MAP.withParams(TypeInfo.of(Holder.class).withParams(TypeInfo.of(Enchantment.class)), TypeInfo.INT);

	public static ItemEnchantments wrap(Object from) {
		var cx = Context.getCurrent();
		if (from instanceof ItemEnchantments e) {
			return e;
		} else if (from instanceof Map<?, ?> ) {
			var map = (Map<Holder<Enchantment>, Integer>) cx.gyToJava(from, MAP_TYPE);
			var mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);

			for (var entry : map.entrySet()) {
				mutable.upgrade(entry.getKey(), entry.getValue());
			}

			return mutable.toImmutable();
		}

		return ItemEnchantments.EMPTY;
	}
}
