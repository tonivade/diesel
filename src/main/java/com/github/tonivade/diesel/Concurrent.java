/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Combine.zip;
import static com.github.tonivade.diesel.Program.evalAll;
import static com.github.tonivade.diesel.Program.raise;
import static com.github.tonivade.diesel.Program.sleep;
import static com.github.tonivade.diesel.Program.success;
import static com.github.tonivade.diesel.Program.unit;
import static java.util.function.Function.identity;

import com.github.tonivade.diesel.function.Finisher2;
import com.github.tonivade.diesel.function.Finisher3;
import com.github.tonivade.diesel.function.Finisher4;
import com.github.tonivade.diesel.function.Finisher5;
import com.github.tonivade.diesel.function.Finisher6;
import com.github.tonivade.diesel.function.Finisher7;
import com.github.tonivade.diesel.function.Finisher8;
import com.github.tonivade.diesel.function.Finisher9;

import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/**
 * Concurrent combinators to execute {@link Program}s in parallel.
 */
public final class Concurrent {

  static final Executor DEFAULT_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

  private Concurrent() {}

  /**
   * Forks the program to be executed asynchronously in a virtual thread.
   *
   * @param program the original program
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   * @return a new program representing the forked computation
   * @see Program#fork()
   */
  public static <S, E, T> Program<S, E, Fiber<E, T>> fork(Program<S, E, T> program) {
    return program.fork();
  }

  /**
   * Forks the program to be executed asynchronously in a virtual thread.
   *
   * @param program the original program
   * @param executor the executor used to execute the program asynchronously
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   * @return a new program representing the forked computation
   * @see Program#fork()
   */
  public static <S, E, T> Program<S, E, Fiber<E, T>> fork(Program<S, E, T> program, Executor executor) {
    return program.fork(executor);
  }

  /**
   * Creates a new program that represents an either of two programs executed in parallel using virtual threads.
   *
   * <p>First without error/exception wins. The other program is cancelled, and the resulting
   * program completes once it has stopped and run its finalizers.
   *
   * @param p1 the first program
   * @param p2 the second program
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result of the first program
   * @param <U> the type of the result of the second program
   * @return a new program representing an either of the two programs
   */
  public static <S, E, T, U> Program<S, E, Either<T, U>> either(Program<S, E, T> p1, Program<S, E, U> p2) {
    return either(p1, p2, DEFAULT_EXECUTOR);
  }

  /**
   * Creates a new program that represents an either of two programs executed in parallel using the provided executor.
   *
   * <p>First without error/exception wins. The other program is cancelled, and the resulting
   * program completes once it has stopped and run its finalizers.
   *
   * @param p1 the first program
   * @param p2 the second program
   * @param executor the executor used to execute the programs in parallel
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result of the first program
   * @param <U> the type of the result of the second program
   * @return a new program representing a race of the two programs
   */
  public static <S, E, T, U> Program<S, E, Either<T, U>> either(
      Program<S, E, T> p1, Program<S, E, U> p2, Executor executor) {
    return zip(p1.fork(executor), p2.fork(executor), Fiber::<S, E, T, U>either)
        .flatMap(identity());
  }

  /**
   * Creates a new program that represents an race of two programs executed in parallel using virtual threads.
   *
   * <p>First to finish wins. The other program is cancelled, and the resulting program completes
   * once it has stopped and run its finalizers.
   *
   * @param p1 the first program
   * @param p2 the second program
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result of the first program
   * @param <U> the type of the result of the second program
   * @return a new program representing a race of the two programs
   */
  public static <S, E, T, U> Program<S, E, Either<T, U>> race(Program<S, E, T> p1, Program<S, E, U> p2) {
    return race(p1, p2, DEFAULT_EXECUTOR);
  }

  /**
   * Creates a new program that represents an race of two programs executed in parallel using the provided executor.
   *
   * <p>First to finish wins. The other program is cancelled, and the resulting program completes
   * once it has stopped and run its finalizers.
   *
   * @param p1 the first program
   * @param p2 the second program
   * @param executor the executor used to execute the programs in parallel
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result of the first program
   * @param <U> the type of the result of the second program
   * @return a new program representing an either of the two programs
   */
  public static <S, E, T, U> Program<S, E, Either<T, U>> race(
      Program<S, E, T> p1, Program<S, E, U> p2, Executor executor) {
    return zip(p1.fork(executor), p2.fork(executor), Fiber::<S, E, T, U>race)
        .flatMap(identity());
  }

  /**
   * Executes all the given programs in parallel using virtual threads and ignores all their results.
   *
   * <p>
   * The execution is fail-fast: as soon as any program fails, the remaining programs are
   * cancelled and the resulting program fails with that error. If several programs fail, the
   * error returned is the first one to happen in time, not the first by position. Cancelled
   * programs stop at their next step and run their finalizers, and the resulting program
   * completes once they have stopped. Cancellation is cooperative, so code that is already
   * running, like a blocking call inside {@code supply} or {@code task}, finishes first.
   *
   * @param programs the programs to be executed
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   * @return a new program representing the parallel computation
   */
  @SafeVarargs
  public static <S, E, T> Program<S, E, Unit> parAll(Program<S, E, T>... programs) {
    return parAll(DEFAULT_EXECUTOR, programs);
  }

  /**
   * Executes all the given programs in parallel using the provided executor.
   *
   * <p>
   * The execution is fail-fast: as soon as any program fails, the remaining programs are
   * cancelled and the resulting program fails with that error. If several programs fail, the
   * error returned is the first one to happen in time, not the first by position. Cancelled
   * programs stop at their next step and run their finalizers, and the resulting program
   * completes once they have stopped. Cancellation is cooperative, so code that is already
   * running, like a blocking call inside {@code supply} or {@code task}, finishes first.
   *
   * @param executor the executor used to execute the programs in parallel
   * @param programs the programs to be executed
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @return a new program representing the parallel computation
   */
  @SafeVarargs
  public static <S, E> Program<S, E, Unit> parAll(Executor executor, Program<S, E, ?>... programs) {
    return parAll(executor, List.of(programs));
  }

  /**
   * Executes all the given programs in parallel using virtual threads and ignores all their results.
   *
   * <p>
   * The execution is fail-fast: as soon as any program fails, the remaining programs are
   * cancelled and the resulting program fails with that error. If several programs fail, the
   * error returned is the first one to happen in time, not the first by position. Cancelled
   * programs stop at their next step and run their finalizers, and the resulting program
   * completes once they have stopped. Cancellation is cooperative, so code that is already
   * running, like a blocking call inside {@code supply} or {@code task}, finishes first.
   *
   * @param programs the programs to be executed
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @return a new program representing the parallel computation
   */
  public static <S, E> Program<S, E, Unit> parAll(Collection<? extends Program<S, E, ?>> programs) {
    return parAll(DEFAULT_EXECUTOR, programs);
  }

  /**
   * Executes all the given programs in parallel using the provided executor.
   *
   * <p>
   * The execution is fail-fast: as soon as any program fails, the remaining programs are
   * cancelled and the resulting program fails with that error. If several programs fail, the
   * error returned is the first one to happen in time, not the first by position. Cancelled
   * programs stop at their next step and run their finalizers, and the resulting program
   * completes once they have stopped. Cancellation is cooperative, so code that is already
   * running, like a blocking call inside {@code supply} or {@code task}, finishes first.
   *
   * @param executor the executor used to execute the programs in parallel
   * @param programs the programs to be executed
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @return a new program representing the parallel computation
   */
  public static <S, E> Program<S, E, Unit> parAll(Executor executor, Collection<? extends Program<S, E, ?>> programs) {
    if (programs.isEmpty()) {
      return unit();
    }

    return forkAllAndThen(executor, programs, Fiber::all);
  }

  /**
   * Executes all the given programs in parallel using virtual threads and returns the
   * result of the first one that finishes successfully.
   *
   * <p>
   * Programs that fail, with an error or an exception, are ignored as long as there are other
   * programs still running. When a program succeeds the remaining programs are cancelled, and the
   * resulting program completes once they have stopped and run their finalizers. If all
   * the programs fail, the resulting program fails with the last error to happen in time.
   *
   * @param programs the programs to be executed
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   * @return a new program representing the first successful computation
   */
  @SafeVarargs
  public static <S, E, T> Program<S, E, T> parAny(Program<S, E, T>... programs) {
    return parAny(List.of(programs));
  }

  /**
   * Executes all the given programs in parallel using the provided executor and returns the
   * result of the first one that finishes successfully.
   *
   * <p>
   * Programs that fail, with an error or an exception, are ignored as long as there are other
   * programs still running. When a program succeeds the remaining programs are cancelled, and the
   * resulting program completes once they have stopped and run their finalizers. If all
   * the programs fail, the resulting program fails with the last error to happen in time.
   *
   * @param executor the executor used to execute the programs in parallel
   * @param programs the programs to be executed
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   * @return a new program representing the first successful computation
   */
  @SafeVarargs
  public static <S, E, T> Program<S, E, T> parAny(Executor executor, Program<S, E, T>... programs) {
    return parAny(executor, List.of(programs));
  }

  /**
   * Executes all the given programs in parallel using virtual threads and returns the
   * result of the first one that finishes successfully.
   *
   * <p>
   * Programs that fail, with an error or an exception, are ignored as long as there are other
   * programs still running. When a program succeeds the remaining programs are cancelled, and the
   * resulting program completes once they have stopped and run their finalizers. If all
   * the programs fail, the resulting program fails with the last error to happen in time.
   *
   * @param programs the programs to be executed
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   * @return a new program representing the first successful computation
   * @throws NoSuchElementException when evaluated if no programs are given
   */
  public static <S, E, T> Program<S, E, T> parAny(Collection<? extends Program<S, E, T>> programs) {
    return parAny(DEFAULT_EXECUTOR, programs);
  }

  /**
   * Executes all the given programs in parallel using the provided executor and returns the
   * result of the first one that finishes successfully.
   *
   * <p>
   * Programs that fail, with an error or an exception, are ignored as long as there are other
   * programs still running. When a program succeeds the remaining programs are cancelled, and the
   * resulting program completes once they have stopped and run their finalizers. If all
   * the programs fail, the resulting program fails with the last error to happen in time.
   *
   * @param executor the executor used to execute the programs in parallel
   * @param programs the programs to be executed
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   * @return a new program representing the first successful computation
   * @throws NoSuchElementException when evaluated if no programs are given
   */
  public static <S, E, T> Program<S, E, T> parAny(Executor executor, Collection<? extends Program<S, E, T>> programs) {
    if (programs.isEmpty()) {
      return raise(NoSuchElementException::new);
    }

    return forkAllAndThen(executor, programs, Fiber::any);
  }

  /**
   * Executes a collection of programs in parallel using virtual threads and sequences
   * their results into a single program containing a collection of success values.
   *
   * <p>
   * The execution is fail-fast: as soon as any program fails, the remaining programs are
   * cancelled and the resulting program fails with that error. If several programs fail, the
   * error returned is the first one to happen in time, not the first by position. Cancelled
   * programs stop at their next step and run their finalizers, and the resulting program
   * completes once they have stopped. Cancellation is cooperative, so code that is already
   * running, like a blocking call inside {@code supply} or {@code task}, finishes first.
   *
   * @param programs the programs to be executed
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   * @return a new program representing the parallel computation with sequenced results
   */
  @SafeVarargs
  public static <S, E, T> Program<S, E, Collection<T>> parSequence(Program<S, E, T>... programs) {
    return parSequence(DEFAULT_EXECUTOR, programs);
  }

  /**
   * Executes a collection of programs in parallel using the provided executor
   * and sequences their results into a single program containing a collection of success values.
   *
   * <p>
   * The execution is fail-fast: as soon as any program fails, the remaining programs are
   * cancelled and the resulting program fails with that error. If several programs fail, the
   * error returned is the first one to happen in time, not the first by position. Cancelled
   * programs stop at their next step and run their finalizers, and the resulting program
   * completes once they have stopped. Cancellation is cooperative, so code that is already
   * running, like a blocking call inside {@code supply} or {@code task}, finishes first.
   *
   * @param executor the executor used to execute the programs in parallel
   * @param programs the programs to be executed
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   * @return a new program representing the parallel computation with sequenced results
   */
  @SafeVarargs
  public static <S, E, T> Program<S, E, Collection<T>> parSequence(Executor executor, Program<S, E, T>... programs) {
    return parSequence(executor, List.of(programs));
  }

  /**
   * Executes a collection of programs in parallel using virtual threads and sequences
   * their results into a single program containing a collection of success values.
   *
   * <p>
   * The execution is fail-fast: as soon as any program fails, the remaining programs are
   * cancelled and the resulting program fails with that error. If several programs fail, the
   * error returned is the first one to happen in time, not the first by position. Cancelled
   * programs stop at their next step and run their finalizers, and the resulting program
   * completes once they have stopped. Cancellation is cooperative, so code that is already
   * running, like a blocking call inside {@code supply} or {@code task}, finishes first.
   *
   * @param programs the programs to be executed
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   * @return a new program representing the parallel computation with sequenced results
   */
  public static <S, E, T> Program<S, E, Collection<T>> parSequence(Collection<? extends Program<S, E, T>> programs) {
    return parSequence(DEFAULT_EXECUTOR, programs);
  }

  /**
   * Executes a collection of programs in parallel using the provided executor
   * and sequences their results into a single program containing a collection of success values.
   *
   * <p>
   * The execution is fail-fast: as soon as any program fails, the remaining programs are
   * cancelled and the resulting program fails with that error. If several programs fail, the
   * error returned is the first one to happen in time, not the first by position. Cancelled
   * programs stop at their next step and run their finalizers, and the resulting program
   * completes once they have stopped. Cancellation is cooperative, so code that is already
   * running, like a blocking call inside {@code supply} or {@code task}, finishes first.
   *
   * @param executor the executor used to execute the programs in parallel
   * @param programs the programs to be executed
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   * @return a new program representing the parallel computation with sequenced results
   */
  public static <S, E, T> Program<S, E, Collection<T>> parSequence(
      Executor executor, Collection<? extends Program<S, E, T>> programs) {
    if (programs.isEmpty()) {
      return success(List.of());
    }

    return forkAllAndThen(executor, programs, Fiber::sequence);
  }

  /**
   * Executes the given programs in parallel using the provided executor and combines their results
   * using the finisher function.
   *
   * <p>
   * The execution is fail-fast: as soon as any program fails, the remaining programs are
   * cancelled and the resulting program fails with that error. If several programs fail, the
   * error returned is the first one to happen in time, not the first by position. Cancelled
   * programs stop at their next step and run their finalizers, and the resulting program
   * completes once they have stopped. Cancellation is cooperative, so code that is already
   * running, like a blocking call inside {@code supply} or {@code task}, finishes first.
   *
   * @param p0 a program to be executed in parallel
   * @param p1 a program to be executed in parallel
   * @param finisher the function used to combine the results
   * @param executor the executor used to execute the programs in parallel
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T0> the result type of {@code p0}
   * @param <T1> the result type of {@code p1}
   * @param <R> the type of the combined result
   * @return a new program representing the parallel computation
   */
  public static <S, E, T0, T1, R> Program<S, E, R> parZip(
     Program<S, E, T0> p0,
     Program<S, E, T1> p1,
     Finisher2<T0, T1, R> finisher,
     Executor executor) {
       return zip(
         p0.fork(executor),
         p1.fork(executor),
         (f0, f1) ->
          Fiber.<S, E, R>joinAll(
            List.of(f0, f1),
            () -> Result.zip(f0.resultNow(), f1.resultNow(), finisher)))
        .flatMap(identity());
  }

  /**
   * Executes the given programs in parallel using the provided executor and combines their results
   * using the finisher function.
   *
   * <p>
   * The execution is fail-fast: as soon as any program fails, the remaining programs are
   * cancelled and the resulting program fails with that error. If several programs fail, the
   * error returned is the first one to happen in time, not the first by position. Cancelled
   * programs stop at their next step and run their finalizers, and the resulting program
   * completes once they have stopped. Cancellation is cooperative, so code that is already
   * running, like a blocking call inside {@code supply} or {@code task}, finishes first.
   *
   * @param p0 a program to be executed in parallel
   * @param p1 a program to be executed in parallel
   * @param p2 a program to be executed in parallel
   * @param finisher the function used to combine the results
   * @param executor the executor used to execute the programs in parallel
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T0> the result type of {@code p0}
   * @param <T1> the result type of {@code p1}
   * @param <T2> the result type of {@code p2}
   * @param <R> the type of the combined result
   * @return a new program representing the parallel computation
   */
  public static <S, E, T0, T1, T2, R> Program<S, E, R> parZip(
     Program<S, E, T0> p0,
     Program<S, E, T1> p1,
     Program<S, E, T2> p2,
     Finisher3<T0, T1, T2, R> finisher,
     Executor executor) {
       return zip(
         p0.fork(executor),
         p1.fork(executor),
         p2.fork(executor),
         (f0, f1, f2) ->
          Fiber.<S, E, R>joinAll(
            List.of(f0, f1, f2),
            () -> Result.zip(f0.resultNow(), f1.resultNow(), f2.resultNow(), finisher)))
        .flatMap(identity());
  }

  /**
   * Executes the given programs in parallel using the provided executor and combines their results
   * using the finisher function.
   *
   * <p>
   * The execution is fail-fast: as soon as any program fails, the remaining programs are
   * cancelled and the resulting program fails with that error. If several programs fail, the
   * error returned is the first one to happen in time, not the first by position. Cancelled
   * programs stop at their next step and run their finalizers, and the resulting program
   * completes once they have stopped. Cancellation is cooperative, so code that is already
   * running, like a blocking call inside {@code supply} or {@code task}, finishes first.
   *
   * @param p0 a program to be executed in parallel
   * @param p1 a program to be executed in parallel
   * @param p2 a program to be executed in parallel
   * @param p3 a program to be executed in parallel
   * @param finisher the function used to combine the results
   * @param executor the executor used to execute the programs in parallel
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T0> the result type of {@code p0}
   * @param <T1> the result type of {@code p1}
   * @param <T2> the result type of {@code p2}
   * @param <T3> the result type of {@code p3}
   * @param <R> the type of the combined result
   * @return a new program representing the parallel computation
   */
  public static <S, E, T0, T1, T2, T3, R> Program<S, E, R> parZip(
     Program<S, E, T0> p0,
     Program<S, E, T1> p1,
     Program<S, E, T2> p2,
     Program<S, E, T3> p3,
     Finisher4<T0, T1, T2, T3, R> finisher,
     Executor executor) {
       return zip(
         p0.fork(executor),
         p1.fork(executor),
         p2.fork(executor),
         p3.fork(executor),
         (f0, f1, f2, f3) ->
          Fiber.<S, E, R>joinAll(
            List.of(f0, f1, f2, f3),
            () -> Result.zip(f0.resultNow(), f1.resultNow(), f2.resultNow(), f3.resultNow(), finisher)))
        .flatMap(identity());
  }

  /**
   * Executes the given programs in parallel using the provided executor and combines their results
   * using the finisher function.
   *
   * <p>
   * The execution is fail-fast: as soon as any program fails, the remaining programs are
   * cancelled and the resulting program fails with that error. If several programs fail, the
   * error returned is the first one to happen in time, not the first by position. Cancelled
   * programs stop at their next step and run their finalizers, and the resulting program
   * completes once they have stopped. Cancellation is cooperative, so code that is already
   * running, like a blocking call inside {@code supply} or {@code task}, finishes first.
   *
   * @param p0 a program to be executed in parallel
   * @param p1 a program to be executed in parallel
   * @param p2 a program to be executed in parallel
   * @param p3 a program to be executed in parallel
   * @param p4 a program to be executed in parallel
   * @param finisher the function used to combine the results
   * @param executor the executor used to execute the programs in parallel
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T0> the result type of {@code p0}
   * @param <T1> the result type of {@code p1}
   * @param <T2> the result type of {@code p2}
   * @param <T3> the result type of {@code p3}
   * @param <T4> the result type of {@code p4}
   * @param <R> the type of the combined result
   * @return a new program representing the parallel computation
   */
  public static <S, E, T0, T1, T2, T3, T4, R> Program<S, E, R> parZip(
     Program<S, E, T0> p0,
     Program<S, E, T1> p1,
     Program<S, E, T2> p2,
     Program<S, E, T3> p3,
     Program<S, E, T4> p4,
     Finisher5<T0, T1, T2, T3, T4, R> finisher,
     Executor executor) {
       return zip(
         p0.fork(executor),
         p1.fork(executor),
         p2.fork(executor),
         p3.fork(executor),
         p4.fork(executor),
         (f0, f1, f2, f3, f4) ->
          Fiber.<S, E, R>joinAll(
            List.of(f0, f1, f2, f3, f4),
            () -> Result.zip(f0.resultNow(), f1.resultNow(), f2.resultNow(), f3.resultNow(), f4.resultNow(), finisher)))
        .flatMap(identity());
  }

  /**
   * Executes the given programs in parallel using the provided executor and combines their results
   * using the finisher function.
   *
   * <p>
   * The execution is fail-fast: as soon as any program fails, the remaining programs are
   * cancelled and the resulting program fails with that error. If several programs fail, the
   * error returned is the first one to happen in time, not the first by position. Cancelled
   * programs stop at their next step and run their finalizers, and the resulting program
   * completes once they have stopped. Cancellation is cooperative, so code that is already
   * running, like a blocking call inside {@code supply} or {@code task}, finishes first.
   *
   * @param p0 a program to be executed in parallel
   * @param p1 a program to be executed in parallel
   * @param p2 a program to be executed in parallel
   * @param p3 a program to be executed in parallel
   * @param p4 a program to be executed in parallel
   * @param p5 a program to be executed in parallel
   * @param finisher the function used to combine the results
   * @param executor the executor used to execute the programs in parallel
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T0> the result type of {@code p0}
   * @param <T1> the result type of {@code p1}
   * @param <T2> the result type of {@code p2}
   * @param <T3> the result type of {@code p3}
   * @param <T4> the result type of {@code p4}
   * @param <T5> the result type of {@code p5}
   * @param <R> the type of the combined result
   * @return a new program representing the parallel computation
   */
  public static <S, E, T0, T1, T2, T3, T4, T5, R> Program<S, E, R> parZip(
     Program<S, E, T0> p0,
     Program<S, E, T1> p1,
     Program<S, E, T2> p2,
     Program<S, E, T3> p3,
     Program<S, E, T4> p4,
     Program<S, E, T5> p5,
     Finisher6<T0, T1, T2, T3, T4, T5, R> finisher,
     Executor executor) {
       return zip(
         p0.fork(executor),
         p1.fork(executor),
         p2.fork(executor),
         p3.fork(executor),
         p4.fork(executor),
         p5.fork(executor),
         (f0, f1, f2, f3, f4, f5) ->
          Fiber.<S, E, R>joinAll(
            List.of(f0, f1, f2, f3, f4, f5),
            () -> Result.zip(f0.resultNow(), f1.resultNow(), f2.resultNow(), f3.resultNow(), f4.resultNow(), f5.resultNow(), finisher)))
        .flatMap(identity());
  }

  /**
   * Executes the given programs in parallel using the provided executor and combines their results
   * using the finisher function.
   *
   * <p>
   * The execution is fail-fast: as soon as any program fails, the remaining programs are
   * cancelled and the resulting program fails with that error. If several programs fail, the
   * error returned is the first one to happen in time, not the first by position. Cancelled
   * programs stop at their next step and run their finalizers, and the resulting program
   * completes once they have stopped. Cancellation is cooperative, so code that is already
   * running, like a blocking call inside {@code supply} or {@code task}, finishes first.
   *
   * @param p0 a program to be executed in parallel
   * @param p1 a program to be executed in parallel
   * @param p2 a program to be executed in parallel
   * @param p3 a program to be executed in parallel
   * @param p4 a program to be executed in parallel
   * @param p5 a program to be executed in parallel
   * @param p6 a program to be executed in parallel
   * @param finisher the function used to combine the results
   * @param executor the executor used to execute the programs in parallel
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T0> the result type of {@code p0}
   * @param <T1> the result type of {@code p1}
   * @param <T2> the result type of {@code p2}
   * @param <T3> the result type of {@code p3}
   * @param <T4> the result type of {@code p4}
   * @param <T5> the result type of {@code p5}
   * @param <T6> the result type of {@code p6}
   * @param <R> the type of the combined result
   * @return a new program representing the parallel computation
   */
  public static <S, E, T0, T1, T2, T3, T4, T5, T6, R> Program<S, E, R> parZip(
     Program<S, E, T0> p0,
     Program<S, E, T1> p1,
     Program<S, E, T2> p2,
     Program<S, E, T3> p3,
     Program<S, E, T4> p4,
     Program<S, E, T5> p5,
     Program<S, E, T6> p6,
     Finisher7<T0, T1, T2, T3, T4, T5, T6, R> finisher,
     Executor executor) {
       return zip(
         p0.fork(executor),
         p1.fork(executor),
         p2.fork(executor),
         p3.fork(executor),
         p4.fork(executor),
         p5.fork(executor),
         p6.fork(executor),
         (f0, f1, f2, f3, f4, f5, f6) ->
          Fiber.<S, E, R>joinAll(
            List.of(f0, f1, f2, f3, f4, f5, f6),
            () -> Result.zip(f0.resultNow(), f1.resultNow(), f2.resultNow(), f3.resultNow(), f4.resultNow(), f5.resultNow(), f6.resultNow(), finisher)))
        .flatMap(identity());
  }

  /**
   * Executes the given programs in parallel using the provided executor and combines their results
   * using the finisher function.
   *
   * <p>
   * The execution is fail-fast: as soon as any program fails, the remaining programs are
   * cancelled and the resulting program fails with that error. If several programs fail, the
   * error returned is the first one to happen in time, not the first by position. Cancelled
   * programs stop at their next step and run their finalizers, and the resulting program
   * completes once they have stopped. Cancellation is cooperative, so code that is already
   * running, like a blocking call inside {@code supply} or {@code task}, finishes first.
   *
   * @param p0 a program to be executed in parallel
   * @param p1 a program to be executed in parallel
   * @param p2 a program to be executed in parallel
   * @param p3 a program to be executed in parallel
   * @param p4 a program to be executed in parallel
   * @param p5 a program to be executed in parallel
   * @param p6 a program to be executed in parallel
   * @param p7 a program to be executed in parallel
   * @param finisher the function used to combine the results
   * @param executor the executor used to execute the programs in parallel
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T0> the result type of {@code p0}
   * @param <T1> the result type of {@code p1}
   * @param <T2> the result type of {@code p2}
   * @param <T3> the result type of {@code p3}
   * @param <T4> the result type of {@code p4}
   * @param <T5> the result type of {@code p5}
   * @param <T6> the result type of {@code p6}
   * @param <T7> the result type of {@code p7}
   * @param <R> the type of the combined result
   * @return a new program representing the parallel computation
   */
  public static <S, E, T0, T1, T2, T3, T4, T5, T6, T7, R> Program<S, E, R> parZip(
     Program<S, E, T0> p0,
     Program<S, E, T1> p1,
     Program<S, E, T2> p2,
     Program<S, E, T3> p3,
     Program<S, E, T4> p4,
     Program<S, E, T5> p5,
     Program<S, E, T6> p6,
     Program<S, E, T7> p7,
     Finisher8<T0, T1, T2, T3, T4, T5, T6, T7, R> finisher,
     Executor executor) {
       return zip(
         p0.fork(executor),
         p1.fork(executor),
         p2.fork(executor),
         p3.fork(executor),
         p4.fork(executor),
         p5.fork(executor),
         p6.fork(executor),
         p7.fork(executor),
         (f0, f1, f2, f3, f4, f5, f6, f7) ->
          Fiber.<S, E, R>joinAll(
            List.of(f0, f1, f2, f3, f4, f5, f6, f7),
            () -> Result.zip(f0.resultNow(), f1.resultNow(), f2.resultNow(), f3.resultNow(), f4.resultNow(), f5.resultNow(), f6.resultNow(), f7.resultNow(), finisher)))
        .flatMap(identity());
  }

  /**
   * Executes the given programs in parallel using the provided executor and combines their results
   * using the finisher function.
   *
   * <p>
   * The execution is fail-fast: as soon as any program fails, the remaining programs are
   * cancelled and the resulting program fails with that error. If several programs fail, the
   * error returned is the first one to happen in time, not the first by position. Cancelled
   * programs stop at their next step and run their finalizers, and the resulting program
   * completes once they have stopped. Cancellation is cooperative, so code that is already
   * running, like a blocking call inside {@code supply} or {@code task}, finishes first.
   *
   * @param p0 a program to be executed in parallel
   * @param p1 a program to be executed in parallel
   * @param p2 a program to be executed in parallel
   * @param p3 a program to be executed in parallel
   * @param p4 a program to be executed in parallel
   * @param p5 a program to be executed in parallel
   * @param p6 a program to be executed in parallel
   * @param p7 a program to be executed in parallel
   * @param p8 a program to be executed in parallel
   * @param finisher the function used to combine the results
   * @param executor the executor used to execute the programs in parallel
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T0> the result type of {@code p0}
   * @param <T1> the result type of {@code p1}
   * @param <T2> the result type of {@code p2}
   * @param <T3> the result type of {@code p3}
   * @param <T4> the result type of {@code p4}
   * @param <T5> the result type of {@code p5}
   * @param <T6> the result type of {@code p6}
   * @param <T7> the result type of {@code p7}
   * @param <T8> the result type of {@code p8}
   * @param <R> the type of the combined result
   * @return a new program representing the parallel computation
   */
  public static <S, E, T0, T1, T2, T3, T4, T5, T6, T7, T8, R> Program<S, E, R> parZip(
     Program<S, E, T0> p0,
     Program<S, E, T1> p1,
     Program<S, E, T2> p2,
     Program<S, E, T3> p3,
     Program<S, E, T4> p4,
     Program<S, E, T5> p5,
     Program<S, E, T6> p6,
     Program<S, E, T7> p7,
     Program<S, E, T8> p8,
     Finisher9<T0, T1, T2, T3, T4, T5, T6, T7, T8, R> finisher,
     Executor executor) {
       return zip(
         p0.fork(executor),
         p1.fork(executor),
         p2.fork(executor),
         p3.fork(executor),
         p4.fork(executor),
         p5.fork(executor),
         p6.fork(executor),
         p7.fork(executor),
         p8.fork(executor),
         (f0, f1, f2, f3, f4, f5, f6, f7, f8) ->
          Fiber.<S, E, R>joinAll(
            List.of(f0, f1, f2, f3, f4, f5, f6, f7, f8),
            () -> Result.zip(f0.resultNow(), f1.resultNow(), f2.resultNow(), f3.resultNow(), f4.resultNow(), f5.resultNow(), f6.resultNow(), f7.resultNow(), f8.resultNow(), finisher)))
        .flatMap(identity());
  }

  /**
   * Executes the given programs in parallel using virtual threads and combines their results
   * using the finisher function.
   *
   * <p>
   * The execution is fail-fast: as soon as any program fails, the remaining programs are
   * cancelled and the resulting program fails with that error. If several programs fail, the
   * error returned is the first one to happen in time, not the first by position. Cancelled
   * programs stop at their next step and run their finalizers, and the resulting program
   * completes once they have stopped. Cancellation is cooperative, so code that is already
   * running, like a blocking call inside {@code supply} or {@code task}, finishes first.
   *
   * @param p0 a program to be executed in parallel
   * @param p1 a program to be executed in parallel
   * @param finisher the function used to combine the results
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T0> the result type of {@code p0}
   * @param <T1> the result type of {@code p1}
   * @param <R> the type of the combined result
   * @return a new program representing the parallel computation
   */
  public static <S, E, T0, T1, R> Program<S, E, R> parZip(
      Program<S, E, T0> p0,
      Program<S, E, T1> p1,
      Finisher2<T0, T1, R> finisher) {
    return parZip(p0, p1, finisher, DEFAULT_EXECUTOR);
  }

  /**
   * Executes the given programs in parallel using virtual threads and combines their results
   * using the finisher function.
   *
   * <p>
   * The execution is fail-fast: as soon as any program fails, the remaining programs are
   * cancelled and the resulting program fails with that error. If several programs fail, the
   * error returned is the first one to happen in time, not the first by position. Cancelled
   * programs stop at their next step and run their finalizers, and the resulting program
   * completes once they have stopped. Cancellation is cooperative, so code that is already
   * running, like a blocking call inside {@code supply} or {@code task}, finishes first.
   *
   * @param p0 a program to be executed in parallel
   * @param p1 a program to be executed in parallel
   * @param p2 a program to be executed in parallel
   * @param finisher the function used to combine the results
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T0> the result type of {@code p0}
   * @param <T1> the result type of {@code p1}
   * @param <T2> the result type of {@code p2}
   * @param <R> the type of the combined result
   * @return a new program representing the parallel computation
   */
  public static <S, E, T0, T1, T2, R> Program<S, E, R> parZip(
      Program<S, E, T0> p0,
      Program<S, E, T1> p1,
      Program<S, E, T2> p2,
      Finisher3<T0, T1, T2, R> finisher) {
    return parZip(p0, p1, p2, finisher, DEFAULT_EXECUTOR);
  }

  /**
   * Executes the given programs in parallel using virtual threads and combines their results
   * using the finisher function.
   *
   * <p>
   * The execution is fail-fast: as soon as any program fails, the remaining programs are
   * cancelled and the resulting program fails with that error. If several programs fail, the
   * error returned is the first one to happen in time, not the first by position. Cancelled
   * programs stop at their next step and run their finalizers, and the resulting program
   * completes once they have stopped. Cancellation is cooperative, so code that is already
   * running, like a blocking call inside {@code supply} or {@code task}, finishes first.
   *
   * @param p0 a program to be executed in parallel
   * @param p1 a program to be executed in parallel
   * @param p2 a program to be executed in parallel
   * @param p3 a program to be executed in parallel
   * @param finisher the function used to combine the results
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T0> the result type of {@code p0}
   * @param <T1> the result type of {@code p1}
   * @param <T2> the result type of {@code p2}
   * @param <T3> the result type of {@code p3}
   * @param <R> the type of the combined result
   * @return a new program representing the parallel computation
   */
  public static <S, E, T0, T1, T2, T3, R> Program<S, E, R> parZip(
      Program<S, E, T0> p0,
      Program<S, E, T1> p1,
      Program<S, E, T2> p2,
      Program<S, E, T3> p3,
      Finisher4<T0, T1, T2, T3, R> finisher) {
    return parZip(p0, p1, p2, p3, finisher, DEFAULT_EXECUTOR);
  }

  /**
   * Executes the given programs in parallel using virtual threads and combines their results
   * using the finisher function.
   *
   * <p>
   * The execution is fail-fast: as soon as any program fails, the remaining programs are
   * cancelled and the resulting program fails with that error. If several programs fail, the
   * error returned is the first one to happen in time, not the first by position. Cancelled
   * programs stop at their next step and run their finalizers, and the resulting program
   * completes once they have stopped. Cancellation is cooperative, so code that is already
   * running, like a blocking call inside {@code supply} or {@code task}, finishes first.
   *
   * @param p0 a program to be executed in parallel
   * @param p1 a program to be executed in parallel
   * @param p2 a program to be executed in parallel
   * @param p3 a program to be executed in parallel
   * @param p4 a program to be executed in parallel
   * @param finisher the function used to combine the results
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T0> the result type of {@code p0}
   * @param <T1> the result type of {@code p1}
   * @param <T2> the result type of {@code p2}
   * @param <T3> the result type of {@code p3}
   * @param <T4> the result type of {@code p4}
   * @param <R> the type of the combined result
   * @return a new program representing the parallel computation
   */
  public static <S, E, T0, T1, T2, T3, T4, R> Program<S, E, R> parZip(
      Program<S, E, T0> p0,
      Program<S, E, T1> p1,
      Program<S, E, T2> p2,
      Program<S, E, T3> p3,
      Program<S, E, T4> p4,
      Finisher5<T0, T1, T2, T3, T4, R> finisher) {
    return parZip(p0, p1, p2, p3, p4, finisher, DEFAULT_EXECUTOR);
  }

  /**
   * Executes the given programs in parallel using virtual threads and combines their results
   * using the finisher function.
   *
   * <p>
   * The execution is fail-fast: as soon as any program fails, the remaining programs are
   * cancelled and the resulting program fails with that error. If several programs fail, the
   * error returned is the first one to happen in time, not the first by position. Cancelled
   * programs stop at their next step and run their finalizers, and the resulting program
   * completes once they have stopped. Cancellation is cooperative, so code that is already
   * running, like a blocking call inside {@code supply} or {@code task}, finishes first.
   *
   * @param p0 a program to be executed in parallel
   * @param p1 a program to be executed in parallel
   * @param p2 a program to be executed in parallel
   * @param p3 a program to be executed in parallel
   * @param p4 a program to be executed in parallel
   * @param p5 a program to be executed in parallel
   * @param finisher the function used to combine the results
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T0> the result type of {@code p0}
   * @param <T1> the result type of {@code p1}
   * @param <T2> the result type of {@code p2}
   * @param <T3> the result type of {@code p3}
   * @param <T4> the result type of {@code p4}
   * @param <T5> the result type of {@code p5}
   * @param <R> the type of the combined result
   * @return a new program representing the parallel computation
   */
  public static <S, E, T0, T1, T2, T3, T4, T5, R> Program<S, E, R> parZip(
      Program<S, E, T0> p0,
      Program<S, E, T1> p1,
      Program<S, E, T2> p2,
      Program<S, E, T3> p3,
      Program<S, E, T4> p4,
      Program<S, E, T5> p5,
      Finisher6<T0, T1, T2, T3, T4, T5, R> finisher) {
    return parZip(p0, p1, p2, p3, p4, p5, finisher, DEFAULT_EXECUTOR);
  }

  /**
   * Executes the given programs in parallel using virtual threads and combines their results
   * using the finisher function.
   *
   * <p>
   * The execution is fail-fast: as soon as any program fails, the remaining programs are
   * cancelled and the resulting program fails with that error. If several programs fail, the
   * error returned is the first one to happen in time, not the first by position. Cancelled
   * programs stop at their next step and run their finalizers, and the resulting program
   * completes once they have stopped. Cancellation is cooperative, so code that is already
   * running, like a blocking call inside {@code supply} or {@code task}, finishes first.
   *
   * @param p0 a program to be executed in parallel
   * @param p1 a program to be executed in parallel
   * @param p2 a program to be executed in parallel
   * @param p3 a program to be executed in parallel
   * @param p4 a program to be executed in parallel
   * @param p5 a program to be executed in parallel
   * @param p6 a program to be executed in parallel
   * @param finisher the function used to combine the results
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T0> the result type of {@code p0}
   * @param <T1> the result type of {@code p1}
   * @param <T2> the result type of {@code p2}
   * @param <T3> the result type of {@code p3}
   * @param <T4> the result type of {@code p4}
   * @param <T5> the result type of {@code p5}
   * @param <T6> the result type of {@code p6}
   * @param <R> the type of the combined result
   * @return a new program representing the parallel computation
   */
  public static <S, E, T0, T1, T2, T3, T4, T5, T6, R> Program<S, E, R> parZip(
      Program<S, E, T0> p0,
      Program<S, E, T1> p1,
      Program<S, E, T2> p2,
      Program<S, E, T3> p3,
      Program<S, E, T4> p4,
      Program<S, E, T5> p5,
      Program<S, E, T6> p6,
      Finisher7<T0, T1, T2, T3, T4, T5, T6, R> finisher) {
    return parZip(p0, p1, p2, p3, p4, p5, p6, finisher, DEFAULT_EXECUTOR);
  }

  /**
   * Executes the given programs in parallel using virtual threads and combines their results
   * using the finisher function.
   *
   * <p>
   * The execution is fail-fast: as soon as any program fails, the remaining programs are
   * cancelled and the resulting program fails with that error. If several programs fail, the
   * error returned is the first one to happen in time, not the first by position. Cancelled
   * programs stop at their next step and run their finalizers, and the resulting program
   * completes once they have stopped. Cancellation is cooperative, so code that is already
   * running, like a blocking call inside {@code supply} or {@code task}, finishes first.
   *
   * @param p0 a program to be executed in parallel
   * @param p1 a program to be executed in parallel
   * @param p2 a program to be executed in parallel
   * @param p3 a program to be executed in parallel
   * @param p4 a program to be executed in parallel
   * @param p5 a program to be executed in parallel
   * @param p6 a program to be executed in parallel
   * @param p7 a program to be executed in parallel
   * @param finisher the function used to combine the results
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T0> the result type of {@code p0}
   * @param <T1> the result type of {@code p1}
   * @param <T2> the result type of {@code p2}
   * @param <T3> the result type of {@code p3}
   * @param <T4> the result type of {@code p4}
   * @param <T5> the result type of {@code p5}
   * @param <T6> the result type of {@code p6}
   * @param <T7> the result type of {@code p7}
   * @param <R> the type of the combined result
   * @return a new program representing the parallel computation
   */
  public static <S, E, T0, T1, T2, T3, T4, T5, T6, T7, R> Program<S, E, R> parZip(
      Program<S, E, T0> p0,
      Program<S, E, T1> p1,
      Program<S, E, T2> p2,
      Program<S, E, T3> p3,
      Program<S, E, T4> p4,
      Program<S, E, T5> p5,
      Program<S, E, T6> p6,
      Program<S, E, T7> p7,
      Finisher8<T0, T1, T2, T3, T4, T5, T6, T7, R> finisher) {
    return parZip(p0, p1, p2, p3, p4, p5, p6, p7, finisher, DEFAULT_EXECUTOR);
  }

  /**
   * Executes the given programs in parallel using virtual threads and combines their results
   * using the finisher function.
   *
   * <p>
   * The execution is fail-fast: as soon as any program fails, the remaining programs are
   * cancelled and the resulting program fails with that error. If several programs fail, the
   * error returned is the first one to happen in time, not the first by position. Cancelled
   * programs stop at their next step and run their finalizers, and the resulting program
   * completes once they have stopped. Cancellation is cooperative, so code that is already
   * running, like a blocking call inside {@code supply} or {@code task}, finishes first.
   *
   * @param p0 a program to be executed in parallel
   * @param p1 a program to be executed in parallel
   * @param p2 a program to be executed in parallel
   * @param p3 a program to be executed in parallel
   * @param p4 a program to be executed in parallel
   * @param p5 a program to be executed in parallel
   * @param p6 a program to be executed in parallel
   * @param p7 a program to be executed in parallel
   * @param p8 a program to be executed in parallel
   * @param finisher the function used to combine the results
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T0> the result type of {@code p0}
   * @param <T1> the result type of {@code p1}
   * @param <T2> the result type of {@code p2}
   * @param <T3> the result type of {@code p3}
   * @param <T4> the result type of {@code p4}
   * @param <T5> the result type of {@code p5}
   * @param <T6> the result type of {@code p6}
   * @param <T7> the result type of {@code p7}
   * @param <T8> the result type of {@code p8}
   * @param <R> the type of the combined result
   * @return a new program representing the parallel computation
   */
  public static <S, E, T0, T1, T2, T3, T4, T5, T6, T7, T8, R> Program<S, E, R> parZip(
      Program<S, E, T0> p0,
      Program<S, E, T1> p1,
      Program<S, E, T2> p2,
      Program<S, E, T3> p3,
      Program<S, E, T4> p4,
      Program<S, E, T5> p5,
      Program<S, E, T6> p6,
      Program<S, E, T7> p7,
      Program<S, E, T8> p8,
      Finisher9<T0, T1, T2, T3, T4, T5, T6, T7, T8, R> finisher) {
    return parZip(p0, p1, p2, p3, p4, p5, p6, p7, p8, finisher, DEFAULT_EXECUTOR);
  }

  static <S, E, T> Program<S, E, T> timeout(
      Program<S, E, T> program, Delay delay, Executor executor) {
    if (delay.isZero()) {
      return raise(TimeoutException::new);
    }
    if (delay.isMax()) {
      return program;
    }
    // the timer is forked first, like in race: with a single thread executor it expires before
    // the program starts, which then is cancelled before it runs
    return zip(
        fork(sleep(delay), executor),
        program.fork(executor),
        Fiber::<S, E, T>timeout)
        .flatMap(identity());
  }

  /**
   * Forks all the programs, and then waits for them with the given combinator.
   *
   * <p>The forks are evaluated inside the async callback, so the combinator gets the fibers as
   * soon as they are all started.
   */
  private static <S, E, T, R> Program<S, E, R> forkAllAndThen(
      Executor executor,
      Collection<? extends Program<S, E, ? extends T>> programs,
      Function<Collection<Fiber<E, T>>, Program<S, E, R>> combinator) {
    var forked = forkAll(executor, programs);

    return Program.<S, E, Program<S, E, R>>async(
        (state, future) -> {
          try {
            future.complete(evalAll(state, forked).map(combinator));
          } catch (Throwable e) {
            Fatal.rethrowIfFatal(e);
            future.completeExceptionally(e);
          }
        })
        .flatMap(identity());
  }

  private static <S, E, T> Collection<Program<S, E, Fiber<E, T>>> forkAll(
      Executor executor, Collection<? extends Program<S, E, ? extends T>> programs) {
    return programs.stream()
        .map(Concurrent::<S, E, T>narrow)
        .map(p -> p.fork(executor))
        .toList();
  }

  @SuppressWarnings("unchecked")
  private static <S, E, T> Program<S, E, T> narrow(Program<S, E, ? extends T> program) {
    return (Program<S, E, T>) program;
  }

}
