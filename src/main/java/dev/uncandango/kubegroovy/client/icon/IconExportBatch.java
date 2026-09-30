package dev.uncandango.kubegroovy.client.icon;

/// Pixel layout for one screenshot. Computed again each frame so resizing the
/// window or changing the requested icon size never puts a cell outside it.
public record IconExportBatch(int columns, int capacity, int cellSize) {
	static final int MAX_ICONS = 64;
	private static final int MAX_COLUMNS = 8;

	public static IconExportBatch fit(int width, int height, int cellSize) {
		if (cellSize <= 0) {
			throw new IllegalArgumentException("Icon cell size must be positive");
		}

		int columns = Math.min(MAX_COLUMNS, Math.max(0, width) / cellSize);
		int rows = Math.min(MAX_ICONS, Math.max(0, height) / cellSize);
		return new IconExportBatch(columns, Math.min(MAX_ICONS, columns * rows), cellSize);
	}

	public int pixelX(int index) {
		java.util.Objects.checkIndex(index, capacity);
		return index % columns * cellSize;
	}

	public int pixelY(int index) {
		java.util.Objects.checkIndex(index, capacity);
		return index / columns * cellSize;
	}
}