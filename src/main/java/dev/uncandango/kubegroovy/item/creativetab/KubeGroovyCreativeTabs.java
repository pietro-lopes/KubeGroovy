package dev.uncandango.kubegroovy.item.creativetab;

import dev.uncandango.kubegroovy.CommonProperties;
import dev.uncandango.kubegroovy.KubeGroovy;
import dev.uncandango.kubegroovy.registry.RegistryObjectStorage;
import dev.uncandango.kubegroovy.util.RegistryAccessContainer;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public interface KubeGroovyCreativeTabs {
	DeferredRegister<CreativeModeTab> REGISTRY = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, KubeGroovy.MOD_ID);

	Supplier<CreativeModeTab> TAB = REGISTRY.register("tab", () -> CreativeModeTab.builder()
		.title(CommonProperties.get().getCreativeModeTabName())
		.icon(() -> {
			var is = ItemStack.OPTIONAL_CODEC.parse(RegistryAccessContainer.BUILTIN.json(), CommonProperties.get().creativeModeTabIcon).result().orElse(ItemStack.EMPTY);
			return is.isEmpty() ? Items.PURPLE_DYE.getDefaultInstance() : is;
		})
		.displayItems((params, output) -> {
			for (var b : RegistryObjectStorage.ITEM) {
				output.accept(b.get().getDefaultInstance());
			}
		})
		.build()
	);
}
