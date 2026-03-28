/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel.impl;

import static com.github.tonivade.diesel.Program.effect;

import com.github.tonivade.diesel.Program;

/**
 * A sealed interface representing a counter that can be incremented or decremented.
 *
 * @param <T> The type of number used by the counter, which must extend {@link Number}.
 */
public interface Counter<T extends Number> {

  interface Service<T extends Number> {
    /**
     * Increments the counter and returns the new value.
     *
     * @return The new value of the counter after incrementing.
     */
    T increment();

    /**
     * Decrements the counter and returns the new value.
     *
     * @return The new value of the counter after decrementing.
     */
    T decrement();
  }

  /**
   * Returns a new {@link Program} that increments the counter when evaluated.
   *
   * @param <T> The type of number used by the counter, which must extend {@link Number}.
   * @param <S> The type of service used by the program, which must extend {@link Service}.
   * @param <E> The type of error used by the program.
   * @return A new program that increments the counter when evaluated.
   */
  static <T extends Number, S extends Service<T>, E> Program<S, E, T> increment() {
    return effect(Service::increment);
  }

  /**
   * Returns a new {@link Program} that decrements the counter when evaluated.
   *
   * @param <T> The type of number used by the counter, which must extend {@link Number}.
   * @param <S> The type of service used by the program, which must extend {@link Service}.
   * @param <E> The type of error used by the program.
   * @return A new program that decrements the counter when evaluated.
   */
  static <T extends Number, S extends Service<T>, E> Program<S, E, T> decrement() {
    return effect(Service::decrement);
  }
}
