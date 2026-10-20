/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

/**
 * The errors that a program never recovers from.
 *
 * <p>A {@link VirtualMachineError}, like an {@link OutOfMemoryError} or a {@link StackOverflowError},
 * means the JVM can't go on safely, so it is not turned into a failure, not caught by
 * {@code catchAll} and the finalizers don't run: it is rethrown as soon as it is caught. Any other
 * {@link Throwable}, including other errors like an {@link AssertionError}, is handled like an
 * exception.
 *
 * <p>Where a program waits for another one, like a fork, the fatal error is still passed to the
 * future it waits for before it is rethrown, so the waiting program fails instead of waiting forever.
 */
final class Fatal {

  private Fatal() {}

  /**
   * Rethrows the error if it is fatal, otherwise it does nothing.
   *
   * @param error the error that was caught
   */
  static void rethrowIfFatal(Throwable error) {
    if (error instanceof VirtualMachineError fatal) {
      throw fatal;
    }
  }
}
