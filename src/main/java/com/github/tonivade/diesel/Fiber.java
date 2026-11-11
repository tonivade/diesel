/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Combine.pipe;
import static com.github.tonivade.diesel.Program.raise;
import static com.github.tonivade.diesel.Program.success;
import static com.github.tonivade.diesel.Program.suspend;
import static com.github.tonivade.diesel.Program.task;
import static java.util.Objects.requireNonNull;

import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;

/**
 * A program running concurrently, started by {@link Program#fork()}.
 *
 * <p>A fiber can be joined, to wait for its result, cancelled, or polled, to look at its result
 * without waiting.
 *
 * <p>Cancellation is cooperative: the program stops at its next step and runs its finalizers.
 * {@link #cancel()} waits until it has stopped. If the program finishes before it sees the
 * cancellation, it keeps its result, and a later {@link #join()} returns it. A program cancelled
 * before it starts never runs.
 *
 * <p>The types of the state and the error of the returned programs are parameters of each method,
 * so a fiber can be used in programs of any state. Java infers them from where the program is
 * used, which a chained call doesn't tell: in {@code fiber.join().map(...)} they are inferred as
 * {@code Object}. Pass the program to a {@link Combine} helper instead, like
 * {@code pipe(fiber.join(), value -> ...)}, or give the types, like
 * {@code fiber.<Void>join().map(...)}.
 *
 * @param <E> the type of the error of the program
 * @param <T> the type of the result of the program
 */
public final class Fiber<E, T> {

  private final CancelableFuture<Result<E, T>> future;

  Fiber(CancelableFuture<Result<E, T>> future) {
    this.future = requireNonNull(future);
  }

  /**
   * Waits for the program and returns its result.
   *
   * <p>If the program fails, with an error or an exception, the returned program fails the same
   * way. If it was cancelled, it throws a {@link CancellationException}. Cancelling the program
   * that waits doesn't cancel the fiber.
   *
   * @param <S> the type of the state
   * @return a program that waits for the result of the fiber
   */
  public <S> Program<S, E, T> join() {
    return Program.join(future);
  }

  /**
   * Cancels the program and waits until it has stopped and run its finalizers.
   *
   * <p>It doesn't fail, whatever the outcome of the program, even if it had already finished.
   *
   * @param <S> the type of the state
   * @return a program that cancels the fiber and waits for it
   */
  public <S> Program<S, Void, Unit> cancel() {
    // the future of a fork only completes once the cancelled program has run its finalizers
    return pipe(
        task(() -> future.cancel(true)),
        _ -> Program.join(future.handle((_, _) -> Result.unit())));
  }

  /**
   * Returns the result of the program if it has finished, without waiting.
   *
   * <p>If the program ended with an exception, or was cancelled, the returned program throws that
   * exception, or a {@link CancellationException}.
   *
   * @param <S> the type of the state
   * @return a program that yields the result, or an empty optional if the fiber is still running
   */
  public <S> Program<S, Void, Optional<Result<E, T>>> poll() {
    return suspend(() -> {
      if (!future.isDone()) {
        return success(Optional.empty());
      }
      try {
        return success(Optional.of(future.join()));
      } catch (CancellationException e) {
        return raise(() -> e);
      } catch (CompletionException e) {
        var cause = e.getCause() != null ? e.getCause() : e;
        return raise(() -> cause);
      }
    });
  }

  /**
   * The future completed when the program stops, for the concurrent combinators and the tests.
   */
  CancelableFuture<Result<E, T>> future() {
    return future;
  }

  /**
   * The result of a fiber that has finished normally, like after waiting for it.
   */
  Result<E, T> resultNow() {
    return future.join();
  }

  @Override
  public String toString() {
    return "Fiber(" + (future.isDone() ? "done" : "running") + ")";
  }
}
