/*
 * Copyright (c) 2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel.function;

// generated code
public interface Finisher2<T0, T1, R> {
 
  R apply(T0 t0, T1 t1);

  static <T0, T1> Finisher2<T0, T1, T0> first() {
    return (t0, _) -> t0;
  }
  
  static <T0, T1> Finisher2<T0, T1, T1> last() {
    return (_, t1) -> t1;
  }
}