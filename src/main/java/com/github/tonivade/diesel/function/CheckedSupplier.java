/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel.function;

import java.util.function.Supplier;

/**
 * Similar to {@link Supplier} but {@code get()} method throws a checked exception.
 *
 * @param <T> the type of the result
 */
@FunctionalInterface
public interface CheckedSupplier<T> {

  /**
   * Gets a result.
   *
   * @return a result
   * @throws Exception a checked exception
   */
  T get() throws Exception;

}
