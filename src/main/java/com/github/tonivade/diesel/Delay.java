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
 * one year in nanoseconds. Longer delays aren't rejected but saturated: the
 * factory methods, the constructor and {@link #scale(double)} return {@code MAX}
 * for anything longer.
 *
 * <p>{@code MAX} also means "no limit": {@link Program#sleep(Delay)} sleeps
 * forever and {@link Program#timeout(Delay)} doesn't add a timeout. So a delay
 * that saturates, like an exponential backoff after enough attempts, waits
 * forever instead of one year.
 *
 * @param nanos the delay in nanoseconds; must be non-negative
 */
public record Delay(long nanos) implements Comparable<Delay> {

  static final long MAX_NANOS = Duration.ofDays(365).toNanos();

  private static final Duration MAX_DURATION = Duration.ofNanos(MAX_NANOS);

  /**
   * Represents a zero-length delay.
   */
  public static final Delay ZERO = new Delay(0);

  /**
   * Represents the maximum possible delay, one year, which {@code sleep} and
   * {@code timeout} treat as no limit.
   */
  public static final Delay MAX = new Delay(MAX_NANOS);

  /**
   * Creates a delay, saturated at {@link #MAX}.
   *
   * @param nanos the delay in nanoseconds; a value greater than the maximum
   *     becomes {@link #MAX}
   * @throws IllegalArgumentException if {@code nanos} is negative
   */
  public Delay {
    if (nanos < 0) {
      throw new IllegalArgumentException("delay cannot be negative");
    }
    if (nanos > MAX_NANOS) {
      nanos = MAX_NANOS;
    }
  }

  /**
   * Creates a delay expressed in hours.
   *
   * @param hours the number of hours
   * @return the resulting delay
   * @throws IllegalArgumentException if {@code hours} is negative
   */
  public static Delay ofHours(long hours) {
    return of(hours, 3_600_000_000_000L);
  }

  /**
   * Creates a delay expressed in minutes.
   *
   * @param minutes the number of minutes
   * @return the resulting delay
   * @throws IllegalArgumentException if {@code minutes} is negative
   */
  public static Delay ofMinutes(long minutes) {
    return of(minutes, 60_000_000_000L);
  }

  /**
   * Creates a delay expressed in seconds.
   *
   * @param seconds the number of seconds
   * @return the resulting delay
   * @throws IllegalArgumentException if {@code seconds} is negative
   */
  public static Delay ofSeconds(long seconds) {
    return of(seconds, 1_000_000_000L);
  }

  /**
   * Creates a delay expressed in milliseconds.
   *
   * @param millis the number of milliseconds
   * @return the resulting delay
   * @throws IllegalArgumentException if {@code millis} is negative
   */
  public static Delay ofMillis(long millis) {
    return of(millis, 1_000_000L);
  }

  /**
   * Creates a delay expressed in microseconds.
   *
   * @param micros the number of microseconds
   * @return the resulting delay
   * @throws IllegalArgumentException if {@code micros} is negative
   */
  public static Delay ofMicros(long micros) {
    return of(micros, 1_000L);
  }

  /**
   * Creates a delay expressed in nanoseconds.
   *
   * @param nanos the number of nanoseconds
   * @return the resulting delay
   * @throws IllegalArgumentException if {@code nanos} is negative
   */
  public static Delay ofNanos(long nanos) {
    return of(nanos, 1);
  }

  /**
   * Creates a delay from the specified duration.
   *
   * <p>If the duration exceeds the maximum representable delay,
   * the result is saturated at {@link #MAX}.
   *
   * @param duration the duration; must be non-negative
   * @return the resulting delay
   * @throws IllegalArgumentException if the duration is negative
   * @throws NullPointerException if the duration is null
   */
  public static Delay from(Duration duration) {
    if (duration.isNegative()) {
      throw new IllegalArgumentException("delay cannot be negative");
    }
    if (duration.isZero()) {
      return ZERO;
    }
    if (duration.compareTo(MAX_DURATION) >= 0) {
      return MAX;
    }
    return new Delay(duration.toNanos());
  }

  /**
   * Returns this delay as a {@link Duration}, like {@code PT1S}.
   *
   * @return the string representation of the equivalent duration
   */
  @Override
  public String toString() {
    return toDuration().toString();
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
    return nanos == MAX_NANOS;
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
   * between zero and one decreases it. The result is rounded to the
   * nearest nanosecond and saturated at {@link #MAX} if it reaches
   * or exceeds the maximum representable value.
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

    if (result >= MAX_NANOS) {
      return MAX;
    }

    return new Delay(Math.round(result));
  }

  // saturates at MAX before multiplying, so the multiplication can't overflow
  private static Delay of(long amount, long nanosPerUnit) {
    if (amount < 0) {
      throw new IllegalArgumentException("delay cannot be negative");
    }
    if (amount == 0) {
      return ZERO;
    }
    if (amount >= MAX_NANOS / nanosPerUnit) {
      return MAX;
    }
    return new Delay(amount * nanosPerUnit);
  }
}
