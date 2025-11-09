/*
 * Copyright (c) 2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel.function;

// generated code
public interface Finisher4<T0, T1, T2, T3, R> {
 
  R apply(T0 t0, T1 t1, T2 t2, T3 t3);

  static <T0, T1, T2, T3> Finisher4<T0, T1, T2, T3, T0> first() {
    return (t0, _, _, _) -> t0;
  }
  
  static <T0, T1, T2, T3> Finisher4<T0, T1, T2, T3, T3> last() {
    return (_, _, _, t3) -> t3;
  }
}