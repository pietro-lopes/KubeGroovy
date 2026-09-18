package dev.uncandango.kubegroovy.plugin.builtin.wrapper;

import dev.uncandango.kubegroovy.KubeGroovy;
import dev.uncandango.kubegroovy.block.BlockBuilder;
import dev.uncandango.kubegroovy.block.custom.ButtonOrPressurePlateBuilder;
import dev.uncandango.kubegroovy.registry.RegistryKubeEvent;
import dev.uncandango.kubegroovy.util.KubeIdentifier;
import dev.uncandango.kubegroovy.util.TickDuration;
import dev.uncandango.kubegroovy.script.bridge.Context;
import dev.uncandango.kubegroovy.script.type.TypeInfo;
import dev.uncandango.kubegroovy.script.annotations.HideFromGY;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockSetType;

import java.util.ArrayList;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public record BuildingMaterialProperties(
	Blocks blocks,
	Optional<Boolean> baseBlock,
	Optional<Boolean> baseBlockSuffix,
	Consumer<BlockBuilder> properties,
	Optional<BlockSetType> behaviour,
	Optional<TickDuration> ticksToStayPressed
) {
	public record Blocks(
		Optional<Boolean> slab,
		Optional<Boolean> stairs,
		Optional<Boolean> fence,
		Optional<Boolean> fenceGate,
		Optional<Boolean> wall,
		Optional<Boolean> pressurePlate,
		Optional<Boolean> button,
		Optional<Boolean> trapdoor,
		Optional<Boolean> door
	) {
	}

	public static final TypeInfo TYPE_INFO = TypeInfo.of(BuildingMaterialProperties.class);

	private boolean add(Function<Blocks, Optional<Boolean>> func) {
		return blocks == null || func.apply(blocks).orElse(true);
	}

	@HideFromGY
	public void register(Context cx, RegistryKubeEvent<Block> event, KubeIdentifier id) {
		var builder = new ArrayList<BlockBuilder>();

		if (baseBlock.orElse(true)) {
			boolean _baseBlockSuffix = baseBlockSuffix.orElse(true);
			var baseBlock = (BlockBuilder) event.create(cx, _baseBlockSuffix ? id.withPath(p -> p + "_block") : id);
			builder.add(baseBlock);

			if (_baseBlockSuffix) {
				baseBlock.texture(id.wrapped().withPath(p -> "block/" + p).toString());
			}
		}

		if (add(Blocks::slab)) {
			builder.add((BlockBuilder) event.create(cx, id.withPath(p -> p + "_slab"), new KubeIdentifier(KubeGroovy.id("slab"))));
		}

		if (add(Blocks::stairs)) {
			builder.add((BlockBuilder) event.create(cx, id.withPath(p -> p + "_stairs"), new KubeIdentifier(KubeGroovy.id("stairs"))));
		}

		if (add(Blocks::fence)) {
			builder.add((BlockBuilder) event.create(cx, id.withPath(p -> p + "_fence"), new KubeIdentifier(KubeGroovy.id("fence"))));
		}

		if (add(Blocks::fenceGate)) {
			builder.add((BlockBuilder) event.create(cx, id.withPath(p -> p + "_fence_gate"), new KubeIdentifier(KubeGroovy.id("fence_gate"))));
		}

		if (add(Blocks::wall)) {
			builder.add((BlockBuilder) event.create(cx, id.withPath(p -> p + "_wall"), new KubeIdentifier(KubeGroovy.id("wall"))));
		}

		if (add(Blocks::pressurePlate)) {
			builder.add((BlockBuilder) event.create(cx, id.withPath(p -> p + "_pressure_plate"), new KubeIdentifier(KubeGroovy.id("pressure_plate"))));
		}

		if (add(Blocks::button)) {
			builder.add((BlockBuilder) event.create(cx, id.withPath(p -> p + "_button"), new KubeIdentifier(KubeGroovy.id("button"))));
		}

		if (add(Blocks::trapdoor)) {
			builder.add((BlockBuilder) event.create(cx, id.withPath(p -> p + "_trapdoor"), new KubeIdentifier(KubeGroovy.id("trapdoor"))));
		}

		if (add(Blocks::door)) {
			builder.add((BlockBuilder) event.create(cx, id.withPath(p -> p + "_door"), new KubeIdentifier(KubeGroovy.id("door"))));
		}

		for (var b : builder) {
			if (properties != null) {
				properties.accept(b);
			}

			if (b instanceof ButtonOrPressurePlateBuilder p) {
				behaviour.ifPresent(p::behaviour);
				ticksToStayPressed.ifPresent(p::ticksToStayPressed);
			}
		}
	}
}
