/*
 * Copyright (c) 2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import java.util.function.BiFunction;
import java.util.function.Function;

public sealed interface Program<S, T> {

  record Done<S, T>(T value) implements Program<S, T> {
    @Override
    public T eval(S state) {
      return value;
    }
  }

  record FlatMap<S, T, R>(Program<S, T> current, Function<T, Program<S, R>> next) implements Program<S, R> {
    @Override
    public R eval(S state) {
      return next.apply(current.eval(state)).eval(state);
    }
  };

  non-sealed interface Dsl<S, T> extends Program<S, T> {}

  T eval(S state);

  static <S, T> Program<S, T> done(T value) {
    return new Done<>(value);
  }

  static <S, T, V, R> Program<S, R> map2(Program<S, T> pt, Program<S, V> pv, BiFunction<T, V, R> mapper) {
    return pt.flatMap(t -> pv.map(v -> mapper.apply(t, v)));
  }

  default <R> Program<S, R> map(Function<T, R> mapper) {
    return flatMap(mapper.andThen(Program::done));
  }

  default <R> Program<S, R> andThen(Program<S, R> next) {
    return flatMap(_ -> next);
  }

  default <R> Program<S, R> flatMap(Function<T, Program<S, R>> next) {
    return new FlatMap<>(this, next);
  }
}
