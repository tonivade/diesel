/*
 * Copyright (c) 2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static java.util.function.Function.identity;

import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A Validator is a function that takes a value of type T and returns a Program that produces either
 * a validation error of type E or indicates success.
 *
 * @param <S> The state type of the Program
 * @param <E> The error type for validation failures
 * @param <T> The type of the value to be validated
 */
public interface Validator<S, E, T> {

  Program<?, Void, ?> VALID = Program.success(Either.left(new Object()));

  /**
   * Applies the validation to the given value.
   *
   * @param value The value to be validated
   * @return A Program that produces either a validation error of type E or indicates success
   */
  Program<S, Void, Either<?, E>> apply(T value);

  @SuppressWarnings("unchecked")
  static <S, E> Program<S, Void, Either<?, E>> valid() {
    return (Program<S, Void, Either<?, E>>) VALID;
  }

  static <S, E> Program<S, Void, Either<?, E>> invalid(E error) {
    return Program.success(Either.right(error));
  }

  /**
   * Creates a Validator based on the given predicate and error mapper.
   *
   * @param predicate The predicate to test the value
   * @param mapper The function to map the invalid value to an error of type E
   * @param <S> The state type of the Program
   * @param <E> The error type for validation failures
   * @param <T> The type of the value to be validated
   * @return A Validator that uses the given predicate and mapper
   */
  static <S, E, T> Validator<S, E, T> of(Predicate<T> predicate, Function<T, E> mapper) {
    return of(identity(), predicate, mapper);
  }

  /**
   * Creates a Validator based on the given accessor, predicate, and error mapper.
   *
   * @param accessor The function to access the field to be validated
   * @param predicate The predicate to test the accessed field
   * @param mapper The function to map the invalid field to an error of type E
   * @param <S> The state type of the Program
   * @param <E> The error type for validation failures
   * @param <T> The type of the value to be validated
   * @param <R> The type of the field to be validated
   * @return A Validator that uses the given accessor, predicate, and mapper
   */
  static <S, E, T, R> Validator<S, E, T> of(Function<T, R> accessor, Predicate<R> predicate, Function<R, E> mapper) {
    return value -> {
      var field = accessor.apply(value);
      if (predicate.test(field)) {
        return valid();
      }
      return invalid(mapper.apply(field));
    };
  }

  /**
   * Creates a Validator from a Program that will fail when the program fails, mapping errors of type E to type F.
   *
   * @param program The Program that produces errors of type E
   * @param mapper The function to map errors of type E to type F
   * @param <S> The state type of the Program
   * @param <E> The error type produced by the Program
   * @param <T> The type of the value to be validated
   * @param <F> The error type for validation failures
   * @return A Validator that uses the given Program and mapper
   */
  static <S, E, T, F> Validator<S, F, T> fromFailure(Program<S, E, ?> program, Function<E, F> mapper) {
    return _ -> program.foldMap(error -> invalid(mapper.apply(error)), _ -> valid());
  }

  /**
   * Creates a Validator from a Program that will fail when the program succeeds, mapping values of type V to errors of type F.
   *
   * @param program The Program that produces values of type V
   * @param mapper The function to map values of type V to errors of type F
   * @param <S> The state type of the Program
   * @param <E> The error type produced by the Program
   * @param <T> The type of the value to be validated
   * @param <V> The value type produced by the Program
   * @param <F> The error type for validation failures
   * @return A Validator that uses the given Program and mapper
   */
  static <S, E, T, V, F> Validator<S, F, T> fromSuccess(Program<S, E, V> program, Function<V, F> mapper) {
    return _ -> program.foldMap(_ -> valid(), value -> invalid(mapper.apply(value)));
  }
}
