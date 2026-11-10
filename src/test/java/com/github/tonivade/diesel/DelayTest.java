/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DelayTest {

  @Nested
  class ConstructorTest {

    @Test
    void shouldCreateDelay() {
      Delay delay = new Delay(100);

      assertEquals(100, delay.nanos());
    }

    @Test
    void shouldAllowZero() {
      Delay delay = new Delay(0);

      assertEquals(0, delay.nanos());
    }

    @Test
    void shouldAllowMaximumValue() {
      Delay delay = new Delay(Delay.MAX_NANOS);

      assertEquals(Delay.MAX_NANOS, delay.nanos());
    }

    @Test
    void shouldAllowCollapseToMaxNanos() {
      Delay delay = new Delay(Delay.MAX_NANOS + 1);

      assertEquals(Delay.MAX_NANOS, delay.nanos());
    }

    @Test
    void shouldRejectNegativeValue() {
      assertThrows(IllegalArgumentException.class, () -> new Delay(-1));
    }
  }

  @Nested
  class ConstantsTest {

    @Test
    void shouldHaveZeroConstant() {
      assertEquals(0, Delay.ZERO.nanos());
      assertTrue(Delay.ZERO.isZero());
    }

    @Test
    void shouldHaveMaximumConstant() {
      assertEquals(Delay.MAX_NANOS, Delay.MAX.nanos());
      assertTrue(Delay.MAX.isMax());
    }
  }

  @Nested
  class FactoryMethodsTest {

    @Test
    void fromDurationContract() {
      assertSame(Delay.ZERO, Delay.from(Duration.ZERO));
      assertSame(Delay.MAX, Delay.from(Duration.ofNanos(Delay.MAX_NANOS)));
      assertSame(Delay.MAX, Delay.from(Duration.ofNanos(Delay.MAX_NANOS + 1)));
      assertSame(Delay.MAX, Delay.from(Duration.ofNanos(Long.MAX_VALUE)));
      assertSame(Delay.MAX, Delay.from(Duration.ofSeconds(Long.MAX_VALUE)));
      assertThrows(IllegalArgumentException.class, () -> Delay.from(Duration.ofNanos(-1)));
      assertThrows(IllegalArgumentException.class, () -> Delay.from(Duration.ofNanos(Long.MIN_VALUE)));
      assertEquals(new Delay(1), Delay.from(Duration.ofNanos(1)));
      assertEquals(new Delay(1_000), Delay.from(Duration.ofNanos(1_000)));
      assertEquals(new Delay(1_000_000), Delay.from(Duration.ofMillis(1)));
      assertEquals(new Delay(1_000_000_000), Delay.from(Duration.ofSeconds(1)));
      assertEquals(new Delay(60_000_000_000L), Delay.from(Duration.ofMinutes(1)));
      assertEquals(new Delay(3_600_000_000_000L), Delay.from(Duration.ofHours(1)));
    }

    @Test
    void ofNanosContract() {
      assertSame(Delay.ZERO, Delay.ofNanos(0));
      assertSame(Delay.MAX, Delay.ofNanos(Delay.MAX_NANOS));
      assertSame(Delay.MAX, Delay.ofNanos(Delay.MAX_NANOS + 1));
      assertSame(Delay.MAX, Delay.ofNanos(Long.MAX_VALUE));
      assertThrows(IllegalArgumentException.class, () -> Delay.ofNanos(-1));
      assertThrows(IllegalArgumentException.class, () -> Delay.ofNanos(Long.MIN_VALUE));
      assertEquals(new Delay(1), Delay.ofNanos(1));
    }

    @Test
    void ofMicrosContract() {
      assertSame(Delay.ZERO, Delay.ofMicros(0));
      assertSame(Delay.MAX, Delay.ofMicros(Delay.MAX_NANOS / 1_000));
      assertSame(Delay.MAX, Delay.ofMicros((Delay.MAX_NANOS / 1_000) + 1));
      assertSame(Delay.MAX, Delay.ofMicros(Long.MAX_VALUE));
      assertThrows(IllegalArgumentException.class, () -> Delay.ofMicros(-1));
      assertThrows(IllegalArgumentException.class, () -> Delay.ofMicros(Long.MIN_VALUE));
      assertEquals(new Delay(1_000), Delay.ofMicros(1));
    }

    @Test
    void ofMillisContract() {
      assertSame(Delay.ZERO, Delay.ofMillis(0));
      assertSame(Delay.MAX, Delay.ofMillis(Delay.MAX_NANOS / 1_000_000));
      assertSame(Delay.MAX, Delay.ofMillis((Delay.MAX_NANOS / 1_000_000) + 1));
      assertSame(Delay.MAX, Delay.ofMillis(Long.MAX_VALUE));
      assertThrows(IllegalArgumentException.class, () -> Delay.ofMillis(-1));
      assertThrows(IllegalArgumentException.class, () -> Delay.ofMillis(Long.MIN_VALUE));
      assertEquals(new Delay(1_000_000), Delay.ofMillis(1));
    }

    @Test
    void ofSecondsContract() {
      assertSame(Delay.ZERO, Delay.ofSeconds(0));
      assertSame(Delay.MAX, Delay.ofSeconds(Delay.MAX_NANOS / 1_000_000_000));
      assertSame(Delay.MAX, Delay.ofSeconds((Delay.MAX_NANOS / 1_000_000_000) + 1));
      assertSame(Delay.MAX, Delay.ofSeconds(Long.MAX_VALUE));
      assertThrows(IllegalArgumentException.class, () -> Delay.ofSeconds(-1));
      assertThrows(IllegalArgumentException.class, () -> Delay.ofSeconds(Long.MIN_VALUE));
      assertEquals(new Delay(1_000_000_000), Delay.ofSeconds(1));
    }

    @Test
    void ofMinutesContract() {
      assertSame(Delay.ZERO, Delay.ofMinutes(0));
      assertSame(Delay.MAX, Delay.ofMinutes(Delay.MAX_NANOS / 60_000_000_000L));
      assertSame(Delay.MAX, Delay.ofMinutes((Delay.MAX_NANOS / 60_000_000_000L) + 1));
      assertSame(Delay.MAX, Delay.ofMinutes(Long.MAX_VALUE));
      assertThrows(IllegalArgumentException.class, () -> Delay.ofMinutes(-1));
      assertThrows(IllegalArgumentException.class, () -> Delay.ofMinutes(Long.MIN_VALUE));
      assertEquals(new Delay(60_000_000_000L), Delay.ofMinutes(1));
    }

    @Test
    void ofHoursContract() {
      assertSame(Delay.ZERO, Delay.ofHours(0));
      assertSame(Delay.MAX, Delay.ofHours(Delay.MAX_NANOS / 3_600_000_000_000L));
      assertSame(Delay.MAX, Delay.ofHours((Delay.MAX_NANOS / 3_600_000_000_000L) + 1));
      assertSame(Delay.MAX, Delay.ofHours(Long.MAX_VALUE));
      assertThrows(IllegalArgumentException.class, () -> Delay.ofHours(-1));
      assertThrows(IllegalArgumentException.class, () -> Delay.ofHours(Long.MIN_VALUE));
      assertEquals(new Delay(3_600_000_000_000L), Delay.ofHours(1));
    }

    @Test
    void shouldCreateDelayFromHours() {
      assertEquals(3_600_000_000_000L, Delay.ofHours(1).nanos());
    }

    @Test
    void shouldCreateDelayFromMinutes() {
      assertEquals(60_000_000_000L, Delay.ofMinutes(1).nanos());
    }

    @Test
    void shouldCreateDelayFromSeconds() {
      assertEquals(1_000_000_000L, Delay.ofSeconds(1).nanos());
    }

    @Test
    void shouldCreateDelayFromMillis() {
      assertEquals(1_000_000L, Delay.ofMillis(1).nanos());
    }

    @Test
    void shouldCreateDelayFromMicros() {
      assertEquals(1_000L, Delay.ofMicros(1).nanos());
    }

    @Test
    void shouldCreateDelayFromNanos() {
      assertEquals(1L, Delay.ofNanos(1).nanos());
    }

    @Test
    void shouldCreateZeroDelayFromAllFactoryMethods() {
      assertAll(() -> assertEquals(Delay.ZERO, Delay.ofHours(0)), () -> assertEquals(Delay.ZERO, Delay.ofMinutes(0)),
          () -> assertEquals(Delay.ZERO, Delay.ofSeconds(0)), () -> assertEquals(Delay.ZERO, Delay.ofMillis(0)),
          () -> assertEquals(Delay.ZERO, Delay.ofMicros(0)), () -> assertEquals(Delay.ZERO, Delay.ofNanos(0)));
    }

    @Test
    void shouldRejectNegativeHours() {
      assertThrows(IllegalArgumentException.class, () -> Delay.ofHours(-1));
    }

    @Test
    void shouldRejectNegativeMinutes() {
      assertThrows(IllegalArgumentException.class, () -> Delay.ofMinutes(-1));
    }

    @Test
    void shouldRejectNegativeSeconds() {
      assertThrows(IllegalArgumentException.class, () -> Delay.ofSeconds(-1));
    }

    @Test
    void shouldRejectNegativeMillis() {
      assertThrows(IllegalArgumentException.class, () -> Delay.ofMillis(-1));
    }

    @Test
    void shouldRejectNegativeMicros() {
      assertThrows(IllegalArgumentException.class, () -> Delay.ofMicros(-1));
    }

    @Test
    void shouldRejectNegativeNanos() {
      assertThrows(IllegalArgumentException.class, () -> Delay.ofNanos(-1));
    }
  }

  @Nested
  class IsZeroTest {

    @Test
    void shouldReturnTrueWhenDelayIsZero() {
      assertTrue(Delay.ZERO.isZero());
    }

    @Test
    void shouldReturnFalseWhenDelayIsNotZero() {
      assertFalse(Delay.ofNanos(1).isZero());
    }

    @Test
    void shouldReturnFalseForMaximumDelay() {
      assertFalse(Delay.MAX.isZero());
    }
  }

  @Nested
  class IsMaxTest {

    @Test
    void shouldReturnTrueWhenDelayIsMaximum() {
      assertTrue(Delay.MAX.isMax());
    }

    @Test
    void shouldReturnFalseWhenDelayIsNotMaximum() {
      assertFalse(Delay.ofNanos(1).isMax());
    }

    @Test
    void shouldReturnFalseForZeroDelay() {
      assertFalse(Delay.ZERO.isMax());
    }
  }

  @Nested
  class ToDurationTest {

    @Test
    void shouldConvertZeroDelay() {
      assertEquals(Duration.ZERO, Delay.ZERO.toDuration());
    }

    @Test
    void shouldConvertDelayToDuration() {
      assertEquals(Duration.ofSeconds(1), Delay.ofSeconds(1).toDuration());
    }

    @Test
    void shouldConvertMaximumDelay() {
      assertEquals(Duration.ofNanos(Delay.MAX_NANOS), Delay.MAX.toDuration());
    }
  }

  @Nested
  class CompareToTest {

    @Test
    void shouldReturnNegativeWhenLessThanOther() {
      assertTrue(Delay.ofNanos(10).compareTo(Delay.ofNanos(20)) < 0);
    }

    @Test
    void shouldReturnPositiveWhenGreaterThanOther() {
      assertTrue(Delay.ofNanos(20).compareTo(Delay.ofNanos(10)) > 0);
    }

    @Test
    void shouldReturnZeroWhenEqual() {
      assertEquals(0, Delay.ofNanos(10).compareTo(Delay.ofNanos(10)));
    }

    @Test
    void shouldCompareZeroAndMaximum() {
      assertTrue(Delay.ZERO.compareTo(Delay.MAX) < 0);
      assertTrue(Delay.MAX.compareTo(Delay.ZERO) > 0);
    }

    @Test
    void shouldCompareMaximumValuesWithoutOverflow() {
      assertTrue(Delay.ofNanos(Delay.MAX_NANOS - 1).compareTo(Delay.MAX) < 0);
    }
  }

  @Nested
  class ScaleTest {

    @Test
    void shouldReturnZeroWhenFactorIsZero() {
      assertEquals(Delay.ZERO, Delay.ofNanos(100).scale(0));
    }

    @Test
    void shouldReturnZeroWhenFactorIsNegativeZero() {
      assertEquals(Delay.ZERO, Delay.ofNanos(100).scale(-0.0));
    }

    @Test
    void shouldReturnZeroWhenDelayIsZero() {
      assertEquals(Delay.ZERO, Delay.ZERO.scale(10));
    }

    @Test
    void shouldReturnSameDelayWhenFactorIsOne() {
      Delay delay = Delay.ofNanos(100);

      assertEquals(delay, delay.scale(1));
    }

    @Test
    void shouldIncreaseDelayWhenFactorIsGreaterThanOne() {
      assertEquals(Delay.ofNanos(200), Delay.ofNanos(100).scale(2));
    }

    @Test
    void shouldDecreaseDelayWhenFactorIsBetweenZeroAndOne() {
      assertEquals(Delay.ofNanos(50), Delay.ofNanos(100).scale(0.5));
    }

    @Test
    void shouldRoundResultWhenIncreasingDelay() {
      assertEquals(Delay.ofNanos(2), Delay.ofNanos(1).scale(1.5));
    }

    @Test
    void shouldRoundResultWhenDecreasingDelay() {
      assertEquals(Delay.ofNanos(1), Delay.ofNanos(1).scale(0.5));
    }

    @Test
    void shouldReturnZeroWhenDecreasingVerySmallDelay() {
      assertEquals(Delay.ZERO, Delay.ofNanos(1).scale(0.4));
    }

    @Test
    void shouldReturnMaximumWhenScalingMaximumDelayUp() {
      assertEquals(Delay.MAX, Delay.MAX.scale(2));
    }

    @Test
    void shouldReturnMaximumWhenScalingMaximumDelayByOne() {
      assertEquals(Delay.MAX, Delay.MAX.scale(1));
    }

    @Test
    void shouldAllowScalingMaximumDelayDown() {
      assertEquals(Delay.ofNanos(Delay.MAX_NANOS / 2), Delay.MAX.scale(0.5));
    }

    @Test
    void shouldRejectNegativeFactor() {
      assertThrows(IllegalArgumentException.class, () -> Delay.ofNanos(100).scale(-1));
    }

    @Test
    void shouldRejectNaNFactor() {
      assertThrows(IllegalArgumentException.class, () -> Delay.ofNanos(100).scale(Double.NaN));
    }

    @Test
    void shouldRejectPositiveInfinityFactor() {
      assertThrows(IllegalArgumentException.class, () -> Delay.ofNanos(100).scale(Double.POSITIVE_INFINITY));
    }

    @Test
    void shouldRejectNegativeInfinityFactor() {
      assertThrows(IllegalArgumentException.class, () -> Delay.ofNanos(100).scale(Double.NEGATIVE_INFINITY));
    }

    @Test
    void shouldNotOverflowWhenScalingLargeDelay() {
      assertEquals(Delay.MAX, Delay.ofNanos(Delay.MAX_NANOS / 2 + 1).scale(2));
    }
  }
}
