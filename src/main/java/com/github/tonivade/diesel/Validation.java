/*
 * Copyright (c) 2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import java.util.Collection;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A Validation represents the result of a validation process, which can be either valid or invalid.
 *
 * @param <E> The type of the error in case of invalid validation
 */
public sealed interface Validation<E> {

  Valid<?> VALID = new Valid<>();

  /**
   * Represents a valid validation result.
   *
   * @param <E> The type of the error (not used in this case)
   */
  record Valid<E>() implements Validation<E> {}

  /**
   * Represents an invalid validation result with an associated error.
   *
   * @param <E> The type of the error
   * @param error The error associated with the invalid validation
   */
  record Invalid<E>(E error) implements Validation<E> {}

  /**
   * Returns the error associated with an invalid validation.
   *
   * @return the error
   */
  default E error() {
    if (this instanceof Invalid<E>(var error)) {
      return error;
    }
    throw new NoSuchElementException("No error present in Valid");
  }

  /**
   * Folds the Validation into a single value based on its state.
   *
   * @param <R> The type of the result
   * @param onValid A supplier to provide a value if the validation is valid
   * @param onInvalid A function to transform the error if the validation is invalid
   * @return The result of applying the appropriate function based on the validation state
   */
  default <R> R fold(Supplier<? extends R> onValid, Function<? super E, ? extends R> onInvalid) {
    if (this instanceof Invalid<E>(var error)) {
      return onInvalid.apply(error);
    }
    return onValid.get();
  }

  /**
   * Creates a valid Validation instance.
   *
   * @param <E> The type of the error (not used in this case)
   * @return A valid Validation instance
   */
  @SuppressWarnings("unchecked")
  static <E> Validation<E> valid() {
    return (Validation<E>) VALID;
  }

  /**
   * Creates an invalid Validation instance with the specified error.
   *
   * @param <E> The type of the error
   * @param error The error associated with the invalid validation
   * @return An invalid Validation instance
   */
  static <E> Validation<E> invalid(E error) {
    return new Invalid<>(error);
  }

  /**
   * Combines a collection of Validation instances into a single Validation.
   * If all validations are valid, the result is valid.
   * If any validation is invalid, the result is invalid with a collection of errors.
   *
   * @param <E> The type of the error
   * @param validations The collection of Validation instances to combine
   * @return A combined Validation instance
   */
  static <E> Validation<Collection<E>> combine(Collection<Validation<E>> validations) {
    var errors = validations.stream()
        .filter(Invalid.class::isInstance)
        .map(Validation::error)
        .toList();
    if (errors.isEmpty()) {
      return valid();
    }
    return invalid(errors);
  }
}
