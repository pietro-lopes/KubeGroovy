package dev.uncandango.kubegroovy.core.mixin;

import dev.uncandango.kubegroovy.core.GameRulesKGY;
import dev.uncandango.kubegroovy.script.annotations.RemapPrefixForGY;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleTypeVisitor;
import net.minecraft.world.level.gamerules.GameRules;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Mixin(GameRules.class)
@RemapPrefixForGY("kgy$")
public abstract class GameRulesMixin implements GameRulesKGY {
	@Shadow
	public abstract <T> T get(GameRule<T> gameRule);

	@Shadow
	public abstract <T> void set(GameRule<T> gameRule, T value, @Nullable MinecraftServer server);

	@Shadow
	public abstract void visitGameRuleTypes(GameRuleTypeVisitor visitor);

	@Unique
	private @Nullable Map<String, GameRule<?>> kgy$ruleCache;

	@Unique
	private void kgy$initCache() {
		if (kgy$ruleCache != null) {
			return;
		}

		kgy$ruleCache = new HashMap<>();

		visitGameRuleTypes(new GameRuleTypeVisitor() {
			@Override
			public <T> void visit(GameRule<T> gameRule) {
				kgy$ruleCache.put(gameRule.id(), gameRule);
				kgy$ruleCache.put(gameRule.getIdentifier().toString(), gameRule);
			}
		});
	}

	@Unique
	@Nullable
	private GameRule<?> kgy$getCachedRule(String rule) {
		kgy$initCache();
		return Objects.requireNonNull(kgy$ruleCache).get(rule);
	}

	@Override
	@Nullable
	public MinecraftServer kgy$getServer() {
		return ServerLifecycleHooks.getCurrentServer();
	}

	@Override
	@Nullable
	public GameRule<?> kgy$getRule(String rule) {
		GameRule<?> cached = kgy$getCachedRule(rule);
		if (cached != null) {
			return cached;
		}
		return GameRulesKGY.super.kgy$getRule(rule);
	}
}
