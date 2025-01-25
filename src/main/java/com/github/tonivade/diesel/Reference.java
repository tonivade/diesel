/*
 * Copyright (c) 2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import org.jspecify.annotations.Nullable;

public sealed interface Reference<V, T> extends Program.Dsl<Reference.Service<V>, T> {

  interface Service<V> {
    void set(V value);
    V get();
  }

  record SetValue<V>(V value) implements Reference<V, Void> {}
  record GetValue<V>() implements Reference<V, V> {}

  @SuppressWarnings("unchecked")
  static <V, S extends Service<V>> Program<S, Void> set(V value) {
    return (Program<S, Void>) new SetValue<>(value);
  }

  @SuppressWarnings("unchecked")
  static <V, S extends Service<V>> Program<S, V> get() {
    return (Program<S, V>) new GetValue<>();
  }

  @Override
  @SuppressWarnings({ "unchecked", "rawtypes" })
  @Nullable
  default T eval(Service<V> state) {
    return (T) switch (this) {
      case SetValue set -> {
        state.set((V) set.value());
        yield null;
      }
      case GetValue _ -> state.get();
    };
  }
}
