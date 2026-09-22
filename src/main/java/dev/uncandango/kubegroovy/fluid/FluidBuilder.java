package dev.uncandango.kubegroovy.fluid;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.uncandango.kubegroovy.KubeGroovy;
import dev.uncandango.kubegroovy.block.BlockRenderType;
import dev.uncandango.kubegroovy.block.BlockTintFunction;
import dev.uncandango.kubegroovy.color.KubeColor;
import dev.uncandango.kubegroovy.color.SimpleColor;
import dev.uncandango.kubegroovy.generator.KubeAssetGenerator;
import dev.uncandango.kubegroovy.registry.AdditionalObjectRegistry;
import dev.uncandango.kubegroovy.registry.BuilderBase;
import dev.uncandango.kubegroovy.util.ID;
import dev.uncandango.kubegroovy.script.annotations.ReturnsSelf;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.FlowingFluid;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Supplier;

import static net.neoforged.neoforge.fluids.BaseFlowingFluid.Properties;
import static net.neoforged.neoforge.fluids.BaseFlowingFluid.Source;

@ReturnsSelf
public class FluidBuilder extends BuilderBase<FlowingFluid> {
	public static final KubeColor WATER_COLOR = new SimpleColor(0xFF3F76E4);

	private static final Identifier GENERATED_BUCKET_MODEL_ID = KubeGroovy.id("item/generated_bucket");

	protected int slopeFindDistance = 4;
	protected int levelDecreasePerBlock = 1;
	protected float explosionResistance = 1;
	protected int tickRate = 5;

	protected FluidTypeBuilder fluidType;
	protected FlowingFluidBuilder flowingFluid;
	protected @Nullable FluidBlockBuilder block;
	protected @Nullable FluidBucketItemBuilder bucketItem;
	protected @Nullable KubeColor bucketColor;
	private @Nullable Properties properties;

	public FluidBuilder(Identifier i) {
		super(i);
		fluidType = new FluidTypeBuilder(id);
		this.stillTexture(Identifier.fromNamespaceAndPath("kubegroovy", "block/thin_fluid_still"))
			.flowingTexture(Identifier.fromNamespaceAndPath("kubegroovy", "block/thin_fluid_flow"));
		flowingFluid = new FlowingFluidBuilder(this);
		block = new FluidBlockBuilder(this);
		bucketItem = new FluidBucketItemBuilder(this);
	}

	@Override
	public BuilderBase<FlowingFluid> displayName(Component name) {
		if (block != null) {
			block.displayName(name);
		}

		if (bucketItem != null) {
			bucketItem.displayName(Component.literal("").append(name).append(" Bucket"));
		}

		return super.displayName(name);
	}

	@SuppressWarnings("DataFlowIssue") // safe, neo hasn't marked nullable params as such
	public Properties createProperties() {
		if (properties == null) {
			properties = new Properties(fluidType, this, flowingFluid);
			properties.bucket(bucketItem);
			properties.block((Supplier) block);
			properties.slopeFindDistance(slopeFindDistance);
			properties.levelDecreasePerBlock(levelDecreasePerBlock);
			properties.explosionResistance(explosionResistance);
			properties.tickRate(tickRate);
		}

		return properties;
	}

	@Override
	public FlowingFluid createObject() {
		return new Source(createProperties());
	}

	@Override
	public void createAdditionalObjects(AdditionalObjectRegistry registry) {
		registry.add(NeoForgeRegistries.Keys.FLUID_TYPES, fluidType);
		registry.add(Registries.FLUID, flowingFluid);

		if (block != null) {
			registry.add(Registries.BLOCK, block);
		}

		if (bucketItem != null) {
			registry.add(Registries.ITEM, bucketItem);
		}
	}

	@Override
	public BuilderBase<FlowingFluid> tag(Identifier[] tag) {
		this.flowingFluid.tag(tag);
		return super.tag(tag);
	}

	public FluidBuilder type(Consumer<FluidTypeBuilder> builder) {
		builder.accept(fluidType);
		return this;
	}

	public FluidBuilder tint(KubeColor c) {
		fluidType.tint(c);
		return this;
	}

	public FluidBuilder tintFunction(BlockTintFunction tint) {
		fluidType.tintFunction(tint);
		return this;
	}

	public FluidBuilder bucketColor(KubeColor color) {
		this.bucketColor = color;
		return this;
	}

	public FluidBuilder stillTexture(Identifier id) {
		fluidType.stillTexture = id;
		return this;
	}

	public FluidBuilder flowingTexture(Identifier id) {
		fluidType.flowingTexture = id;
		return this;
	}

	public FluidBuilder renderType(BlockRenderType l) {
		fluidType.renderType = l;
		return this;
	}

	public FluidBuilder translucent() {
		return renderType(BlockRenderType.TRANSLUCENT);
	}

	public FluidBuilder slopeFindDistance(int slopeFindDistance) {
		this.slopeFindDistance = slopeFindDistance;
		return this;
	}

	public FluidBuilder levelDecreasePerBlock(int levelDecreasePerBlock) {
		this.levelDecreasePerBlock = levelDecreasePerBlock;
		return this;
	}

	public FluidBuilder explosionResistance(float explosionResistance) {
		this.explosionResistance = explosionResistance;
		return this;
	}

	public FluidBuilder tickRate(int tickRate) {
		this.tickRate = tickRate;
		return this;
	}

	public FluidBuilder noBucket() {
		this.bucketItem = null;
		return this;
	}

	public FluidBuilder noBlock() {
		this.block = null;
		return this;
	}

	@dev.uncandango.kubegroovy.script.annotations.HideFromGY
	public final FluidTypeBuilder kgy$getFluidType() {
		return fluidType;
	}

	@dev.uncandango.kubegroovy.script.annotations.HideFromGY
	public final FlowingFluidBuilder kgy$getFlowingFluid() {
		return flowingFluid;
	}

	@dev.uncandango.kubegroovy.script.annotations.HideFromGY
	public final @Nullable KubeColor kgy$getBucketColor() {
		return bucketColor;
	}

	@Override
	public void generateAssets(KubeAssetGenerator generator) {
		var customStill = generator.loadTexture(newID("block/", "_still"));
		var stillTexture = customStill.width > 0 && customStill.height > 0 ? customStill : generator.loadTexture(fluidType.stillTexture);

		if (!(stillTexture.width <= 0 || stillTexture.height <= 0)) {
			generator.texture(fluidType.actualStillTexture, stillTexture.tint(fluidType.textureTint));
		}

		var customFlow = generator.loadTexture(newID("block/", "_flow"));
		var flowingTexture = customFlow.width > 0 && customFlow.height > 0 ? customFlow : generator.loadTexture(fluidType.flowingTexture);

		if (!(flowingTexture.width <= 0 || flowingTexture.height <= 0)) {
			generator.texture(fluidType.actualFlowingTexture, flowingTexture.tint(fluidType.textureTint));
		}

		generator.blockState(id, m -> m.simpleVariant("", id.withPath(ID.BLOCK)));
		generator.blockModel(id, m -> {
			m.parent(null);
			m.texture("particle", fluidType.actualStillTexture.toString());

			if (fluidType.renderType != BlockRenderType.SOLID) {
				m.custom(json -> json.addProperty("render_type", switch (fluidType.renderType) {
					case CUTOUT -> "cutout";
					case TRANSLUCENT -> "translucent";
					default -> "solid";
				}));
			}
		});

		if (bucketItem != null && !bucketItem.hasCustomModel()) {
			var fluidPath = newID("item/generated/", "_bucket_fluid");

			generator.mask(fluidPath, KubeGroovy.id("item/bucket_mask"), fluidType.actualStillTexture);

			generator.itemModel(bucketItem.getId(), m -> {
				m.parent(GENERATED_BUCKET_MODEL_ID);
				m.texture("bucket_fluid", fluidPath.toString());
			});

			var tints = new JsonArray();

			var baseTint = new JsonObject();
			baseTint.addProperty("type", "minecraft:constant");
			baseTint.addProperty("value", -1);
			tints.add(baseTint);

			KubeColor tintColor = bucketColor;

			if (tintColor == null && fluidType.tint instanceof BlockTintFunction.Fixed(KubeColor color)) {
				tintColor = color;
			}

			var fluidTint = new JsonObject();
			fluidTint.addProperty("type", "minecraft:constant");
			fluidTint.addProperty("value", tintColor != null ? tintColor.kgy$getARGB() : -1);
			tints.add(fluidTint);

			generator.itemDefinition(bucketItem.getId(), bucketItem.getId().withPath(ID.ITEM), tints);
		}
	}
}
