/*
 * Copyright (c) 2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Result.success;
import java.util.function.UnaryOperator;

public sealed interface Reference<V, T> extends Program.Dsl<Reference.Service<V>, Void, T> {

  interface Service<V> {
    void set(V value);
    V get();
    default void update(UnaryOperator<V> update) {
      set(update.apply(get()));
    }
  }

  record SetValue<V>(V value) implements Reference<V, Void> {}
  record GetValue<V>() implements Reference<V, V> {}

  @SuppressWarnings("unchecked")
  static <V, S extends Service<V>, E> Program<S, E, Void> set(V value) {
    return (Program<S, E, Void>) new SetValue<>(value);
  }

  @SuppressWarnings("unchecked")
  static <V, S extends Service<V>, E> Program<S, E, V> get() {
    return (Program<S, E, V>) new GetValue<>();
  }

  static <V, S extends Service<V>, E> Program<S, E, Void> update(UnaryOperator<V> update) {
    return Reference.<V, S, E>get().map(update).flatMap(Reference::set);
  }

  @Override
  @SuppressWarnings({ "unchecked", "rawtypes" })
  default Result<Void, T> eval(Service<V> state) {
    return success((T) switch (this) {
      case SetValue set -> {
        state.set((V) set.value());
        yield null;
      }
      case GetValue _ -> state.get();
    });
  }
}
