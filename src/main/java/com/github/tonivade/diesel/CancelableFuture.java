/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Future of a forked program, cancelling it cancels the program.
 *
 * <p>Cancellation is cooperative: {@link #cancel(boolean)} only signals the program, which stops at
 * its next step, runs its finalizers and then completes this future with a
 * {@link java.util.concurrent.CancellationException}.
 *
 * <p>Unlike {@link java.util.concurrent.Future#cancel(boolean)}, cancelling is a request, not a
 * completion: the future isn't done when {@code cancel} returns, and if the program finishes before
 * it sees the request, the future completes with its result even though {@code cancel} returned
 * {@code true}.
 *
 * <p>A program cancelled before it starts never runs: its future completes right away. Otherwise a
 * cancelled program waiting for a fork that is queued behind it, on the same bounded executor,
 * would wait forever.
 */
final class CancelableFuture<T> extends CompletableFuture<T> {

  private final CancelToken token;
  // claimed either by the task that runs the program or by a cancellation, whichever comes first
  private final AtomicBoolean started = new AtomicBoolean();

  CancelableFuture(CancelToken token) {
    this.token = token;
    // the cancellation can also come from the parent program, through the token
    var _ = token.onCancel(this::cancelIfNotStarted);
  }

  /**
   * Claims the future for the task that runs the program, returns false if the program was
   * cancelled before it started, and then it must not run.
   */
  boolean start() {
    return started.compareAndSet(false, true);
  }

  private void cancelIfNotStarted() {
    if (started.compareAndSet(false, true)) {
      token.detachFromParent();
      completeExceptionally(new CancellationException("cancelled before it started"));
    }
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
