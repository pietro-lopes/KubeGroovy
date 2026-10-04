package dev.uncandango.kubegroovy.plugin.builtin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.uncandango.kubegroovy.KubeGroovy;
import dev.uncandango.kubegroovy.block.BlockTintFunction;
import dev.uncandango.kubegroovy.block.DetectorBlock;
import dev.uncandango.kubegroovy.block.MapColorHelper;
import dev.uncandango.kubegroovy.block.SoundTypeWrapper;
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
import dev.uncandango.kubegroovy.block.state.BlockStatePredicate;
import dev.uncandango.kubegroovy.client.icon.AtlasSpriteKubeIcon;
import dev.uncandango.kubegroovy.client.icon.ItemKubeIcon;
import dev.uncandango.kubegroovy.client.icon.KubeIcon;
import dev.uncandango.kubegroovy.client.icon.KubeIconTypeRegistry;
import dev.uncandango.kubegroovy.client.icon.TextureKubeIcon;
import dev.uncandango.kubegroovy.color.KubeColor;
import dev.uncandango.kubegroovy.component.DataComponentWrapper;
import dev.uncandango.kubegroovy.core.PlayerSelector;
import dev.uncandango.kubegroovy.entity.AttributeBuilder;
import dev.uncandango.kubegroovy.error.KubeRuntimeException;
import dev.uncandango.kubegroovy.event.EventGroupRegistry;
import dev.uncandango.kubegroovy.event.EventGroupWrapper;
import dev.uncandango.kubegroovy.event.EventGroups;
import dev.uncandango.kubegroovy.fluid.FluidBuilder;
import dev.uncandango.kubegroovy.fluid.FluidTypeBuilder;
import dev.uncandango.kubegroovy.fluid.FluidWrapper;
import dev.uncandango.kubegroovy.fluid.ThickFluidBuilder;
import dev.uncandango.kubegroovy.fluid.ThinFluidBuilder;
import dev.uncandango.kubegroovy.generator.KubeDataGenerator;
import dev.uncandango.kubegroovy.holder.HolderWrapper;
import dev.uncandango.kubegroovy.item.ItemBuilder;
import dev.uncandango.kubegroovy.item.ItemEnchantmentsWrapper;
import dev.uncandango.kubegroovy.item.ItemModificationKubeEvent;
import dev.uncandango.kubegroovy.item.ItemPredicate;
import dev.uncandango.kubegroovy.item.ItemTintFunction;
import dev.uncandango.kubegroovy.item.ItemToolMaterials;
import dev.uncandango.kubegroovy.item.JukeboxSongBuilder;
import dev.uncandango.kubegroovy.item.creativetab.CreativeTabBuilder;
import dev.uncandango.kubegroovy.item.custom.ArmorItemBuilder;
import dev.uncandango.kubegroovy.item.custom.DiggerItemBuilder;
import dev.uncandango.kubegroovy.item.custom.ShearsItemBuilder;
import dev.uncandango.kubegroovy.item.custom.SmithingTemplateItemBuilder;
import dev.uncandango.kubegroovy.item.custom.SwordItemBuilder;
import dev.uncandango.kubegroovy.misc.CustomStatBuilder;
import dev.uncandango.kubegroovy.misc.MobEffectBuilder;
import dev.uncandango.kubegroovy.misc.PaintingVariantBuilder;
import dev.uncandango.kubegroovy.misc.ParticleTypeBuilder;
import dev.uncandango.kubegroovy.misc.PoiTypeBuilder;
import dev.uncandango.kubegroovy.misc.PotionBuilder;
import dev.uncandango.kubegroovy.misc.SoundEventBuilder;
import dev.uncandango.kubegroovy.misc.VillagerProfessionBuilder;
import dev.uncandango.kubegroovy.misc.VillagerTypeBuilder;
import dev.uncandango.kubegroovy.player.PlayerStatsGY;
import dev.uncandango.kubegroovy.plugin.KubeGroovyPlugin;
import dev.uncandango.kubegroovy.plugin.builtin.event.BlockEvents;
import dev.uncandango.kubegroovy.plugin.builtin.event.EntityEvents;
import dev.uncandango.kubegroovy.plugin.builtin.event.ItemEvents;
import dev.uncandango.kubegroovy.plugin.builtin.event.LevelEvents;
import dev.uncandango.kubegroovy.plugin.builtin.event.NetworkEvents;
import dev.uncandango.kubegroovy.plugin.builtin.event.PlayerEvents;
import dev.uncandango.kubegroovy.plugin.builtin.event.RecipeViewerEvents;
import dev.uncandango.kubegroovy.plugin.builtin.event.ServerEvents;
import dev.uncandango.kubegroovy.plugin.builtin.event.StartupEvents;
import dev.uncandango.kubegroovy.plugin.builtin.wrapper.AABBWrapper;
import dev.uncandango.kubegroovy.plugin.builtin.wrapper.BlockWrapper;
import dev.uncandango.kubegroovy.plugin.builtin.wrapper.ColorWrapper;
import dev.uncandango.kubegroovy.plugin.builtin.wrapper.DamageSourceWrapper;
import dev.uncandango.kubegroovy.plugin.builtin.wrapper.DataMapWrapper;
import dev.uncandango.kubegroovy.plugin.builtin.wrapper.DirectionWrapper;
import dev.uncandango.kubegroovy.plugin.builtin.wrapper.EntitySelectorWrapper;
import dev.uncandango.kubegroovy.plugin.builtin.wrapper.IngredientWrapper;
import dev.uncandango.kubegroovy.plugin.builtin.wrapper.ItemStackTemplateWrapper;
import dev.uncandango.kubegroovy.plugin.builtin.wrapper.ItemWrapper;
import dev.uncandango.kubegroovy.plugin.builtin.wrapper.JavaWrapper;
import dev.uncandango.kubegroovy.plugin.builtin.wrapper.KMath;
import dev.uncandango.kubegroovy.plugin.builtin.wrapper.MiscWrappers;
import dev.uncandango.kubegroovy.plugin.builtin.wrapper.NBTWrapper;
import dev.uncandango.kubegroovy.plugin.builtin.wrapper.NativeEventWrapper;
import dev.uncandango.kubegroovy.plugin.builtin.wrapper.ParticleOptionsWrapper;
import dev.uncandango.kubegroovy.plugin.builtin.wrapper.RegistryWrapper;
import dev.uncandango.kubegroovy.plugin.builtin.wrapper.SizedIngredientWrapper;
import dev.uncandango.kubegroovy.plugin.builtin.wrapper.StringUtilsWrapper;
import dev.uncandango.kubegroovy.plugin.builtin.wrapper.TextIcons;
import dev.uncandango.kubegroovy.plugin.builtin.wrapper.TextWrapper;
import dev.uncandango.kubegroovy.plugin.builtin.wrapper.UUIDWrapper;
import dev.uncandango.kubegroovy.plugin.builtin.wrapper.UtilsWrapper;
import dev.uncandango.kubegroovy.recipe.component.BlockComponent;
import dev.uncandango.kubegroovy.recipe.component.BlockStateComponent;
import dev.uncandango.kubegroovy.recipe.component.BookCategoryComponent;
import dev.uncandango.kubegroovy.recipe.component.BooleanComponent;
import dev.uncandango.kubegroovy.recipe.component.CharacterComponent;
import dev.uncandango.kubegroovy.recipe.component.CustomObjectRecipeComponent;
import dev.uncandango.kubegroovy.recipe.component.EitherRecipeComponent;
import dev.uncandango.kubegroovy.recipe.component.EnumComponent;
import dev.uncandango.kubegroovy.recipe.component.FluidIngredientComponent;
import dev.uncandango.kubegroovy.recipe.component.FluidStackComponent;
import dev.uncandango.kubegroovy.recipe.component.IgnoreComponent;
import dev.uncandango.kubegroovy.recipe.component.IngredientComponent;
import dev.uncandango.kubegroovy.recipe.component.ItemStackComponent;
import dev.uncandango.kubegroovy.recipe.component.ItemStackTemplateComponent;
import dev.uncandango.kubegroovy.recipe.component.ListRecipeComponent;
import dev.uncandango.kubegroovy.recipe.component.MapRecipeComponent;
import dev.uncandango.kubegroovy.recipe.component.NestedRecipeComponent;
import dev.uncandango.kubegroovy.recipe.component.NumberComponent;
import dev.uncandango.kubegroovy.recipe.component.RecipeComponentTypeRegistry;
import dev.uncandango.kubegroovy.recipe.component.RegistryComponent;
import dev.uncandango.kubegroovy.recipe.component.ResourceKeyComponent;
import dev.uncandango.kubegroovy.recipe.component.SizedFluidIngredientComponent;
import dev.uncandango.kubegroovy.recipe.component.SizedIngredientComponent;
import dev.uncandango.kubegroovy.recipe.component.StringComponent;
import dev.uncandango.kubegroovy.recipe.component.TagKeyComponent;
import dev.uncandango.kubegroovy.recipe.component.TimeComponent;
import dev.uncandango.kubegroovy.recipe.filter.RecipeFilter;
import dev.uncandango.kubegroovy.recipe.ingredientaction.ConsumeAction;
import dev.uncandango.kubegroovy.recipe.ingredientaction.CustomIngredientAction;
import dev.uncandango.kubegroovy.recipe.ingredientaction.DamageAction;
import dev.uncandango.kubegroovy.recipe.ingredientaction.IngredientActionTypeRegistry;
import dev.uncandango.kubegroovy.recipe.ingredientaction.KeepAction;
import dev.uncandango.kubegroovy.recipe.ingredientaction.ReplaceAction;
import dev.uncandango.kubegroovy.recipe.match.ReplacementMatch;
import dev.uncandango.kubegroovy.recipe.match.ReplacementMatchInfo;
import dev.uncandango.kubegroovy.recipe.schema.RecipeFactoryRegistry;
import dev.uncandango.kubegroovy.recipe.schema.UnknownKubeRecipe;
import dev.uncandango.kubegroovy.recipe.schema.function.AddToListFunction;
import dev.uncandango.kubegroovy.recipe.schema.function.BundleFunction;
import dev.uncandango.kubegroovy.recipe.schema.function.CustomFunction;
import dev.uncandango.kubegroovy.recipe.schema.function.RecipeSchemaFunctionRegistry;
import dev.uncandango.kubegroovy.recipe.schema.function.SetFunction;
import dev.uncandango.kubegroovy.recipe.schema.minecraft.ShapedKubeRecipe;
import dev.uncandango.kubegroovy.recipe.schema.minecraft.ShapelessKubeRecipe;
import dev.uncandango.kubegroovy.recipe.schema.postprocessing.KeyPatternCleanupPostProcessor;
import dev.uncandango.kubegroovy.recipe.schema.postprocessing.RecipePostProcessorTypeRegistry;
import dev.uncandango.kubegroovy.registry.BuilderTypeRegistry;
import dev.uncandango.kubegroovy.registry.RegistryObjectStorage;
import dev.uncandango.kubegroovy.registry.ServerRegistryRegistry;
import dev.uncandango.kubegroovy.script.BindingRegistry;
import dev.uncandango.kubegroovy.script.DataComponentTypeInfoRegistry;
import dev.uncandango.kubegroovy.script.KubeGroovyContext;
import dev.uncandango.kubegroovy.script.PlatformWrapper;
import dev.uncandango.kubegroovy.script.RecordDefaultsRegistry;
import dev.uncandango.kubegroovy.script.ScriptManager;
import dev.uncandango.kubegroovy.script.TypeDescriptionRegistry;
import dev.uncandango.kubegroovy.script.TypeWrapperRegistry;
import dev.uncandango.kubegroovy.script.bridge.Context;
import dev.uncandango.kubegroovy.script.bridge.RhinoException;
import dev.uncandango.kubegroovy.server.ScheduledServerEvent;
import dev.uncandango.kubegroovy.server.ServerScriptManager;
import dev.uncandango.kubegroovy.util.Cast;
import dev.uncandango.kubegroovy.util.FluidAmounts;
import dev.uncandango.kubegroovy.util.GlobalUnmodifiableMap;
import dev.uncandango.kubegroovy.util.ID;
import dev.uncandango.kubegroovy.util.JsonIO;
import dev.uncandango.kubegroovy.util.JsonUtils;
import dev.uncandango.kubegroovy.util.KubeIdentifier;
import dev.uncandango.kubegroovy.util.MobEffectUtil;
import dev.uncandango.kubegroovy.util.NBTIOWrapper;
import dev.uncandango.kubegroovy.util.NameProvider;
import dev.uncandango.kubegroovy.util.NotificationToastData;
import dev.uncandango.kubegroovy.util.RegExpKGY;
import dev.uncandango.kubegroovy.util.RotationAxis;
import dev.uncandango.kubegroovy.util.ScheduledEvents;
import dev.uncandango.kubegroovy.util.SlotFilter;
import dev.uncandango.kubegroovy.util.TickDuration;
import dev.uncandango.kubegroovy.util.TimeGY;
import dev.uncandango.kubegroovy.util.Tristate;
import dev.uncandango.kubegroovy.util.registrypredicate.RegistryPredicate;
import dev.uncandango.kubegroovy.web.LocalWebServer;
import dev.uncandango.kubegroovy.web.LocalWebServerRegistry;
import dev.uncandango.kubegroovy.web.local.KubeGroovyWeb;
import dev.uncandango.kubegroovy.script.type.RecordTypeInfo;
import dev.uncandango.kubegroovy.script.type.TypeInfo;
import net.minecraft.advancements.criterion.DataComponentMatchers;
import net.minecraft.advancements.criterion.MinMaxBounds;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CollectionTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.util.ColorRGBA;
import net.minecraft.util.Unit;
import net.minecraft.util.Util;
import net.minecraft.util.valueproviders.FloatProvider;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.LockCode;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.wolf.WolfVariant;
import net.minecraft.world.entity.decoration.painting.PaintingVariant;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.providers.EnchantmentProvider;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.item.equipment.trim.TrimPattern;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorPreset;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ItemAbility;
import net.neoforged.neoforge.common.SoundAction;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.datamaps.builtin.FurnaceFuel;
import net.neoforged.neoforge.registries.datamaps.builtin.NeoForgeDataMaps;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.time.temporal.TemporalAmount;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;

public class BuiltinKubeGroovyPlugin implements KubeGroovyPlugin {
	public static final HashMap<String, Object> GLOBAL = new HashMap<>();

	@Override
	public void registerBuilderTypes(BuilderTypeRegistry registry) {
		registry.addDefault(Registries.SOUND_EVENT, SoundEventBuilder.class, SoundEventBuilder::new);

		registry.addDefault(Registries.BLOCK, BasicKubeBlock.Builder.class, BasicKubeBlock.Builder::new);

		registry.of(Registries.BLOCK, reg -> {
			reg.add(KubeGroovy.id("detector"), DetectorBlock.Builder.class, DetectorBlock.Builder::new);
			reg.add(KubeGroovy.id("slab"), SlabBlockBuilder.class, SlabBlockBuilder::new);
			reg.add(KubeGroovy.id("stairs"), StairBlockBuilder.class, StairBlockBuilder::new);
			reg.add(KubeGroovy.id("fence"), FenceBlockBuilder.class, FenceBlockBuilder::new);
			reg.add(KubeGroovy.id("wall"), WallBlockBuilder.class, WallBlockBuilder::new);
			reg.add(KubeGroovy.id("fence_gate"), FenceGateBlockBuilder.class, FenceGateBlockBuilder::new);
			reg.add(KubeGroovy.id("pressure_plate"), PressurePlateBlockBuilder.class, PressurePlateBlockBuilder::new);
			reg.add(KubeGroovy.id("button"), ButtonBlockBuilder.class, ButtonBlockBuilder::new);
			reg.add(KubeGroovy.id("falling"), FallingBlockBuilder.class, FallingBlockBuilder::new);
			reg.add(KubeGroovy.id("crop"), CropBlockBuilder.class, CropBlockBuilder::new);
			reg.add(KubeGroovy.id("cardinal"), CardinalBlockBuilder.class, CardinalBlockBuilder::new);
			reg.add(KubeGroovy.id("carpet"), CarpetBlockBuilder.class, CarpetBlockBuilder::new);
			reg.add(KubeGroovy.id("door"), DoorBlockBuilder.class, DoorBlockBuilder::new);
			reg.add(KubeGroovy.id("trapdoor"), TrapdoorBlockBuilder.class, TrapdoorBlockBuilder::new);
			reg.add(KubeGroovy.id("pillar"), PillarBlockBuilder.class, PillarBlockBuilder::new);
		});

		registry.addDefault(Registries.ITEM, ItemBuilder.class, ItemBuilder::new);

		registry.of(Registries.ITEM, reg -> {
			reg.add(KubeGroovy.id("sword"), SwordItemBuilder.class, SwordItemBuilder::new);
			reg.add(KubeGroovy.id("pickaxe"), DiggerItemBuilder.Pickaxe.class, DiggerItemBuilder.Pickaxe::new);
			reg.add(KubeGroovy.id("axe"), DiggerItemBuilder.Axe.class, DiggerItemBuilder.Axe::new);
			reg.add(KubeGroovy.id("shovel"), DiggerItemBuilder.Shovel.class, DiggerItemBuilder.Shovel::new);
			reg.add(KubeGroovy.id("hoe"), DiggerItemBuilder.Hoe.class, DiggerItemBuilder.Hoe::new);
			reg.add(KubeGroovy.id("shears"), ShearsItemBuilder.class, ShearsItemBuilder::new);
			reg.add(KubeGroovy.id("helmet"), ArmorItemBuilder.Helmet.class, ArmorItemBuilder.Helmet::new);
			reg.add(KubeGroovy.id("chestplate"), ArmorItemBuilder.Chestplate.class, ArmorItemBuilder.Chestplate::new);
			reg.add(KubeGroovy.id("leggings"), ArmorItemBuilder.Leggings.class, ArmorItemBuilder.Leggings::new);
			reg.add(KubeGroovy.id("boots"), ArmorItemBuilder.Boots.class, ArmorItemBuilder.Boots::new);
			reg.add(KubeGroovy.id("animal_armor"), ArmorItemBuilder.AnimalArmor.class, ArmorItemBuilder.AnimalArmor::new);
			reg.add(KubeGroovy.id("smithing_template"), SmithingTemplateItemBuilder.class, SmithingTemplateItemBuilder::new);
		});

		registry.addDefault(Registries.FLUID, FluidBuilder.class, FluidBuilder::new);

		registry.of(Registries.FLUID, reg -> {
			reg.add(KubeGroovy.id("thin"), ThinFluidBuilder.class, ThinFluidBuilder::new);
			reg.add(KubeGroovy.id("thick"), ThickFluidBuilder.class, ThickFluidBuilder::new);
		});

		registry.addDefault(NeoForgeRegistries.Keys.FLUID_TYPES, FluidTypeBuilder.class, FluidTypeBuilder::new);
		registry.addDefault(Registries.MOB_EFFECT, MobEffectBuilder.class, MobEffectBuilder::new);
		registry.addDefault(Registries.POTION, PotionBuilder.class, PotionBuilder::new);
		registry.addDefault(Registries.PARTICLE_TYPE, ParticleTypeBuilder.class, ParticleTypeBuilder::new);
		registry.addDefault(Registries.CUSTOM_STAT, CustomStatBuilder.class, CustomStatBuilder::new);
		registry.addDefault(Registries.POINT_OF_INTEREST_TYPE, PoiTypeBuilder.class, PoiTypeBuilder::new);
		registry.addDefault(Registries.VILLAGER_TYPE, VillagerTypeBuilder.class, VillagerTypeBuilder::new);
		registry.addDefault(Registries.VILLAGER_PROFESSION, VillagerProfessionBuilder.class, VillagerProfessionBuilder::new);
		registry.addDefault(Registries.CREATIVE_MODE_TAB, CreativeTabBuilder.class, CreativeTabBuilder::new);

		// FIXME registry.addDefault(Registries.ENCHANTMENT, EnchantmentBuilder.class, EnchantmentBuilder::new);
		registry.addDefault(Registries.PAINTING_VARIANT, PaintingVariantBuilder.class, PaintingVariantBuilder::new);
		registry.addDefault(Registries.JUKEBOX_SONG, JukeboxSongBuilder.class, JukeboxSongBuilder::new);
		registry.addDefault(Registries.ATTRIBUTE, AttributeBuilder.class, AttributeBuilder::new);
	}

	@Override
	public void registerServerRegistries(ServerRegistryRegistry registry) {
		// VanillaRegistries
		registry.register(Registries.DIMENSION_TYPE, DimensionType.DIRECT_CODEC, DimensionType.class);
		registry.register(Registries.CONFIGURED_CARVER, ConfiguredWorldCarver.DIRECT_CODEC, TypeInfo.of(ConfiguredWorldCarver.class));
		registry.register(Registries.CONFIGURED_FEATURE, ConfiguredFeature.DIRECT_CODEC, TypeInfo.of(ConfiguredFeature.class));
		registry.register(Registries.PLACED_FEATURE, PlacedFeature.DIRECT_CODEC, PlacedFeature.class);
		registry.register(Registries.STRUCTURE, Structure.DIRECT_CODEC, Structure.class);
		registry.register(Registries.STRUCTURE_SET, StructureSet.DIRECT_CODEC, StructureSet.class);
		registry.register(Registries.PROCESSOR_LIST, StructureProcessorType.DIRECT_CODEC, StructureProcessorList.class);
		registry.register(Registries.TEMPLATE_POOL, StructureTemplatePool.DIRECT_CODEC, StructureTemplatePool.class);
		registry.register(Registries.BIOME, Biome.DIRECT_CODEC, Biome.class);
		registry.register(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST, MultiNoiseBiomeSourceParameterList.DIRECT_CODEC, MultiNoiseBiomeSourceParameterList.class);
		registry.register(Registries.NOISE, NormalNoise.NoiseParameters.DIRECT_CODEC, NormalNoise.NoiseParameters.class);
		registry.register(Registries.DENSITY_FUNCTION, DensityFunction.DIRECT_CODEC, DensityFunction.class);
		registry.register(Registries.NOISE_SETTINGS, NoiseGeneratorSettings.DIRECT_CODEC, NoiseGeneratorSettings.class);
		registry.register(Registries.WORLD_PRESET, WorldPreset.DIRECT_CODEC, WorldPreset.class);
		registry.register(Registries.FLAT_LEVEL_GENERATOR_PRESET, FlatLevelGeneratorPreset.DIRECT_CODEC, FlatLevelGeneratorPreset.class);
		registry.register(Registries.CHAT_TYPE, ChatType.DIRECT_CODEC, ChatType.class);
		registry.register(Registries.TRIM_PATTERN, TrimPattern.DIRECT_CODEC, TrimPattern.class);
		registry.register(Registries.TRIM_MATERIAL, TrimMaterial.DIRECT_CODEC, TrimMaterial.class);
		registry.register(Registries.WOLF_VARIANT, WolfVariant.DIRECT_CODEC, WolfVariant.class);
		registry.register(Registries.PAINTING_VARIANT, PaintingVariant.DIRECT_CODEC, PaintingVariant.class);
		registry.register(Registries.DAMAGE_TYPE, DamageType.DIRECT_CODEC, DamageType.class);
		registry.register(Registries.BANNER_PATTERN, BannerPattern.DIRECT_CODEC, BannerPattern.class);
		registry.register(Registries.ENCHANTMENT, Enchantment.DIRECT_CODEC, Enchantment.class);
		registry.register(Registries.ENCHANTMENT_PROVIDER, EnchantmentProvider.DIRECT_CODEC, EnchantmentProvider.class);
		registry.register(Registries.JUKEBOX_SONG, JukeboxSong.DIRECT_CODEC, JukeboxSong.class);
	}

	@Override
	public void registerEvents(EventGroupRegistry registry) {
		registry.register(StartupEvents.GROUP);
		registry.register(ServerEvents.GROUP);
		registry.register(LevelEvents.GROUP);
		registry.register(NetworkEvents.GROUP);
		registry.register(ItemEvents.GROUP);
		registry.register(BlockEvents.GROUP);
		registry.register(EntityEvents.GROUP);
		registry.register(PlayerEvents.GROUP);
		registry.register(RecipeViewerEvents.GROUP);
	}

	@Override
	public void registerBindings(BindingRegistry bindings) {
		bindings.add("global", bindings.type().isStartup() ? GLOBAL : new GlobalUnmodifiableMap<>(GLOBAL));
		bindings.add("Platform", PlatformWrapper.class);
		bindings.add("console", bindings.type().console);

		for (var group : EventGroups.ALL.get().map().values()) {
			bindings.add(group.name, new EventGroupWrapper(bindings.type(), group));
		}

		bindings.add("JavaMath", Math.class);
		bindings.add("ID", ID.class);

		bindings.add("Duration", Duration.class);


		if (bindings.type().isServer() && bindings.context().kgyFactory.manager instanceof ServerScriptManager) {
			var se = ScheduledServerEvent.EVENTS;

			bindings.add("setTimeout", new ScheduledEvents.TimeoutFunction(se, false, false));
			bindings.add("clearTimeout", new ScheduledEvents.TimeoutFunction(se, true, false));
			bindings.add("setInterval", new ScheduledEvents.TimeoutFunction(se, false, true));
			bindings.add("clearInterval", new ScheduledEvents.TimeoutFunction(se, true, true));
		}

		bindings.add("KMath", KMath.class);
		bindings.add("Utils", UtilsWrapper.class);
		bindings.add("StringUtils", StringUtilsWrapper.class);
		bindings.add("Java", JavaWrapper.class);
		bindings.add("Text", TextWrapper.class);
		bindings.add("Component", TextWrapper.class);
		bindings.add("TextIcons", TextIcons.class);
		bindings.add("UUID", UUIDWrapper.class);
		bindings.add("JsonUtils", JsonUtils.class);
		bindings.add("JsonIO", JsonIO.class);
		bindings.add("Block", BlockWrapper.class);
		bindings.add("Blocks", Blocks.class);
		bindings.add("Item", ItemWrapper.class);
		bindings.add("Items", Items.class);
		bindings.add("Ingredient", IngredientWrapper.class);
		bindings.add("NBT", NBTWrapper.class);
		bindings.add("NBTIO", NBTIOWrapper.class);
		bindings.add("Direction", DirectionWrapper.class);
		bindings.add("Facing", DirectionWrapper.class);
		bindings.add("AABB", AABBWrapper.class);
		bindings.add("Stats", Stats.class);
		bindings.add("FluidAmounts", FluidAmounts.class);
		bindings.add("Notification", NotificationToastData.class);
		bindings.add("SizedIngredient", SizedIngredientWrapper.class);
		bindings.add("ParticleOptions", ParticleOptionsWrapper.class);
		bindings.add("Registry", RegistryWrapper.class);
		bindings.add("DataMap", DataMapWrapper.class);
		bindings.add("EntitySelector", EntitySelectorWrapper.class);

		bindings.add("Fluid", FluidWrapper.class);
		bindings.add("Fluids", Fluids.class);

		bindings.add("SECOND", 1000L);
		bindings.add("MINUTE", 60000L);
		bindings.add("HOUR", 3600000L);

		bindings.add("Color", ColorWrapper.class);
		bindings.add("BlockStatePredicate", BlockStatePredicate.class);

		bindings.add("Vec3d", Vec3.class);
		bindings.add("Vec3i", Vec3i.class);
		bindings.add("Vec3f", Vector3f.class);
		bindings.add("Vec4f", Vector4f.class);
		bindings.add("Matrix3f", Matrix3f.class);
		bindings.add("Matrix4f", Matrix4f.class);
		bindings.add("Matrix4f", Matrix4f.class);
		bindings.add("Quaternionf", Quaternionf.class);
		bindings.add("RotationAxis", RotationAxis.class);
		bindings.add("BlockPos", BlockPos.class);
		bindings.add("DamageSource", DamageSource.class);
		bindings.add("SoundType", SoundType.class);

		bindings.add("BlockProperties", BlockStateProperties.class);
		bindings.add("NativeEvents", NativeEventWrapper.class);
		bindings.add("MobEffectUtil", MobEffectUtil.class);
	}

	@Override
	public void registerTypeWrappers(TypeWrapperRegistry registry) {
		registry.register(RegistryPredicate.class, (dev.uncandango.kubegroovy.script.bridge.TypeWrapperFactory) RegistryPredicate::of, true);

		// Java / Minecraft //
		// registry.register(String.class, String::valueOf);
		// registry.register(CharSequence.class, String::valueOf);
		registry.register(UUID.class, UUIDWrapper::fromString, true);
		registry.register(Pattern.class, RegExpKGY::wrap, true);
		registry.register(JsonObject.class, JsonUtils::objectOf, true);
		registry.register(JsonArray.class, JsonUtils::arrayOf, true);
		registry.register(JsonElement.class, JsonUtils::of, true);
		registry.register(JsonPrimitive.class, JsonUtils::primitiveOf, true);
		registry.register(Path.class, MiscWrappers::wrapPath, true);
		registry.register(File.class, MiscWrappers::wrapFile, true);
		registry.register(TemporalAmount.class, TimeGY::wrapTemporalAmount, true);
		registry.register(Duration.class, TimeGY::wrapDuration, true);
		registry.register(TickDuration.class, TickDuration::wrap, true);

		registry.register(HolderSet.class, (dev.uncandango.kubegroovy.script.bridge.TypeWrapperFactory) (from, target) -> {
			var cx = Context.getCurrent();
			return HolderWrapper.wrapSet((KubeGroovyContext) cx, from, target.param(0));
		}, false);
		registry.register(Identifier.class, ID::mc, true);
		registry.register(KubeIdentifier.class, KubeIdentifier::wrap, true);
		registry.register(CompoundTag.class, (from, target) -> NBTWrapper.isTagCompound(from), NBTWrapper::wrapCompound, true);
		registry.register(CollectionTag.class, (from, target) -> NBTWrapper.isTagCollection(from), NBTWrapper::wrapCollection, true);
		registry.register(ListTag.class, (from, target) -> NBTWrapper.isTagCollection(from), NBTWrapper::wrapListTag, true);
		registry.register(Tag.class, NBTWrapper::wrap, true);
		registry.register(DataComponentType.class, DataComponentWrapper::wrapType, true);
		registry.register(DataComponentMap.class, DataComponentWrapper::filter, DataComponentWrapper::mapOf, true);
		registry.register(DataComponentPatch.class, DataComponentWrapper::filter, DataComponentWrapper::patchOf, true);

		registry.register(BlockPos.class, MiscWrappers::wrapBlockPos, false);
		registry.register(Vec3.class, MiscWrappers::wrapVec3, false);
		registry.register(Vec3i.class, MiscWrappers::wrapBlockPos, false);

		registry.register(Item.class, ItemWrapper::wrapItem, true);
		registry.register(ItemLike.class, ItemWrapper::wrapItemLike, true);
		registry.registerEnumFromStringCodec(MobCategory.class, MobCategory.CODEC);
		registry.register(ItemEnchantments.class, ItemEnchantmentsWrapper::wrap, false);

		registry.register(AABB.class, AABBWrapper::wrap, false);
		registry.register(IntProvider.class, MiscWrappers::wrapIntProvider, false);
		registry.register(FloatProvider.class, MiscWrappers::wrapFloatProvider, false);
		registry.register(NumberProvider.class, MiscWrappers::wrapNumberProvider, false);
		registry.registerEnumFromStringCodec(LootContext.EntityTarget.class, LootContext.EntityTarget.CODEC);

		// No equivalent
		//registry.registerEnumFromStringCodec(CopyNameFunction.NameSource.class, CopyNameFunction.NameSource.CODEC);

		// FIXME registry.register(Enchantment.Cost.class, EnchantmentBuilder::wrapCost);

		registry.registerEnumFromStringCodec(ArmorType.class, ArmorType.CODEC);
		registry.register(BlockSetType.class, BlockWrapper::wrapSetType, true);
		registry.register(BlockState.class, BlockWrapper::wrapBlockState, true);
		registry.register(ItemAbility.class, ItemWrapper::wrapItemAbility, true);
		registry.register(ColorRGBA.class, ColorWrapper::wrapColorRGBA, true);

		// KubeGroovy //
		registry.register(ItemStack.class, ItemWrapper::wrap, true);
		registry.register(ItemStackTemplate.class, ItemStackTemplateWrapper::wrap, true);
		registry.register(Ingredient.class, IngredientWrapper::wrap, true);
		registry.register(ItemPredicate.class, ItemPredicate::wrap, true);
		registry.registerCodec(net.minecraft.advancements.criterion.ItemPredicate.class, net.minecraft.advancements.criterion.ItemPredicate.CODEC);
		registry.register(SizedIngredient.class, SizedIngredientWrapper::wrap, true);
		registry.register(BlockStatePredicate.class, BlockStatePredicate::wrap, true);
		registry.register(RuleTest.class, BlockStatePredicate::wrapRuleTest, true);
		registry.register(FluidStack.class, FluidWrapper::wrap, true);
		registry.register(FluidIngredient.class, FluidWrapper::wrapIngredient, true);
		registry.register(SizedFluidIngredient.class, FluidWrapper::wrapSizedIngredient, true);
		registry.register(RecipeFilter.class, RecipeFilter::wrap, true);
		registry.register(SlotFilter.class, SlotFilter::wrap, true);


		registry.register(ArmorMaterial.class, o -> o instanceof ArmorMaterial armorMaterial ? armorMaterial : null, false);
		registry.register(ToolMaterial.class, ItemToolMaterials::wrap, true);

		registry.register(PlayerSelector.class, PlayerSelector::wrap, true);
		registry.register(DamageSource.class, DamageSourceWrapper::wrap, true);
		registry.register(EntitySelector.class, EntitySelectorWrapper::wrap, true);
		registry.register(ReplacementMatch.class, ReplacementMatch::wrap, true);
		registry.register(ReplacementMatchInfo.class, ReplacementMatchInfo::wrap, true);
		registry.register(Stat.class, PlayerStatsGY::wrapStat, true);
		registry.register(MapColor.class, MapColorHelper::wrap, true);
		registry.register(SoundType.class, SoundTypeWrapper.INSTANCE, true);
		registry.register(ParticleOptions.class, ParticleOptionsWrapper::wrap, true);
		registry.register(ItemTintFunction.class, ItemTintFunction::wrap, true);
		registry.register(BlockTintFunction.class, BlockTintFunction::wrap, true);
		registry.register(Tristate.class, Tristate::wrap, true);

		// components //
		registry.register(MutableComponent.class, TextWrapper::wrap, true);
		registry.register(KubeColor.class, ColorWrapper::wrap, true);
		registry.register(TextColor.class, ColorWrapper::wrapTextColor, true);
		registry.register(ClickEvent.class, TextWrapper::wrapClickEvent, true);
		registry.registerAlias(Component.class, TextWrapper.TYPE_INFO, Function.identity(), true);
		registry.registerAlias(ItemLore.class, TypeInfo.RAW_LIST.withParams(TextWrapper.TYPE_INFO), TextWrapper::lore, false);
		registry.registerAlias(ItemAttributeModifiers.class, TypeInfo.RAW_LIST.withParams(TypeInfo.of(ItemAttributeModifiers.Entry.class)), ItemAttributeModifiers::new, false);

		// codecs
		registry.registerCodec(Fireworks.class, Fireworks.CODEC);
		registry.registerCodec(KubeIcon.class, KubeIcon.CODEC);

		// alias
		registry.registerAlias(Unit.class, TypeInfo.NONE, o -> Unit.INSTANCE, false);
		registry.registerAlias(CustomData.class, CompoundTag.class, CustomData::of, true);
		registry.registerAlias(CustomModelData.class, TypeInfo.PRIMITIVE_INT, (Integer i) ->
			new CustomModelData(List.of(), List.of(), List.of(), List.of(i)),
			true
		);
		// LockCode lost its String constructor in 1.21.2 (now wraps a vanilla item
		// predicate), so interpret the input as an item id instead of delegating
		// to a constructor that no longer exists.
		registry.registerAlias(LockCode.class, net.minecraft.advancements.criterion.ItemPredicate.class, LockCode::new, false);
		registry.registerAlias(BlockItemStateProperties.class, TypeInfo.RAW_MAP.withParams(TypeInfo.STRING, TypeInfo.STRING), BlockItemStateProperties::new, false);

		registry.registerAlias(ItemResource.class, ItemStack.class, ItemResource::of, true);
		registry.registerAlias(FluidResource.class, FluidStack.class, FluidResource::of, true);

		registry.register(SoundAction.class, obj -> switch (obj) {
			case SoundAction sa -> sa;
			case CharSequence cs -> SoundAction.get(cs.toString());
			default -> throw new RhinoException("Cannot convert " + obj + " to SoundAction");
		}, true);
	}

	@Override
	public void registerRecordDefaults(RecordDefaultsRegistry registry) {
		registry.register(BlockSetType.OAK);
		registry.register(new NotificationToastData(NotificationToastData.DEFAULT_DURATION, Component.empty(), Optional.empty(), 16, Optional.empty(), Optional.empty(), Optional.empty(), false));
	}

	@Override
	public void registerTypeDescriptions(TypeDescriptionRegistry registry) {
		registry.register(SlotFilter.class, ((RecordTypeInfo) TypeInfo.of(SlotFilter.class)).createCombinedType(TypeInfo.INT, IngredientWrapper.TYPE_INFO));
	}

	@Override
	public void registerRecipeFactories(RecipeFactoryRegistry registry) {
		registry.register(UnknownKubeRecipe.RECIPE_FACTORY);
		registry.register(ShapedKubeRecipe.RECIPE_FACTORY);
		registry.register(ShapelessKubeRecipe.RECIPE_FACTORY);
	}

	@Override
	public void registerRecipeComponents(RecipeComponentTypeRegistry registry) {
		registry.unit(IgnoreComponent.INSTANCE);
		registry.unit(BooleanComponent.BOOLEAN);
		registry.unit(StringComponent.STRING);
		registry.unit(StringComponent.OPTIONAL_STRING);
		registry.unit(StringComponent.ID);
		registry.unit(CharacterComponent.CHARACTER);

		registry.register(NumberComponent.INT_TYPE, NumberComponent.INT_CODEC);
		registry.register(NumberComponent.LONG_TYPE, NumberComponent.LONG_CODEC);
		registry.register(NumberComponent.FLOAT_TYPE, NumberComponent.FLOAT_CODEC);
		registry.register(NumberComponent.DOUBLE_TYPE, NumberComponent.DOUBLE_CODEC);
		registry.unit(NumberComponent.NON_NEGATIVE_INT);
		registry.unit(NumberComponent.POSITIVE_INT);
		registry.unit(NumberComponent.NON_NEGATIVE_LONG);
		registry.unit(NumberComponent.POSITIVE_LONG);
		registry.unit(NumberComponent.NON_NEGATIVE_FLOAT);
		registry.unit(NumberComponent.POSITIVE_FLOAT);
		registry.unit(NumberComponent.NON_NEGATIVE_DOUBLE);
		registry.unit(NumberComponent.POSITIVE_DOUBLE);

		registry.register(ListRecipeComponent.TYPE, ListRecipeComponent.MAP_CODEC);
		registry.register(EnumComponent.TYPE, EnumComponent.MAP_CODEC);
		registry.register(MapRecipeComponent.TYPE, MapRecipeComponent.MAP_CODEC);
		registry.register(MapRecipeComponent.PATTERN_TYPE, MapRecipeComponent.PATTERN_MAP_CODEC);
		registry.register(EitherRecipeComponent.TYPE, EitherRecipeComponent.MAP_CODEC);
		registry.register(CustomObjectRecipeComponent.TYPE, CustomObjectRecipeComponent.MAP_CODEC);

		registry.unit(IngredientComponent.INGREDIENT);
		registry.unit(IngredientComponent.OPTIONAL_INGREDIENT);

		registry.unit(SizedIngredientComponent.SIZED_INGREDIENT);
		registry.unit(SizedIngredientComponent.OPTIONAL_SIZED_INGREDIENT);

		registry.unit(ItemStackComponent.ITEM_STACK);
		registry.unit(ItemStackTemplateComponent.ITEM_STACK_TEMPLATE);
		registry.unit(ItemStackComponent.OPTIONAL_ITEM_STACK);
		registry.register(ItemStackComponent.FILTERED_TYPE, ItemStackComponent.FILTERED_CODEC);

		registry.unit(FluidStackComponent.FLUID_STACK);
		registry.unit(FluidStackComponent.OPTIONAL_FLUID_STACK);

		registry.unit(FluidIngredientComponent.FLUID_INGREDIENT);
		registry.unit(FluidIngredientComponent.OPTIONAL_FLUID_INGREDIENT);

		registry.unit(SizedFluidIngredientComponent.SIZED_FLUID_INGREDIENT);
		registry.unit(SizedFluidIngredientComponent.OPTIONAL_SIZED_FLUID_INGREDIENT);

		registry.unit(BlockComponent.BLOCK);
		registry.unit(BlockComponent.OPTIONAL_BLOCK);

		registry.unit(BlockStateComponent.BLOCK_STATE);
		registry.unit(BlockStateComponent.BLOCK_STATE_STRING);
		registry.unit(BlockStateComponent.OPTIONAL_BLOCK_STATE);
		registry.unit(BlockStateComponent.OPTIONAL_BLOCK_STATE_STRING);

		registry.unit(TimeComponent.TICKS);
		registry.unit(TimeComponent.SECONDS);
		registry.unit(TimeComponent.MINUTES);
		registry.unit(TimeComponent.HOURS);

		registry.unit(TagKeyComponent.BLOCK);
		registry.unit(TagKeyComponent.ITEM);
		registry.unit(TagKeyComponent.FLUID);
		registry.unit(TagKeyComponent.ENTITY_TYPE);
		registry.unit(TagKeyComponent.BIOME);
		registry.unit(TagKeyComponent.HASHED_BLOCK);
		registry.unit(TagKeyComponent.HASHED_ITEM);
		registry.unit(TagKeyComponent.HASHED_FLUID);
		registry.unit(TagKeyComponent.HASHED_ENTITY_TYPE);
		registry.unit(TagKeyComponent.HASHED_BIOME);

		registry.unit(NestedRecipeComponent.RECIPE);

		registry.unit(BookCategoryComponent.CRAFTING_BOOK_CATEGORY);
		registry.unit(BookCategoryComponent.COOKING_BOOK_CATEGORY);

		registry.unit(ResourceKeyComponent.DIMENSION);
		registry.unit(ResourceKeyComponent.LOOT_TABLE);

		registry.register(TagKeyComponent.TYPE, TagKeyComponent.MAP_CODEC);
		registry.register(RegistryComponent.TYPE, RegistryComponent.MAP_CODEC);
		registry.register(ResourceKeyComponent.TYPE, ResourceKeyComponent.MAP_CODEC);
	}

	@Override
	public void registerRecipeSchemaFunctionTypes(RecipeSchemaFunctionRegistry registry) {
		registry.register(CustomFunction.TYPE);
		registry.register(BundleFunction.TYPE);
		registry.register(SetFunction.TYPE);
		registry.register(AddToListFunction.TYPE);
	}

	@Override
	public void registerRecipePostProcessors(RecipePostProcessorTypeRegistry registry) {
		registry.register(KeyPatternCleanupPostProcessor.TYPE);
	}

	@Override
	public void registerIngredientActionTypes(IngredientActionTypeRegistry registry) {
		registry.register(ConsumeAction.TYPE);
		registry.register(CustomIngredientAction.TYPE);
		registry.register(DamageAction.TYPE);
		registry.register(KeepAction.TYPE);
		registry.register(ReplaceAction.TYPE);
	}

	@Override
	public void beforeScriptsLoaded(ScriptManager manager) {
	}

	@Override
	public void registerDataComponentTypeDescriptions(DataComponentTypeInfoRegistry registry) {
		// DataComponents.ATTRIBUTE_MODIFIERS
	}

	@Override
	public void registerLocalWebServer(LocalWebServerRegistry registry) {
		KubeGroovyWeb.register(registry);
	}

	@Override
	public void registerLocalWebServerWithAuth(LocalWebServerRegistry registry) {
		KubeGroovyWeb.registerWithAuth(registry);
	}

	@Override
	public void localWebServerStarted(LocalWebServer server) {
		KubeGroovyWeb.serverStarted(server);
	}

	@Override
	public void registerItemNameProviders(NameProvider.Registry<Item, ItemStack> registry) {
		registry.register(Items.ENCHANTED_BOOK, (registries, stack) -> {
			var enchants = EnchantmentHelper.getEnchantmentsForCrafting(stack);

			if (enchants.isEmpty()) {
				return null;
			}

			var c = Component.empty();
			c.append(stack.getHoverName());
			boolean first = true;

			for (var e : enchants.entrySet()) {
				if (first) {
					first = false;
					c.append(": ");
				} else {
					c.append(", ");
				}

				c.append(Enchantment.getFullname(e.getKey(), e.getIntValue()));
			}

			return c;
		});

		/*
		registry.register(List.of(Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION), (registries, stack) -> {
			var potions = stack.get(DataComponents.POTION_CONTENTS);

			if (potions == null || potions.potion().isEmpty()) {
				return null;
			}

			var c = Component.empty();
			c.append(stack.getHoverName());
			// c.append(Potion.getName(potions.potion().get(), potions.));
			return c;
		});
		 */

		for (var item : BuiltInRegistries.ITEM) {
			var song = item.components().get(DataComponents.JUKEBOX_PLAYABLE);

			if (song != null) {
				registry.register(item, (registries, stack) -> {
					var key = Util.makeDescriptionId("jukebox_song", song.song().getKey().identifier());
					return Component.empty().append(stack.getHoverName()).append(": ").append(Component.translatable(key));
				});
			}
		}

		registry.register(Items.PAINTING, (registries, stack) -> {
			var variant = stack.get(DataComponents.PAINTING_VARIANT);
			if (variant == null) {
				return null;
			}
			var key = variant.unwrapKey().map(ResourceKey::identifier).orElse(null);
			if (key == null) {
				return null;
			}
			return Component.empty()
				.append(stack.getHoverName())
				.append(": ")
				.append(Component.translatable(key.toLanguageKey("painting", "author")))
				.append(" - ")
				.append(Component.translatable(key.toLanguageKey("painting", "title")));
		});
	}

	@Override
	public void registerIconTypes(KubeIconTypeRegistry registry) {
		registry.register(TextureKubeIcon.TYPE);
		registry.register(AtlasSpriteKubeIcon.TYPE);
		registry.register(ItemKubeIcon.TYPE);
	}

	@Override
	public void generateData(KubeDataGenerator generator) {
		generator.dataMap(NeoForgeDataMaps.FURNACE_FUELS, callback -> {
			for (var entry : ItemModificationKubeEvent.ItemModifications.BURN_TIME_OVERRIDES.reference2IntEntrySet()) {
				callback.accept(entry.getKey().kgy$getIdLocation(), new FurnaceFuel(entry.getIntValue()));
			}

			for (var builder : RegistryObjectStorage.ITEM) {
				if (builder instanceof ItemBuilder item && item.getBurnTime() > 0) {
					callback.accept(item.getId(), new FurnaceFuel(item.getBurnTime()));
				}
			}
		});
	}
}
