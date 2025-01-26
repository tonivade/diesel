/*
 * Copyright (c) 2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import java.util.function.BiFunction;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

public sealed interface Program<S, T> {

  record Pure<S, T>(T value) implements Program<S, T> {
    @Override @Nullable public T eval(S state) {
      return value;
    }
  }

  record FlatMap<S, T, R>(Program<S, T> current, Function<T, Program<S, R>> next) implements Program<S, R> {
    @Override @Nullable public R eval(S state) {
      return next.apply(current.eval(state)).eval(state);
    }
  };

  non-sealed interface Dsl<S, T> extends Program<S, T> {}

  @Nullable
  T eval(S state);

  static <S, T> Program<S, T> pure(T value) {
    return new Pure<>(value);
  }

  static <S, T, V, R> Program<S, R> map2(Program<S, T> pt, Program<S, V> pv, BiFunction<T, V, R> mapper) {
    return pt.flatMap(t -> pv.map(v -> mapper.apply(t, v)));
  }

  default <R> Program<S, R> map(Function<T, R> mapper) {
    return flatMap(mapper.andThen(Program::pure));
  }

  default <R> Program<S, R> andThen(Program<S, R> next) {
    return flatMap(_ -> next);
  }

  default <R> Program<S, R> flatMap(Function<T, Program<S, R>> next) {
    return new FlatMap<>(this, next);
  }
}
