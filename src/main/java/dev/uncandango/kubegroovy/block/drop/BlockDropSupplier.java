package dev.uncandango.kubegroovy.block.drop;

public interface BlockDropSupplier {
	BlockDropSupplier NO_DROPS = () -> BlockDrops.EMPTY;

	BlockDrops get();
}
