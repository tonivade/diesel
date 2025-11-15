/*
 * Copyright (c) 2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

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

  static <L, R> Either<L, R> left(L left) {
    return new Left<>(left);
  }

  static <L, R> Either<L, R> right(R right) {
    return new Right<>(right);
  }

  record Left<L, R>(L left) implements Either<L, R> {}
  record Right<L, R>(R right) implements Either<L, R> {}

  default <T> T fold(Function<L, T> onLeft, Function<R, T> onRight) {
    return switch (this) {
      case Left<L, R>(L left) -> onLeft.apply(left);
      case Right<L, R>(R right) -> onRight.apply(right);
    };
  }
}
