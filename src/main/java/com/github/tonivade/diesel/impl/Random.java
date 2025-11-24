/*
 * Copyright (c) 2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel.impl;

import static com.github.tonivade.diesel.Result.success;

import com.github.tonivade.diesel.Program;
import com.github.tonivade.diesel.Result;

import java.util.concurrent.ThreadLocalRandom;

/**
 * This sealed interface represents a random number generator that can be used within the Diesel framework.
 * It provides a way to generate random integers within a specified range.
 *
 * @param <T> the type of random value being generated
 */
public sealed interface Random<T> extends Program.Dsl<Random.Service, Void, T> {

  /**
   * This interface represents the service provided by the random number generator.
   * It provides a method to generate a random integer within a specified range.
   */
  interface Service {
    /**
     * Generates a random integer within the range [0, bound).
     *
     * @param bound the upper bound of the range (exclusive)
     * @return a random integer within the specified range
     */
    default Integer nextInt(int bound) {
      return ThreadLocalRandom.current().nextInt(bound);
    }
  }

  /**
   * This record represents a request to generate a random integer within a specified range.
   *
   * @param bound the upper bound of the range (exclusive)
   */
  record NextInt(int bound) implements Random<Integer> {}

  /**
   * Creates a program that generates a random integer within a specified range.
   *
   * @param bound the upper bound of the range (exclusive)
   * @return a program that generates a random integer
   */
  @SuppressWarnings("unchecked")
  static <S extends Service, E> Program<S, E, Integer> nextInt(int bound) {
    return (Program<S, E, Integer>) new NextInt(bound);
  }

  /**
   * Evaluates this random number generator using the provided service.
   *
   * @param state the service used to generate the random number
   * @return the result of the evaluation, which is either a success containing the generated random number or a failure
   */
  @Override
  @SuppressWarnings("unchecked")
  default Result<Void, T> handle(Service state) {
    return success((T) switch (this) {
      case NextInt(int bound) -> state.nextInt(bound);
    });
  }
}
