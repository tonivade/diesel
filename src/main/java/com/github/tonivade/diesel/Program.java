/*
 * Copyright (c) 2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Trampoline.done;
import static com.github.tonivade.diesel.Trampoline.more;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import com.github.tonivade.diesel.function.Finisher2;
import com.github.tonivade.diesel.function.Finisher3;
import com.github.tonivade.diesel.function.Finisher4;
import com.github.tonivade.diesel.function.Finisher5;
import com.github.tonivade.diesel.function.Finisher6;
import com.github.tonivade.diesel.function.Finisher7;
import com.github.tonivade.diesel.function.Finisher8;
import com.github.tonivade.diesel.function.Finisher9;

/**
 * A {@code Program} represents a computation that can be executed in a specific context.
 * It is a functional programming construct that allows for the composition of computations
 * and error handling.
 *
 * @param <S> the type of the state
 * @param <E> the type of the error
 * @param <T> the type of the result
 */
public sealed interface Program<S, E, T> {

  Program<?, ?, Void> UNIT = success((Void) null);

  @SuppressWarnings("unchecked")
  static <S, E> Program<S, E, Void> unit() {
    return (Program<S, E, Void>) UNIT;
  }

  /**
   * Creates a new program that represents a computation that can be executed in a specific context.
   *
   * @param result the Result representing the computation
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   * @return a new program representing the computation
   */
  static <S, E, T> Program<S, E, T> from(Result<E, T> result) {
    return result.fold(Program::failure, Program::success);
  }

  /**
   * Creates a new program that represents a computation that can be executed in a specific context.
   *
   * @param either the Either representing the computation
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   * @return a new program representing the computation
   */
  static <S, E, T> Program<S, E, T> from(Either<E, T> either) {
    return either.fold(Program::failure, Program::success);
  }

  /**
   * Creates a new program that represents an asynchronous computation.
   *
   * @param future the CompletableFuture representing the asynchronous computation
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   * @return a new program representing an asynchronous computation
   */
  static <S, E, T> Program<S, E, T> from(CompletableFuture<Result<E, T>> future) {
    return async((_, callback) -> future.whenCompleteAsync(callback));
  }

  /**
   * Creates a new program that represents a successful computation with the given value.
   *
   * @param value the value of the successful computation
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   * @return a new program representing a successful computation
   */
  static <S, E, T> Program<S, E, T> success(@Nullable T value) {
    return new Success<>(value);
  }

  /**
   * Creates a function that maps a value to a successful program.
   *
   * @param mapper the function used to map the value
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the input value
   * @param <R> the type of the result
   * @return a function that maps a value to a successful program
   */
  static <S, E, T, R> Function<T, Program<S, E, R>> success(Function<T, R> mapper) {
    return t -> Program.<S, E, R>success(mapper.apply(t));
  }

  /**
   * Creates a function that maps a value to a failed program.
   *
   * @param mapper the function used to map the value
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the input value
   * @param <R> the type of the result
   * @return a function that maps a value to a failed program
   */
  static <S, E, T, R> Function<T, Program<S, E, R>> failure(Function<T, E> mapper) {
    return t -> Program.<S, E, R>failure(mapper.apply(t));
  }

  /**
   * Creates a new program that represents a failed computation with the given error.
   *
   * @param error the error of the failed computation
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   * @return a new program representing a failed computation
   */
  static <S, E, T> Program<S, E, T> failure(E error) {
    return new Failure<>(error);
  }

  /**
   * Creates a new program that represents a computation that raises an exception.
   *
   * @param throwable the exception to be raised
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   * @param <X> the type of the exception
   * @return a new program representing a computation that raises an exception
   */
  static <S, E, T, X extends Throwable> Program<S, E, T> raise(Supplier<X> throwable) {
    return supply(() -> sneakyThrow(throwable.get()));
  }

  /**
   * Creates a new program that represents a computation that supplies a value.
   *
   * @param supplier the supplier of the value
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   * @return a new program representing a computation that supplies a value
   */
  static <S, E, T> Program<S, E, T> supply(Supplier<T> supplier) {
    return Program.<S, E>unit().map(_ -> supplier.get());
  }

  /**
   * Creates a new program that represents a computation that suspends execution.
   *
   * @param supplier the supplier of the program to be executed
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   * @return a new program representing a computation that suspends execution
   */
  static <S, E, T> Program<S, E, T> suspend(Supplier<Program<S, E, T>> supplier) {
    return Program.<S, E>unit().flatMap(_ -> supplier.get());
  }

  /**
   * Creates a new program that represents a computation that executes a runnable.
   *
   * @param runnable the runnable to be executed
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @return a new program representing a computation that executes a runnable
   */
  static <S, E> Program<S, E, Void> task(Runnable runnable) {
    return Program.<S, E>unit().map(_ -> {
      runnable.run();
      return null;
    });
  }

  /**
   * Creates a new program that represents an asynchronous computation.
   *
   * @param callback the callback to be executed asynchronously
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   * @return a new program representing an asynchronous computation
   */
  static <S, E, T> Program<S, E, T> async(
      BiConsumer<S, BiConsumer<Result<E, T>, Throwable>> callback) {
    return new Async<>(callback);
  }

  record Success<S, E, T>(@Nullable T value) implements Program<S, E, T> {}

  record Failure<S, E, T>(E error) implements Program<S, E, T> {}

  record Catch<S, E, T>(
      Program<S, E, T> current,
      Function<Throwable, Program<S, E, T>> recover) implements Program<S, E, T> {}

  record FoldMap<S, E, F, T, R>(
      Program<S, E, T> current,
      Function<E, Program<S, F, R>> onFailure,
      Function<T, Program<S, F, R>> onSuccess) implements Program<S, F, R> {
    private Trampoline<Result<F, R>> foldEval(S state) {
      return more(() -> current.safeEval(state))
          .flatMap(result -> more(() -> result.fold(onFailure, onSuccess).safeEval(state)));
    }
  }

  record Async<S, E, T>(
      BiConsumer<S, BiConsumer<Result<E, T>, Throwable>> callback) implements Program<S, E, T> {
  }

  non-sealed interface Dsl<S, E, T> extends Program<S, E, T> {
    Result<E, T> handle(S state);
  }

  /**
   * Evaluates the program using the provided state.
   *
   * @param state the state used to evaluate the program
   * @return the result of the evaluation
   */
  default Result<E, T> eval(S state) {
    return safeEval(state).run();
  }

  private Trampoline<Result<E, T>> safeEval(S state) {
    return switch (this) {
      case Success<S, E, T>(var value) -> done(Result.success(value));
      case Failure<S, E, T>(var error) -> done(Result.failure(error));
      case Catch<S, E, T>(var current, var recover) -> {
        try {
          yield done(current.eval(state));
        } catch (Throwable t) {
          yield recover.apply(t).safeEval(state);
        }
      }
      case Async<S, E, T>(var callback) -> {
        var future = new CompletableFuture<Result<E, T>>();
        callback.accept(state, (result, error) -> {
          if (error != null) {
            future.completeExceptionally(error);
          } else {
            future.complete(result);
          }
        });
        yield done(future.join());
      }
      case FoldMap<S, ?, E, ?, T> foldMap -> foldMap.foldEval(state);
      case Dsl<S, E, T> dsl -> done(dsl.handle(state));
    };
  }

  /**
   * Maps the program to a new program using the provided mapper function.
   *
   * @param mapper the function used to map the program
   * @param <R> the type of the new program
   * @return a new program representing the mapped computation
   */
  default <R> Program<S, E, R> map(Function<T, R> mapper) {
    return flatMap(mapper.andThen(Program::success));
  }

  /**
   * Maps the program to a new program using the provided error mapper function.
   *
   * @param mapper the function used to map the error
   * @return a new program representing the mapped computation
   */
  default Program<S, E, T> redeem(Function<E, T> mapper) {
    return recover(mapper.andThen(Program::success));
  }

  default Program<S, E, T> redeemWith(T value) {
    return recoverWith(success(value));
  }

  /**
   * Maps the program to a new program using the provided mapper function for errors.
   *
   * @param mapper the function used to map the program
   * @param <F> the type of the new program
   * @return a new program representing the mapped computation
   */
  default <F> Program<S, F, T> mapError(Function<E, F> mapper) {
    return flatMapError(mapper.andThen(Program::failure));
  }

  /**
   * Maps the program to a new program using the provided error mapper function.
   *
   * @param mapper the function used to map the error
   * @return a new program representing the mapped computation
   */
  default Program<S, E, T> recover(Function<E, Program<S, E, T>> mapper) {
    return flatMapError(mapper);
  }

  default Program<S, E, T> recoverWith(Program<S, E, T> value) {
    return flatMapError(_ -> value);
  }

  default <R> Program<S, E, R> andThen(Program<S, E, R> next) {
    return flatMap(_ -> next);
  }

  default Program<S, E, T> peek(Function<T, Program<S, E, Void>> insert) {
    return flatMap(value -> insert.apply(value).andThen(success(value)));
  }

  default Program<S, E, T> peekError(Function<E, Program<S, E, Void>> insert) {
    return flatMapError(error -> insert.apply(error).andThen(failure(error)));
  }

  /**
   * Maps the program to a new program using the provided function.
   *
   * @param next the function used to map the program
   * @param <R> the type of the new program
   * @return a new program representing the mapped computation
   */
  default <R> Program<S, E, R> flatMap(Function<T, Program<S, E, R>> next) {
    return foldMap(Program::failure, next);
  }

  default <F> Program<S, F, T> flatMapError(Function<E, Program<S, F, T>> next) {
    return foldMap(next, Program::success);
  }

  default Program<S, E, T> catchAll(Function<Throwable, Program<S, E, T>> recover) {
    return new Catch<>(this, recover);
  }

  default <F, R> Program<S, F, R> foldMap(
      Function<E, Program<S, F, R>> onFailure,
      Function<T, Program<S, F, R>> onSuccess) {
    return new FoldMap<>(this, onFailure, onSuccess);
  }

  default Program<S, E, ElapsedTime<T>> timed() {
    return pipe(
        start(),
        start -> map(value -> end(start, value)));
  }

  default Program<S, E, T> retry(int retries) {
    return retry(retries, unit());
  }

  default Program<S, E, T> retry(int retries, Duration delay) {
    return retry(retries, sleep(delay));
  }

  default Program<S, E, T> retry(int retries, Program<S, E, Void> delay) {
    return recover(error -> {
      if (retries > 0) {
        return delay.andThen(retry(retries - 1, delay));
      }
      return failure(error);
    });
  }

  default Program<S, E, T> repeat(int times) {
    return repeat(times, unit());
  }

  default Program<S, E, T> repeat(int times, Duration delay) {
    return repeat(times, sleep(delay));
  }

  default Program<S, E, T> repeat(int times, Program<S, E, Void> delay) {
    return flatMap(value -> {
      if (times > 0) {
        return delay.andThen(repeat(times - 1, delay));
      }
      return success(value);
    });
  }

  default Program<S, E, Fiber<E, T>> fork(Executor executor) {
    return async((state, callback) -> {
      var future = CompletableFuture.supplyAsync(() -> eval(state), executor);
      callback.accept(Result.success(new Fiber<>(future)), null);
    });
  }

  default Program<S, E, T> timeout(Duration duration, Executor executor) {
    return either(sleep(duration, executor), this, executor)
      .flatMap(either -> either.fold(_ -> raise(TimeoutException::new), Program::success));
  }

  static <S, E, T> Program<S, E, T> delay(Duration duration, Supplier<T> supplier, Executor executor) {
    return Program.<S, E>sleep(duration, executor).flatMap(_ -> success(supplier.get()));
  }

  static <S, E> Program<S, E, Void> sleep(Duration duration) {
    return sleep(duration, ForkJoinPool.commonPool());
  }

  static <S, E> Program<S, E, Void> sleep(Duration duration, Executor executor) {
    return async((_, callback) -> {
      var delayed = CompletableFuture.delayedExecutor(duration.toMillis(), TimeUnit.MILLISECONDS, executor);
      var future = CompletableFuture.runAsync(() -> {}, delayed);
      future.whenCompleteAsync((_, _) -> callback.accept(Result.success(null), null));
    });
  }

  // start generated code

  static <S, E, T0, T1> Program<S, E, T1> pipe(
      Program<S, E, T0> p0,
      Function<T0, Program<S, E, T1>> p1) {
    return p0.flatMap(p1);
  }

  static <S, E, T0, T1, T2> Program<S, E, T2> pipe(
      Program<S, E, T0> p0,
      Function<T0, Program<S, E, T1>> p1,
      Function<T1, Program<S, E, T2>> p2) {
    return p0.flatMap(p1).flatMap(p2);
  }

  static <S, E, T0, T1, T2, T3> Program<S, E, T3> pipe(
      Program<S, E, T0> p0,
      Function<T0, Program<S, E, T1>> p1,
      Function<T1, Program<S, E, T2>> p2,
      Function<T2, Program<S, E, T3>> p3) {
    return p0.flatMap(p1).flatMap(p2).flatMap(p3);
  }

  static <S, E, T0, T1, T2, T3, T4> Program<S, E, T4> pipe(
      Program<S, E, T0> p0,
      Function<T0, Program<S, E, T1>> p1,
      Function<T1, Program<S, E, T2>> p2,
      Function<T2, Program<S, E, T3>> p3,
      Function<T3, Program<S, E, T4>> p4) {
    return p0.flatMap(p1).flatMap(p2).flatMap(p3).flatMap(p4);
  }

  static <S, E, T0, T1, T2, T3, T4, T5> Program<S, E, T5> pipe(
      Program<S, E, T0> p0,
      Function<T0, Program<S, E, T1>> p1,
      Function<T1, Program<S, E, T2>> p2,
      Function<T2, Program<S, E, T3>> p3,
      Function<T3, Program<S, E, T4>> p4,
      Function<T4, Program<S, E, T5>> p5) {
    return p0.flatMap(p1).flatMap(p2).flatMap(p3).flatMap(p4).flatMap(p5);
  }

  static <S, E, T0, T1, T2, T3, T4, T5, T6> Program<S, E, T6> pipe(
      Program<S, E, T0> p0,
      Function<T0, Program<S, E, T1>> p1,
      Function<T1, Program<S, E, T2>> p2,
      Function<T2, Program<S, E, T3>> p3,
      Function<T3, Program<S, E, T4>> p4,
      Function<T4, Program<S, E, T5>> p5,
      Function<T5, Program<S, E, T6>> p6) {
    return p0.flatMap(p1).flatMap(p2).flatMap(p3).flatMap(p4).flatMap(p5).flatMap(p6);
  }

  static <S, E, T0, T1, T2, T3, T4, T5, T6, T7> Program<S, E, T7> pipe(
      Program<S, E, T0> p0,
      Function<T0, Program<S, E, T1>> p1,
      Function<T1, Program<S, E, T2>> p2,
      Function<T2, Program<S, E, T3>> p3,
      Function<T3, Program<S, E, T4>> p4,
      Function<T4, Program<S, E, T5>> p5,
      Function<T5, Program<S, E, T6>> p6,
      Function<T6, Program<S, E, T7>> p7) {
    return p0.flatMap(p1).flatMap(p2).flatMap(p3).flatMap(p4).flatMap(p5).flatMap(p6).flatMap(p7);
  }

  static <S, E, T0, T1, T2, T3, T4, T5, T6, T7, T8> Program<S, E, T8> pipe(
      Program<S, E, T0> p0,
      Function<T0, Program<S, E, T1>> p1,
      Function<T1, Program<S, E, T2>> p2,
      Function<T2, Program<S, E, T3>> p3,
      Function<T3, Program<S, E, T4>> p4,
      Function<T4, Program<S, E, T5>> p5,
      Function<T5, Program<S, E, T6>> p6,
      Function<T6, Program<S, E, T7>> p7,
      Function<T7, Program<S, E, T8>> p8) {
    return p0.flatMap(p1).flatMap(p2).flatMap(p3).flatMap(p4).flatMap(p5).flatMap(p6).flatMap(p7).flatMap(p8);
  }

  static <S, E, T0, T1> Program<S, E, T1> chain(
      Program<S, E, T0> p0,
     Function<T0, T1> p1
    ) {
      return p0.map(p1);
  }

  static <S, E, T0, T1, T2> Program<S, E, T2> chain(
    Program<S, E, T0> p0,
     Function<T0, T1> p1,
     Function<T1, T2> p2
    ) {
      return p0.map(p1).map(p2);
  }

  static <S, E, T0, T1, T2, T3> Program<S, E, T3> chain(
    Program<S, E, T0> p0,
     Function<T0, T1> p1,
     Function<T1, T2> p2,
     Function<T2, T3> p3
    ) {
      return p0.map(p1).map(p2).map(p3);
  }

  static <S, E, T0, T1, T2, T3, T4> Program<S, E, T4> chain(
    Program<S, E, T0> p0,
     Function<T0, T1> p1,
     Function<T1, T2> p2,
     Function<T2, T3> p3,
     Function<T3, T4> p4
    ) {
      return p0.map(p1).map(p2).map(p3).map(p4);
  }

  static <S, E, T0, T1, T2, T3, T4, T5> Program<S, E, T5> chain(
    Program<S, E, T0> p0,
     Function<T0, T1> p1,
     Function<T1, T2> p2,
     Function<T2, T3> p3,
     Function<T3, T4> p4,
     Function<T4, T5> p5
    ) {
      return p0.map(p1).map(p2).map(p3).map(p4).map(p5);
  }

  static <S, E, T0, T1, T2, T3, T4, T5, T6> Program<S, E, T6> chain(
    Program<S, E, T0> p0,
     Function<T0, T1> p1,
     Function<T1, T2> p2,
     Function<T2, T3> p3,
     Function<T3, T4> p4,
     Function<T4, T5> p5,
     Function<T5, T6> p6
    ) {
      return p0.map(p1).map(p2).map(p3).map(p4).map(p5).map(p6);
  }

  static <S, E, T0, T1, T2, T3, T4, T5, T6, T7> Program<S, E, T7> chain(
    Program<S, E, T0> p0,
     Function<T0, T1> p1,
     Function<T1, T2> p2,
     Function<T2, T3> p3,
     Function<T3, T4> p4,
     Function<T4, T5> p5,
     Function<T5, T6> p6,
     Function<T6, T7> p7
    ) {
      return p0.map(p1).map(p2).map(p3).map(p4).map(p5).map(p6).map(p7);
  }

  static <S, E, T0, T1, T2, T3, T4, T5, T6, T7, T8> Program<S, E, T8> chain(
    Program<S, E, T0> p0,
     Function<T0, T1> p1,
     Function<T1, T2> p2,
     Function<T2, T3> p3,
     Function<T3, T4> p4,
     Function<T4, T5> p5,
     Function<T5, T6> p6,
     Function<T6, T7> p7,
     Function<T7, T8> p8
    ) {
      return p0.map(p1).map(p2).map(p3).map(p4).map(p5).map(p6).map(p7).map(p8);
  }
  static <S, E, T0, T1, R> Program<S, E, R> zip(
     Program<S, E, T0> p0,
     Program<S, E, T1> p1,
     Finisher2<T0, T1, R> finisher) {
      return async((state, callback) -> {
        try {
          callback.accept(Result.zip(p0.eval(state), p1.eval(state), finisher), null);
        } catch (RuntimeException e) {
          callback.accept(null, e);
        }
      });
  }

  static <S, E, T0, T1, T2, R> Program<S, E, R> zip(
     Program<S, E, T0> p0,
     Program<S, E, T1> p1,
     Program<S, E, T2> p2,
     Finisher3<T0, T1, T2, R> finisher) {
      return async((state, callback) -> {
        try {
          callback.accept(Result.zip(p0.eval(state), p1.eval(state), p2.eval(state), finisher), null);
        } catch (RuntimeException e) {
          callback.accept(null, e);
        }
      });
  }

  static <S, E, T0, T1, T2, T3, R> Program<S, E, R> zip(
     Program<S, E, T0> p0,
     Program<S, E, T1> p1,
     Program<S, E, T2> p2,
     Program<S, E, T3> p3,
     Finisher4<T0, T1, T2, T3, R> finisher) {
      return async((state, callback) -> {
        try {
          callback.accept(Result.zip(p0.eval(state), p1.eval(state), p2.eval(state), p3.eval(state), finisher), null);
        } catch (RuntimeException e) {
          callback.accept(null, e);
        }
      });
  }

  static <S, E, T0, T1, T2, T3, T4, R> Program<S, E, R> zip(
     Program<S, E, T0> p0,
     Program<S, E, T1> p1,
     Program<S, E, T2> p2,
     Program<S, E, T3> p3,
     Program<S, E, T4> p4,
     Finisher5<T0, T1, T2, T3, T4, R> finisher) {
      return async((state, callback) -> {
        try {
          callback.accept(Result.zip(p0.eval(state), p1.eval(state), p2.eval(state), p3.eval(state), p4.eval(state), finisher), null);
        } catch (RuntimeException e) {
          callback.accept(null, e);
        }
      });
  }

  static <S, E, T0, T1, T2, T3, T4, T5, R> Program<S, E, R> zip(
     Program<S, E, T0> p0,
     Program<S, E, T1> p1,
     Program<S, E, T2> p2,
     Program<S, E, T3> p3,
     Program<S, E, T4> p4,
     Program<S, E, T5> p5,
     Finisher6<T0, T1, T2, T3, T4, T5, R> finisher) {
      return async((state, callback) -> {
        try {
          callback.accept(Result.zip(p0.eval(state), p1.eval(state), p2.eval(state), p3.eval(state), p4.eval(state), p5.eval(state), finisher), null);
        } catch (RuntimeException e) {
          callback.accept(null, e);
        }
      });
  }

  static <S, E, T0, T1, T2, T3, T4, T5, T6, R> Program<S, E, R> zip(
     Program<S, E, T0> p0,
     Program<S, E, T1> p1,
     Program<S, E, T2> p2,
     Program<S, E, T3> p3,
     Program<S, E, T4> p4,
     Program<S, E, T5> p5,
     Program<S, E, T6> p6,
     Finisher7<T0, T1, T2, T3, T4, T5, T6, R> finisher) {
      return async((state, callback) -> {
        try {
          callback.accept(Result.zip(p0.eval(state), p1.eval(state), p2.eval(state), p3.eval(state), p4.eval(state), p5.eval(state), p6.eval(state), finisher), null);
        } catch (RuntimeException e) {
          callback.accept(null, e);
        }
      });
  }

  static <S, E, T0, T1, T2, T3, T4, T5, T6, T7, R> Program<S, E, R> zip(
     Program<S, E, T0> p0,
     Program<S, E, T1> p1,
     Program<S, E, T2> p2,
     Program<S, E, T3> p3,
     Program<S, E, T4> p4,
     Program<S, E, T5> p5,
     Program<S, E, T6> p6,
     Program<S, E, T7> p7,
     Finisher8<T0, T1, T2, T3, T4, T5, T6, T7, R> finisher) {
      return async((state, callback) -> {
        try {
          callback.accept(Result.zip(p0.eval(state), p1.eval(state), p2.eval(state), p3.eval(state), p4.eval(state), p5.eval(state), p6.eval(state), p7.eval(state), finisher), null);
        } catch (RuntimeException e) {
          callback.accept(null, e);
        }
      });
  }

  static <S, E, T0, T1, T2, T3, T4, T5, T6, T7, T8, R> Program<S, E, R> zip(
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
      return async((state, callback) -> {
        try {
          callback.accept(Result.zip(p0.eval(state), p1.eval(state), p2.eval(state), p3.eval(state), p4.eval(state), p5.eval(state), p6.eval(state), p7.eval(state), p8.eval(state), finisher), null);
        } catch (RuntimeException e) {
          callback.accept(null, e);
        }
      });
  }

  static <S, E, T0, T1, R> Program<S, E, R> parZip(
     Program<S, E, T0> p0,
     Program<S, E, T1> p1,
     Finisher2<T0, T1, R> finisher,
    Executor executor) {
      return zip(
         p0.fork(executor),
         p1.fork(executor),
         (f0, f1) -> Fiber.zip(f0, f1, finisher))
        .flatMap(Fiber::join);
  }

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
         (f0, f1, f2) -> Fiber.zip(f0, f1, f2, finisher))
        .flatMap(Fiber::join);
  }

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
         (f0, f1, f2, f3) -> Fiber.zip(f0, f1, f2, f3, finisher))
        .flatMap(Fiber::join);
  }

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
         (f0, f1, f2, f3, f4) -> Fiber.zip(f0, f1, f2, f3, f4, finisher))
        .flatMap(Fiber::join);
  }

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
         (f0, f1, f2, f3, f4, f5) -> Fiber.zip(f0, f1, f2, f3, f4, f5, finisher))
        .flatMap(Fiber::join);
  }

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
         (f0, f1, f2, f3, f4, f5, f6) -> Fiber.zip(f0, f1, f2, f3, f4, f5, f6, finisher))
        .flatMap(Fiber::join);
  }

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
         (f0, f1, f2, f3, f4, f5, f6, f7) -> Fiber.zip(f0, f1, f2, f3, f4, f5, f6, f7, finisher))
        .flatMap(Fiber::join);
  }

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
         (f0, f1, f2, f3, f4, f5, f6, f7, f8) -> Fiber.zip(f0, f1, f2, f3, f4, f5, f6, f7, f8, finisher))
        .flatMap(Fiber::join);
  }

  // end generated code

  static <S, E, T, U> Program<S, E, Either<T, U>> either(
      Program<S, E, T> p1,
      Program<S, E, U> p2,
      Executor executor) {
    return zip(p1.fork(executor), p2.fork(executor), Fiber::either).flatMap(Fiber::join);
  }

  record ElapsedTime<T>(Duration duration, T value) {}

  private static <S, E> Program<S, E, Long> start() {
    return supply(System::nanoTime);
  }

  private static <T> ElapsedTime<T> end(Long start, T value) {
    return new ElapsedTime<>(Duration.ofNanos(System.nanoTime() - start), value);
  }

  // XXX: https://www.baeldung.com/java-sneaky-throws
  @SuppressWarnings("unchecked")
  private static <X extends Throwable, R> R sneakyThrow(Throwable t) throws X {
    throw (X) t;
  }
}
