/*
 * Copyright (c) 2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel.function;

public interface Finisher2<T0, T1, R> {
 
  R apply(T0 t0, T1 t1);

}