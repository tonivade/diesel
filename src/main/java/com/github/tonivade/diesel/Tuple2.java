/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static java.util.Objects.requireNonNull;

/**
 * A pair of values. Null values are not allowed.
 *
 * @param <A> the type of the first value
 * @param <B> the type of the second value
 * @param first first elevement of the tuple
 * @param second second elevement of the tuple
 */
public record Tuple2<A, B>(A first, B second) {

  public Tuple2 {
    requireNonNull(first);
    requireNonNull(second);
  }

  /**
   * Creates a pair containing the supplied values.
   *
   * @param <A> the type of the first value
   * @param <B> the type of the second value
   * @param a the first value
   * @param b the second value
   * @return a pair containing {@code a} and {@code b}
   */
  public static <A, B> Tuple2<A, B> tuple(A a, B b) {
    return new Tuple2<>(a, b);
  }
}
