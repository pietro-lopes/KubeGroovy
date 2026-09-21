package dev.uncandango.kubegroovy.testmod.builder;

import dev.uncandango.kubegroovy.block.DetectorBlock;
import dev.uncandango.kubegroovy.block.custom.CardinalBlockBuilder;
import dev.uncandango.kubegroovy.block.custom.PillarBlockBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.decoration.painting.PaintingVariant;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.SmithingTemplateItem;
import net.minecraft.world.item.component.Weapon;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.material.FlowingFluid;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.common.BooleanAttribute;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.testframework.DynamicTest;
import net.neoforged.testframework.annotation.ForEachTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.GameTest;
import net.neoforged.testframework.impl.test.MethodBasedEventTest;

import java.util.List;

import static dev.uncandango.kubegroovy.testmod.GameAsserts.assertj;
import static net.minecraft.world.item.CreativeModeTabs.tryRebuildTabContents;
import static org.assertj.core.api.Assertions.assertThat;

/// Integration for the registry builder examples in `wiki_builders.groovy`. Each active builder family is
/// exercised through the public Minecraft or NeoForge registry and representative configured values.
@ForEachTest(groups = "kubegroovy.builder")
public class RegistryBuilderTests {
	private static final Ingredient TAB_CONTENT = Ingredient.of(Items.DIRT, Items.GRASS_BLOCK);

	private static Identifier id(final String path) {
		return Identifier.fromNamespaceAndPath("kubegroovy", path);
	}

	private static <T> T registryValue(final GameTestHelper helper, final ResourceKey<Registry<T>> registry, final Identifier id) {
		return helper.getLevel().registryAccess().lookupOrThrow(registry)
			.get(ResourceKey.create(registry, id))
			.orElseThrow(() -> new IllegalStateException("Missing registry value " + id + " in " + registry.registry()))
			.value();
	}

	private static ItemStack stack(final String path) {
		return new ItemStack(BuiltInRegistries.ITEM.getValue(id(path)));
	}

	@GameTest
	@EmptyTemplate
	@TestHolder(value = "wiki_registry_builders", description = "The wiki registry examples register an item, block, creative tab and mob effect")
	static void wikiRegistryBuilders(final DynamicTest test) {
		test.onGameTest(helper -> {
			var itemId = Identifier.parse("kubegroovy:wiki_item");
			var glowId = Identifier.parse("kubegroovy:wiki_item_glow");
			var blockId = Identifier.parse("kubegroovy:wiki_block");
			var tabId = Identifier.parse("kubegroovy:wiki_tab");
			var effectId = Identifier.parse("kubegroovy:wiki_effect");

			var glowStack = new ItemStack(BuiltInRegistries.ITEM.getValue(glowId));
			var block = BuiltInRegistries.BLOCK.getValue(blockId);
			var mobEffects = helper.getLevel().registryAccess().lookupOrThrow(Registries.MOB_EFFECT);

			assertj(helper, () -> {
				assertThat(BuiltInRegistries.ITEM.containsKey(itemId)).as("wiki_item registered").isTrue();
				assertThat(glowStack.getMaxStackSize()).as("wiki_item_glow max stack size").isEqualTo(16);
				assertThat(BuiltInRegistries.BLOCK.containsKey(blockId)).as("wiki_block registered").isTrue();
				assertThat(block.defaultBlockState().is(BlockTags.MINEABLE_WITH_PICKAXE)).as("wiki_block mineable with pickaxe").isTrue();
				var creativeTab = assertThat(BuiltInRegistries.CREATIVE_MODE_TAB.get(tabId).get().value()).as("wiki_tab registered");
				creativeTab.as("carrot as icon").extracting(CreativeModeTab::getIconItem).matches(icon -> icon.kgy$areItemsEqual(Items.CARROT.getDefaultInstance()), "carrot as icon");
				tryRebuildTabContents(FeatureFlagSet.of(),true, helper.getLevel().registryAccess());
				assertThat(mobEffects.get(ResourceKey.create(Registries.MOB_EFFECT, effectId)).isPresent()).as("wiki_effect registered").isTrue();
			});

			helper.succeed();
		});
	}

	@GameTest
	@EmptyTemplate
	@TestHolder(value = "wiki_block_builder_types", description = "The wiki fixture exercises every active custom block builder type")
	static void wikiBlockBuilderTypes(final DynamicTest test) {
		test.onGameTest(helper -> {
			assertj(helper, () -> {
				assertThat(BuiltInRegistries.BLOCK.getValue(id("wiki_detector"))).as("detector builder").isInstanceOf(DetectorBlock.class);
				assertThat(BuiltInRegistries.BLOCK.getValue(id("wiki_slab"))).as("slab builder").isInstanceOf(SlabBlock.class);
				assertThat(BuiltInRegistries.BLOCK.getValue(id("wiki_stairs"))).as("stairs builder").isInstanceOf(StairBlock.class);
				assertThat(BuiltInRegistries.BLOCK.getValue(id("wiki_fence"))).as("fence builder").isInstanceOf(FenceBlock.class);
				assertThat(BuiltInRegistries.BLOCK.getValue(id("wiki_wall"))).as("wall builder").isInstanceOf(WallBlock.class);
				assertThat(BuiltInRegistries.BLOCK.getValue(id("wiki_fence_gate"))).as("fence gate builder").isInstanceOf(FenceGateBlock.class);
				assertThat(BuiltInRegistries.BLOCK.getValue(id("wiki_pressure_plate"))).as("pressure plate builder").isInstanceOf(PressurePlateBlock.class);
				assertThat(BuiltInRegistries.BLOCK.getValue(id("wiki_button"))).as("button builder").isInstanceOf(ButtonBlock.class);
				assertThat(BuiltInRegistries.BLOCK.getValue(id("wiki_falling"))).as("falling builder").isInstanceOf(FallingBlock.class);
				assertThat(BuiltInRegistries.BLOCK.getValue(id("wiki_crop"))).as("crop builder").isInstanceOf(CropBlock.class);
				assertThat(BuiltInRegistries.BLOCK.getValue(id("wiki_cardinal"))).as("cardinal builder").isInstanceOf(CardinalBlockBuilder.CardinalKubeBlock.class);
				assertThat(BuiltInRegistries.BLOCK.getValue(id("wiki_carpet"))).as("carpet builder").isInstanceOf(CarpetBlock.class);
				assertThat(BuiltInRegistries.BLOCK.getValue(id("wiki_door"))).as("door builder").isInstanceOf(DoorBlock.class);
				assertThat(BuiltInRegistries.BLOCK.getValue(id("wiki_trapdoor"))).as("trapdoor builder").isInstanceOf(TrapDoorBlock.class);
				assertThat(BuiltInRegistries.BLOCK.getValue(id("wiki_pillar"))).as("pillar builder").isInstanceOf(PillarBlockBuilder.PillarKubeBlock.class);

				var crop = (CropBlock) BuiltInRegistries.BLOCK.getValue(id("wiki_crop"));
				assertThat(crop.getMaxAge()).as("wiki_crop age").isEqualTo(3);
				assertThat(BuiltInRegistries.ITEM.containsKey(id("wiki_crop_seeds"))).as("wiki_crop seed item").isTrue();
				assertThat(BuiltInRegistries.BLOCK.getValue(id("wiki_cardinal")).defaultBlockState().hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING))
					.as("cardinal facing property").isTrue();
				assertThat(BuiltInRegistries.BLOCK.getValue(id("wiki_pillar")).defaultBlockState().hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS))
					.as("pillar axis property").isTrue();
				assertThat(BuiltInRegistries.BLOCK.getValue(id("wiki_block")).defaultBlockState().is(BlockTags.MINEABLE_WITH_PICKAXE))
					.as("base block tag").isTrue();
			});
			helper.succeed();
		});
	}

	@GameTest
	@EmptyTemplate
	@TestHolder(value = "wiki_item_builder_types", description = "The wiki fixture exercises every active custom item builder type")
	static void wikiItemBuilderTypes(final DynamicTest test) {
		test.onGameTest(helper -> {
			assertj(helper, () -> {
				for (var path : List.of("wiki_sword", "wiki_pickaxe", "wiki_axe", "wiki_shovel", "wiki_hoe", "wiki_shears",
					"wiki_helmet", "wiki_chestplate", "wiki_leggings", "wiki_boots", "wiki_animal_armor", "wiki_smithing_template")) {
					assertThat(BuiltInRegistries.ITEM.containsKey(id(path))).as(path + " registered").isTrue();
				}
				var swordWeaponComp = assertThat(stack("wiki_sword").get(DataComponents.WEAPON));
				swordWeaponComp.as("sword weapon damage per attack").extracting(Weapon::itemDamagePerAttack).isEqualTo(4);
				swordWeaponComp.as("sword weapon blocking cooldown").extracting(Weapon::disableBlockingForSeconds).isEqualTo(1f);

				var swordAttributesMod = assertThat(stack("wiki_sword").get(DataComponents.ATTRIBUTE_MODIFIERS));
				swordAttributesMod.as("sword attack damage").extracting(mod -> mod.compute(Attributes.ATTACK_DAMAGE,0, EquipmentSlot.MAINHAND)).isNotEqualTo(0d);
				swordAttributesMod.as("sword attack speed").extracting(mod -> mod.compute(Attributes.ATTACK_SPEED,0, EquipmentSlot.MAINHAND)).isNotEqualTo(0d);
				assertThat(stack("wiki_pickaxe").get(DataComponents.TOOL)).as("pickaxe tool component").isNotNull();
				assertThat(BuiltInRegistries.ITEM.getValue(id("wiki_shears"))).as("shears runtime type").isInstanceOf(ShearsItem.class);
				assertThat(stack("wiki_helmet").get(DataComponents.EQUIPPABLE)).as("helmet equipment component").isNotNull();
				assertThat(BuiltInRegistries.ITEM.getValue(id("wiki_smithing_template"))).as("smithing template runtime type").isInstanceOf(SmithingTemplateItem.class);
				assertThat(stack("wiki_item_glow").getMaxStackSize()).as("base item max stack size").isEqualTo(16);
			});
			helper.succeed();
		});
	}

	@GameTest
	@EmptyTemplate
	@TestHolder(value = "wiki_fluid_builder_types", description = "The wiki fixture exercises fluid, fluid type, and companion object builders")
	static void wikiFluidBuilderTypes(final DynamicTest test) {
		test.onGameTest(helper -> {
			assertj(helper, () -> {
				for (var path : List.of("wiki_fluid", "wiki_thin_fluid", "wiki_thick_fluid")) {
					assertThat(BuiltInRegistries.FLUID.containsKey(id(path))).as(path + " source fluid").isTrue();
					assertThat(BuiltInRegistries.FLUID.containsKey(id("flowing_" + path))).as(path + " flowing fluid").isTrue();
					assertThat(NeoForgeRegistries.FLUID_TYPES.containsKey(id(path))).as(path + " fluid type").isTrue();
				}

				assertThat(BuiltInRegistries.FLUID.getValue(id("wiki_fluid"))).as("fluid source runtime type").isInstanceOf(FlowingFluid.class);
				assertThat(BuiltInRegistries.FLUID.getValue(id("flowing_wiki_fluid"))).as("fluid flowing runtime type").isInstanceOf(FlowingFluid.class);
				assertThat(BuiltInRegistries.BLOCK.getValue(id("wiki_fluid"))).as("fluid block").isInstanceOf(LiquidBlock.class);
				assertThat(BuiltInRegistries.ITEM.getValue(id("wiki_fluid_bucket"))).as("fluid bucket").isInstanceOf(BucketItem.class);
				assertThat(BuiltInRegistries.BLOCK.containsKey(id("wiki_thin_fluid"))).as("thin fluid block").isTrue();
				assertThat(BuiltInRegistries.ITEM.containsKey(id("wiki_thin_fluid_bucket"))).as("thin fluid has no bucket").isFalse();
				assertThat(BuiltInRegistries.BLOCK.containsKey(id("wiki_thick_fluid"))).as("thick fluid has no block").isFalse();
				assertThat(BuiltInRegistries.ITEM.getValue(id("wiki_thick_fluid_bucket"))).as("thick fluid bucket").isInstanceOf(BucketItem.class);

				var fluidType = NeoForgeRegistries.FLUID_TYPES.getValue(id("wiki_fluid_type"));
				assertThat(fluidType).as("standalone fluid type").isInstanceOf(FluidType.class);
				assertThat(fluidType.getDensity()).as("fluid type density").isEqualTo(1200);
				assertThat(fluidType.getViscosity()).as("fluid type viscosity").isEqualTo(1000);
				assertThat(fluidType.getTemperature()).as("fluid type temperature").isEqualTo(300);
			});
			helper.succeed();
		});
	}

	@GameTest
	@EmptyTemplate
	@TestHolder(value = "wiki_misc_builder_types", description = "The wiki fixture exercises the remaining active registry builder types")
	static void wikiMiscBuilderTypes(final DynamicTest test) {
		test.onGameTest(helper -> {
			assertj(helper, () -> {
				assertThat(registryValue(helper, Registries.SOUND_EVENT, id("wiki_sound"))).as("sound event").isNotNull();
				assertThat(registryValue(helper, Registries.POTION, id("wiki_potion"))).as("potion").isNotNull();
				assertThat(registryValue(helper, Registries.PARTICLE_TYPE, id("wiki_particle"))).as("particle type").isNotNull();
				assertThat(registryValue(helper, Registries.CUSTOM_STAT, id("wiki_stat"))).as("custom stat").isEqualTo(id("wiki_stat"));

				var poi = registryValue(helper, Registries.POINT_OF_INTEREST_TYPE, id("wiki_poi"));
				assertThat(poi).as("poi type").isInstanceOf(PoiType.class);
				assertThat(poi.maxTickets()).as("poi max tickets").isEqualTo(2);
				assertThat(poi.validRange()).as("poi valid range").isEqualTo(4);
				assertThat(registryValue(helper, Registries.VILLAGER_TYPE, id("wiki_villager_type"))).as("villager type").isNotNull();
				assertThat(registryValue(helper, Registries.VILLAGER_PROFESSION, id("wiki_profession"))).as("villager profession").isNotNull();

				var painting = registryValue(helper, Registries.PAINTING_VARIANT, id("wiki_painting"));
				assertThat(painting).as("painting variant").isInstanceOf(PaintingVariant.class);
				assertThat(painting.width()).as("painting width").isEqualTo(2);
				assertThat(painting.height()).as("painting height").isEqualTo(3);

				var song = registryValue(helper, Registries.JUKEBOX_SONG, id("wiki_song"));
				assertThat(song).as("jukebox song").isInstanceOf(JukeboxSong.class);
				assertThat(song.lengthInSeconds()).as("song length").isEqualTo(120F);
				assertThat(song.comparatorOutput()).as("song comparator output").isEqualTo(7);

				var rangedAttribute = registryValue(helper, Registries.ATTRIBUTE, id("wiki_range_attribute"));
				assertThat(rangedAttribute).as("ranged attribute").isInstanceOf(RangedAttribute.class);
				assertThat(rangedAttribute.getDefaultValue()).as("ranged attribute default").isEqualTo(4.0);
				assertThat(rangedAttribute.isClientSyncable()).as("ranged attribute syncable").isFalse();
				assertThat(registryValue(helper, Registries.ATTRIBUTE, id("wiki_boolean_attribute"))).as("boolean attribute").isInstanceOf(BooleanAttribute.class);
			});
			helper.succeed();
		});
	}

	@GameTest
	@EmptyTemplate
	@TestHolder(value = "wiki_creative_tab", description = "Assert the wiki registry examples register an item, block, creative tab and mob effect", enabledByDefault = true)
	static void wikiCreativeTab(final BuildCreativeModeTabContentsEvent event, final MethodBasedEventTest test) {
		if (event.getTabKey().identifier().equals(Identifier.parse("kubegroovy:wiki_tab"))){
			assertj(() -> {
				assertThat(event.getParentEntries().stream()).allMatch(TAB_CONTENT, "dirt and grass block are present");
			});
			test.pass();
		}
	}
}
