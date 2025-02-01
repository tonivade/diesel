/*
 * Copyright (c) 2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Result.success;

public sealed interface Queue<V, T> extends Program.Dsl<Queue.Service<V>, Void, T> {

  interface Service<V> {
    void offer(V item);
    V take();
  }

  record Offer<V>(V item) implements Queue<V, Void> {}
  record Take<V>() implements Queue<V, V> {}

  @SuppressWarnings("unchecked")
  static <V, S extends Service<V>, E> Program<S, E, Void> offer(V item) {
    return (Program<S, E, Void>) new Offer<>(item);
  }

  @SuppressWarnings("unchecked")
  static <V, S extends Service<V>, E> Program<S, E, V> take() {
    return (Program<S, E, V>) new Take<>();
  }

  @Override
  @SuppressWarnings({ "rawtypes", "unchecked" })
  default Result<Void, T> dslEval(Service<V> state) {
    return success((T) switch (this) {
      case Offer offer -> {
        state.offer((V) offer.item());
        yield null;
      }
      case Take take -> state.take();
    });
  }
}
