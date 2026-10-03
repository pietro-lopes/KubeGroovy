package dev.uncandango.kubegroovy.utiltest;

import com.google.gson.JsonPrimitive;
import dev.uncandango.kubegroovy.script.bridge.Context;
import dev.uncandango.kubegroovy.script.bridge.ContextFactory;
import dev.uncandango.kubegroovy.util.TickDuration;
import dev.uncandango.kubegroovy.util.TickTemporalUnit;
import dev.uncandango.kubegroovy.util.TimeGY;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimeTests {
	@Test
	void tickDurationOfCachesZero() {
		assertSame(TickDuration.ZERO, TickDuration.of(0L));
		assertEquals(0L, TickDuration.of(0L).ticks());
		assertEquals(5L, TickDuration.of(5L).ticks());
	}

	@Test
	void tickDurationTemporalMethods() {
		var duration = TickDuration.of(20L);
		assertEquals(20L, duration.get(TickTemporalUnit.INSTANCE));
		assertEquals(0L, duration.get(ChronoUnit.MILLIS));
		assertEquals(1, duration.getUnits().size());

		var instant = Instant.parse("2026-01-01T00:00:00Z");
		assertEquals(instant.plusMillis(1000L), duration.addTo(instant));
		assertEquals(instant.minusMillis(1000L), duration.subtractFrom(instant));
		assertSame(instant, TickDuration.ZERO.addTo(instant));
		assertSame(instant, TickDuration.ZERO.subtractFrom(instant));

		assertEquals("20 ticks", duration.toString());
		assertEquals(Integer.MAX_VALUE, TickDuration.of((long) Integer.MAX_VALUE + 100L).intTicks());
		assertEquals(Integer.MIN_VALUE, TickDuration.of((long) Integer.MIN_VALUE - 100L).intTicks());
	}

	@Test
	void tickDurationWrap() {
		ScopedValue.where(Context.CURRENT, new Context(new ContextFactory())).run(() -> {
			assertSame(TickDuration.ZERO, TickDuration.wrap(null));
			var same = TickDuration.of(7L);
			assertSame(same, TickDuration.wrap(same));
			assertEquals(42L, TickDuration.wrap(42).ticks());
			assertEquals(9L, TickDuration.wrap(new JsonPrimitive(9L)).ticks());
			assertEquals(20L, TickDuration.wrap("1s").ticks());
			assertEquals(10L, TickDuration.wrap("10t").ticks());
		});
	}

	@Test
	void tickTemporalUnitBasics() {
		assertEquals(Duration.ofMillis(50L), TickTemporalUnit.INSTANCE.getDuration());
		assertFalse(TickTemporalUnit.INSTANCE.isDurationEstimated());
		assertFalse(TickTemporalUnit.INSTANCE.isDateBased());
		assertTrue(TickTemporalUnit.INSTANCE.isTimeBased());
		assertEquals("Ticks", TickTemporalUnit.INSTANCE.toString());

		var start = Instant.parse("2026-01-01T00:00:00Z");
		assertEquals(start.plusMillis(100L), TickTemporalUnit.INSTANCE.addTo(start, 2L));
		assertEquals(4L, TickTemporalUnit.INSTANCE.between(start, start.plusMillis(200L)));
	}

	@Test
	void wrapTemporalAmount() {
		ScopedValue.where(Context.CURRENT, new Context(new ContextFactory())).run(() -> {
			assertEquals(Duration.ofMillis(123L), TimeGY.wrapTemporalAmount(123));
			var amount = TickDuration.of(3L);
			assertSame(amount, TimeGY.wrapTemporalAmount(amount));
			assertEquals(Duration.ofSeconds(5L), TimeGY.wrapTemporalAmount("5s"));
			assertEquals(TickDuration.of(10L), TimeGY.wrapTemporalAmount("10t"));
			assertEquals(Duration.ofMillis(1500L), TimeGY.wrapDuration("1.5s"));

			assertThrows(dev.uncandango.kubegroovy.error.KubeRuntimeException.class,
				() -> TimeGY.wrapTemporalAmount(null));
			assertThrows(dev.uncandango.kubegroovy.error.KubeRuntimeException.class,
				() -> TimeGY.wrapTemporalAmount("not a time"));
			assertThrows(dev.uncandango.kubegroovy.error.KubeRuntimeException.class,
				() -> TimeGY.wrapTemporalAmount(new Object()));
		});
	}

	@Test
	void readDuration() {
		assertTrue(TimeGY.readDuration("5s").result().isPresent());
		assertEquals(Duration.ofSeconds(5L), TimeGY.readDuration("5s").getOrThrow());
		assertEquals(Duration.ofMillis(500L), TimeGY.readDuration("10t").getOrThrow());
		assertTrue(TimeGY.readDuration("bogus").error().isPresent());
		assertTrue(TimeGY.readDuration("").error().isPresent());
	}

	@Test
	void msToStringAndTimestamp() {
		assertEquals("500 ms", TimeGY.msToString(500L));
		assertEquals("1.500 s", TimeGY.msToString(1500L));

		var calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
		calendar.set(2026, Calendar.JANUARY, 2, 3, 4, 5);
		var builder = new StringBuilder();
		TimeGY.appendTimestamp(builder, calendar);
		assertEquals("03:04:05", builder.toString());

		calendar.set(2026, Calendar.JANUARY, 2, 13, 14, 15);
		builder.setLength(0);
		TimeGY.appendTimestamp(builder, calendar);
		assertEquals("13:14:15", builder.toString());
	}
}
