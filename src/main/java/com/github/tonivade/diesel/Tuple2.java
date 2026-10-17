/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

public record Tuple2<A, B>(A first, B second) {

  public static final <A, B> Tuple2<A, B> tuple(A a, B b) {
    return new Tuple2<>(a, b);
  }
}
