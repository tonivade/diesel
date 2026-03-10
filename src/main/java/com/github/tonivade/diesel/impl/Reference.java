/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel.impl;

import static com.github.tonivade.diesel.Program.pipe;
import java.util.function.UnaryOperator;

import com.github.tonivade.diesel.Program;

/**
 * A {@code Reference} represents a program that operates on a {@link Service} to get or set a value.
 * It provides a sealed interface that can be extended by a limited set of subclasses, namely
 * {@link SetValue} and {@link GetValue}.
 *
 * @param <V> the type of the value being stored in the reference
 * @param <T> the type of the result returned by the reference
 */
public interface Reference<V, T> {

  /**
   * A {@code Service} provides methods to set and get a value from the reference.
   *
   * @param <V> the type of the value being stored in the reference
   */
  interface Service<V> {
    /**
     * Sets the value of the reference.
     *
     * @param value the new value to be set
     */
    void set(V value);

    /**
     * Gets the value of the reference.
     *
     * @return the current value of the reference
     */
    V get();
  }

  /**
   * Creates a program that sets a new value in the reference.
   *
   * @param <V> the type of the value being stored in the reference
   * @param <S> the type of the service used by the program
   * @param <E> the type of the error that may occur during execution
   * @param value the new value to be set
   * @return a program that sets the value in the reference
   */
  static <V, S extends Service<V>, E> Program<S, E, Void> set(V value) {
    return Program.access(state -> {
      state.set(value);
      return null;
    });
  }

  /**
   * Creates a program that retrieves the current value from the reference.
   *
   * @param <V> the type of the value being stored in the reference
   * @param <S> the type of the service used by the program
   * @param <E> the type of the error that may occur during execution
   * @return a program that retrieves the value from the reference
   */
  static <V, S extends Service<V>, E> Program<S, E, V> get() {
    return Program.access(state -> state.get());
  }

  /**
   * Creates a program that updates the value in the reference using the provided update function.
   *
   * @param <V> the type of the value being stored in the reference
   * @param <S> the type of the service used by the program
   * @param <E> the type of the error that may occur during execution
   * @param update the function used to update the value in the reference
   * @return a program that updates the value in the reference
   */
  static <V, S extends Service<V>, E> Program<S, E, Void> update(UnaryOperator<V> update) {
    return pipe(get(), value -> set(update.apply(value)));
  }
}
