/*
 * Copyright (c) 2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Result.success;

public sealed interface Counter<T extends Number> extends Program.Dsl<Counter.Service<T>, Void, T> {

  interface Service<T extends Number> {
    T increment();
    T decrement();
  }

  record Increment<T extends Number>() implements Counter<T> {}
  record Decrement<T extends Number>() implements Counter<T> {}

  @SuppressWarnings("unchecked")
  static <T extends Number, S extends Service<T>, E> Program<S, E, T> increment() {
    return (Program<S, E, T>) new Increment<>();
  }

  @SuppressWarnings("unchecked")
  static <T extends Number, S extends Service<T>, E> Program<S, E, T> decrement() {
    return (Program<S, E, T>) new Decrement<>();
  }

  @Override
  default Result<Void, T> dslEval(Service<T> state) {
    return success(switch (this) {
      case Increment<T>() -> state.increment();
      case Decrement<T>() -> state.decrement();
    });
  }
}
