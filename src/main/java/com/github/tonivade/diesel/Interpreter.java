/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import com.github.tonivade.diesel.Frame.CatchFrame;
import com.github.tonivade.diesel.Frame.FinalizerFrame;
import com.github.tonivade.diesel.Frame.FoldFrame;
import com.github.tonivade.diesel.Frame.UnmaskFrame;
import com.github.tonivade.diesel.Program.Async;
import com.github.tonivade.diesel.Program.Catch;
import com.github.tonivade.diesel.Program.Effect;
import com.github.tonivade.diesel.Program.Ensuring;
import com.github.tonivade.diesel.Program.FoldMap;
import com.github.tonivade.diesel.Program.Forked;
import com.github.tonivade.diesel.Program.Memoized;
import com.github.tonivade.diesel.Program.Pure;
import com.github.tonivade.diesel.Program.Raise;
import com.github.tonivade.diesel.Program.Suspend;
import com.github.tonivade.diesel.Program.Uncancelable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

/**
 * Stack-safe interpreter that evaluates a {@link Program}.
 */
final class Interpreter {

  private Interpreter() {}

  /**
   * Evaluates the program with the cancellation token of the current fork, if any.
   */
  static <S, E, T> Result<E, T> eval(Program<S, E, T> program, @Nullable S state) {
    return run(program, state, CancelToken.current());
  }

  // XXX: https://www.baeldung.com/java-sneaky-throws
  @SuppressWarnings("unchecked")
  static <X extends Throwable, R> R sneakyThrow(Throwable t) throws X {
    throw (X) t;
  }

  @SuppressWarnings("unchecked")
  private static <S, E, T> Result<E, T> run(Program<S, E, T> program, @Nullable S state, CancelToken token) {
    Program<S, ?, ?> current = program;
    Deque<Frame<S>> stack = new ArrayDeque<>();
    // depth of nested uncancelable regions, a cancellation only takes effect outside of them
    int masked = 0;

    while (true) {
      try {
        // installing a finalizer never observes a cancellation, see bracket
        if (masked == 0 && !(current instanceof Ensuring) && token.isCancelled()) {
          throw new CancelToken.Cancelled();
        }
        if (current instanceof Pure(var result)) {
          var resumed = false;
          while (!resumed) {
            var frame = stack.poll();
            if (frame == null) {
              return (Result<E, T>) result;
            }
            if (frame instanceof FoldFrame(var onFailure, var onSuccess)) {
              current = result.fold(onFailure, onSuccess);
              resumed = true;
            } else if (frame instanceof FinalizerFrame(var finalizer)) {
              // run the finalizer and then continue with the result, unless the finalizer fails
              stack.push(new FoldFrame<>(Program::failure, _ -> Program.from(result)));
              masked = enterUncancelable(stack, masked);
              current = finalizer;
              resumed = true;
            } else if (frame instanceof UnmaskFrame) {
              masked--;
            }
            // leaving a catchAll scope normally, its handler no longer applies
          }
        } else if (current instanceof Effect(var mapper)) {
          current = mapper.apply(state);
        } else if (current instanceof Async(var callback)) {
          var future = new CompletableFuture<Result<?, ?>>();
          // wake up the wait if the program is cancelled
          var registration = masked == 0
              ? token.onCancel(() -> future.completeExceptionally(new CancelToken.Cancelled()))
              : null;
          try {
            ((BiConsumer<S, CompletableFuture<?>>) callback).accept(state, future);
            current = Program.from(future.join());
          } finally {
            if (registration != null) {
              registration.remove();
            }
          }
        } else if (current instanceof Forked forked) {
          current = Program.success(fork(state, token.child(), forked.current(), forked.executor()));
        } else if (current instanceof Ensuring(var source, var finalizer)) {
          stack.push(new FinalizerFrame<>(finalizer));
          current = source;
        } else if (current instanceof Uncancelable(var source)) {
          masked = enterUncancelable(stack, masked);
          current = source;
        } else if (current instanceof FoldMap(var source, var onFailure, var onSuccess)) {
          stack.push(new FoldFrame<>(
              (Function<Object, Program<S, ?, ?>>) onFailure,
              (Function<Object, Program<S, ?, ?>>) onSuccess));
          current = source;
        } else if (current instanceof Raise(var throwable)) {
          return sneakyThrow(throwable.get());
        } else if (current instanceof Catch(var source, var recover)) {
          stack.push(new CatchFrame<>((Function<Throwable, Program<S, ?, ?>>) recover));
          current = source;
        } else if (current instanceof Suspend(var supplier)) {
          current = supplier.get();
        } else if (current instanceof Memoized memoized) {
          var result = memoized.get();
          if (result != null) {
            current = Program.from(result);
          } else {
            stack.push(new FoldFrame<>(
                error -> {
                  memoized.set(Result.failure(error));
                  return Program.failure(error);
                },
                value -> {
                  memoized.set(Result.success(value));
                  return Program.success(value);
                }));
            current = memoized.current();
          }
        } else {
          // every subtype is handled above, so only a null program can reach here
          throw new NullPointerException("program cannot be null");
        }
      } catch (Throwable e) {
        // unwind to the nearest catchAll, discarding the continuations inside its scope and
        // running the finalizers found on the way. A cancellation can't be caught, so it unwinds
        // the whole stack
        var cancelled = e instanceof CancelToken.Cancelled;
        var resumed = false;
        while (!resumed) {
          var frame = stack.poll();
          if (frame == null) {
            return sneakyThrow(e);
          }
          if (frame instanceof CatchFrame(var recover) && !cancelled) {
            // run the handler inside the loop so an exception it throws reaches an outer catchAll
            current = Program.suspend(() -> recover.apply(e));
            resumed = true;
          } else if (frame instanceof FinalizerFrame(var finalizer)) {
            // keep unwinding with the same exception once the finalizer is done
            stack.push(new FoldFrame<>(_ -> Program.raise(() -> e), _ -> Program.raise(() -> e)));
            masked = enterUncancelable(stack, masked);
            current = finalizer;
            resumed = true;
          } else if (frame instanceof UnmaskFrame) {
            masked--;
          }
        }
      }
    }
  }

  // the region ends when the UnmaskFrame is popped
  private static <S> int enterUncancelable(Deque<Frame<S>> stack, int masked) {
    stack.push(new UnmaskFrame<>());
    return masked + 1;
  }

  private static <S, E, T> CompletableFuture<Result<E, T>> fork(
      @Nullable S state, CancelToken token, Program<S, E, T> program, Executor executor) {
    var future = new CancelableFuture<Result<E, T>>(token);
    executor.execute(() -> {
      try {
        future.complete(ScopedValue.where(CancelToken.CURRENT, token).call(() -> run(program, state, token)));
      } catch (Throwable e) {
        future.completeExceptionally(e);
      } finally {
        token.detach();
      }
    });
    return future;
  }
}
