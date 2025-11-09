/*
 * Copyright (c) 2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel.function;

// generated code
public interface Finisher3<T0, T1, T2, R> {
 
  R apply(T0 t0, T1 t1, T2 t2);

  static <T0, T1, T2> Finisher3<T0, T1, T2, T0> first() {
    return (t0, _, _) -> t0;
  }
  
  static <T0, T1, T2> Finisher3<T0, T1, T2, T2> last() {
    return (_, _, t2) -> t2;
  }
}