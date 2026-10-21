/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

public enum Unit {

  INSTANCE;

  public static Unit unit() {
    return INSTANCE;
  }
  
  @Override
  public String toString() {
    return "Unit";
  }
}
