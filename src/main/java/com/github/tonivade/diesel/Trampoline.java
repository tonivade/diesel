/*
 * Copyright (c) 2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import java.util.function.Function;
import java.util.function.Supplier;

public sealed interface Trampoline<T> {

  @SuppressWarnings("NullAway")
  static Trampoline<Void> UNIT = done(null);

  record Done<T>(T value) implements Trampoline<T> {
    @Override public <R> Trampoline<R> apply(Function<T, Trampoline<R>> parent) {
      return parent.apply(value);
    }
  }

  record FlatMap<T, R>(Trampoline<T> current, Function<T, Trampoline<R>> next) implements Trampoline<R> {
    @Override public <S> Trampoline<S> apply(Function<R, Trampoline<S>> parent) {
      return current.flatMap(value -> next.apply(value).flatMap(parent));
    }

    private Trampoline<R> resume() {
      return current.apply(next);
    }
  }

  static <T> Trampoline<T> done(T value) {
    return new Done<>(value);
  }

  static <T> Trampoline<T> more(Supplier<Trampoline<T>> next) {
    return UNIT.flatMap(_ -> next.get());
  }

  default <R> Trampoline<R> map(Function<T, R> mapper) {
    return flatMap(mapper.andThen(Trampoline::done));
  }

  default <R> Trampoline<R> andThen(Trampoline<R> next) {
    return flatMap(_ -> next);
  }

  default <R> Trampoline<R> flatMap(Function<T, Trampoline<R>> mapper) {
    return new FlatMap<>(this, mapper);
  }

  <R> Trampoline<R> apply(Function<T, Trampoline<R>> parent);

  default T run() {
    var current = this;
    while (current instanceof FlatMap<?, T> flatMap) {
      current = flatMap.resume();
    }
    return ((Done<T>) current).value();
  }
}
