/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Program.evalAll;
import static com.github.tonivade.diesel.Program.raise;
import static com.github.tonivade.diesel.Program.success;
import static com.github.tonivade.diesel.Program.unit;
import static com.github.tonivade.diesel.Program.zip;
import static java.util.function.Function.identity;

import com.github.tonivade.diesel.Result.Failure;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Concurrent combinators to execute {@link Program}s in parallel.
 */
public interface Concurrent {

  /**
   * Creates a new program that represents an either of two programs executed in parallel using the common fork-join pool.
   *
   * <p>First without error/exception wins
   *
   * @param p1 the first program
   * @param p2 the second program
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result of the first program
   * @param <U> the type of the result of the second program
   * @return a new program representing an either of the two programs
   */
  static <S, E, T, U> Program<S, E, Either<T, U>> either(Program<S, E, T> p1, Program<S, E, U> p2) {
    return either(p1, p2, ForkJoinPool.commonPool());
  }

  /**
   * Creates a new program that represents an either of two programs executed in parallel using the provided executor.
   *
   * <p>First without error/exception wins
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
  static <S, E, T, U> Program<S, E, Either<T, U>> either(
      Program<S, E, T> p1, Program<S, E, U> p2, Executor executor) {
    return zip(p1.fork(executor), p2.fork(executor), Concurrent::either)
        .flatMap(Program::from);
  }

  /**
   * Creates a new program that represents an race of two programs executed in parallel using the common fork-join pool.
   *
   * <p>First to finish wins
   *
   * @param p1 the first program
   * @param p2 the second program
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result of the first program
   * @param <U> the type of the result of the second program
   * @return a new program representing an either of the two programs
   */
  static <S, E, T, U> Program<S, E, Either<T, U>> race(Program<S, E, T> p1, Program<S, E, U> p2) {
    return race(p1, p2, ForkJoinPool.commonPool());
  }

  /**
   * Creates a new program that represents an race of two programs executed in parallel using the provided executor.
   *
   * <p>First to finish wins
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
  static <S, E, T, U> Program<S, E, Either<T, U>> race(
      Program<S, E, T> p1, Program<S, E, U> p2, Executor executor) {
    return zip(p1.fork(executor), p2.fork(executor), Concurrent::race)
        .flatMap(Program::from);
  }

  /**
   * Executes all the given programs in parallel using the common fork-join pool and ignores all their results.
   *
   * <p>
   * The execution is fail-fast: as soon as any program fails, the resulting program fails with
   * that error without waiting for the rest. If several programs fail, the error returned is the
   * first one to happen in time, not the first by position. The remaining programs are not
   * cancelled and keep running in the background.
   *
   * @param programs the programs to be executed
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   * @return a new program representing the parallel computation
   */
  @SafeVarargs
  static <S, E, T> Program<S, E, Void> parAll(Program<S, E, T>... programs) {
    return parAll(ForkJoinPool.commonPool(), programs);
  }

  /**
   * Executes all the given programs in parallel using the provided executor.
   *
   * <p>
   * The execution is fail-fast: as soon as any program fails, the resulting program fails with
   * that error without waiting for the rest. If several programs fail, the error returned is the
   * first one to happen in time, not the first by position. The remaining programs are not
   * cancelled and keep running in the background.
   *
   * @param executor the executor used to execute the programs in parallel
   * @param programs the programs to be executed
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @return a new program representing the parallel computation
   */
  @SafeVarargs
  static <S, E> Program<S, E, Void> parAll(Executor executor, Program<S, E, ?>... programs) {
    return parAll(executor, List.of(programs));
  }

  /**
   * Executes all the given programs in parallel using the common fork-join pool and ignores all their results.
   *
   * <p>
   * The execution is fail-fast: as soon as any program fails, the resulting program fails with
   * that error without waiting for the rest. If several programs fail, the error returned is the
   * first one to happen in time, not the first by position. The remaining programs are not
   * cancelled and keep running in the background.
   *
   * @param programs the programs to be executed
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @return a new program representing the parallel computation
   */
  static <S, E> Program<S, E, Void> parAll(Collection<? extends Program<S, E, ?>> programs) {
    return parAll(ForkJoinPool.commonPool(), programs);
  }

  /**
   * Executes all the given programs in parallel using the provided executor.
   *
   * <p>
   * The execution is fail-fast: as soon as any program fails, the resulting program fails with
   * that error without waiting for the rest. If several programs fail, the error returned is the
   * first one to happen in time, not the first by position. The remaining programs are not
   * cancelled and keep running in the background.
   *
   * @param executor the executor used to execute the programs in parallel
   * @param programs the programs to be executed
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @return a new program representing the parallel computation
   */
  static <S, E> Program<S, E, Void> parAll(Executor executor, Collection<? extends Program<S, E, ?>> programs) {
    if (programs.isEmpty()) {
      return unit();
    }

    var forked = forkAll(executor, programs);

    return Program.<S, E, CompletableFuture<Result<E, Void>>>async(
        (state, future) -> {
          try {
            var result = evalAll(state, forked).map(Concurrent::parAllFailFast);
            future.complete(result);
          } catch (RuntimeException e) {
            future.completeExceptionally(e);
          }
        })
        .flatMap(Program::from);
  }

  /**
   * Executes all the given programs in parallel using the common fork-join pool and returns the
   * result of the first one that finishes successfully.
   *
   * <p>
   * Programs that fail, with an error or an exception, are ignored as long as there are other
   * programs still running. When a program succeeds the remaining programs are cancelled. If all
   * the programs fail, the resulting program fails with the last error to happen in time.
   *
   * @param programs the programs to be executed
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   * @return a new program representing the first successful computation
   */
  @SafeVarargs
  static <S, E, T> Program<S, E, T> parAny(Program<S, E, T>... programs) {
    return parAny(List.of(programs));
  }

  /**
   * Executes all the given programs in parallel using the provided executor and returns the
   * result of the first one that finishes successfully.
   *
   * <p>
   * Programs that fail, with an error or an exception, are ignored as long as there are other
   * programs still running. When a program succeeds the remaining programs are cancelled. If all
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
  static <S, E, T> Program<S, E, T> parAny(Executor executor, Program<S, E, T>... programs) {
    return parAny(executor, List.of(programs));
  }

  /**
   * Executes all the given programs in parallel using the common fork-join pool and returns the
   * result of the first one that finishes successfully.
   *
   * <p>
   * Programs that fail, with an error or an exception, are ignored as long as there are other
   * programs still running. When a program succeeds the remaining programs are cancelled. If all
   * the programs fail, the resulting program fails with the last error to happen in time.
   *
   * @param programs the programs to be executed
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   * @return a new program representing the first successful computation
   * @throws NoSuchElementException when evaluated if no programs are given
   */
  static <S, E, T> Program<S, E, T> parAny(Collection<? extends Program<S, E, T>> programs) {
    return parAny(ForkJoinPool.commonPool(), programs);
  }

  /**
   * Executes all the given programs in parallel using the provided executor and returns the
   * result of the first one that finishes successfully.
   *
   * <p>
   * Programs that fail, with an error or an exception, are ignored as long as there are other
   * programs still running. When a program succeeds the remaining programs are cancelled. If all
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
  static <S, E, T> Program<S, E, T> parAny(Executor executor, Collection<? extends Program<S, E, T>> programs) {
    if (programs.size() == 0) {
      return raise(NoSuchElementException::new);
    }

    var forked = forkAll(executor, programs);

    return Program.<S, E, CompletableFuture<Result<E, T>>>async(
        (state, future) -> {
          try {
            var result = evalAll(state, forked).map(Concurrent::parAnySuccess);
            future.complete(result);
          } catch (RuntimeException e) {
            future.completeExceptionally(e);
          }
        })
        .flatMap(Program::from);
  }

  /**
   * Executes a collection of programs in parallel using the common fork-join pool and sequences
   * their results into a single program containing a collection of success values.
   *
   * <p>
   * The execution is fail-fast: as soon as any program fails, the resulting program fails with
   * that error without waiting for the rest. If several programs fail, the error returned is the
   * first one to happen in time, not the first by position. The remaining programs are not
   * cancelled and keep running in the background.
   *
   * @param programs the programs to be executed
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   * @return a new program representing the parallel computation with sequenced results
   */
  @SafeVarargs
  static <S, E, T> Program<S, E, Collection<T>> parSequence(Program<S, E, T>... programs) {
    return parSequence(ForkJoinPool.commonPool(), programs);
  }

  /**
   * Executes a collection of programs in parallel using the provided executor
   * and sequences their results into a single program containing a collection of success values.
   *
   * <p>
   * The execution is fail-fast: as soon as any program fails, the resulting program fails with
   * that error without waiting for the rest. If several programs fail, the error returned is the
   * first one to happen in time, not the first by position. The remaining programs are not
   * cancelled and keep running in the background.
   *
   * @param executor the executor used to execute the programs in parallel
   * @param programs the programs to be executed
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   * @return a new program representing the parallel computation with sequenced results
   */
  @SafeVarargs
  static <S, E, T> Program<S, E, Collection<T>> parSequence(Executor executor, Program<S, E, T>... programs) {
    return parSequence(executor, List.of(programs));
  }

  /**
   * Executes a collection of programs in parallel using the common fork-join pool and sequences
   * their results into a single program containing a collection of success values.
   *
   * <p>
   * The execution is fail-fast: as soon as any program fails, the resulting program fails with
   * that error without waiting for the rest. If several programs fail, the error returned is the
   * first one to happen in time, not the first by position. The remaining programs are not
   * cancelled and keep running in the background.
   *
   * @param programs the programs to be executed
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   * @return a new program representing the parallel computation with sequenced results
   */
  static <S, E, T> Program<S, E, Collection<T>> parSequence(Collection<? extends Program<S, E, T>> programs) {
    return parSequence(ForkJoinPool.commonPool(), programs);
  }

  /**
   * Executes a collection of programs in parallel using the provided executor
   * and sequences their results into a single program containing a collection of success values.
   *
   * <p>
   * The execution is fail-fast: as soon as any program fails, the resulting program fails with
   * that error without waiting for the rest. If several programs fail, the error returned is the
   * first one to happen in time, not the first by position. The remaining programs are not
   * cancelled and keep running in the background.
   *
   * @param executor the executor used to execute the programs in parallel
   * @param programs the programs to be executed
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   * @return a new program representing the parallel computation with sequenced results
   */
  static <S, E, T> Program<S, E, Collection<T>> parSequence(
      Executor executor, Collection<? extends Program<S, E, T>> programs) {
    if (programs.isEmpty()) {
      return success(List.of());
    }

    var forked = forkAll(executor, programs);

    return Program.<S, E, CompletableFuture<Result<E, Collection<T>>>>async(
        (state, future) -> {
          try {
            var result = evalAll(state, forked).map(Concurrent::parSequenceFailFast);
            future.complete(result);
          } catch (RuntimeException e) {
            future.completeExceptionally(e);
          }
        })
        .flatMap(Program::from);
  }

  /**
   * Executes the given programs in parallel using the provided executor and combines their results
   * using the finisher function.
   *
   * <p>
   * The execution is fail-fast: as soon as any program fails, the resulting program fails with
   * that error without waiting for the rest. If several programs fail, the error returned is the
   * first one to happen in time, not the first by position. The remaining programs are not
   * cancelled and keep running in the background.
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
  static <S, E, T0, T1, R> Program<S, E, R> parZip(
      Program<S, E, T0> p0,
      Program<S, E, T1> p1,
      Finisher2<T0, T1, R> finisher,
      Executor executor) {
    return zip(
        p0.fork(executor),
        p1.fork(executor),
        (f0, f1) -> {
          return parAllFailFast(List.of(f0, f1))
              .thenApply(result -> result.fold(
                  Result::<E, R>failure,
                  _ -> Result.zip(f0.join(), f1.join(), finisher)));
        })
        .flatMap(Program::from);
  }

  /**
   * Executes the given programs in parallel using the provided executor and combines their results
   * using the finisher function.
   *
   * <p>
   * The execution is fail-fast: as soon as any program fails, the resulting program fails with
   * that error without waiting for the rest. If several programs fail, the error returned is the
   * first one to happen in time, not the first by position. The remaining programs are not
   * cancelled and keep running in the background.
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
  static <S, E, T0, T1, T2, R> Program<S, E, R> parZip(
      Program<S, E, T0> p0,
      Program<S, E, T1> p1,
      Program<S, E, T2> p2,
      Finisher3<T0, T1, T2, R> finisher,
      Executor executor) {
    return zip(
        p0.fork(executor),
        p1.fork(executor),
        p2.fork(executor),
        (f0, f1, f2) -> {
          return parAllFailFast(List.of(f0, f1, f2))
              .thenApply(result -> result.fold(
                  Result::<E, R>failure,
                  _ -> Result.zip(f0.join(), f1.join(), f2.join(), finisher)));
        })
        .flatMap(Program::from);
  }

  /**
   * Executes the given programs in parallel using the provided executor and combines their results
   * using the finisher function.
   *
   * <p>
   * The execution is fail-fast: as soon as any program fails, the resulting program fails with
   * that error without waiting for the rest. If several programs fail, the error returned is the
   * first one to happen in time, not the first by position. The remaining programs are not
   * cancelled and keep running in the background.
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
  static <S, E, T0, T1, T2, T3, R> Program<S, E, R> parZip(
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
        (f0, f1, f2, f3) -> {
          return parAllFailFast(List.of(f0, f1, f2, f3))
              .thenApply(result -> result.fold(
                  Result::<E, R>failure,
                  _ -> Result.zip(f0.join(), f1.join(), f2.join(), f3.join(), finisher)));
        })
        .flatMap(Program::from);
  }

  /**
   * Executes the given programs in parallel using the provided executor and combines their results
   * using the finisher function.
   *
   * <p>
   * The execution is fail-fast: as soon as any program fails, the resulting program fails with
   * that error without waiting for the rest. If several programs fail, the error returned is the
   * first one to happen in time, not the first by position. The remaining programs are not
   * cancelled and keep running in the background.
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
  static <S, E, T0, T1, T2, T3, T4, R> Program<S, E, R> parZip(
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
        (f0, f1, f2, f3, f4) -> {
          return parAllFailFast(List.of(f0, f1, f2, f3, f4))
              .thenApply(result -> result.fold(
                  Result::<E, R>failure,
                  _ -> Result.zip(f0.join(), f1.join(), f2.join(), f3.join(), f4.join(), finisher)));
        })
        .flatMap(Program::from);
  }

  /**
   * Executes the given programs in parallel using the provided executor and combines their results
   * using the finisher function.
   *
   * <p>
   * The execution is fail-fast: as soon as any program fails, the resulting program fails with
   * that error without waiting for the rest. If several programs fail, the error returned is the
   * first one to happen in time, not the first by position. The remaining programs are not
   * cancelled and keep running in the background.
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
  static <S, E, T0, T1, T2, T3, T4, T5, R> Program<S, E, R> parZip(
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
        (f0, f1, f2, f3, f4, f5) -> {
          return parAllFailFast(List.of(f0, f1, f2, f3, f4, f5))
              .thenApply(result -> result.fold(
                  Result::<E, R>failure,
                  _ -> Result.zip(f0.join(), f1.join(), f2.join(), f3.join(), f4.join(), f5.join(), finisher)));
        })
        .flatMap(Program::from);
  }

  /**
   * Executes the given programs in parallel using the provided executor and combines their results
   * using the finisher function.
   *
   * <p>
   * The execution is fail-fast: as soon as any program fails, the resulting program fails with
   * that error without waiting for the rest. If several programs fail, the error returned is the
   * first one to happen in time, not the first by position. The remaining programs are not
   * cancelled and keep running in the background.
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
  static <S, E, T0, T1, T2, T3, T4, T5, T6, R> Program<S, E, R> parZip(
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
        (f0, f1, f2, f3, f4, f5, f6) -> {
          return parAllFailFast(List.of(f0, f1, f2, f3, f4, f5, f6))
              .thenApply(result -> result.fold(
                  Result::<E, R>failure,
                  _ -> Result.zip(f0.join(), f1.join(), f2.join(), f3.join(), f4.join(), f5.join(), f6.join(), finisher)));
        })
        .flatMap(Program::from);
  }

  /**
   * Executes the given programs in parallel using the provided executor and combines their results
   * using the finisher function.
   *
   * <p>
   * The execution is fail-fast: as soon as any program fails, the resulting program fails with
   * that error without waiting for the rest. If several programs fail, the error returned is the
   * first one to happen in time, not the first by position. The remaining programs are not
   * cancelled and keep running in the background.
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
  static <S, E, T0, T1, T2, T3, T4, T5, T6, T7, R> Program<S, E, R> parZip(
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
        (f0, f1, f2, f3, f4, f5, f6, f7) -> {
          return parAllFailFast(List.of(f0, f1, f2, f3, f4, f5, f6, f7))
              .thenApply(result -> result.fold(
                  Result::<E, R>failure,
                  _ -> Result.zip(f0.join(), f1.join(), f2.join(), f3.join(), f4.join(), f5.join(), f6.join(), f7.join(), finisher)));
        })
        .flatMap(Program::from);
  }

  /**
   * Executes the given programs in parallel using the provided executor and combines their results
   * using the finisher function.
   *
   * <p>
   * The execution is fail-fast: as soon as any program fails, the resulting program fails with
   * that error without waiting for the rest. If several programs fail, the error returned is the
   * first one to happen in time, not the first by position. The remaining programs are not
   * cancelled and keep running in the background.
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
  static <S, E, T0, T1, T2, T3, T4, T5, T6, T7, T8, R> Program<S, E, R> parZip(
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
        (f0, f1, f2, f3, f4, f5, f6, f7, f8) -> {
          return parAllFailFast(List.of(f0, f1, f2, f3, f4, f5, f6, f7, f8))
              .thenApply(result -> result.fold(
                  Result::<E, R>failure,
                  _ -> Result.zip(f0.join(), f1.join(), f2.join(), f3.join(), f4.join(), f5.join(), f6.join(), f7.join(), f8.join(), finisher)));
        })
        .flatMap(Program::from);
  }

  /**
   * Executes the given programs in parallel using the common fork-join pool and combines their results
   * using the finisher function.
   *
   * <p>
   * The execution is fail-fast: as soon as any program fails, the resulting program fails with
   * that error without waiting for the rest. If several programs fail, the error returned is the
   * first one to happen in time, not the first by position. The remaining programs are not
   * cancelled and keep running in the background.
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
  static <S, E, T0, T1, R> Program<S, E, R> parZip(
      Program<S, E, T0> p0,
      Program<S, E, T1> p1,
      Finisher2<T0, T1, R> finisher) {
    return parZip(p0, p1, finisher, ForkJoinPool.commonPool());
  }

  /**
   * Executes the given programs in parallel using the common fork-join pool and combines their results
   * using the finisher function.
   *
   * <p>
   * The execution is fail-fast: as soon as any program fails, the resulting program fails with
   * that error without waiting for the rest. If several programs fail, the error returned is the
   * first one to happen in time, not the first by position. The remaining programs are not
   * cancelled and keep running in the background.
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
  static <S, E, T0, T1, T2, R> Program<S, E, R> parZip(
      Program<S, E, T0> p0,
      Program<S, E, T1> p1,
      Program<S, E, T2> p2,
      Finisher3<T0, T1, T2, R> finisher) {
    return parZip(p0, p1, p2, finisher, ForkJoinPool.commonPool());
  }

  /**
   * Executes the given programs in parallel using the common fork-join pool and combines their results
   * using the finisher function.
   *
   * <p>
   * The execution is fail-fast: as soon as any program fails, the resulting program fails with
   * that error without waiting for the rest. If several programs fail, the error returned is the
   * first one to happen in time, not the first by position. The remaining programs are not
   * cancelled and keep running in the background.
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
  static <S, E, T0, T1, T2, T3, R> Program<S, E, R> parZip(
      Program<S, E, T0> p0,
      Program<S, E, T1> p1,
      Program<S, E, T2> p2,
      Program<S, E, T3> p3,
      Finisher4<T0, T1, T2, T3, R> finisher) {
    return parZip(p0, p1, p2, p3, finisher, ForkJoinPool.commonPool());
  }

  /**
   * Executes the given programs in parallel using the common fork-join pool and combines their results
   * using the finisher function.
   *
   * <p>
   * The execution is fail-fast: as soon as any program fails, the resulting program fails with
   * that error without waiting for the rest. If several programs fail, the error returned is the
   * first one to happen in time, not the first by position. The remaining programs are not
   * cancelled and keep running in the background.
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
  static <S, E, T0, T1, T2, T3, T4, R> Program<S, E, R> parZip(
      Program<S, E, T0> p0,
      Program<S, E, T1> p1,
      Program<S, E, T2> p2,
      Program<S, E, T3> p3,
      Program<S, E, T4> p4,
      Finisher5<T0, T1, T2, T3, T4, R> finisher) {
    return parZip(p0, p1, p2, p3, p4, finisher, ForkJoinPool.commonPool());
  }

  /**
   * Executes the given programs in parallel using the common fork-join pool and combines their results
   * using the finisher function.
   *
   * <p>
   * The execution is fail-fast: as soon as any program fails, the resulting program fails with
   * that error without waiting for the rest. If several programs fail, the error returned is the
   * first one to happen in time, not the first by position. The remaining programs are not
   * cancelled and keep running in the background.
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
  static <S, E, T0, T1, T2, T3, T4, T5, R> Program<S, E, R> parZip(
      Program<S, E, T0> p0,
      Program<S, E, T1> p1,
      Program<S, E, T2> p2,
      Program<S, E, T3> p3,
      Program<S, E, T4> p4,
      Program<S, E, T5> p5,
      Finisher6<T0, T1, T2, T3, T4, T5, R> finisher) {
    return parZip(p0, p1, p2, p3, p4, p5, finisher, ForkJoinPool.commonPool());
  }

  /**
   * Executes the given programs in parallel using the common fork-join pool and combines their results
   * using the finisher function.
   *
   * <p>
   * The execution is fail-fast: as soon as any program fails, the resulting program fails with
   * that error without waiting for the rest. If several programs fail, the error returned is the
   * first one to happen in time, not the first by position. The remaining programs are not
   * cancelled and keep running in the background.
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
  static <S, E, T0, T1, T2, T3, T4, T5, T6, R> Program<S, E, R> parZip(
      Program<S, E, T0> p0,
      Program<S, E, T1> p1,
      Program<S, E, T2> p2,
      Program<S, E, T3> p3,
      Program<S, E, T4> p4,
      Program<S, E, T5> p5,
      Program<S, E, T6> p6,
      Finisher7<T0, T1, T2, T3, T4, T5, T6, R> finisher) {
    return parZip(p0, p1, p2, p3, p4, p5, p6, finisher, ForkJoinPool.commonPool());
  }

  /**
   * Executes the given programs in parallel using the common fork-join pool and combines their results
   * using the finisher function.
   *
   * <p>
   * The execution is fail-fast: as soon as any program fails, the resulting program fails with
   * that error without waiting for the rest. If several programs fail, the error returned is the
   * first one to happen in time, not the first by position. The remaining programs are not
   * cancelled and keep running in the background.
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
  static <S, E, T0, T1, T2, T3, T4, T5, T6, T7, R> Program<S, E, R> parZip(
      Program<S, E, T0> p0,
      Program<S, E, T1> p1,
      Program<S, E, T2> p2,
      Program<S, E, T3> p3,
      Program<S, E, T4> p4,
      Program<S, E, T5> p5,
      Program<S, E, T6> p6,
      Program<S, E, T7> p7,
      Finisher8<T0, T1, T2, T3, T4, T5, T6, T7, R> finisher) {
    return parZip(p0, p1, p2, p3, p4, p5, p6, p7, finisher, ForkJoinPool.commonPool());
  }

  /**
   * Executes the given programs in parallel using the common fork-join pool and combines their results
   * using the finisher function.
   *
   * <p>
   * The execution is fail-fast: as soon as any program fails, the resulting program fails with
   * that error without waiting for the rest. If several programs fail, the error returned is the
   * first one to happen in time, not the first by position. The remaining programs are not
   * cancelled and keep running in the background.
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
  static <S, E, T0, T1, T2, T3, T4, T5, T6, T7, T8, R> Program<S, E, R> parZip(
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
    return parZip(p0, p1, p2, p3, p4, p5, p6, p7, p8, finisher, ForkJoinPool.commonPool());
  }

  private static <S, E, T> Collection<Program<S, E, CompletableFuture<Result<E, T>>>> forkAll(
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

  private static <E, T> CompletableFuture<Result<E, Collection<T>>> parSequenceFailFast(
      Collection<? extends CompletableFuture<Result<E, T>>> futures) {
    return parAllFailFast(futures)
        .thenApply(value -> value.fold(
            Result::failure,
            _ -> Result.sequence(futures.stream().map(CompletableFuture::join).toList())));
  }

  private static <E> CompletableFuture<Result<E, Void>> parAllFailFast(
      Collection<? extends CompletableFuture<? extends Result<E, ?>>> futures) {
    if (futures.isEmpty()) {
      return CompletableFuture.completedFuture(Result.unit());
    }
    var result = new CompletableFuture<Result<E, Void>>();
    var remaining = new AtomicInteger(futures.size());

    for (var future : futures) {
      future.whenComplete((value, error) -> {
        if (error != null) {
          result.completeExceptionally(error);
        } else if (value instanceof Failure(var fail)) {
          result.complete(Result.failure(fail));
        } else if (remaining.decrementAndGet() == 0) {
          result.complete(Result.unit());
        }
      });
    }

    return result;
  }

  private static <E, T, U> CompletableFuture<Result<E, Either<T, U>>> race(
      CompletableFuture<Result<E, T>> f1, CompletableFuture<Result<E, U>> f2) {
    return f1.thenApply(t -> t.map(Either::<T, U>left))
        .applyToEither(f2.thenApply(u -> u.map(Either::<T, U>right)), identity())
        .whenComplete((_, _) -> cancelBoth(f1, f2));
  }

  private static <E, T, U> CompletableFuture<Result<E, Either<T, U>>> either(
      CompletableFuture<Result<E, T>> f1, CompletableFuture<Result<E, U>> f2) {
    return parAnySuccess(List.of(
            f1.thenApply(t -> t.map(Either::<T, U>left)),
            f2.thenApply(u -> u.map(Either::<T, U>right))))
        .whenComplete((_, _) -> cancelBoth(f1, f2));
  }

  private static <E, T> CompletableFuture<Result<E, T>> parAnySuccess(
      Collection<? extends CompletableFuture<Result<E, T>>> futures) {
    var result = new CompletableFuture<Result<E, T>>();
    var remaining = new AtomicInteger(futures.size());

    for (var future : futures) {
      future.whenComplete((value, error) -> {
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

    return result.whenComplete((_, _) -> futures.forEach(f -> f.cancel(true)));
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
