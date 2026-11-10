/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Program.access;

import com.github.tonivade.diesel.Program.ElapsedTime;

import java.time.Instant;

/**
 * An interface representing a clock that provides the current time.
 *
 * @see Service
 */
public interface Clock {

  /**
   * The service interface of the clock, providing a method to retrieve the
   * current time.
   */
  interface Service {

    /**
     * Retrieves the current time.
     *
     * @return the current time in millisecons
     */
    default long currentTime() {
      return System.currentTimeMillis();
    }

    /**
     * Monotonic time in nanoseconds, only meaningful to measure elapsed time.
     *
     * @return monotonic time in nanoseconds
     */
    default long nanoTime() {
      return System.nanoTime();
    }

    /**
     * Wall-clock time.
     *
     * @return the current instant using the system clock
     */
    default Instant now() {
      return Instant.now();
    }
  }

  /**
   * Creates a new program that retrieves the current time in milliseconds.
   *
   * @param <S> the service type
   * @param <E> the error type
   * @return a program that retrieves the current time in milliseconds
   */
  static <S extends Service, E> Program<S, E, Long> currentTime() {
    return access(Service::currentTime);
  }

  /**
   * Creates a new program that retrieves the monotonic time in nanoseconds.
   *
   * @param <S> the service type
   * @param <E> the error type
   * @return a program that retrieves the monotonic time in nanoseconds
   */
  static <S extends Service, E> Program<S, E, Long> nanoTime() {
    return access(Service::nanoTime);
  }

  /**
   * Creates a new program that retrieves the current instant using the system clock.
   *
   * @param <S> the service type
   * @param <E> the error type
   * @return a program that retrieves the current instant using the system clock
   */
  static <S extends Service, E> Program<S, E, Instant> now() {
    return access(Service::now);
  }

  /**
   * Like {@link Program#timed()}, but reading the time from the {@link Service}
   * of the context.
   *
   * @param program the program to measure
   * @param <S> the service type
   * @param <E> the error type
   * @param <T> the type of the result
   * @return a program that yields the result with the elapsed time
   */
  static <S extends Service, E, T> Program<S, E, ElapsedTime<T>> timed(Program<S, E, T> program) {
    return program.timed(nanoTime());
  }

  /**
   * Like {@link Program#timedResult()}, but reading the time from the {@link Service}
   * of the context.
   *
   * @param program the program to measure
   * @param <S> the service type
   * @param <E> the error type
   * @param <T> the type of the result
   * @return a program that yields the elapsed time and the original result
   */
  static <S extends Service, E, T> Program<S, Void, ElapsedTime<Result<E, T>>> timedResult(Program<S, E, T> program) {
    return program.timedResult(nanoTime());
  }
}
