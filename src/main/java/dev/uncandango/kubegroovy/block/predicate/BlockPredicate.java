package dev.uncandango.kubegroovy.block.predicate;

import dev.uncandango.kubegroovy.level.LevelBlock;

public interface BlockPredicate {
	boolean check(LevelBlock block);
}