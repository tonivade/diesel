/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Program.accessProgram;
import static com.github.tonivade.diesel.Program.raise;
import static com.github.tonivade.diesel.Program.success;
import static com.github.tonivade.diesel.Program.supply;
import static com.github.tonivade.diesel.Program.task;
import static java.util.Objects.requireNonNull;

import java.io.Serial;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

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
  // supervise holds the read lock from the check of closed until the fork is in the scope, so the
  // release, that sets closed under the write lock, sees every program that was admitted
  private final ReadWriteLock admission = new ReentrantReadWriteLock();
  // written under the write lock, volatile only for toString
  private volatile boolean closed;
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
   * <p>With {@code await}, the release waits forever if a supervised program never finishes. The
   * release always runs in full, even when the program that uses the supervisor is cancelled. So a
   * supervisor with {@code await} created inside a program supervised by another one keeps its
   * policy: cancelling the outer supervisor doesn't cancel its programs, and the outer release
   * waits until they finish.
   *
   * <p>While the release waits, the supervisor is already closed: a supervised program that tries
   * to start another one in the same supervisor, like a loop that keeps spawning work, fails with
   * a {@link SupervisorClosedException}.
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
   * <p>It fails with a {@link SupervisorClosedException} if the supervisor has been released. A
   * program supervised at the same time as the release is either rejected the same way, or
   * admitted, and then the release waits for it or cancels it like the others.
   *
   * @param program the program to run in the background
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result of the program
   * @return a program that starts the supervised program and yields its fiber
   * @throws SupervisorClosedException when evaluated, if the supervisor has been released
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
   * <p>With an executor that runs the program in the calling thread, the release of the supervisor
   * waits until the program finishes, even if it would cancel it.
   *
   * @param program the program to run in the background
   * @param executor the executor that runs the program
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result of the program
   * @return a program that starts the supervised program and yields its fiber
   * @throws SupervisorClosedException when evaluated, if the supervisor has been released
   */
  public <S, E, T> Program<S, E, Fiber<E, T>> supervise(Program<S, E, T> program, Executor executor) {
    requireNonNull(program);
    requireNonNull(executor);
    return accessProgram(state -> {
      var lock = admission.readLock();
      lock.lock();
      try {
        if (closed) {
          return raise(SupervisorClosedException::new);
        }
        return success(new Fiber<>(Interpreter.startFork(state, scope, program, executor)));
      } finally {
        lock.unlock();
      }
    });
  }

  private <S> Program<S, Void, Unit> close(boolean await) {
    return task(() -> {
      var lock = admission.writeLock();
      lock.lock();
      try {
        closed = true;
      } finally {
        lock.unlock();
      }
      // from here on, every admitted program is in the scope
      if (await) {
        scope.awaitForks();
      } else {
        scope.cancel();
        scope.awaitForks();
      }
    });
  }

  @Override
  public String toString() {
    return "Supervisor(" + (closed ? "closed" : "open") + ")";
  }

  /**
   * Raised by {@link Supervisor#supervise(Program)} when the supervisor has been released, so it
   * can't start more programs.
   */
  public static final class SupervisorClosedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception with the message "supervisor is closed".
     */
    public SupervisorClosedException() {
      super("supervisor is closed");
    }
  }
}
