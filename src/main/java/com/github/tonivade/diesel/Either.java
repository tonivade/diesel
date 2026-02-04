/*
 * Copyright (c) 2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import java.util.Collection;
import java.util.function.Function;

/**
 * Represents a value of one of two possible types (a disjoint union).
 * <p>
 * This is a simple implementation of the Either type, which is often used
 * in functional programming to represent a value that can be one of two
 * types. It is commonly used for error handling, where one type represents
 * a success value and the other type represents an error value.
 *
 * @param <L> the type of the left value
 * @param <R> the type of the right value
 */
public sealed interface Either<L, R> {

  /**
   * Represents the left value of the Either type.
   *
   * @param <L> the type of the left value
   * @param <R> the type of the right value
   */
  record Left<L, R>(L left) implements Either<L, R> {}

  /**
   * Represents the right value of the Either type.
   *
   * @param <L> the type of the left value
   * @param <R> the type of the right value
   */
  record Right<L, R>(R right) implements Either<L, R> {}

  /**
   * Creates an instance of Either representing a left value.
   *
   * @param left the left value
   * @param <L> the type of the left value
   * @param <R> the type of the right value
   */
  static <L, R> Either<L, R> left(L left) {
    return new Left<>(left);
  }

  /**
   * Creates an instance of Either representing a right value.
   *
   * @param right the right value
   * @param <L> the type of the left value
   * @param <R> the type of the right value
   */
  static <L, R> Either<L, R> right(R right) {
    return new Right<>(right);
  }

  /**
   * Collects all right values from a collection of Either instances.
   *
   * @param values the collection of Either instances
   * @param <R> the type of the right values
   * @return a collection of right values
   */
  static <R> Collection<R> collectRight(Collection<Either<?, R>> values) {
    return values.stream().<R>mapMulti((item, consumer) -> {
      if (item instanceof Right(var right)) {
        consumer.accept(right);
      }
    }).toList();
  }

  /**
   * Collects all left values from a collection of Either instances.
   *
   * @param values the collection of Either instances
   * @param <L> the type of the left values
   * @return a collection of left values
   */
  static <L> Collection<L> collectLeft(Collection<Either<L, ?>> values) {
    return values.stream().<L>mapMulti((item, consumer) -> {
      if (item instanceof Left(var left)) {
        consumer.accept(left);
      }
    }).toList();
  }

  /**
   * Folds the Either instance by applying the appropriate function based on its type.
   *
   * @param <T> return type of the folding functions
   * @param onLeft the function to apply if the instance is a Left
   * @param onRight the function to apply if the instance is a Right
   * @return the result of applying the appropriate function
   */
  default <T> T fold(Function<L, T> onLeft, Function<R, T> onRight) {
    return switch (this) {
      case Left<L, R>(L left) -> onLeft.apply(left);
      case Right<L, R>(R right) -> onRight.apply(right);
    };
  }
}
