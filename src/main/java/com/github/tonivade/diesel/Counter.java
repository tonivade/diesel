/*
 * Copyright (c) 2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

public sealed interface Counter<T> extends Program.Dsl<Counter.Service, T> {

  interface Service {
    Integer increment();
    Integer decrement();
  }

  record Increment() implements Counter<Integer> {}
  record Decrement() implements Counter<Integer> {}

  @SuppressWarnings("unchecked")
  static <S extends Service> Program<S, Integer> increment() {
    return (Program<S, Integer>) new Increment();
  }

  @SuppressWarnings("unchecked")
  static <S extends Service> Program<S, Integer> decrement() {
    return (Program<S, Integer>) new Decrement();
  }

  @Override
  @SuppressWarnings("unchecked")
  default T eval(Service state) {
    return (T) switch (this) {
      case Increment _ -> state.increment();
      case Decrement _ -> state.decrement();
    };
  }
}
