/*
 * Copyright (c) 2025, Antonio Gabriel Muñoz Conejo <me at tonviade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import java.util.function.Function;

import org.jspecify.annotations.Nullable;

public sealed interface Result<F, S> {

  static <F, S> Result<F, S> failure(F error) {
    return new Failure<>(error);
  }

  static <F, S> Result<F, S> success(@Nullable S value) {
    return new Success<>(value);
  }

  record Failure<F, S>(F error) implements Result<F, S> {}
  record Success<F, S>(@Nullable S value) implements Result<F, S> {}

  @SuppressWarnings("unchecked")
  default <R> Result<F, R> map(Function<S, R> mapper) {
    return switch (this) {
      case Failure<F, S> _ -> (Result<F, R>) this;
      case Success<F, S>(S value) -> success(mapper.apply(value));
    };
  }

  @SuppressWarnings("unchecked")
  default <R> Result<R, S> mapError(Function<F, R> mapper) {
    return switch (this) {
      case Failure<F, S>(F error) -> failure(mapper.apply(error));
      case Success<F, S> _ -> (Result<R, S>) this;
    };
  }

  @SuppressWarnings("unchecked")
  default <R> Result<F, R> flatMap(Function<S, Result<F, R>> mapper) {
    return switch (this) {
      case Failure<F, S> _ -> (Result<F, R>) this;
      case Success<F, S>(S value) -> mapper.apply(value);
    };
  }

  default <R> R fold(Function<F, R> failureMapper, Function<S, R> successMapper) {
    return switch (this) {
      case Failure<F, S>(F error) -> failureMapper.apply(error);
      case Success<F, S>(S value) -> successMapper.apply(value);
    };
  }
}
