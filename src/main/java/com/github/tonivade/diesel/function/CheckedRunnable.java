/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel.function;

/**
 * Similar to {@link Runnable} but {@code run()} method throws a checked exception.
 */
@FunctionalInterface
public interface CheckedRunnable {

  /**
   * Runs this operation.
   *
   * @throws Exception a checked exception
   */
  void run() throws Exception;

}
