/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import java.util.concurrent.CompletableFuture;

/**
 * Future of a forked program, cancelling it cancels the program.
 *
 * <p>Cancellation is cooperative: {@link #cancel(boolean)} only signals the program, which stops at
 * its next step, runs its finalizers and then completes this future with a
 * {@link java.util.concurrent.CancellationException}.
 */
final class CancelableFuture<T> extends CompletableFuture<T> {

  private final CancelToken token;

  CancelableFuture(CancelToken token) {
    this.token = token;
  }

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    if (isDone()) {
      return false;
    }
    token.cancel();
    return true;
  }
}
