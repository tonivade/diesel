/*
 * Copyright (c) 2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Result.success;
import java.util.function.UnaryOperator;

/**
 * A {@code Reference} represents a program that operates on a {@link Service} to get or set a value.
 * It provides a sealed interface that can be extended by a limited set of subclasses, namely
 * {@link SetValue} and {@link GetValue}.
 *
 * @param <V> the type of the value being stored in the reference
 * @param <T> the type of the result returned by the reference
 * @since 1.0
 */
public sealed interface Reference<V, T> extends Program.Dsl<Reference.Service<V>, Void, T> {

  /**
   * A {@code Service} provides methods to set and get a value from the reference.
   *
   * @param <V> the type of the value being stored in the reference
   * @since 1.0
   */
  interface Service<V> {
    /**
     * Sets the value of the reference.
     *
     * @param value the new value to be set
     * @since 1.0
     */
    void set(V value);

    /**
     * Gets the value of the reference.
     *
     * @return the current value of the reference
     * @since 1.0
     */
    V get();
  }

  /**
   * A {@code SetValue} represents a program that sets a new value in the reference.
   *
   * @param <V> the type of the value being stored in the reference
   * @since 1.0
   */
  record SetValue<V>(V value) implements Reference<V, Void> {}

  /**
   * A {@code GetValue} represents a program that retrieves the current value from the reference.
   *
   * @param <V> the type of the value being stored in the reference
   * @since 1.0
   */
  record GetValue<V>() implements Reference<V, V> {}

  /**
   * Creates a program that sets a new value in the reference.
   *
   * @param <V> the type of the value being stored in the reference
   * @param <S> the type of the service used by the program
   * @param <E> the type of the error that may occur during execution
   * @param value the new value to be set
   * @return a program that sets the value in the reference
   * @since 1.0
   */
  @SuppressWarnings("unchecked")
  static <V, S extends Service<V>, E> Program<S, E, Void> set(V value) {
    return (Program<S, E, Void>) new SetValue<>(value);
  }

  /**
   * Creates a program that retrieves the current value from the reference.
   *
   * @param <V> the type of the value being stored in the reference
   * @param <S> the type of the service used by the program
   * @param <E> the type of the error that may occur during execution
   * @return a program that retrieves the value from the reference
   * @since 1.0
   */
  @SuppressWarnings("unchecked")
  static <V, S extends Service<V>, E> Program<S, E, V> get() {
    return (Program<S, E, V>) new GetValue<>();
  }

  /**
   * Creates a program that updates the value in the reference using the provided update function.
   *
   * @param <V> the type of the value being stored in the reference
   * @param <S> the type of the service used by the program
   * @param <E> the type of the error that may occur during execution
   * @param update the function used to update the value in the reference
   * @return a program that updates the value in the reference
   * @since 1.0
   */
  static <V, S extends Service<V>, E> Program<S, E, Void> update(UnaryOperator<V> update) {
    return Reference.<V, S, E>get().map(update).flatMap(Reference::set);
  }

  /**
   * Evaluates the reference program using the provided service.
   *
   * @param state the service used to evaluate the program
   * @return the result of the program evaluation
   * @since 1.0
   */
  @Override
  @SuppressWarnings({ "unchecked", "rawtypes" })
  default Result<Void, T> handle(Service<V> state) {
    return success((T) switch (this) {
      case SetValue set -> {
        state.set((V) set.value());
        yield null;
      }
      case GetValue __ -> state.get();
    });
  }
}
