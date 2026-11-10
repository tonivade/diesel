/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

/**
 * A type with a single value, used as the result of a program that has no meaningful result, like
 * {@code void} in a method.
 */
public enum Unit {

  /**
   * The only value of the type.
   */
  INSTANCE;

  /**
   * Returns the only value of the type.
   *
   * @return {@link #INSTANCE}
   */
  public static Unit unit() {
    return INSTANCE;
  }
  
  @Override
  public String toString() {
    return "Unit";
  }
}
