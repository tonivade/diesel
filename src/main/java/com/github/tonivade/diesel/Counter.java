/*
 * Copyright (c) 2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

public sealed interface Counter extends Program.Dsl<Counter.Service, Integer> {

  interface Service {
    int increment();
    int decrement();
  }

  record Increment() implements Counter {}
  record Decrement() implements Counter {}

  @SuppressWarnings("unchecked")
  static <S extends Service> Program<S, Integer> increment() {
    return (Program<S, Integer>) new Increment();
  }

  @SuppressWarnings("unchecked")
  static <S extends Service> Program<S, Integer> decrement() {
    return (Program<S, Integer>) new Decrement();
  }

  @Override
  default Integer eval(Service state) {
    return switch (this) {
      case Increment _ -> state.increment();
      case Decrement _ -> state.decrement();
    };
  }
}
