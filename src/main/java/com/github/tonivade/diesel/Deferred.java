/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Program.asyncCancelable;
import static com.github.tonivade.diesel.Program.supply;
import static com.github.tonivade.diesel.Program.task;
import static java.util.Objects.requireNonNull;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Models a value that is set only once and that programs can wait for.
 *
 * <p>It starts empty. {@link #complete(Object)} sets the value, only the first time it's called,
 * and {@link #get()} waits until the value is set. It's a way to signal between concurrent
 * programs, or to hand a result from one to others, and together with {@link Ref} it can be used
 * to build other synchronization primitives, like latches or semaphores.
 *
 * <p>A program waiting in {@link #get()} can be cancelled, like by a {@code timeout} or a
 * {@code race}, without affecting the value or the other programs waiting for it. A waiting
 * program holds its thread while it waits, which is cheap on virtual threads. A program waiting in
 * an uncancelable region, like a finalizer, can't be woken up, so it waits forever if the value is
 * never set.
 *
 * <p>The value can't be {@code null}.
 *
 * @param <S> the type of the state
 * @param <E> the type of the error
 * @param <A> type of stored value
 */
public final class Deferred<S, E, A> {

  private final CompletableFuture<A> value = new CompletableFuture<>();
  // the programs waiting in get: each one leaves when it's woken up or cancelled, so the waiters
  // that are cancelled while the value isn't set are not kept
  private final Set<Consumer<A>> waiters = ConcurrentHashMap.newKeySet();

  private Deferred() {}

  /**
   * Waits until the value is set and returns it.
   *
   * <p>If the value is already set, it returns it at once.
   *
   * @param <S1> the type of the state
   * @param <E1> the type of the error
   * @return a program that waits for the value when evaluated
   */
  public <S1 extends S, E1 extends E> Program<S1, E1, A> get() {
    return asyncCancelable((_, callback) -> {
      Consumer<A> waiter = current -> callback.complete(Result.success(current));
      waiters.add(waiter);
      // complete sets the value before it wakes up the waiters, so a value set before the waiter
      // was added is seen here
      if (value.isDone()) {
        waiters.remove(waiter);
        waiter.accept(value.join());
      }
      return task(() -> waiters.remove(waiter));
    });
  }

  /**
   * Sets the value, if it isn't set yet, and wakes up the programs waiting for it.
   *
   * @param <S1> the type of the state
   * @param <E1> the type of the error
   * @param newValue the value to set, not {@code null}
   * @return a program that returns {@code true} if it set the value, or {@code false} if the value
   *     was already set, in which case the value doesn't change
   * @throws NullPointerException if the value is {@code null}
   */
  public <S1 extends S, E1 extends E> Program<S1, E1, Boolean> complete(A newValue) {
    requireNonNull(newValue, "the value of a deferred can't be null");
    return supply(() -> {
      if (value.complete(newValue)) {
        // a waiter added after this point sees the value in get and wakes itself up, so clearing
        // the set never leaves a waiter asleep. Waking one that was just cancelled does nothing
        waiters.forEach(waiter -> waiter.accept(newValue));
        waiters.clear();
        return true;
      }
      return false;
    });
  }

  /**
   * Returns the value if it's set, without waiting.
   *
   * @param <S1> the type of the state
   * @param <E1> the type of the error
   * @return a program that returns the value, or an empty optional if it isn't set yet
   */
  public <S1 extends S, E1 extends E> Program<S1, E1, Optional<A>> tryGet() {
    return supply(this::current);
  }

  /**
   * Creates a program that allocates a new, empty deferred each time the program is evaluated.
   *
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <A> the type of the value
   * @return a program that creates a fresh deferred when evaluated
   */
  public static <S, E, A> Program<S, E, Deferred<S, E, A>> make() {
    return supply(Deferred::create);
  }

  /**
   * Creates a new, empty deferred immediately.
   *
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <A> the type of the value
   * @return a new deferred
   */
  public static <S, E, A> Deferred<S, E, A> create() {
    return new Deferred<>();
  }

  /**
   * Returns a string representation containing the value, if it's set.
   *
   * @return a string representation of this deferred
   */
  @Override
  public String toString() {
    return current()
        .map(v -> "Deferred(%s)".formatted(v))
        .orElse("Deferred(<empty>)");
  }

  // the number of programs waiting in get, for the tests
  int waiting() {
    return waiters.size();
  }

  private Optional<A> current() {
    // the future is never completed exceptionally, so getNow doesn't throw
    return Optional.ofNullable(value.getNow(null));
  }
}
