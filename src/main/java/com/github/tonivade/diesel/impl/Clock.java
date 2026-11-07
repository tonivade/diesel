/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel.impl;

import static com.github.tonivade.diesel.Program.access;

import com.github.tonivade.diesel.Program;

import java.time.Instant;

/**
 * A sealed interface representing a clock that provides the current time.
 * This interface is part of the Diesel DSL (Domain Specific Language) and
 * provides a way to retrieve the current time in a pure and referentially
 * transparent way.
 *
 * @see Service
 */
public interface Clock {

  interface Service {
    /**
     * Retrieves the current time.
     *
     * @return the current time in nanoseconds
     * @deprecated use {@link #nanoTime()}
     */
    @Deprecated
    long currentTime();

    /**
     * Monotonic time in nanoseconds, only meaningful to measure elapsed time.
     */
    default long nanoTime() {
      return System.nanoTime();
    }

    /**
     * Wall-clock time.
     */
    default Instant now() {
      return Instant.now();
    }
  }

  static <S extends Service, E> Program<S, E, Long> nanoTime() {
    return access(Service::nanoTime);
  }

  static <S extends Service, E> Program<S, E, Instant> now() {
    return access(Service::now);
  }

  /**
   * Like {@link Program#timed()}, but reading the time from the {@link Service}
   * of the context.
   */
  static <S extends Service, E, T> Program<S, E, Program.ElapsedTime<T>> timed(Program<S, E, T> program) {
    return program.timed(nanoTime());
  }
}
