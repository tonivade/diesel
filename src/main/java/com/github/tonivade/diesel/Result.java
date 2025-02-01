/*
 * Copyright (c) 2025, Antonio Gabriel Muñoz Conejo <me at tonviade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import java.util.NoSuchElementException;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

public sealed interface Result<F, S> {

  static <F, S> Result<F, S> failure(F error) {
    return new Failure<>(error);
  }

  static <F, S> Result<F, S> success(@Nullable S value) {
    return new Success<>(value);
  }

  static <F, S, T, R> Result<F, R> map2(Result<F, S> r1, Result<F, T> r2, BiFunction<S, T, R> mapper) {
    return r1.flatMap(a -> r2.map(b -> mapper.apply(a, b)));
  }

  record Failure<F, S>(F error) implements Result<F, S> {}
  record Success<F, S>(@Nullable S value) implements Result<F, S> {}

  @SuppressWarnings("unchecked")
  default <R> Result<F, R> map(Function<S, R> mapper) {
    return switch (this) {
      case Failure<F, S> ignore -> (Result<F, R>) this;
      case Success<F, S>(S value) -> success(mapper.apply(value));
    };
  }

  @SuppressWarnings("unchecked")
  default <R> Result<R, S> mapError(Function<F, R> mapper) {
    return switch (this) {
      case Failure<F, S>(F error) -> failure(mapper.apply(error));
      case Success<F, S> ignore -> (Result<R, S>) this;
    };
  }

  @SuppressWarnings("unchecked")
  default <R> Result<F, R> flatMap(Function<S, Result<F, R>> mapper) {
    return switch (this) {
      case Failure<F, S> ignore -> (Result<F, R>) this;
      case Success<F, S>(S value) -> mapper.apply(value);
    };
  }

  default <R> R fold(Function<F, R> onFailure, Function<S, R> onSuccess) {
    return switch (this) {
      case Failure<F, S>(F error) -> onFailure.apply(error);
      case Success<F, S>(S value) -> onSuccess.apply(value);
    };
  }

  default S getOrElseThrow() {
    return getOrElseThrow(i -> new NoSuchElementException());
  }

  default <X extends Throwable> S getOrElseThrow(Function<F, X> mapper) throws X {
    return switch (this) {
      case Success<F, S>(S value) -> value;
      case Failure<F, S>(F error) -> throw mapper.apply(error);
    };
  }
}
