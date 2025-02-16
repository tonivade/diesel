/*
 * Copyright (c) 2025, Antonio Gabriel Muñoz Conejo <me at tonviade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import java.util.NoSuchElementException;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * The Result type is used to represent a value that may or may not be present.
 * This type is useful for handling errors and exceptions in a more functional programming style.
 *
 * @param <F> The type of the failure value.
 * @param <S> The type of the success value.
 */
public sealed interface Result<F, S> {

  /**
   * Creates a new failure result.
   *
   * @param error The error value.
   * @param <F> The type of the failure value.
   * @param <S> The type of the success value.
   * @return A new failure result.
   */
  static <F, S> Result<F, S> failure(F error) {
    return new Failure<>(error);
  }

  /**
   * Creates a new success result.
   *
   * @param value The success value, which may be null.
   * @param <F> The type of the failure value.
   * @param <S> The type of the success value.
   * @return A new success result.
   */
  static <F, S> Result<F, S> success(@Nullable S value) {
    return new Success<>(value);
  }

  /**
   * Creates a new result by combining two results using the provided mapper function.
   *
   * @param r1 The first result.
   * @param r2 The second result.
   * @param mapper The function used to combine the success values of the two results.
   * @param <F> The type of the failure value.
   * @param <S> The type of the success value of the first result.
   * @param <T> The type of the success value of the second result.
   * @param <R> The type of the success value of the combined result.
   * @return A new result that combines the two input results.
   */
  static <F, S, T, R> Result<F, R> zip(Result<F, S> r1, Result<F, T> r2, BiFunction<S, T, R> mapper) {
    return r1.flatMap(a -> r2.map(b -> mapper.apply(a, b)));
  }

  /**
   * A record representing a failure result.
   *
   * @param <F> The type of the failure value.
   * @param <S> The type of the success value.
   */
  record Failure<F, S>(F error) implements Result<F, S> {}

  /**
   * A record representing a success result.
   *
   * @param <F> The type of the failure value.
   * @param <S> The type of the success value.
   */
  record Success<F, S>(@Nullable S value) implements Result<F, S> {}

  /**
   * Maps the success value of this result using the provided mapper function.
   *
   * @param mapper The function used to map the success value.
   * @param <R> The type of the mapped success value.
   * @return A new result with the mapped success value.
   */
  @SuppressWarnings("unchecked")
  default <R> Result<F, R> map(Function<S, R> mapper) {
    return switch (this) {
      case Failure<F, S> __ -> (Result<F, R>) this;
      case Success<F, S>(S value) -> success(mapper.apply(value));
    };
  }

  /**
   * Maps the failure value of this result using the provided mapper function.
   *
   * @param mapper The function used to map the failure value.
   * @param <R> The type of the mapped failure value.
   * @return A new result with the mapped failure value.
   */
  @SuppressWarnings("unchecked")
  default <R> Result<R, S> mapError(Function<F, R> mapper) {
    return switch (this) {
      case Failure<F, S>(F error) -> failure(mapper.apply(error));
      case Success<F, S> __ -> (Result<R, S>) this;
    };
  }

  /**
   * Maps the success value of this result using the provided mapper function and returns a new result.
   *
   * @param mapper The function used to map the success value.
   * @param <R> The type of the mapped success value.
   * @return A new result with the mapped success value.
   */
  @SuppressWarnings("unchecked")
  default <R> Result<F, R> flatMap(Function<S, Result<F, R>> mapper) {
    return switch (this) {
      case Failure<F, S> __ -> (Result<F, R>) this;
      case Success<F, S>(S value) -> mapper.apply(value);
    };
  }

  /**
   * Folds the result into a single value using the provided functions.
   *
   * @param onFailure The function used to handle the failure value.
   * @param onSuccess The function used to handle the success value.
   * @param <R> The type of the folded value.
   * @return The folded value.
   */
  default <R> R fold(Function<F, R> onFailure, Function<S, R> onSuccess) {
    return switch (this) {
      case Failure<F, S>(F error) -> onFailure.apply(error);
      case Success<F, S>(S value) -> onSuccess.apply(value);
    };
  }

  /**
   * Returns the success value of this result, throwing a NoSuchElementException if this is a failure.
   *
   * @return The success value of this result.
   * @throws NoSuchElementException If this is a failure.
   */
  default S getOrElseThrow() {
    return getOrElseThrow(__ -> new NoSuchElementException());
  }

  /**
   * Returns the success value of this result, throwing an exception if this is a failure.
   *
   * @param mapper The function used to map the failure value to an exception.
   * @param <X> The type of the exception.
   * @return The success value of this result.
   * @throws X If this is a failure.
   */
  default <X extends Throwable> S getOrElseThrow(Function<F, X> mapper) throws X {
    return switch (this) {
      case Success<F, S>(S value) -> value;
      case Failure<F, S>(F error) -> throw mapper.apply(error);
    };
  }
}
