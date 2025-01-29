/*
 * Copyright (c) 2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import java.util.function.Function;

public sealed interface Either<L, R> {

  static <L, R> Either<L, R> left(L left) {
    return new Left<>(left);
  }

  static <L, R> Either<L, R> right(R right) {
    return new Right<L, R>(right);
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
