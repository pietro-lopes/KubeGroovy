package dev.uncandango.kubegroovy.testmod.wrapper;

import net.neoforged.testframework.DynamicTest;
import net.neoforged.testframework.annotation.ForEachTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.GameTest;

import static dev.uncandango.kubegroovy.testmod.GameAsserts.assertFired;
import static dev.uncandango.kubegroovy.testmod.GameAsserts.assertVerified;

/// Category 2. Verifies explicit Groovy casts in the `wrapper_checks.groovy` fixture.
@ForEachTest(groups = "kubegroovy.wrapper")
public class WrapperTests {
	private static final String[] IDS = {
		"wrapper.vec3",
		"wrapper.blockpos",
		"wrapper.itemstack",
		"wrapper.component",
		"wrapper.nbt",
		"wrapper.color",
		"wrapper.id",
		"wrapper.uuid",
		"wrapper.tristate",
		"wrapper.duration",
		"wrapper.regexp",
		"wrapper.int_provider",
		"wrapper.map_color",
		"wrapper.registry_predicate",
		"wrapper.json_object",
		"wrapper.json_array",
		"wrapper.json_element",
		"wrapper.json_primitive",
		"wrapper.temporal_amount",
		"wrapper.tick_duration",
		"wrapper.kube_identifier",
		"wrapper.nbt_collection",
		"wrapper.nbt_list",
		"wrapper.nbt_tag",
		"wrapper.data_component_type",
		"wrapper.data_component_map",
		"wrapper.data_component_patch",
		"wrapper.vec3i",
		"wrapper.item",
		"wrapper.item_like",
		"wrapper.item_enchantments",
		"wrapper.aabb",
		"wrapper.float_provider",
		"wrapper.number_provider",
		"wrapper.block_set_type",
		"wrapper.block_state",
		"wrapper.item_ability",
		"wrapper.color_rgba",
		"wrapper.ingredient",
		"wrapper.item_predicate",
		"wrapper.sized_ingredient",
		"wrapper.block_state_predicate",
		"wrapper.rule_test",
		"wrapper.fluid_stack",
		"wrapper.fluid_ingredient",
		"wrapper.sized_fluid_ingredient",
		"wrapper.recipe_filter",
		"wrapper.slot_filter",
		"wrapper.tool_material",
		"wrapper.damage_source",
		"wrapper.entity_selector",
		"wrapper.replacement_match",
		"wrapper.replacement_match_info",
		"wrapper.stat",
		"wrapper.sound_type",
		"wrapper.particle_options",
		"wrapper.item_tint",
		"wrapper.block_tint",
		"wrapper.text_color",
		"wrapper.click_event",
		"wrapper.path",
		"wrapper.file",
		"wrapper.mob_category",
		"wrapper.armor_type",
		"wrapper.entity_target",
		"wrapper.armor_material",
		"wrapper.player_selector",
		"wrapper.component_alias",
		"wrapper.item_lore",
		"wrapper.item_attribute_modifiers",
		"wrapper.fireworks",
		"wrapper.kube_icon",
		"wrapper.unit",
		"wrapper.custom_data",
		"wrapper.custom_model_data",
		"wrapper.lock_code",
		"wrapper.block_state_properties",
		"wrapper.item_resource",
		"wrapper.fluid_resource",
		"wrapper.registry_related",
		"wrapper.generics",
	};

	@GameTest
	@EmptyTemplate
	@TestHolder(value = "wrapper_conversions", description = "Explicit Groovy casts use the registered type wrappers")
	static void wrapperConversions(final DynamicTest test) {
		test.onGameTest(helper -> {
			var sequence = helper.startSequence();

			for (var id : IDS) {
				sequence = sequence
					.thenWaitUntil(() -> assertFired(helper, id))
					.thenExecute(() -> assertVerified(helper, id));
			}

			sequence.thenSucceed();
		});
	}
}
