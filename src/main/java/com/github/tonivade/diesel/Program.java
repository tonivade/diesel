/*
 * Copyright (c) 2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Result.failure;
import static com.github.tonivade.diesel.Result.success;

import java.util.function.BiFunction;
import java.util.function.Function;

public sealed interface Program<S, E, T> {

  record Success<S, E, T>(T value) implements Program<S, E, T> {
    @Override public Result<E, T> eval(S state) {
      return success(value);
    }
  }

  record Failure<S, E, T>(E error) implements Program<S, E, T> {
    @Override public Result<E, T> eval(S state) {
      return failure(error);
    }
  }

  record FlatMap<S, E, T, R>(Program<S, E, T> current, Function<T, Program<S, E, R>> next) implements Program<S, E, R> {
    @Override public Result<E, R> eval(S state) {
      return current.eval(state).flatMap(t -> next.apply(t).eval(state));
    }
  };

  non-sealed interface Dsl<S, E, T> extends Program<S, E, T> {}

  Result<E, T> eval(S state);

  static <S, E, T> Program<S, E, T> pure(T value) {
    return new Success<>(value);
  }

  static <S, E, T, V, R> Program<S, E, R> map2(Program<S, E, T> pt, Program<S, E, V> pv, BiFunction<T, V, R> mapper) {
    return pt.flatMap(t -> pv.map(v -> mapper.apply(t, v)));
  }

  default <R> Program<S, E, R> map(Function<T, R> mapper) {
    return flatMap(mapper.andThen(Program::pure));
  }

  default <R> Program<S, E, R> andThen(Program<S, E, R> next) {
    return flatMap(_ -> next);
  }

  default <R> Program<S, E, R> flatMap(Function<T, Program<S, E, R>> next) {
    return new FlatMap<>(this, next);
  }
}
