package dev.uncandango.kubegroovy.level.ruletest;

import com.mojang.serialization.MapCodec;
import dev.uncandango.kubegroovy.KubeGroovy;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleTestType;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public interface KubeGroovyRuleTests {
	DeferredRegister<RuleTestType<?>> REGISTRY = DeferredRegister.create(Registries.RULE_TEST, KubeGroovy.MOD_ID);

	Supplier<RuleTestType<InvertRuleTest>> INVERT = register("invert", InvertRuleTest.CODEC);
	Supplier<RuleTestType<AlwaysFalseRuleTest>> ALWAYS_FALSE = register("always_false", AlwaysFalseRuleTest.CODEC);
	Supplier<RuleTestType<AllMatchRuleTest>> ALL_MATCH = register("all_match", AllMatchRuleTest.CODEC);
	Supplier<RuleTestType<AnyMatchRuleTest>> ANY_MATCH = register("any_match", AnyMatchRuleTest.CODEC);

	static <P extends RuleTest> Supplier<RuleTestType<P>> register(String id, MapCodec<P> codec) {
		var type = (RuleTestType<P>) () -> codec;
		return REGISTRY.register(id, () -> type);
	}
}
