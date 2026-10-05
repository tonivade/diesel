/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cooperative cancellation signal shared by a running program and the programs it forks.
 *
 * <p>Cancelling a token cancels all its children, so a cancelled program also cancels the
 * programs it has forked.
 */
final class CancelToken {

  /**
   * Token of the program running in the current thread, bound while a forked program runs.
   */
  static final ScopedValue<CancelToken> CURRENT = ScopedValue.newInstance();

  /**
   * Root token that is never cancelled, used by programs evaluated outside of a fork.
   */
  static final CancelToken NONE = new CancelToken();

  private static final Registration NOOP = () -> {};

  private final AtomicBoolean cancelled = new AtomicBoolean();
  private final Set<Callback> callbacks = ConcurrentHashMap.newKeySet();
  private final Set<CompletableFuture<?>> forks = ConcurrentHashMap.newKeySet();
  private Registration parent = NOOP;

  private CancelToken() {}

  static CancelToken current() {
    return CURRENT.orElse(NONE);
  }

  boolean isCancelled() {
    return cancelled.get();
  }

  /**
   * Creates a token that is cancelled when this one is cancelled.
   */
  CancelToken child() {
    var child = new CancelToken();
    child.parent = onCancel(child::cancel);
    return child;
  }

  /**
   * Stops listening to the parent token, call it once the program using this token has finished.
   */
  void detach() {
    parent.remove();
  }

  /**
   * Keeps track of a program forked by the program using this token until it completes.
   */
  // futures are compared by identity, which is what is needed to track each fork
  @SuppressWarnings("CollectionUndefinedEquality")
  void track(CompletableFuture<?> fork) {
    if (this == NONE) {
      // never cancelled, so there is never anything to wait for
      return;
    }
    forks.add(fork);
    var _ = fork.whenComplete((_, _) -> forks.remove(fork));
  }

  /**
   * Waits until all the tracked forks have completed, so a cancelled program finishes only after
   * the programs it forked have stopped and run their finalizers.
   */
  void awaitForks() {
    while (!forks.isEmpty()) {
      CompletableFuture.allOf(forks.toArray(new CompletableFuture<?>[0]))
          // the forks are expected to complete exceptionally, they have been cancelled
          .handle((_, _) -> null)
          .join();
    }
  }

  /**
   * Registers an action to run when the token is cancelled, it runs immediately if the token is
   * already cancelled.
   */
  Registration onCancel(Runnable action) {
    if (this == NONE) {
      return NOOP;
    }
    var callback = new Callback(action);
    callbacks.add(callback);
    if (cancelled.get()) {
      // cancelled while registering: run it here unless cancel() already did
      callback.runOnce(callbacks);
    }
    return () -> callbacks.remove(callback);
  }

  void cancel() {
    if (this != NONE && cancelled.compareAndSet(false, true)) {
      for (var callback : callbacks) {
        callback.runOnce(callbacks);
      }
    }
  }

  /**
   * Handle to stop listening to a token.
   */
  interface Registration {
    void remove();
  }

  /**
   * Signal used by the interpreter to unwind a cancelled program, unlike other exceptions it can't
   * be caught by {@link Program#catchAll(java.util.function.Function)}.
   */
  static final class Cancelled extends CancellationException {

    private static final long serialVersionUID = 1L;

    Cancelled() {
      super("program cancelled");
    }
  }

  private static final class Callback {

    private final Runnable action;

    private Callback(Runnable action) {
      this.action = action;
    }

    private void runOnce(Set<Callback> callbacks) {
      // removing it first guarantees the action runs only once
      if (callbacks.remove(this)) {
        action.run();
      }
    }
  }
}
