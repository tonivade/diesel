/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Program.accessProgram;
import static com.github.tonivade.diesel.Program.raise;
import static com.github.tonivade.diesel.Program.success;
import static com.github.tonivade.diesel.Program.supply;
import static com.github.tonivade.diesel.Program.suspend;
import static com.github.tonivade.diesel.Program.task;
import static java.util.Objects.requireNonNull;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A scope for programs that run in the background, whose lifetime is tied to the supervisor
 * instead of to the program that starts them.
 *
 * <p>A supervisor is a {@link Resource}: when it's released, the programs it supervises that are
 * still running are cancelled, and the release waits until they have stopped and run their
 * finalizers. With {@code await}, the release waits for them to finish on their own instead.
 *
 * <p>Unlike {@link Program#fork()}, a supervised program isn't cancelled with the program that
 * started it, so it can outlive it, but never the supervisor.
 *
 * <p>The types of the state and the error aren't parameters of the class but of each operation.
 * In a chained call like {@code supervisor.supervise(p).map(...)} they are inferred as
 * {@code Object}: use a {@link Combine} helper, like {@code pipe(supervisor.supervise(p), ...)}.
 */
public final class Supervisor {

  // the parent of every supervised program, cancelled when the supervisor is released
  private final CancelToken scope = CancelToken.newRoot();
  private final AtomicBoolean closed = new AtomicBoolean();
  private final Executor executor;

  private Supervisor(Executor executor) {
    this.executor = requireNonNull(executor);
  }

  /**
   * Creates a supervisor that cancels the programs still running when it's released.
   *
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @return a resource with a new supervisor
   */
  public static <S, E> Resource<S, E, Supervisor> make() {
    return make(false);
  }

  /**
   * Creates a supervisor that, when released, waits for the programs still running if
   * {@code await} is true, or cancels them otherwise.
   *
   * @param await whether the release waits for the supervised programs instead of cancelling them
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @return a resource with a new supervisor
   */
  public static <S, E> Resource<S, E, Supervisor> make(boolean await) {
    return make(await, Concurrent.DEFAULT_EXECUTOR);
  }

  /**
   * Like {@link #make(boolean)}, but the supervised programs run in the given executor.
   *
   * @param await whether the release waits for the supervised programs instead of cancelling them
   * @param executor the executor that runs the supervised programs
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @return a resource with a new supervisor
   */
  public static <S, E> Resource<S, E, Supervisor> make(boolean await, Executor executor) {
    requireNonNull(executor);
    return Resource.make(supply(() -> new Supervisor(executor)), supervisor -> supervisor.close(await));
  }

  /**
   * Starts a program in the background, supervised by this supervisor.
   *
   * <p>The returned {@link Fiber} can be joined, cancelled or polled. Cancelling the program that
   * calls this method doesn't cancel the supervised program: only the fiber or the release of the
   * supervisor do. Its errors are ignored unless the fiber is joined or polled.
   *
   * <p>It fails with an {@link IllegalStateException} if the supervisor has been released. A
   * program supervised while the supervisor is being released is cancelled before it starts.
   *
   * @param program the program to run in the background
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result of the program
   * @return a program that starts the supervised program and yields its fiber
   */
  public <S, E, T> Program<S, E, Fiber<E, T>> supervise(Program<S, E, T> program) {
    return supervise(program, executor);
  }

  /**
   * Like {@link #supervise(Program)}, but the program runs in the given executor instead of the
   * one of the supervisor.
   *
   * <p>If the executor refuses to run the program, like a full or shut down executor, it doesn't
   * fail: the returned fiber fails with that error.
   *
   * @param program the program to run in the background
   * @param executor the executor that runs the program
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result of the program
   * @return a program that starts the supervised program and yields its fiber
   */
  public <S, E, T> Program<S, E, Fiber<E, T>> supervise(Program<S, E, T> program, Executor executor) {
    requireNonNull(program);
    requireNonNull(executor);
    return accessProgram(state -> suspend(() -> {
      if (closed.get()) {
        return raise(() -> new IllegalStateException("supervisor already released"));
      }
      return success(new Fiber<>(Interpreter.startFork(state, scope, program, executor)));
    }));
  }

  private <S> Program<S, Void, Unit> close(boolean await) {
    return task(() -> {
      closed.set(true);
      if (await) {
        scope.awaitForks();
      }
      // also with await: a program supervised while closing is cancelled instead of left running
      scope.cancel();
      scope.awaitForks();
    });
  }

  @Override
  public String toString() {
    return "Supervisor(" + (closed.get() ? "released" : "open") + ")";
  }
}
