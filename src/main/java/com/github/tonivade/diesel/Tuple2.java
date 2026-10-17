/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import org.jspecify.annotations.Nullable;

/**
 * A pair of values.
 *
 * @param <A> the type of the first value
 * @param <B> the type of the second value
 */
public record Tuple2<A, B>(@Nullable A first, @Nullable B second) {

  /**
   * Creates a pair containing the supplied values.
   *
   * @param <A> the type of the first value
   * @param <B> the type of the second value
   * @param a the first value
   * @param b the second value
   * @return a pair containing {@code a} and {@code b}
   */
  public static <A, B> Tuple2<A, B> tuple(@Nullable A a, @Nullable B b) {
    return new Tuple2<>(a, b);
  }
}
