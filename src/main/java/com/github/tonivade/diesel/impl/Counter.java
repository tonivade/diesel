/*
 * Copyright (c) 2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel.impl;

import static com.github.tonivade.diesel.Result.success;

import com.github.tonivade.diesel.Program;
import com.github.tonivade.diesel.Result;

/**
 * A sealed interface representing a counter that can be incremented or decremented.
 * This interface extends the {@link Program.Dsl} interface, allowing it to be used as a DSL program.
 *
 * @param <T> The type of number used by the counter, which must extend {@link Number}.
 */
public sealed interface Counter<T extends Number> extends Program.Dsl<Counter.Service<T>, Void, T> {

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
   * A record implementing the {@link Counter} interface that represents an increment operation.
   *
   * @param <T> The type of number used by the counter, which must extend {@link Number}.
   */
  record Increment<T extends Number>() implements Counter<T> {}

  /**
   * A record implementing the {@link Counter} interface that represents a decrement operation.
   *
   * @param <T> The type of number used by the counter, which must extend {@link Number}.
   */
  record Decrement<T extends Number>() implements Counter<T> {}

  /**
   * Returns a new {@link Program} that increments the counter when evaluated.
   *
   * @param <T> The type of number used by the counter, which must extend {@link Number}.
   * @param <S> The type of service used by the program, which must extend {@link Service}.
   * @param <E> The type of error used by the program.
   * @return A new program that increments the counter when evaluated.
   */
  @SuppressWarnings("unchecked")
  static <T extends Number, S extends Service<T>, E> Program<S, E, T> increment() {
    return (Program<S, E, T>) new Increment<>();
  }

  /**
   * Returns a new {@link Program} that decrements the counter when evaluated.
   *
   * @param <T> The type of number used by the counter, which must extend {@link Number}.
   * @param <S> The type of service used by the program, which must extend {@link Service}.
   * @param <E> The type of error used by the program.
   * @return A new program that decrements the counter when evaluated.
   */
  @SuppressWarnings("unchecked")
  static <T extends Number, S extends Service<T>, E> Program<S, E, T> decrement() {
    return (Program<S, E, T>) new Decrement<>();
  }

  /**
   * Evaluates the counter operation and returns the new value of the counter.
   *
   * @param state The service used to evaluate the counter operation.
   * @return A new value of the counter after the operation has been applied.
   */
  @Override
  default Result<Void, T> handle(Service<T> state) {
    return success(switch (this) {
      case Increment<T>() -> state.increment();
      case Decrement<T>() -> state.decrement();
    });
  }
}
