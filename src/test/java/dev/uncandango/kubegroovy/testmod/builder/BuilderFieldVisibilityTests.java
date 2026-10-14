package dev.uncandango.kubegroovy.testmod.builder;

import dev.uncandango.kubegroovy.block.BlockBuilder;
import dev.uncandango.kubegroovy.block.BlockItemBuilder;
import dev.uncandango.kubegroovy.block.entity.BlockEntityBuilder;
import dev.uncandango.kubegroovy.block.DetectorBlock;
import dev.uncandango.kubegroovy.block.custom.BasicKubeBlock;
import dev.uncandango.kubegroovy.block.custom.ButtonBlockBuilder;
import dev.uncandango.kubegroovy.block.custom.CardinalBlockBuilder;
import dev.uncandango.kubegroovy.block.custom.CarpetBlockBuilder;
import dev.uncandango.kubegroovy.block.custom.CropBlockBuilder;
import dev.uncandango.kubegroovy.block.custom.DoorBlockBuilder;
import dev.uncandango.kubegroovy.block.custom.FallingBlockBuilder;
import dev.uncandango.kubegroovy.block.custom.FenceBlockBuilder;
import dev.uncandango.kubegroovy.block.custom.FenceGateBlockBuilder;
import dev.uncandango.kubegroovy.block.custom.PillarBlockBuilder;
import dev.uncandango.kubegroovy.block.custom.PressurePlateBlockBuilder;
import dev.uncandango.kubegroovy.block.custom.SlabBlockBuilder;
import dev.uncandango.kubegroovy.block.custom.StairBlockBuilder;
import dev.uncandango.kubegroovy.block.custom.TrapdoorBlockBuilder;
import dev.uncandango.kubegroovy.block.custom.WallBlockBuilder;
import dev.uncandango.kubegroovy.fluid.FluidBlockBuilder;
import dev.uncandango.kubegroovy.fluid.FluidBucketItemBuilder;
import dev.uncandango.kubegroovy.fluid.FluidBuilder;
import dev.uncandango.kubegroovy.fluid.FluidTypeBuilder;
import dev.uncandango.kubegroovy.fluid.FlowingFluidBuilder;
import dev.uncandango.kubegroovy.fluid.ThickFluidBuilder;
import dev.uncandango.kubegroovy.fluid.ThinFluidBuilder;
import dev.uncandango.kubegroovy.item.FoodBuilder;
import dev.uncandango.kubegroovy.item.ItemBuilder;
import dev.uncandango.kubegroovy.block.SeedItemBuilder;
import dev.uncandango.kubegroovy.item.custom.ArmorItemBuilder;
import dev.uncandango.kubegroovy.item.custom.DiggerItemBuilder;
import dev.uncandango.kubegroovy.item.custom.ShearsItemBuilder;
import dev.uncandango.kubegroovy.item.custom.SmithingTemplateItemBuilder;
import dev.uncandango.kubegroovy.item.custom.SwordItemBuilder;
import dev.uncandango.kubegroovy.item.creativetab.CreativeTabBuilder;
import dev.uncandango.kubegroovy.entity.AttributeBuilder;
import dev.uncandango.kubegroovy.item.JukeboxSongBuilder;
import dev.uncandango.kubegroovy.misc.MobEffectBuilder;
import dev.uncandango.kubegroovy.misc.PaintingVariantBuilder;
import dev.uncandango.kubegroovy.misc.ParticleTypeBuilder;
import dev.uncandango.kubegroovy.misc.PoiTypeBuilder;
import dev.uncandango.kubegroovy.misc.PotionBuilder;
import dev.uncandango.kubegroovy.misc.SoundEventBuilder;
import dev.uncandango.kubegroovy.misc.VillagerProfessionBuilder;
import dev.uncandango.kubegroovy.registry.BuilderBase;
import dev.uncandango.kubegroovy.registry.BuilderFactory;
import dev.uncandango.kubegroovy.registry.BuilderTypeRegistry;
import dev.uncandango.kubegroovy.plugin.builtin.BuiltinKubeGroovyPlugin;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertFalse;

/** Protects the script-facing builder state contract from accidental field exposure. */
class BuilderFieldVisibilityTests {
	@Test
	void registeredBuilderHierarchiesHaveNoPublicOrTransientInstanceState() {
		var builders = registeredBuilderTypes();
		// These helpers are part of the builder DSL even though they are not registry roots.
		builders.addAll(Set.of(
			FoodBuilder.class,
			BlockItemBuilder.class,
			SeedItemBuilder.class,
			BlockEntityBuilder.class,
			FluidTypeBuilder.class,
			FluidBlockBuilder.class,
			FluidBucketItemBuilder.class,
			FlowingFluidBuilder.class
		));

		for (var builder : builders) {
			for (var current = builder; current != null && BuilderBase.class.isAssignableFrom(current); current = current.getSuperclass()) {
				for (Field field : current.getDeclaredFields()) {
					if (Modifier.isStatic(field.getModifiers())) {
						continue;
					}
					assertFalse(Modifier.isPublic(field.getModifiers()),
						builder.getName() + " exposes public instance field " + current.getName() + "." + field.getName());
					assertFalse(Modifier.isTransient(field.getModifiers()),
						builder.getName() + " retains transient field " + current.getName() + "." + field.getName());
				}
			}
		}
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static Set<Class<?>> registeredBuilderTypes() {
		var classes = new HashSet<Class<?>>();
		BuilderTypeRegistry registry = new BuilderTypeRegistry() {
			@Override
			public <T> void of(ResourceKey<? extends Registry<T>> registryKey, Consumer<Callback<T>> callback) {
				callback.accept(new Callback<>() {
					@Override
					public void addDefault(Class<? extends BuilderBase<? extends T>> builderType, BuilderFactory factory) {
						classes.add(builderType);
					}

					@Override
					public void add(Identifier type, Class<? extends BuilderBase<? extends T>> builderType, BuilderFactory factory) {
						classes.add(builderType);
					}
				});
			}
		};
		new BuiltinKubeGroovyPlugin().registerBuilderTypes(registry);
		return classes;
	}
}
