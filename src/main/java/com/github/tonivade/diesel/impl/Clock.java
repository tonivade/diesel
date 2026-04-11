/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel.impl;

import static com.github.tonivade.diesel.Program.effect;

import com.github.tonivade.diesel.Program;

/**
 * A sealed interface representing a clock that provides the current time.
 * This interface is part of the Diesel DSL (Domain Specific Language) and
 * provides a way to retrieve the current time in a pure and referentially
 * transparent way.
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
     * @return the current time in nanoseconds
     */
    long currentTime();
  }

  /**
   * Creates a new program that retrieves the current time.
   *
   * @param <S> the service type
   * @param <E> the error type
   * @return a program that retrieves the current time
   */
  static <S extends Service, E> Program<S, E, Long> currentTime() {
    return effect(Service::currentTime);
  }
}
