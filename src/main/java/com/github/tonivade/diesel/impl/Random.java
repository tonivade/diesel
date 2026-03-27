/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel.impl;

import com.github.tonivade.diesel.Program;
import java.util.concurrent.ThreadLocalRandom;

/**
 * This sealed interface represents a random number generator that can be used within the Diesel framework.
 * It provides a way to generate random integers within a specified range.
 *
 */
public interface Random {

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
    default int nextInt(int bound) {
      return ThreadLocalRandom.current().nextInt(bound);
    }
  }

  /**
   * Creates a program that generates a random integer within a specified range.
   *
   * @param bound the upper bound of the range (exclusive)
   * @return a program that generates a random integer
   */
  static <S extends Service, E> Program<S, E, Integer> nextInt(int bound) {
    return Program.effect(state -> state.nextInt(bound));
  }
}
