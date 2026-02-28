/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel.function;

// generated code
public interface Finisher9<T0, T1, T2, T3, T4, T5, T6, T7, T8, R> {
 
  R apply(T0 t0, T1 t1, T2 t2, T3 t3, T4 t4, T5 t5, T6 t6, T7 t7, T8 t8);

  static <T0, T1, T2, T3, T4, T5, T6, T7, T8> Finisher9<T0, T1, T2, T3, T4, T5, T6, T7, T8, T0> first() {
    return (t0, _, _, _, _, _, _, _, _) -> t0;
  }
  
  static <T0, T1, T2, T3, T4, T5, T6, T7, T8> Finisher9<T0, T1, T2, T3, T4, T5, T6, T7, T8, T8> last() {
    return (_, _, _, _, _, _, _, _, t8) -> t8;
  }
}