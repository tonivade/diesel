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
import static java.util.function.Function.identity;

import com.github.tonivade.diesel.Result.Failure;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

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

  // the combinators of Concurrent: they wait for the forked programs, cancel the ones that are no
  // longer needed, and only complete once the cancelled ones have stopped

  /**
   * Waits, fail-fast, for all the fibers: the first one that fails cancels the others.
   */
  static <S, E> Program<S, E, Unit> all(Collection<? extends Fiber<E, ?>> fibers) {
    return Program.join(allFailFast(futures(fibers)));
  }

  /**
   * Waits, fail-fast, for all the fibers and then combines their results in the program that
   * waits, not in the thread that completes the last one.
   */
  static <S, E, R> Program<S, E, R> joinAll(
      Collection<? extends Fiber<E, ?>> fibers, Supplier<? extends Result<E, R>> combine) {
    return pipe(
        all(fibers),
        // built when it runs, in the thread of the parent, so if combine throws it's an ordinary
        // exception of the program and catchAll can handle it
        _ -> suspend(() -> Program.from(combine.get())));
  }

  /**
   * Waits, fail-fast, for all the fibers and collects their results in order.
   */
  static <S, E, T> Program<S, E, Collection<T>> sequence(Collection<Fiber<E, T>> fibers) {
    return joinAll(fibers, () -> Result.sequence(fibers.stream().map(Fiber::resultNow).toList()));
  }

  /**
   * Waits for the first fiber that succeeds and cancels the others. It fails only if all of them
   * fail.
   */
  static <S, E, T> Program<S, E, T> any(Collection<Fiber<E, T>> fibers) {
    return Program.join(anySuccess(fibers.stream().map(Fiber::future).toList()));
  }

  /**
   * Waits for the first fiber that finishes, whatever its outcome, and cancels the other.
   */
  static <S, E, T, U> Program<S, E, Either<T, U>> race(Fiber<E, T> f1, Fiber<E, U> f2) {
    return Program.join(raceFuture(f1.future, f2.future));
  }

  /**
   * Waits for the first fiber that succeeds and cancels the other. It fails only if both fail.
   */
  static <S, E, T, U> Program<S, E, Either<T, U>> either(Fiber<E, T> f1, Fiber<E, U> f2) {
    return Program.join(eitherFuture(f1.future, f2.future));
  }

  /**
   * Waits for the work, cancelling it when the timer expires first.
   */
  static <S, E, T> Program<S, E, T> timeout(Fiber<E, Unit> timer, Fiber<E, T> work) {
    return Program.join(timeoutFuture(timer.future, work.future));
  }

  private static <E> List<CompletableFuture<? extends Result<E, ?>>> futures(
      Collection<? extends Fiber<E, ?>> fibers) {
    return fibers.stream().<CompletableFuture<? extends Result<E, ?>>>map(Fiber::future).toList();
  }

  private static <E, T> CompletableFuture<Result<E, T>> timeoutFuture(
      CompletableFuture<Result<E, Unit>> timer, CompletableFuture<Result<E, T>> work) {
    var timedOut = new AtomicBoolean();
    var result = new CompletableFuture<Result<E, T>>();

    var _ = timer.whenComplete((_, error) -> {
      if (error == null) {
        // the timer expired: it only asks the program to stop, the program decides the result
        timedOut.set(true);
        work.cancel(true);
      } else if (!isCancellation(error)) {
        // the timer itself failed, like a rejected executor: don't leave the program unbounded
        result.completeExceptionally(error);
        work.cancel(true);
      }
    });

    var _ = work.whenComplete((value, error) -> {
      if (error == null) {
        // even if the timer expired: a value that was already produced must not be lost
        result.complete(value);
      } else if (timedOut.get() && isCancellation(error)) {
        result.completeExceptionally(new TimeoutException());
      } else {
        result.completeExceptionally(error);
      }
      // the program is done, so the timer isn't needed anymore
      timer.cancel(true);
    });

    return afterAll(List.of(timer, work), result);
  }

  private static boolean isCancellation(Throwable error) {
    return error instanceof CancellationException
        || (error instanceof CompletionException && error.getCause() instanceof CancellationException);
  }

  private static <E> CompletableFuture<Result<E, Unit>> allFailFast(
      Collection<? extends CompletableFuture<? extends Result<E, ?>>> futures) {
    if (futures.isEmpty()) {
      return CompletableFuture.completedFuture(Result.unit());
    }
    var result = new CompletableFuture<Result<E, Unit>>();
    var remaining = new AtomicInteger(futures.size());

    for (var future : futures) {
      var _ = future.whenComplete((value, error) -> {
        if (error != null) {
          result.completeExceptionally(error);
        } else if (value instanceof Failure(var fail)) {
          result.complete(Result.failure(fail));
        } else if (remaining.decrementAndGet() == 0) {
          result.complete(Result.unit());
        }
      });
    }

    var outcome = result.whenComplete((value, error) -> {
      if (error != null || value instanceof Failure) {
        // fail-fast: cancel the programs that are still running
        futures.forEach(future -> future.cancel(true));
      }
    });
    return afterAll(futures, outcome);
  }

  private static <E, T, U> CompletableFuture<Result<E, Either<T, U>>> raceFuture(
      CompletableFuture<Result<E, T>> f1, CompletableFuture<Result<E, U>> f2) {
    var outcome = f1.thenApply(t -> t.map(Either::<T, U>left))
        .applyToEither(f2.thenApply(u -> u.map(Either::<T, U>right)), identity())
        .whenComplete((_, _) -> cancelBoth(f1, f2));
    return afterAll(List.of(f1, f2), outcome);
  }

  private static <E, T, U> CompletableFuture<Result<E, Either<T, U>>> eitherFuture(
      CompletableFuture<Result<E, T>> f1, CompletableFuture<Result<E, U>> f2) {
    // parAnySuccess only cancels the mapped futures it receives, and cancelling a future doesn't
    // reach the future it was mapped from: the forked programs have to be cancelled through f1
    // and f2, and waited for through them too
    var outcome = anySuccess(List.of(
            f1.thenApply(t -> t.map(Either::<T, U>left)),
            f2.thenApply(u -> u.map(Either::<T, U>right))))
        .whenComplete((_, _) -> cancelBoth(f1, f2));
    return afterAll(List.of(f1, f2), outcome);
  }

  private static <E, T> CompletableFuture<Result<E, T>> anySuccess(
      Collection<? extends CompletableFuture<Result<E, T>>> futures) {
    var result = new CompletableFuture<Result<E, T>>();
    var remaining = new AtomicInteger(futures.size());

    for (var future : futures) {
      var _ = future.whenComplete((value, error) -> {
        // only fail when all programs have failed, otherwise wait for the others
        if (error != null) {
          if (remaining.decrementAndGet() == 0) {
            result.completeExceptionally(error);
          }
        } else if (value instanceof Failure(var fail)) {
          if (remaining.decrementAndGet() == 0) {
            result.complete(Result.failure(fail));
          }
        } else {
          result.complete(value);
        }
      });
    }

    var outcome = result.whenComplete((_, _) -> futures.forEach(f -> f.cancel(true)));
    return afterAll(futures, outcome);
  }

  /**
   * Completes like {@code outcome} once all the futures are done too, so the cancelled programs
   * have stopped and run their finalizers before the caller continues.
   */
  private static <T> CompletableFuture<T> afterAll(
      Collection<? extends CompletableFuture<?>> futures, CompletableFuture<T> outcome) {
    var all = new ArrayList<CompletableFuture<?>>(futures);
    all.add(outcome);
    return CompletableFuture.allOf(all.toArray(new CompletableFuture<?>[0]))
        // the cancelled programs complete exceptionally, that is expected
        .handle((_, _) -> null)
        .thenCompose(_ -> outcome);
  }

  private static void cancelBoth(CompletableFuture<?> f1, CompletableFuture<?> f2) {
    try {
      if (!f1.isDone()) {
        f1.cancel(true);
      }
    } finally {
      if (!f2.isDone()) {
        f2.cancel(true);
      }
    }
  }
}
