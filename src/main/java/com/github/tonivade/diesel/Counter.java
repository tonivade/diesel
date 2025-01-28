/*
 * Copyright (c) 2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Result.success;

public sealed interface Counter extends Program.Dsl<Counter.Service, Void, Integer> {

  interface Service {
    int increment();
    int decrement();
  }

  record Increment() implements Counter {}
  record Decrement() implements Counter {}

  @SuppressWarnings("unchecked")
  static <S extends Service, E> Program<S, E, Integer> increment() {
    return (Program<S, E, Integer>) new Increment();
  }

  @SuppressWarnings("unchecked")
  static <S extends Service, E> Program<S, E, Integer> decrement() {
    return (Program<S, E, Integer>) new Decrement();
  }

  @Override
  default Result<Void, Integer> eval(Service state) {
    return success(switch (this) {
      case Increment _ -> state.increment();
      case Decrement _ -> state.decrement();
    });
  }
}
