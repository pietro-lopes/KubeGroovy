package dev.uncandango.kubegroovy.client.icontest;

import dev.uncandango.kubegroovy.client.icon.IconExportBatch;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IconExportBatchTest {
	@Test
	void gridFitsEverySupportedScaleAndKeepsCellsSeparate() {
		for (int scale = 1; scale <= 128; scale++) {
			int cellSize = ((scale + 15) / 16) * 16;
			var layout = IconExportBatch.fit(1920, 1080, cellSize);
			assertEquals(64, layout.capacity());
			assertEquals(8, layout.columns());
			for (int i = 0; i < layout.capacity(); i++) {
				assertTrue(layout.pixelX(i) + cellSize <= 1920);
				assertTrue(layout.pixelY(i) + cellSize <= 1080);
				if (i > 0) {
					assertTrue(layout.pixelX(i) != layout.pixelX(i - 1) || layout.pixelY(i) != layout.pixelY(i - 1));
				}
			}
			assertEquals(0, layout.pixelX(8));
			assertEquals(cellSize, layout.pixelY(8));
			assertEquals(7 * cellSize, layout.pixelX(63));
			assertEquals(7 * cellSize, layout.pixelY(63));
		}
	}

	@Test
	void resizedAndNarrowWindowsReduceTheBatchWithoutClipping() {
		var layout = IconExportBatch.fit(650, 360, 128);
		assertEquals(5, layout.columns());
		assertEquals(10, layout.capacity());
		assertEquals(512, layout.pixelX(9));
		assertEquals(128, layout.pixelY(9));
		assertEquals(1, IconExportBatch.fit(128, 128, 128).capacity());
		assertEquals(64, IconExportBatch.fit(32, 4096, 32).capacity());
		assertEquals(64, IconExportBatch.fit(Integer.MAX_VALUE, Integer.MAX_VALUE, 16).capacity());
	}

	@Test
	void undersizedFramebuffersDoNotProduceUsableCells() {
		for (var layout : new IconExportBatch[]{IconExportBatch.fit(127, 512, 128),
			IconExportBatch.fit(512, 127, 128), IconExportBatch.fit(0, 0, 32)}) {
			assertEquals(0, layout.capacity());
			assertThrows(IndexOutOfBoundsException.class, () -> layout.pixelX(0));
			assertThrows(IndexOutOfBoundsException.class, () -> layout.pixelY(0));
		}
		assertThrows(IllegalArgumentException.class, () -> IconExportBatch.fit(1920, 1080, 0));
	}
}