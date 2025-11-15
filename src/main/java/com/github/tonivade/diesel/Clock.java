/*
 * Copyright (c) 2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Result.success;

/**
 * A sealed interface representing a clock that provides the current time.
 * This interface is part of the Diesel DSL (Domain Specific Language) and
 * provides a way to retrieve the current time in a pure and referentially
 * transparent way.
 *
 * @see Program.Dsl
 * @see Service
 */
public sealed interface Clock extends Program.Dsl<Clock.Service, Void, Long> {

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
    Long currentTime();
  }

  /**
   * A record class representing a clock that retrieves the current time.
   */
  record CurrentTime() implements Clock {}

  /**
   * Creates a new program that retrieves the current time.
   *
   * @param <S> the service type
   * @param <E> the error type
   * @return a program that retrieves the current time
   */
  @SuppressWarnings("unchecked")
  static <S extends Service, E> Program<S, E, Long> currentTime() {
    return (Program<S, E, Long>) new CurrentTime();
  }

  /**
   * Evaluates the clock in the context of the given service and returns the
   * result as a success or failure.
   *
   * @param service the service to use for evaluation
   * @return the result of the evaluation
   */
  @Override
  default Result<Void, Long> handle(Service service) {
    return success(switch (this) {
      case CurrentTime() -> service.currentTime();
    });
  }
}
