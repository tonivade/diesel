/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Program.pipe;
import static com.github.tonivade.diesel.Program.zip;
import static java.util.function.Function.identity;

import java.util.Collection;
import java.util.List;
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
@FunctionalInterface
public interface Validator<S, E, T> {

  /**
   * Applies the validation to the given value.
   *
   * @param value The value to be validated
   * @return A Program that produces either a validation error of type E or indicates success
   */
  Program<S, Void, Validation<E>> apply(T value);

  /**
   * Combines this Validator with another Validator using logical AND.
   * Both validators must succeed for the combined validator to succeed.
   *
   * @param other The other Validator to combine with
   * @return A Validator that represents the logical AND of this and the other Validator
   */
  default Validator<S, E, T> and(Validator<S, E, T> other) {
    return value -> pipe(
        this.apply(value),
        result -> result.fold(
            () -> other.apply(value),
            Program::invalid
        )
    );
  }

  /**
   * Combines this Validator with another Validator using logical OR.
   * If this validator succeeds, the combined validator succeeds.
   * If this validator fails, the other validator is applied.
   *
   * @param other The other Validator to combine with
   * @return A Validator that represents the logical OR of this and the other Validator
   */
  default Validator<S, E, T> or(Validator<S, E, T> other) {
    return value -> pipe(
        this.apply(value),
        result -> result.fold(
            Program::valid,
            _ -> other.apply(value)
        )
    );
  }

  /**
   * Combines this Validator with another Validator, collecting all validation errors.
   * Both validators are applied, and all errors are collected into a collection.
   *
   * @param other The other Validator to combine with
   * @return A Validator that collects all validation errors from both Validators
   */
  default Validator<S, Collection<E>, T> combine(Validator<S, E, T> other) {
    return value -> zip(
        this.apply(value),
        other.apply(value),
        (first, second) -> Validation.combine(List.of(first, second))
    );
  }

  /**
   * Creates a Validator based on the given predicate and error.
   *
   * @param predicate The predicate to test the value
   * @param error The error to return if the validation fails
   * @param <S> The state type of the Program
   * @param <E> The error type for validation failures
   * @param <T> The type of the value to be validated
   * @return A Validator that uses the given predicate and error
   */
  static <S, E, T> Validator<S, E, T> of(Predicate<? super T> predicate, E error) {
    return of(predicate, _ -> error);
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
  static <S, E, T> Validator<S, E, T> of(Predicate<? super T> predicate, Function<? super T, ? extends E> mapper) {
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
  static <S, E, T, R> Validator<S, E, T> of(Function<? super T, ? extends R> accessor, Predicate<? super R> predicate, Function<? super R, ? extends E> mapper) {
    return of(value -> {
      var field = accessor.apply(value);
      if (predicate.test(field)) {
        return Validation.valid();
      }
      return Validation.invalid(mapper.apply(field));
    });
  }

  /**
   * Creates a Validator based on the given accessor, predicate, and error.
   *
   * @param accessor The function to access the field to be validated
   * @param predicate The predicate to test the accessed field
   * @param error The error to return if the validation fails
   * @param <S> The state type of the Program
   * @param <E> The error type for validation failures
   * @param <T> The type of the value to be validated
   * @param <R> The type of the field to be validated
   * @return A Validator that uses the given accessor, predicate, and error
   */
  static <S, E, T, R> Validator<S, E, T> of(Function<? super T, ? extends R> accessor, Predicate<? super R> predicate, E error) {
    return of(accessor, predicate, _ -> error);
  }

  static <S, E, T> Validator<S, E, T> of(Function<? super T, ? extends Validation<E>> validator) {
    return value -> validator.apply(value).fold(Program::valid, Program::invalid);
  }

  /**
   * Creates a Validator from a Program that will fail when the program fails, using the identity function for error mapping.
   *
   * @param program The Program that produces errors of type E
   * @param <S> The state type of the Program
   * @param <E> The error type produced by the Program
   * @param <T> The type of the value to be validated
   * @return A Validator that uses the given Program and identity mapper
   */
  static <S, E, T> Validator<S, E, T> fromFailure(Program<S, E, ?> program) {
    return fromFailure(program, identity());
  }

  static <S, E, T> Validator<S, E, T> fromFailure(Function<? super T, ? extends Program<S, E, ?>> program) {
    return fromFailure(program, identity());
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
  static <S, E, T, F> Validator<S, F, T> fromFailure(Program<S, E, ?> program, Function<? super E, ? extends F> mapper) {
    return fromFailure(_ -> program, mapper);
  }

  static <S, E, T, F> Validator<S, F, T> fromFailure(Function<? super T, ? extends Program<S, E, ?>> program, Function<? super E, ? extends F> mapper) {
    return value -> program.apply(value).foldMap(
        mapper.andThen(Program::invalid), _ -> Program.valid());
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
  static <S, E, T, V, F> Validator<S, F, T> fromSuccess(Program<S, E, V> program, Function<? super V, ? extends F> mapper) {
    return fromSuccess(_ -> program, mapper);
  }

  static <S, E, T, V, F> Validator<S, F, T> fromSuccess(Function<? super T, ? extends Program<S, E, V>> program, Function<? super V, ? extends F> mapper) {
    return value -> program.apply(value).foldMap(
        _ -> Program.valid(), mapper.andThen(Program::invalid));
  }
}
