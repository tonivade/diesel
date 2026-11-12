/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Fatal.rethrowIfFatal;
import static com.github.tonivade.diesel.Fatal.sneakyThrow;

import com.github.tonivade.diesel.Frame.CatchFrame;
import com.github.tonivade.diesel.Frame.FinalizerFrame;
import com.github.tonivade.diesel.Frame.FoldFrame;
import com.github.tonivade.diesel.Frame.Region;
import com.github.tonivade.diesel.Frame.RemaskFrame;
import com.github.tonivade.diesel.Frame.UnmaskFrame;
import com.github.tonivade.diesel.Program.Access;
import com.github.tonivade.diesel.Program.Async;
import com.github.tonivade.diesel.Program.Catch;
import com.github.tonivade.diesel.Program.Finalize;
import com.github.tonivade.diesel.Program.FoldMap;
import com.github.tonivade.diesel.Program.Forked;
import com.github.tonivade.diesel.Program.Memoized;
import com.github.tonivade.diesel.Program.Poll;
import com.github.tonivade.diesel.Program.Polled;
import com.github.tonivade.diesel.Program.Pure;
import com.github.tonivade.diesel.Program.Raise;
import com.github.tonivade.diesel.Program.Suspend;
import com.github.tonivade.diesel.Program.Uncancelable;
import com.github.tonivade.diesel.Program.UncancelableWith;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

final class Interpreter {

  @SuppressWarnings({ "unchecked", "rawtypes" })
  static <S, E, T> Result<E, T> run(Program<S, E, T> self, @Nullable S state, CancelToken token) {
    Program<S, ?, ?> current = self;
    Deque<Frame<S>> stack = new ArrayDeque<>();
    Deque<Region> regions = new ArrayDeque<>();

    while (true) {
      try {
        // a cancelled program stops at the start of its next step, unwinding the stack like an
        // exception so the finalizers run
        if (shouldStop(token, regions, current)) {
          throw new CancelToken.Cancelled();
        }
        switch (current) {
          case null -> {
            return sneakyThrow(new NullPointerException("program cannot be null"));
          }
          case Pure(var result) -> {
            var resumed = false;
            while (!resumed) {
              switch (stack.poll()) {
                case null -> {
                  return (Result<E, T>) result;
                }
                case FoldFrame(var onFailure, var onSuccess) -> {
                  current = result.fold(onFailure, onSuccess);
                  resumed = true;
                }
                case FinalizerFrame(var finalizer) -> {
                  // run the finalizer and then continue with the result, unless the finalizer throws
                  stack.push(Frame.fold(Program::failure, _ -> Program.from(result)));
                  enterUncancelable(stack, regions);
                  current = Program.suspend(() -> finalizer.apply(Outcome.from(result)));
                  resumed = true;
                }
                case UnmaskFrame(var region) -> regions.remove(region);
                case RemaskFrame(var region) -> regions.push(region);
                case CatchFrame<S> _ -> {
                  // leaving a catchAll scope normally, its handler no longer applies
                }
              }
            }
          }
          case Access(var mapper) -> {
            current = mapper.apply(state);
          }
          case Async(var callback) -> {
            var async = (BiConsumer<S, CompletableFuture<?>>) callback;
            var result = regions.isEmpty() ?
                awaitCancelable(state, async, token) : awaitUncancelable(state, async);
            current = Program.from(result);
          }
          case Finalize(var source, var finalizer) -> {
            stack.push(Frame.finalizer((Function<Outcome<?, ?>, Program<S, Void, ?>>) finalizer));
            current = source;
          }
          case Uncancelable(var source) -> {
            enterUncancelable(stack, regions);
            current = source;
          }
          case UncancelableWith(var body) -> {
            var region = enterUncancelable(stack, regions);
            current = body.apply(createPoll(region));
          }
          case Polled(var source, var region) -> {
            if (regions.peek() == region) {
              regions.pop();
              stack.push(Frame.remask(region));
            }
            current = source;
          }
          case Forked forked -> {
            // a fork started in an uncancelable region, like a finalizer, can't be cancelled either.
            // It isn't tracked by the parent either, so a cancelled parent doesn't wait for it: the
            // programs that wait for their forks, like timeout or par*, are not affected
            var parent = regions.isEmpty() ? token : CancelToken.NONE;
            current = Program.success(new Fiber<>(startFork(state, parent, forked.current(), forked.executor())));
          }
          case FoldMap(var source, var onFailure, var onSuccess) -> {
            stack.push(Frame.fold(
                (Function<Object, Program<S, ?, ?>>) onFailure,
                (Function<Object, Program<S, ?, ?>>) onSuccess));
            current = source;
          }
          case Raise(var throwable) -> {
            return sneakyThrow(throwable.get());
          }
          case Catch(var source, var recover) -> {
            stack.push(Frame.catch_((Function<Throwable, Program<S, ?, ?>>) recover));
            current = source;
          }
          case Suspend(var supplier) -> {
            current = supplier.get();
          }
          case Memoized memoized -> {
            current = memoized.run();
          }
        }
      } catch (Throwable e) {
        // a fatal error leaves right away: no catchAll and no finalizers, see Fatal
        rethrowIfFatal(e);
        // unwind to the nearest catchAll, discarding the continuations inside its scope and
        // running the finalizers found on the way
        var resumed = false;
        while (!resumed) {
          switch (stack.poll()) {
            case null -> {
              return sneakyThrow(e);
            }
            case CatchFrame(var recover) -> {
              // run the handler inside the loop so an exception it throws reaches an outer catchAll.
              // A cancelled program never runs it: shouldStop stops it before the next step, and the
              // unwinding goes on, so a cancellation can't be caught
              current = Program.suspend(() -> recover.apply(e));
              resumed = true;
            }
            case FinalizerFrame(var finalizer) -> {
              // cancelled if this program's cancellation is what is unwinding, also when the
              // finalizer was installed inside an uncancelable region around a polled program,
              // like the release of bracketCase. Checking the region instead would report a
              // cancelled polled program as Errored
              var outcome = token.isCancelled() && e instanceof CancellationException
                  ? Outcome.canceled()
                  : Outcome.errored(e);
              // run the finalizer, then keep unwinding with the same exception
              runFinalizer(stack, regions, e);
              current = Program.suspend(() -> finalizer.apply(outcome));
              resumed = true;
            }
            case UnmaskFrame(var region) -> regions.remove(region);
            case RemaskFrame(var region) -> regions.push(region);
            case FoldFrame<S> _ -> {
              // a continuation inside the catchAll scope, discarded
            }
          }
        }
      }
    }
  }

  /**
   * Whether a cancelled program has to stop before running the current step.
   *
   * <p>A cancelled program stops at its next step, except in two cases:
   * <ul>
   *   <li>Inside an uncancelable region ({@code masked > 0}), like a finalizer: the cleanup has to
   *   run to completion.</li>
   *   <li>When the step installs a finalizer ({@link Finalize}). {@code bracket} acquires the resource
   *   in an uncancelable region, and the step right after it installs the release. Stopping there
   *   would leave the resource acquired with no release to run, so a finalizer is always installed
   *   and the program stops at the step after it, running the finalizer while it unwinds.</li>
   * </ul>
   */
  private static boolean shouldStop(CancelToken token, Deque<Region> regions, Program<?, ?, ?> current) {
    if (!regions.isEmpty() || current instanceof Finalize) {
      return false;
    }
    return token.isCancelled();
  }

  /**
   * Waits for an async computation, like a sleep or a future, that can be cancelled.
   *
   * <p>A waiting program doesn't reach the cancellation check of the next step, so the wait has to
   * be woken up: until the wait is over, a cancellation of the program fails the future it is
   * waiting on, and {@code join()} throws right away.
   */
  private static <S> Result<?, ?> awaitCancelable(
      @Nullable S state, BiConsumer<S, CompletableFuture<?>> async, CancelToken token) {
    var future = new CompletableFuture<Result<?, ?>>();
    var wakeUpOnCancel = token.onCancel(() -> future.completeExceptionally(new CancelToken.Cancelled()));
    try {
      async.accept(state, future);
      return joinOrThrows(future);
    } finally {
      // the wait is over, so stop listening, otherwise the token keeps a callback for each wait
      wakeUpOnCancel.remove();
    }
  }

  /**
   * Waits for an async computation in an uncancelable region, like a finalizer: the wait isn't
   * woken up by a cancellation, and the programs the computation evaluates, like the forks of
   * {@code par*}, can't be cancelled either.
   */
  private static <S> Result<?, ?> awaitUncancelable(
      @Nullable S state, BiConsumer<S, CompletableFuture<?>> async) {
    var future = new CompletableFuture<Result<?, ?>>();
    ScopedValue.where(CancelToken.CURRENT, CancelToken.NONE).run(() -> async.accept(state, future));
    return joinOrThrows(future);
  }

  /**
   * Waits for the future and throws the original exception if it fails.
   *
   * <p>The futures of the forked programs are combined with methods like {@code thenApply}, which
   * wrap the exception of a failed future in a {@link CompletionException}. Without unwrapping it,
   * a {@code catchAll} would receive the wrapper instead of the exception the program raised.
   */
  private static Result<?, ?> joinOrThrows(CompletableFuture<Result<?, ?>> future) {
    try {
      return future.join();
    } catch (CompletionException e) {
      return sneakyThrow(e.getCause() != null ? e.getCause() : e);
    }
  }

  /**
   * Prepares the stack to run a finalizer while unwinding an exception: the finalizer can't be
   * cancelled, and once it's done the same exception goes on. If the finalizer throws too, its
   * exception is added as suppressed to the one being unwound, like try-with-resources does, so
   * neither is lost. It compares the instances because an exception can't suppress itself.
   */
  @SuppressWarnings("ReferenceEquality")
  private static <S> void runFinalizer(Deque<Frame<S>> stack, Deque<Region> regions, Throwable e) {
    stack.push(Frame.fold(
        _ -> Program.raise(() -> e),
        _ -> Program.raise(() -> e)));
    enterUncancelable(stack, regions);
    // inside the region, so the handler runs even if the program is being cancelled
    stack.push(Frame.catch_(error -> {
      if (error != e) {
        e.addSuppressed(error);
      }
      return Program.raise(() -> e);
    }));
  }

  // the region ends when the UnmaskFrame is popped
  private static <S> Region enterUncancelable(Deque<Frame<S>> stack, Deque<Region> regions) {
    var region = new Region();
    regions.push(region);
    stack.push(Frame.unmask(region));
    return region;
  }

  /**
   * Starts a forked program on the executor and returns the future of its result.
   *
   * <p>The forked program gets its own token, a child of {@code parent}: cancelling the parent
   * cancels it, but cancelling it doesn't affect the parent. The returned future cancels that token
   * when it is cancelled, and it only completes once the program has stopped, see
   * {@link #runForked}.
   *
   * <p>The future is tracked by the parent from the start, so a cancelled parent can wait for it
   * to stop. If the executor refuses to run the program, the future fails with that error,
   * otherwise the parent would wait for it forever. The error is not thrown, so the combinators,
   * like {@code parZip}, see a failed program and cancel the ones they already started. A fatal
   * error is thrown too, see {@link Fatal}.
   */
  private static <S, E, T> CancelableFuture<Result<E, T>> startFork(
      @Nullable S state, CancelToken parent, Program<S, E, T> program, Executor executor) {
    var token = parent.newChild();
    var future = new CancelableFuture<Result<E, T>>(token);
    parent.trackFork(future);
    try {
      executor.execute(() -> runForked(state, token, program, future));
    } catch (Throwable e) {
      // claimed like runForked does: if a cancellation claimed it first, the future is already
      // completed as cancelled, and failing it again would overwrite how it ended
      if (future.start()) {
        token.detachFromParent();
        future.fail(e, false);
      }
      rethrowIfFatal(e);
    }
    return future;
  }

  /**
   * Runs a forked program and completes its future.
   *
   * <p>The token is bound to {@link CancelToken#CURRENT} while the program runs, so the programs
   * evaluated in a nested {@code eval}, like the forks of {@code par*}, are its children too.
   *
   * <p>The future is completed last, so whoever waits for it continues only when everything
   * related to the program is done: if it was cancelled, the forks it started, cancelled through
   * the token, have stopped too; and its token doesn't listen to the parent anymore.
   */
  private static <S, E, T> void runForked(
      @Nullable S state, CancelToken token, Program<S, E, T> program, CancelableFuture<Result<E, T>> future) {
    if (!future.start()) {
      // cancelled before it started: its future is already completed
      return;
    }
    Result<E, T> result = null;
    Throwable error = null;
    try {
      result = ScopedValue.where(CancelToken.CURRENT, token).call(() -> run(program, state, token));
    } catch (Throwable e) {
      error = e;
    }
    // read when the program stops: a cancellation that arrives later doesn't change how it ended
    var cancelled = token.isCancelled();
    if (cancelled) {
      // only when cancelled: a program that finishes normally can leave forks running, and they
      // are not awaited even if a cancellation arrives right after the program has finished
      token.awaitForks();
    }
    // otherwise the parent keeps a callback for each program it has ever forked
    token.detachFromParent();
    if (error != null) {
      future.fail(error, cancelled);
      // after completing the future, so the parent fails instead of waiting forever
      rethrowIfFatal(error);
    } else {
      future.complete(result);
    }
  }

  private static Poll createPoll(Region region) {
    return new Poll() {
      @Override
      public <S1, E1, T1> Program<S1, E1, T1> apply(Program<S1, E1, T1> program) {
        return new Polled<>(program, region);
      }
    };
  }
}
