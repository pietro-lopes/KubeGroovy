package dev.uncandango.kubegroovy.testmod.builder;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.flag.FeatureFlag;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.testframework.DynamicTest;
import net.neoforged.testframework.annotation.ForEachTest;
import net.neoforged.testframework.annotation.OnInit;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.GameTest;
import net.neoforged.testframework.impl.test.MethodBasedEventTest;

import java.util.List;

import static dev.uncandango.kubegroovy.testmod.GameAsserts.assertj;
import static net.minecraft.world.item.CreativeModeTabs.CACHED_PARAMETERS;
import static net.minecraft.world.item.CreativeModeTabs.tryRebuildTabContents;
import static org.assertj.core.api.Assertions.assertThat;

/// Integration for the wiki registry example scripts (`wiki_builders.groovy`): the item, block, creative
/// tab and mob effect all register, and one wiki-set property or tag is verified for each.
@ForEachTest(groups = "kubegroovy.builder")
public class RegistryBuilderTests {
	private static final Ingredient TAB_CONTENT = Ingredient.of(Items.DIRT, Items.GRASS_BLOCK);

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
