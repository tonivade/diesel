/*
 * Copyright (c) 2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import java.util.function.BiFunction;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

public sealed interface Program<S, E, T> {

  record Pure<S, E, T>(Result<E, T> result) implements Program<S, E, T> {
    @Override public Result<E, T> eval(S state) {
      return result;
    }
  }

  record FoldMap<S, E, F, T, R>(
      Program<S, E, T> current,
      Function<E, Program<S, F, R>> onFailure,
      Function<T, Program<S, F, R>> onSuccess) implements Program<S, F, R> {
    @Override public Result<F, R> eval(S state) {
      return current.eval(state)
          .fold(onFailure, onSuccess)
          .eval(state);
    }
  };

  non-sealed interface Dsl<S, E, T> extends Program<S, E, T> {}

  Result<E, T> eval(S state);

  static <S, E, T> Program<S, E, T> success(@Nullable T value) {
    return new Pure<>(Result.success(value));
  }

  static <S, E, T> Program<S, E, T> failure(E error) {
    return new Pure<>(Result.failure(error));
  }

  static <S, E, T, V, R> Program<S, E, R> map2(Program<S, E, T> pt, Program<S, E, V> pv, BiFunction<T, V, R> mapper) {
    return pt.flatMap(t -> pv.map(v -> mapper.apply(t, v)));
  }

  default <R> Program<S, E, R> map(Function<T, R> mapper) {
    return flatMap(mapper.andThen(Program::success));
  }

  default Program<S, E, T> redeem(Function<E, T> mapper) {
    return recover(mapper.andThen(Program::success));
  }

  default Program<S, E, T> redeemWith(T value) {
    return recoverWith(success(value));
  }

  default <F> Program<S, F, T> mapError(Function<E, F> mapper) {
    return flatMapError(mapper.andThen(Program::failure));
  }

  default Program<S, E, T> recover(Function<E, Program<S, E, T>> mapper) {
    return flatMapError(mapper);
  }

  default Program<S, E, T> recoverWith(Program<S, E, T> value) {
    return flatMapError(_ -> value);
  }

  default <R> Program<S, E, R> andThen(Program<S, E, R> next) {
    return flatMap(_ -> next);
  }

  default <R> Program<S, E, R> flatMap(Function<T, Program<S, E, R>> next) {
    return foldMap(Program::failure, next);
  }

  default <F> Program<S, F, T> flatMapError(Function<E, Program<S, F, T>> next) {
    return foldMap(next, Program::success);
  }

  default <F, R> Program<S, F, R> foldMap(Function<E, Program<S, F, R>> onFailure, Function<T, Program<S, F, R>> onSuccess) {
    return new FoldMap<>(this, onFailure, onSuccess);
  }
}
