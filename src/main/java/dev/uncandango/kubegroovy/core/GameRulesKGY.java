package dev.uncandango.kubegroovy.core;

import dev.uncandango.kubegroovy.script.annotations.HideFromGY;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;
import org.jspecify.annotations.Nullable;

public interface GameRulesKGY {
	@HideFromGY
	default GameRules kgy$self() {
		return (GameRules) this;
	}

	@Nullable
	MinecraftServer kgy$getServer();

	@Nullable
	default GameRule<?> kgy$getRule(String rule) {
		Identifier id = Identifier.tryParse(rule);
		if (id == null) {
			id = Identifier.fromNamespaceAndPath("minecraft", rule);
		}
		return BuiltInRegistries.GAME_RULE.getOptional(id).orElse(null);
	}

	default String kgy$getString(String rule) {
		GameRule<?> r = kgy$getRule(rule);
		if (r == null) {
			return "";
		}
		return kgy$serialize(kgy$self(), r);
	}

	default boolean kgy$getBoolean(String rule) {
		GameRule<?> r = kgy$getRule(rule);
		if (r == null) {
			return false;
		}
		Object v = kgy$self().get((GameRule<Object>) r);
		return v instanceof Boolean b && b;
	}

	default int kgy$getInt(String rule) {
		GameRule<?> r = kgy$getRule(rule);
		if (r == null) {
			return 0;
		}
		Object v = kgy$self().get((GameRule<Object>) r);
		return v instanceof Integer i ? i : 0;
	}

	default void kgy$set(String rule, String value) {
		GameRule r = kgy$getRule(rule);
		if (r == null) {
			return;
		}

		r.deserialize(value).result()
			.ifPresent((v) -> kgy$self().set(r, v, kgy$getServer()));
	}

	static <T> String kgy$serialize(GameRules rules, GameRule<T> rule) {
		return rule.serialize(rules.get(rule));
	}
}
