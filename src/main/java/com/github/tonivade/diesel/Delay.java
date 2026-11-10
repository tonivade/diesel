/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import java.time.Duration;

/**
 * Represents a non-negative time delay expressed in nanoseconds.
 *
 * <p>A {@code Delay} is an immutable value object that provides factory
 * methods for creating delays from different time units, comparison
 * operations, conversion to {@link Duration}, and scaling.
 *
 * <p>The maximum representable delay is {@link #MAX}, corresponding to
 * {@link Long#MAX_VALUE} nanoseconds.
 *
 * @param nanos the delay in nanoseconds; must be non-negative
 */
public record Delay(long nanos) implements Comparable<Delay> {

  /**
   * Represents a zero-length delay.
   */
  public static final Delay ZERO = new Delay(0);

  /**
   * Represents the maximum possible delay.
   */
  public static final Delay MAX = new Delay(Long.MAX_VALUE);

  /**
   * Creates a delay.
   *
   * @throws IllegalArgumentException if {@code nanos} is negative
   */
  public Delay {
    if (nanos < 0) {
      throw new IllegalArgumentException("delay cannot be a negative value");
    }
  }

  /**
   * Creates a delay expressed in hours.
   *
   * @param hours the number of hours
   * @return the resulting delay
   */
  public static Delay ofHours(long hours) {
    return ofMinutes(Math.multiplyExact(hours, 60));
  }

  /**
   * Creates a delay expressed in minutes.
   *
   * @param minutes the number of minutes
   * @return the resulting delay
   */
  public static Delay ofMinutes(long minutes) {
    return ofSeconds(Math.multiplyExact(minutes, 60));
  }

  /**
   * Creates a delay expressed in seconds.
   *
   * @param seconds the number of seconds
   * @return the resulting delay
   */
  public static Delay ofSeconds(long seconds) {
    return ofMillis(Math.multiplyExact(seconds, 1000));
  }

  /**
   * Creates a delay expressed in milliseconds.
   *
   * @param millis the number of milliseconds
   * @return the resulting delay
   */
  public static Delay ofMillis(long millis) {
    return ofMicros(Math.multiplyExact(millis, 1000));
  }

  /**
   * Creates a delay expressed in microseconds.
   *
   * @param micros the number of microseconds
   * @return the resulting delay
   */
  public static Delay ofMicros(long micros) {
    return ofNanos(Math.multiplyExact(micros, 1000));
  }

  /**
   * Creates a delay expressed in nanoseconds.
   *
   * @param nanos the number of nanoseconds
   * @return the resulting delay
   * @throws IllegalArgumentException if {@code nanos} is negative
   */
  public static Delay ofNanos(long nanos) {
    return new Delay(nanos);
  }

  /**
   * Checks whether this delay represents zero time.
   *
   * @return {@code true} if the delay is zero; {@code false} otherwise
   */
  public boolean isZero() {
    return nanos == 0;
  }

  /**
   * Checks whether this delay represents the maximum possible value.
   *
   * @return {@code true} if the delay equals {@link #MAX};
   *         {@code false} otherwise
   */
  public boolean isMax() {
    return nanos == Long.MAX_VALUE;
  }

  /**
   * Converts this delay into a {@link Duration}.
   *
   * @return the equivalent duration
   */
  public Duration toDuration() {
    return Duration.ofNanos(nanos);
  }

  /**
   * Compares this delay with another delay.
   *
   * @param other the delay to compare against
   * @return a negative integer, zero, or a positive integer if this delay
   *         is respectively less than, equal to, or greater than the
   *         specified delay
   */
  @Override
  public int compareTo(Delay other) {
    return Long.compare(this.nanos, other.nanos);
  }

  /**
   * Scales this delay by the specified factor.
   *
   * <p>A factor greater than one increases the delay, while a factor
   * between zero and one decreases it. A zero factor returns
   * {@link #ZERO}. Results exceeding the maximum representable value
   * are not explicitly saturated except when the original delay is
   * already {@link #MAX} and the factor is greater than one.
   *
   * @param factor the scaling factor; must be finite and non-negative
   * @return the scaled delay
   * @throws IllegalArgumentException if the factor is negative or not finite
   */
  public Delay scale(double factor) {
    if (!Double.isFinite(factor)) {
      throw new IllegalArgumentException("factor must be a finite value");
    }
    if (factor < 0) {
      throw new IllegalArgumentException("factor cannot be negative");
    }
    if (factor == 0) {
      return ZERO;
    }

    double result = nanos * factor;

    if (result >= Long.MAX_VALUE) {
      return MAX;
    }

    return new Delay(Math.round(result));
  }
}
