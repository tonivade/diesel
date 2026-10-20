/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Program.supply;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

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
 * <p>Each waiting program registers a callback in the deferred, which is released when the value
 * is set. A waiter that is cancelled before that keeps its callback registered until then, so a
 * deferred that is never set, waited by many programs that are cancelled, keeps them all.
 *
 * @param <A> type of stored value
 */
public final class Deferred<A> {

  private final CompletableFuture<A> value = new CompletableFuture<>();

  private Deferred() {}

  /**
   * Waits until the value is set and returns it.
   *
   * <p>If the value is already set, it returns it at once.
   *
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @return a program that waits for the value when evaluated
   */
  public <S, E> Program<S, E, A> get() {
    // join doesn't cancel the future when the waiter is cancelled, so the others keep waiting
    return Program.join(value.thenApply(Result::<E, A>success));
  }

  /**
   * Sets the value, if it isn't set yet, and wakes up the programs waiting for it.
   *
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param newValue the value to set
   * @return a program that returns {@code true} if it set the value, or {@code false} if the value
   *     was already set, in which case the value doesn't change
   */
  public <S, E> Program<S, E, Boolean> complete(A newValue) {
    return supply(() -> value.complete(newValue));
  }

  /**
   * Returns the value if it's set, without waiting.
   *
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @return a program that returns the value, or an empty optional if it isn't set yet
   */
  public <S, E> Program<S, E, Optional<A>> tryGet() {
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
  public static <S, E, A> Program<S, E, Deferred<A>> make() {
    return supply(Deferred::create);
  }

  /**
   * Creates a new, empty deferred immediately.
   *
   * @param <A> the type of the value
   * @return a new deferred
   */
  public static <A> Deferred<A> create() {
    return new Deferred<>();
  }

  /**
   * Returns a string representation containing the value, if it's set.
   *
   * @return a string representation of this deferred
   */
  @Override
  public String toString() {
    return current().map(v -> String.format("Deferred(%s)", v)).orElse("Deferred(<empty>)");
  }

  private Optional<A> current() {
    // the future is never completed exceptionally, so getNow doesn't throw
    return Optional.ofNullable(value.getNow(null));
  }
}
