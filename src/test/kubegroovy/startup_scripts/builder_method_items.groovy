import dev.uncandango.kubegroovy.item.custom.ShearsItemBuilder
import dev.uncandango.kubegroovy.item.MutableToolTier
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.ToolMaterial

// Every public item/food/behavior method has a direct DSL call in this fixture.
$StartupEvents.registry('item') { event ->
	def itemBuilder = event.create('coverage_item')
	itemBuilder.with {
		translationKey('item.kubegroovy.coverage_item')
		displayName('Coverage Item')
		formattedDisplayName('Coverage Formatted')
		formattedDisplayName()
		texture('kubegroovy:item/coverage_item')
		texture(['layer0'], 'kubegroovy:item/coverage_item_layer')
		textures([layer0: 'kubegroovy:item/coverage_item', layer1: 'kubegroovy:item/coverage_item_overlay'])
		parentModel('minecraft:item/generated')
		modelGenerator { modelBuilder ->
			modelBuilder.with {
				parent('minecraft:item/generated')
				textures([layer0: 'kubegroovy:item/coverage_item', particle: 'kubegroovy:item/coverage_item'])
			}
		}
		component("custom_name", Component.literal('Coverage'))
		maxStackSize(32)
		unstackable()
		maxDamage(42)
		burnTime(40)
		containerItem('minecraft:bucket')
		subtypes { stack -> [stack] }
		rarity('rare')
		group(null)
		color(0) { stack -> 0xFFFFFFFF }
		color { stack -> 0xFF00FFFF }
		food(3, 0.6f)
		food { foodBuilder ->
			foodBuilder.with {
				nutrition(4)
				saturation(0.8f)
				alwaysEdible(false)
				alwaysEdible()
				eatSeconds(1.2f)
				fastToEat()
				usingConvertsTo(ItemStack.EMPTY)
				effect("luck", 20, 0, 1.0f)
				removeEffect("luck")
				eaten { eaten -> }
				getUsingConvertsTo()
				getEatSeconds()
				getEffects()
			}
		}
		fireResistant(false)
		fireResistant()
		jukeboxPlayable('minecraft:13')
		disableRepair()
		glow(true)
		tooltip('Coverage tooltip')
		barColor { stack -> 0xFFAA5500 }
		barWidth { stack -> 8 }
		name { stack -> Component.literal('Dynamic coverage') }
		useAnimation('eat')
		useDuration { stack, entity -> 20 }
		use { level, player, hand -> true }
		finishUsing { stack, level, entity -> stack }
		releaseUsing { stack, level, entity, ticks -> }
		hurtEnemy { context -> true }
		isPiglinCurrency(true)
		isPiglinCurrency { stack -> true }
		protectFromGaze(true)
		protectFromGaze { stack, player, entity -> true }
		makesPiglinsNeutral(true)
		makesPiglinsNeutral { stack, entity -> true }
		craftingRemainingItem { stack -> stack }
		getEntityLifespan(600)
		getEntityLifespan { stack, level -> 600 }
		canWalkOnPowderedSnow(true)
		canWalkOnPowderedSnow { stack, entity -> true }
		canPerformAction { stack, action -> true }
		canBeHurtBy { stack, source -> true }
		onlyHurtBy([])
		immuneTo([])
		applyEnchantments { stack, enchantments -> stack }
	}

	event.create('coverage_sword', 'sword').with {
		tier(new MutableToolTier(ToolMaterial.DIAMOND))
		attackDamageBaseline(5)
		disableBlockingForSeconds(2)
		speedBaseline(-2.0)
		modifyTier { tierBuilder ->
			tierBuilder.with {
				setUses(1000)
				setSpeed(9)
				setAttackDamageBonus(4)
				setEnchantmentValue(15)
			}
		}
		attackDamageBonus(4)
		speed(1.5)
	}
	event.create('coverage_pickaxe', 'pickaxe').with {
		attackDamageBaseline(2)
		speedBaseline(-2.5)
	}
	event.create('coverage_axe', 'axe').with {
		attackDamageBaseline(7)
		speedBaseline(-3.0)
	}
	event.create('coverage_shovel', 'shovel').with {
		attackDamageBaseline(2)
		speedBaseline(-2.8)
	}
	event.create('coverage_hoe', 'hoe').with {
		attackDamageBaseline(1)
		speedBaseline(-2.2)
	}
	event.create('coverage_shears', 'shears').with {
		speedBaseline(7)
	}
	ShearsItemBuilder.isCustomShears(ItemStack.EMPTY)
	event.create('coverage_helmet', 'helmet').with {
		material('iron')
	}
	event.create('coverage_chestplate', 'chestplate').with {
		material('gold')
	}
	event.create('coverage_leggings', 'leggings').with {
		material('diamond')
	}
	event.create('coverage_boots', 'boots').with {
		material('netherite')
	}
	event.create('coverage_animal_armor', 'animal_armor').with {
		kind('wolf')
		material('iron')
	}

	def templateBuilder = event.create('coverage_smithing', 'smithing_template')
	templateBuilder.with {
		appliesTo('Custom Armor')
		ingredients('Custom Ingots')
		appliesToSlotDescription('Custom base')
		ingredientsSlotDescription('Custom ingredient')
		addAppliesToSlotIcon('minecraft:item/empty_armor_slot_helmet')
		addIngredientsSlotIcon('minecraft:item/empty_slot_ingot')
		armorIcons()
		equipmentIcons()
		toolIcons()
		ingotAndCrystalIcons()
		crystalIcons()
		ingotIcon()
		dustIcon()
		shardIcon()
		diamondIcon()
		emeraldIcon()
		quartzIcon()
		lapisIcon()
		swordIcon()
		shovelIcon()
		axeIcon()
		pickaxeIcon()
		hoeIcon()
		helmetIcon()
		chestplateIcon()
		leggingsIcon()
		bootsIcon()
		displayName('Coverage Smithing')
	}
}