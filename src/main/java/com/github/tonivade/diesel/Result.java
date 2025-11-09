/*
 * Copyright (c) 2025, Antonio Gabriel Muñoz Conejo <me at tonviade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import java.util.NoSuchElementException;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import com.github.tonivade.diesel.function.Finisher2;
import com.github.tonivade.diesel.function.Finisher3;
import com.github.tonivade.diesel.function.Finisher4;
import com.github.tonivade.diesel.function.Finisher5;
import com.github.tonivade.diesel.function.Finisher6;
import com.github.tonivade.diesel.function.Finisher7;
import com.github.tonivade.diesel.function.Finisher8;
import com.github.tonivade.diesel.function.Finisher9;

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

  // start generated code

  static <F, T0, T1, R> Result<F, R> zip(
     Result<F, T0> r0,
     Result<F, T1> r1,
     Finisher2<T0, T1, R> finisher) {
    return r0.flatMap(_0 ->
      r1.map(_1 -> finisher.apply(_0, _1))
      );
  }

  static <F, T0, T1, T2, R> Result<F, R> zip(
     Result<F, T0> r0,
     Result<F, T1> r1,
     Result<F, T2> r2,
     Finisher3<T0, T1, T2, R> finisher) {
    return r0.flatMap(_0 ->
      r1.flatMap(_1 ->
      r2.map(_2 -> finisher.apply(_0, _1, _2))
      ));
  }

  static <F, T0, T1, T2, T3, R> Result<F, R> zip(
     Result<F, T0> r0,
     Result<F, T1> r1,
     Result<F, T2> r2,
     Result<F, T3> r3,
     Finisher4<T0, T1, T2, T3, R> finisher) {
    return r0.flatMap(_0 ->
      r1.flatMap(_1 ->
      r2.flatMap(_2 ->
      r3.map(_3 -> finisher.apply(_0, _1, _2, _3))
      )));
  }

  static <F, T0, T1, T2, T3, T4, R> Result<F, R> zip(
     Result<F, T0> r0,
     Result<F, T1> r1,
     Result<F, T2> r2,
     Result<F, T3> r3,
     Result<F, T4> r4,
     Finisher5<T0, T1, T2, T3, T4, R> finisher) {
    return r0.flatMap(_0 ->
      r1.flatMap(_1 ->
      r2.flatMap(_2 ->
      r3.flatMap(_3 ->
      r4.map(_4 -> finisher.apply(_0, _1, _2, _3, _4))
      ))));
  }

  static <F, T0, T1, T2, T3, T4, T5, R> Result<F, R> zip(
     Result<F, T0> r0,
     Result<F, T1> r1,
     Result<F, T2> r2,
     Result<F, T3> r3,
     Result<F, T4> r4,
     Result<F, T5> r5,
     Finisher6<T0, T1, T2, T3, T4, T5, R> finisher) {
    return r0.flatMap(_0 ->
      r1.flatMap(_1 ->
      r2.flatMap(_2 ->
      r3.flatMap(_3 ->
      r4.flatMap(_4 ->
      r5.map(_5 -> finisher.apply(_0, _1, _2, _3, _4, _5))
      )))));
  }

  static <F, T0, T1, T2, T3, T4, T5, T6, R> Result<F, R> zip(
     Result<F, T0> r0,
     Result<F, T1> r1,
     Result<F, T2> r2,
     Result<F, T3> r3,
     Result<F, T4> r4,
     Result<F, T5> r5,
     Result<F, T6> r6,
     Finisher7<T0, T1, T2, T3, T4, T5, T6, R> finisher) {
    return r0.flatMap(_0 ->
      r1.flatMap(_1 ->
      r2.flatMap(_2 ->
      r3.flatMap(_3 ->
      r4.flatMap(_4 ->
      r5.flatMap(_5 ->
      r6.map(_6 -> finisher.apply(_0, _1, _2, _3, _4, _5, _6))
      ))))));
  }

  static <F, T0, T1, T2, T3, T4, T5, T6, T7, R> Result<F, R> zip(
     Result<F, T0> r0,
     Result<F, T1> r1,
     Result<F, T2> r2,
     Result<F, T3> r3,
     Result<F, T4> r4,
     Result<F, T5> r5,
     Result<F, T6> r6,
     Result<F, T7> r7,
     Finisher8<T0, T1, T2, T3, T4, T5, T6, T7, R> finisher) {
    return r0.flatMap(_0 ->
      r1.flatMap(_1 ->
      r2.flatMap(_2 ->
      r3.flatMap(_3 ->
      r4.flatMap(_4 ->
      r5.flatMap(_5 ->
      r6.flatMap(_6 ->
      r7.map(_7 -> finisher.apply(_0, _1, _2, _3, _4, _5, _6, _7))
      )))))));
  }

  static <F, T0, T1, T2, T3, T4, T5, T6, T7, T8, R> Result<F, R> zip(
     Result<F, T0> r0,
     Result<F, T1> r1,
     Result<F, T2> r2,
     Result<F, T3> r3,
     Result<F, T4> r4,
     Result<F, T5> r5,
     Result<F, T6> r6,
     Result<F, T7> r7,
     Result<F, T8> r8,
     Finisher9<T0, T1, T2, T3, T4, T5, T6, T7, T8, R> finisher) {
    return r0.flatMap(_0 ->
      r1.flatMap(_1 ->
      r2.flatMap(_2 ->
      r3.flatMap(_3 ->
      r4.flatMap(_4 ->
      r5.flatMap(_5 ->
      r6.flatMap(_6 ->
      r7.flatMap(_7 ->
      r8.map(_8 -> finisher.apply(_0, _1, _2, _3, _4, _5, _6, _7, _8))
      ))))))));
  }

  // end generated code

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
      case Failure<F, S> _ -> (Result<F, R>) this;
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
      case Success<F, S> _ -> (Result<R, S>) this;
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
      case Failure<F, S> _ -> (Result<F, R>) this;
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
    return getOrElseThrow(_ -> new NoSuchElementException());
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
