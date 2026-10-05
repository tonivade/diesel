/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import com.github.tonivade.purefun.Kind;

import java.lang.reflect.UndeclaredThrowableException;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

/**
 *
 *
 * A {@code Program} represents a computation that can be executed in a specific context.
 * It is a functional programming construct that allows for the composition of computations
 * and error handling.
 *
 * @param <S> the type of the state
 * @param <E> the type of the error
 * @param <T> the type of the result
 * @see Concurrent
 * @see Combine
 */
public sealed interface Program<S, E, T> extends Kind<Program<S, E, ?>, T> {

  /**
   * A program that completes successfully with no meaningful value.
   */
  Program<?, ?, Void> UNIT = from(Result.UNIT);

  /**
   * Converts a higher-kinded {@link Kind} value back into a {@code Program}.
   *
   * @param value the value to be converted
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   * @return the same value as a {@code Program}
   */
  @SuppressWarnings("unchecked")
  static <S, E, T> Program<S, E, T> toProgram(Kind<Program<S, E, ?>, ? extends T> value) {
    return (Program<S, E, T>) value;
  }

  /**
   * Returns a program that represents a computation that yields no result.
   *
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @return a program representing a computation that yields no result
   */
  @SuppressWarnings("unchecked")
  static <S, E> Program<S, E, Void> unit() {
    return (Program<S, E, Void>) UNIT;
  }

  /**
   * Represents a computation that yields a pure result.
   *
   * @param result the result of the computation
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   */
  record Pure<S, E, T>(Result<E, T> result) implements Program<S, E, T> {}

  /**
   * Represents a computation that catches exceptions within the program.
   *
   * @param current the current program
   * @param recover the function used to recover from exceptions
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   */
  record Catch<S, E, T>(
      Program<S, E, T> current,
      Function<? super Throwable, ? extends Program<S, E, T>> recover) implements Program<S, E, T> {}

  /**
   * Represents a computation that raises an exception.
   *
   * @param throwable the exception to be raised
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   */
  record Raise<S, E, T>(Supplier<? extends Throwable> throwable) implements Program<S, E, T> {}

  /**
   * Represents a computation that folds over the result of the program.
   *
   * @param current the current program
   * @param onFailure the function used to map the program in case of failure
   * @param onSuccess the function used to map the program in case of success
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <F> the type of the new error
   * @param <T> the type of the result
   * @param <R> the type of the new result
   */
  record FoldMap<S, E, F, T, R>(
      Program<S, E, T> current,
      Function<? super E, ? extends Program<S, F, R>> onFailure,
          Function<? super T, ? extends Program<S, F, R>> onSuccess) implements Program<S, F, R> {}

  /**
   * Represents an asynchronous computation within the program.
   *
   * @param callback the callback to be executed asynchronously
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   */
  record Async<S, E, T>(
      BiConsumer<? super S, ? super CompletableFuture<Result<E, T>>> callback) implements Program<S, E, T> {
  }

  /**
   * Represents a computation that forks the execution of the program using the provided executor.
   *
   * @param current the current program
   * @param executor the executor used to execute the program in parallel
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   */
  record Forked<S, E, T>(Program<S, E, T> current, Executor executor) implements Program<S, E, CompletableFuture<Result<E, T>>> {}

  /**
   * Represents an effectful computation that accesses a domain-specific language (DSL) using the provided function.
   *
   * @param mapper the function used to access the DSL computation
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   */
  record Effect<S, E, T>(Function<? super S, ? extends Program<S, E, T>> mapper) implements Program<S, E, T> {}

  /**
   * Represents a new program that describes a computation that suspends its execution.
   *
   * @param supplier the supplier of the program to be executed
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   */
  record Suspend<S, E, T>(Supplier<? extends Program<S, E, T>> supplier) implements Program<S, E, T> {}

  /**
   * Represents a computation that runs a finalizer after the program, whether it succeeds, fails,
   * throws an exception or is cancelled. The finalizer can't be cancelled.
   *
   * @param current the current program
   * @param finalizer the program to be executed as a finalizer
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   */
  record Ensuring<S, E, T>(Program<S, E, T> current, Program<S, E, ?> finalizer) implements Program<S, E, T> {}

  /**
   * Represents a memoized computation that caches the result of the program.
   *
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   */
  final class Memoized<S, E, T> implements Program<S, E, T> {

    private final Program<S, E, T> current;
    private final AtomicReference<Result<E, T>> cache = new AtomicReference<>();

    /**
     * Creates a memoized program that caches the result of the given program.
     *
     * @param current the program whose result will be cached
     */
    public Memoized(Program<S, E, T> current) {
      this.current = current;
    }

    Program<S, E, T> current() {
      return current;
    }

    /**
     * Returns the cached result of the computation if it is available, or {@code null} if the computation has not been executed yet.
     *
     * @return the cached result or {@code null} if not available
     */
    @Nullable
    public Result<E, T> get() {
      return cache.get();
    }

    /**
     * Sets the result of the computation in the cache if it is not already set.
     *
     * @param result the result to be set in the cache
     */
    public void set(Result<E, T> result) {
      cache.compareAndSet(null, result);
    }
  }

  /**
   * Represents a computation that can't be cancelled, a cancellation requested while it runs
   * takes effect after it finishes.
   *
   * @param current the current program
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   */
  record Uncancelable<S, E, T>(Program<S, E, T> current) implements Program<S, E, T> {}

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
    return new Pure<>(result);
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
   * Creates a new program thar represent a computation
   *
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param validation
   * @return a new program representing the computation
   */
  static <S, E> Program<S, E, Void> from(Validation<E> validation) {
    return validation.fold(Program::unit, Program::failure);
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
  static <S, E, T> Program<S, E, T> from(CompletableFuture<? extends Result<E, T>> future) {
    return async((_, callback) -> {
      // not async: completing the callback is cheap, and it avoids depending on the common pool
      // the returned future can be ignored because the action cannot throw
      var _ = future.whenComplete((result, error) -> {
        if (error != null) {
          callback.completeExceptionally(error);
        } else {
          callback.complete(result);
        }
      });
    });
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
    return from(Result.success(value));
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
  static <S, E, T> Program<S, E, T> failure(@Nullable E error) {
    return from(Result.failure(error));
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
    return mapper.andThen(Program::success);
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
    return mapper.andThen(Program::failure);
  }

  /**
   * Creates a program that returns a success with a valid validation result.
   *
   * @param <S> The state type of the Program
   * @param <E> The error type for validation failures
   * @return A Program representing a valid validation result
   */
  static <S, E> Program<S, Void, Validation<E>> valid() {
    return success(Validation.valid());
  }

  /**
   * Creates a program that returns a success with an invalid validation result.
   *
   * @param <S> The state type of the Program
   * @param <E> The error type for validation failures
   * @param error The error associated with the invalid validation
   * @return A Program representing an invalid validation result
   */
  static <S, E> Program<S, Void, Validation<E>> invalid(E error) {
    return success(Validation.invalid(error));
  }

  /**
   * Creates a new program that represents a computation that attempts to execute the given supplier.
   *
   * @param supplier the supplier of the value
   * @param <S> the type of the state
   * @param <T> the type of the result
   * @return a new program representing a computation that attempts to execute the supplier
   */
  static <S, T> Program<S, Throwable, T> attempt(Supplier<? extends T> supplier) {
    return suspend(() -> from(Result.attempt(supplier)));
  }

  /**
   * Creates a new program that represents a computation that attempts to execute the given supplier and maps any
   * exceptions to errors using the provided function.
   *
   * @param supplier the supplier of the value
   * @param mapError the function used to map exceptions to errors
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   * @return a new program representing a computation that attempts to execute the supplier and maps exceptions to errors
   */
  static <S, E, T> Program<S, E, T> attempt(
      Supplier<? extends T> supplier, Function<? super Throwable, ? extends E> mapError) {
    return recover(attempt(supplier), mapError.andThen(Program::failure));
  }

  /**
   * Creates a new program that represents a computation that raises an exception.
   *
   * @param throwable the exception to be raised
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   * @return a new program representing a computation that raises an exception
   */
  static <S, E, T> Program<S, E, T> raise(Supplier<? extends Throwable> throwable) {
    return new Raise<>(throwable);
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
    return suspend(() -> success(supplier.get()));
  }

  /**
   * Creates a new program that represents a computation that suspends execution.
   *
   * <p>The supplier is only called when the program is evaluated. Use it to write recursive programs:
   * the recursion then runs on the interpreter's stack instead of the Java call stack.
   *
   * @param supplier the supplier of the program to be executed
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   * @return a new program representing a computation that suspends execution
   */
  static <S, E, T> Program<S, E, T> suspend(Supplier<Program<S, E, T>> supplier) {
    return new Suspend<>(supplier);
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
    return supply(() -> {
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
  static <S, E, T> Program<S, E, T> async(BiConsumer<? super S, ? super CompletableFuture<Result<E, T>>> callback) {
    return new Async<>(callback);
  }

  /**
   * Creates a new program that represent a program that never ends.
   *
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   * @return a new program representing a program that never ends
   */
  static <S, E, T> Program<S, E, T> never() {
    return async((_, _) -> {});
  }

  /**
   * Creates a new program that represents an effectful computation that accesses a domain-specific language (DSL)
   * sing the provided function.
   *
   * @param mapper the function used to access the DSL computation
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   * @return a new program representing a DSL access
   */
  static <S, E, T> Program<S, E, T> effect(Function<? super S, ? extends T> mapper) {
    return effectR(mapper.andThen(Result::success));
  }

  /**
   * Creates a new program that represents an effectful computation that accesses a domain-specific language (DSL)
   * using the provided consumer.
   *
   * @param consumer the consumer used to access the DSL computation
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @return a new program representing a DSL access
   */
  static <S, E> Program<S, E, Void> inspect(Consumer<S> consumer) {
    return effectR(state -> {
      consumer.accept(state);
      return Result.unit();
    });
  }

  /**
   * Creates a new program that represents an effectful computation that accesses a domain-specific language (DSL)
   * using the provided function that returns a Result.
   *
   * @param mapper the function used to access the DSL computation
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   * @return a new program representing a DSL access
   */
  static <S, E, T> Program<S, E, T> effectR(Function<? super S, ? extends Result<E, T>> mapper) {
    return effectP(mapper.andThen(Program::from));
  }

  /**
   * Creates a new program that represents a domain-specific language (DSL) access.
   *
   * @param mapper the function used to access the DSL computation
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   * @return a new program representing a DSL access
   */
  static <S, E, T> Program<S, E, T> effectP(Function<? super S, ? extends Program<S, E, T>> mapper) {
    return new Effect<>(mapper);
  }

  /**
   * Evaluates all the given programs using the provided state.
   *
   * @param state the state used to evaluate the programs
   * @param programs the programs to be evaluated
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   * @return the result of evaluating all the programs
   */
  static <S, E, T> Result<E, Collection<T>> evalAll(@Nullable S state, Collection<Program<S, E, T>> programs) {
    return Result.traverse(programs, p -> p.eval(state));
  }

  /**
   * Evaluates the program without any state and returns the result, throwing an exception if the program fails.
   *
   * @return the result of evaluating thr program
   */
  default T evalOrElseThrow() {
    return evalOrElseThrow(e -> {
      if (e instanceof Throwable throwable) {
        return Interpreter.sneakyThrow(throwable);
      }
      return Interpreter.sneakyThrow(new NoSuchElementException());
    });
  }

  /**
   * Evaluates the program without any state and returns the result, throwing an exception mapped from the error if
   * the program fails.
   *
   * @param mapper the function used to map the error to an exception
   * @return the result of evaluating the program
   */
  default T evalOrElseThrow(Function<? super E, ? extends Throwable> mapper) {
    return eval().getOrElseThrow(mapper);
  }

  /**
   * Evaluates the program without any state and return the result.
   *
   * @return the result of the evaluation
   */
  default Result<E, T> eval() {
    return eval(null);
  }

  /**
   * Evaluates the program using the provided state.
   *
   * @param state the state used to evaluate the program
   * @return the result of the evaluation
   */
  default Result<E, T> eval(@Nullable S state) {
    return Interpreter.eval(this, state);
  }

  /**
   * Maps the program to a new program using the provided mapper function.
   *
   * @param mapper the function used to map the program
   * @param <R> the type of the new program
   * @return a new program representing the mapped computation
   */
  default <R> Program<S, E, R> map(Function<? super T, ? extends R> mapper) {
    return flatMap(mapper.andThen(Program::success));
  }

  /**
   * Maps the program to a new program using the provided mapper function for errors.
   *
   * @param mapper the function used to map the program
   * @param <F> the type of the new program
   * @return a new program representing the mapped computation
   */
  default <F> Program<S, F, T> mapError(Function<? super E, ? extends F> mapper) {
    return flatMapError(mapper.andThen(Program::failure));
  }

  /**
   * Maps the program to a new program using the provided mapper functions for success and failure.
   *
   * @param mapFailure the function used to map the program in case of failure
   * @param mapSuccess the function used to map the program in case of success
   * @param <F> the type of the new program in case of failure
   * @param <R> the type of the new program in case of success
   * @return a new program representing the mapped computation
   */
  default <R, F> Program<S, F, R> bimap(
      Function<? super E, ? extends F> mapFailure, Function<? super T, ? extends R> mapSuccess) {
    return foldMap(
        mapFailure.andThen(Program::failure),
        mapSuccess.andThen(Program::success));
  }

  /**
   * Chains the program with the next program using the provided next program.
   *
   * @param next the next program to be executed
   * @param <R> the type of the new program
   * @return a new program representing the chained computation
   */
  default <R> Program<S, E, R> andThen(Program<S, E, R> next) {
    return flatMap(_ -> next);
  }

  /**
   * Inserts a program to be executed with the current value without modifying it.
   *
   * @param insert the function used to insert the program
   * @return a new program representing the computation with the inserted program
   */
  default Program<S, E, T> peek(Consumer<T> insert) {
    return flatMap(value -> {
      insert.accept(value);
      return success(value);
    });
  }

  /**
   * Inserts a program to be executed with the current error without modifying it.
   *
   * @param insert the function used to insert the program
   * @return a new program representing the computation with the inserted program
   */
  default Program<S, E, T> peekError(Consumer<E> insert) {
    return flatMapError(error -> {
      insert.accept(error);
      return failure(error);
    });
  }

  /**
   * Maps the program to a new program using the provided function.
   *
   * @param next the function used to map the program
   * @param <R> the type of the new program
   * @return a new program representing the mapped computation
   */
  default <R> Program<S, E, R> flatMap(Function<? super T, ? extends Program<S, E, R>> next) {
    return foldMap(Program::failure, next);
  }

  /**
   * Maps the program to a new program using the provided function for errors.
   *
   * @param next the function used to map the program
   * @param <F> the type of the new program
   * @return a new program representing the mapped computation
   */
  default <F> Program<S, F, T> flatMapError(Function<? super E, ? extends Program<S, F, T>> next) {
    return foldMap(next, Program::success);
  }

  /**
   * Catches all exceptions thrown during the execution of the program and recovers using the provided function.
   *
   * @param recover the function used to recover from exceptions
   * @return a new program representing the computation with exception handling
   */
  default Program<S, E, T> catchAll(Function<? super Throwable, ? extends Program<S, E, T>> recover) {
    return new Catch<>(this, recover);
  }

  /**
   * Maps the program to a new program using the provided functions for success and failure.
   *
   * @param onFailure the function used to map the program in case of failure
   * @param onSuccess the function used to map the program in case of success
   * @param <F> the type of the new program in case of failure
   * @param <R> the type of the new program in case of success
   * @return a new program representing the mapped computation
   */
  default <F, R> Program<S, F, R> foldMap(
      Function<? super E, ? extends Program<S, F, R>> onFailure,
          Function<? super T, ? extends Program<S, F, R>> onSuccess) {
    return new FoldMap<>(this, onFailure, onSuccess);
  }

  /**
   * Maps the program to a new program using the provided functions for success and failure.
   *
   * @param onFailure the function used to map the program in case of failure
   * @param onSuccess the function used to map the program in case of success
   * @param <R> the type of the new program in case of success
   * @return a new program representing the mapped computation
   */
  default <R> Program<S, Void, R> fold(
      Function<? super E, ? extends R> onFailure,
      Function<? super T, ? extends R> onSuccess) {
    return foldMap(onFailure.andThen(Program::success), onSuccess.andThen(Program::success));
  }

  /**
   * Measures the time taken to execute the program and returns the elapsed time along with the result.
   *
   * @return a new program representing the computation with elapsed time measurement
   */
  default Program<S, E, ElapsedTime<T>> timed() {
    return Combine.pipe(
        start(),
        start -> map(value -> end(start, value))
        );
  }

  /**
   * Retries the program a specified number of times in case of failure.
   *
   * @param retries the number of retries
   * @return a new program representing the computation with retries
   */
  default Program<S, E, T> retry(int retries) {
    return retry(retries, unit());
  }

  /**
   * Retries the program a specified number of times with a delay in case of failure.
   *
   * @param retries the number of retries
   * @param delay the delay between retries
   * @return a new program representing the computation with retries and delay
   */
  default Program<S, E, T> retry(int retries, Duration delay) {
    return retry(retries, sleep(delay));
  }

  /**
   * Retries the program a specified number of times with a delay using the provided executor in case of failure.
   *
   * @param retries the number of retries
   * @param delay the delay between retries
   * @param executor the executor used to execute the delay
   * @return a new program representing the computation with retries and delay
   */
  default Program<S, E, T> retry(int retries, Duration delay, Executor executor) {
    return retry(retries, sleep(delay, executor));
  }

  /**
   * Retries the program a specified number of times with a delay program in case of failure.
   *
   * @param retries the number of retries
   * @param delay the delay program between retries
   * @return a new program representing the computation with retries and delay
   */
  default Program<S, E, T> retry(int retries, Program<S, E, Void> delay) {
    return flatMapError(error -> {
      if (retries > 0) {
        return delay.andThen(retry(retries - 1, delay));
      }
      return failure(error);
    });
  }

  /**
   * Repeats the program a specified number of times.
   *
   * @param times the number of times to repeat
   * @return a new program representing the computation repeated
   */
  default Program<S, E, T> repeat(int times) {
    return repeat(times, unit());
  }

  /**
   * Repeats the program a specified number of times with a delay.
   *
   * @param times the number of times to repeat
   * @param delay the delay between repetitions
   * @return a new program representing the computation repeated with delay
   */
  default Program<S, E, T> repeat(int times, Duration delay) {
    return repeat(times, sleep(delay));
  }

  /**
   * Repeats the program a specified number of times with a delay using the provided executor.
   *
   * @param times the number of times to repeat
   * @param delay the delay between repetitions
   * @param executor the executor used to execute the delay
   * @return a new program representing the computation repeated with delay
   */
  default Program<S, E, T> repeat(int times, Duration delay, Executor executor) {
    return repeat(times, sleep(delay, executor));
  }

  /**
   * Repeats the program a specified number of times with a delay program.
   *
   * @param times the number of times to repeat
   * @param delay the delay program between repetitions
   * @return a new program representing the computation repeated with delay
   */
  default Program<S, E, T> repeat(int times, Program<S, E, Void> delay) {
    return flatMap(value -> {
      if (times > 0) {
        return delay.andThen(repeat(times - 1, delay));
      }
      return success(value);
    });
  }

  /**
   * Forks the program to be executed asynchronously using the common fork-join pool.
   *
   * <p>Cancelling the returned future cancels the program: it stops at its next step, runs its
   * finalizers and then completes the future with a {@link java.util.concurrent.CancellationException}.
   * The forked program is also cancelled when the program that forked it is cancelled.
   *
   * @return a new program representing the forked computation
   */
  default Program<S, E, CompletableFuture<Result<E, T>>> fork() {
    return fork(ForkJoinPool.commonPool());
  }

  /**
   * Forks the program to be executed asynchronously using the provided executor.
   *
   * <p>Cancelling the returned future cancels the program: it stops at its next step, runs its
   * finalizers and then completes the future with a {@link java.util.concurrent.CancellationException}.
   * The forked program is also cancelled when the program that forked it is cancelled.
   *
   * @param executor the executor used to execute the program asynchronously
   * @return a new program representing the forked computation
   */
  default Program<S, E, CompletableFuture<Result<E, T>>> fork(Executor executor) {
    return new Forked<>(this, executor);
  }

  /**
   * Adds a timeout to the program using the provided duration and the common fork-join pool.
   *
   * <p>If the timeout expires first, the program is cancelled, it stops at its next step and runs
   * its finalizers, and then the resulting program fails with a {@link TimeoutException}.
   *
   * @param duration the duration of the timeout
   * @return a new program representing the computation with timeout
   */
  default Program<S, E, T> timeout(Duration duration) {
    return timeout(duration, ForkJoinPool.commonPool());
  }

  /**
   * Adds a timeout to the program using the provided duration and executor.
   *
   * <p>If the timeout expires first, the program is cancelled, it stops at its next step and runs
   * its finalizers, and then the resulting program fails with a {@link TimeoutException}.
   *
   * @param duration the duration of the timeout
   * @param executor the executor used to execute the timeout
   * @return a new program representing the computation with timeout
   */
  default Program<S, E, T> timeout(Duration duration, Executor executor) {
    return Concurrent.race(sleep(duration, executor), this, executor)
        .flatMap(either -> either.fold(_ -> raise(TimeoutException::new), Program::success));
  }

  /**
   * Ensures that the finalizer program is executed after the current program, regardless of success or failure.
   *
   * <p>The finalizer also runs when the program throws an exception or is cancelled, and it can't be
   * cancelled itself. If the finalizer fails, its failure replaces the result of the program.
   *
   * @param finalizer the program to be executed as a finalizer
   * @return a new program representing the computation with the finalizer
   */
  default Program<S, E, T> ensuring(Program<S, E, ?> finalizer) {
    return new Ensuring<>(this, finalizer);
  }

  /**
   * Creates a new program that memoizes the result of the current program, caching it for future evaluations.
   *
   * @return a new program representing the memoized computation
   */
  default Program<S, E, T> memoized() {
    if (this instanceof Memoized) {
      return this;
    }
    return new Memoized<>(this);
  }

  /**
   * Creates a function that maps a value to a memoized program using the provided function.
   *
   * <p>Recursive calls must be wrapped in {@link #suspend(Supplier)}, so they happen during evaluation
   * instead of while the program is being built. Calling the memoized function directly from inside
   * {@code function} updates the cache while it is already being updated, which can fail with
   * {@code IllegalStateException: Recursive update}. For recursive functions, prefer
   * {@link #memoizeRecursive(BiFunction)}, which does this for you.
   *
   * @param function the function used to map the value to a program
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the input value
   * @param <R> the type of the result
   * @return a function that maps a value to a memoized program
   */
  static <S, E, T, R> Function<T, Program<S, E, R>> memoize(Function<? super T, ? extends Program<S, E, R>> function) {
    final Map<T, Program<S, E, R>> cache = new ConcurrentHashMap<>();
    final var memoized = function.andThen(Program::memoized);
    return input -> cache.computeIfAbsent(input, memoized);
  }

  /**
   * Creates a function for recursive programs. The provided function receives the function itself
   * as its first argument, to be used for the recursive calls.
   *
   * <p>Recursive calls made through that argument are suspended, so they happen during evaluation
   * and use the interpreter's stack instead of the Java call stack.
   *
   * <pre>{@code
   * Function<Integer, Program<Void, Void, Integer>> fib = Program.recursive((self, n) -> n < 2
   *     ? Program.success(1)
   *     : Combine.zip(self.apply(n - 2), self.apply(n - 1), Integer::sum));
   * }</pre>
   *
   * @param function the function used to map the value to a program, receiving function itself
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the input value
   * @param <R> the type of the result
   * @return a function that maps a value to a program
   */
  static <S, E, T, R> Function<T, Program<S, E, R>> recursive(
      BiFunction<Function<T, Program<S, E, R>>, ? super T, ? extends Program<S, E, R>> function) {
    return new Function<>() {
      // recursive calls are suspended so they don't update the cache while it's being updated
      final Function<T, Program<S, E, R>> self = input -> suspend(() -> apply(input));

      @Override
      public Program<S, E, R> apply(T input) {
        return function.apply(self, input);
      }
    };
  }

  /**
   * Creates a memoized function for recursive programs. The provided function receives the memoized
   * function itself as its first argument, to be used for the recursive calls.
   *
   * <p>Recursive calls made through that argument are suspended, so they happen during evaluation
   * and use the interpreter's stack instead of the Java call stack.
   *
   * <pre>{@code
   * Function<Integer, Program<Void, Void, Integer>> fib = Program.memoizeRecursive((self, n) -> n < 2
   *     ? Program.success(1)
   *     : Combine.zip(self.apply(n - 2), self.apply(n - 1), Integer::sum));
   * }</pre>
   *
   * @param function the function used to map the value to a program, receiving the memoized function itself
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the input value
   * @param <R> the type of the result
   * @return a function that maps a value to a memoized program
   */
  static <S, E, T, R> Function<T, Program<S, E, R>> memoizeRecursive(
      BiFunction<Function<T, Program<S, E, R>>, ? super T, ? extends Program<S, E, R>> function) {
    final Map<T, Program<S, E, R>> cache = new ConcurrentHashMap<>();
    return new Function<>() {
      // recursive calls are suspended so they don't update the cache while it's being updated
      final Function<T, Program<S, E, R>> self = input -> suspend(() -> apply(input));

      @Override
      public Program<S, E, R> apply(T input) {
        return cache.computeIfAbsent(input, key -> function.apply(self, key).memoized());
      }
    };
  }

  /**
   * Delays the execution of the program using the provided duration and supplier, and the common fork-join pool.
   *
   * @param duration the duration of the delay
   * @param supplier the supplier of the value to be returned after the delay
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   * @return a new program representing the delayed computation
   */
  static <S, E, T> Program<S, E, T> delayed(Duration duration, Supplier<T> supplier) {
    return delayed(duration, supply(supplier));
  }

  /**
   * Delays the execution of the program using the provided duration and the common fork-join pool.
   *
   * @param duration the duration of the delay
   * @param program the next program to be executed after the delay
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   * @return a new program representing the delayed computation
   */
  static <S, E, T> Program<S, E, T> delayed(Duration duration, Program<S, E, T> program) {
    return delayed(duration, program, ForkJoinPool.commonPool());
  }

  /**
   * Delays the execution of the program using the provided duration, supplier, and executor.
   *
   * @param duration the duration of the delay
   * @param supplier the supplier of the value to be returned after the delay
   * @param executor the executor used to execute the delay
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   * @return a new program representing the delayed computation
   */
  static <S, E, T> Program<S, E, T> delayed(Duration duration, Supplier<T> supplier, Executor executor) {
    return delayed(duration, supply(supplier), executor);
  }

  /**
   * Delays the execution of the program using the provided duration, next program, and executor.
   *
   * @param duration the duration of the delay
   * @param program the next program to be executed after the delay
   * @param executor the executor used to execute the delay
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   * @return a new program representing the delayed computation
   */
  static <S, E, T> Program<S, E, T> delayed(Duration duration, Program<S, E, T> program, Executor executor) {
    return Combine.pipe(
        sleep(duration, executor),
        _ -> program
        );
  }

  /**
   * Creates a new program that represents a sleep for the given duration using the common fork-join pool.
   *
   * @param duration the duration of the sleep
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @return a new program representing a sleep
   */
  static <S, E> Program<S, E, Void> sleep(Duration duration) {
    return sleep(duration, ForkJoinPool.commonPool());
  }

  /**
   * Creates a new program that represents a sleep for the given duration using the provided executor.
   *
   * @param duration the duration of the sleep
   * @param executor the executor used to execute the sleep
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @return a new program representing a sleep
   */
  static <S, E> Program<S, E, Void> sleep(Duration duration, Executor executor) {
    var delayed = CompletableFuture.delayedExecutor(duration.toNanos(), TimeUnit.NANOSECONDS, executor);
    return async((_, callback) -> delayed.execute(() -> callback.complete(Result.unit())));
  }

  /**
   * Creates a function that validates a value using the provided validators.
   *
   * @param validators the validators used to validate the value
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the value to be validated
   * @return a function that validates a value
   */
  @SafeVarargs
  static <S, E, T> Function<T, Program<S, Collection<E>, T>> validator(Validator<S, E, T>... validators) {
    return value -> validate(value, validators);
  }

  /**
   * Validates a value using the provided validators.
   *
   * @param value the value to be validated
   * @param validators the validators used to validate the value
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the value to be validated
   * @return a new program representing the validation computation
   */
  @SafeVarargs
  static <S, E, T> Program<S, Collection<E>, T> validate(T value, Validator<S, E, T>... validators) {
    return Combine.traverse(v -> v.apply(value), validators)
        .foldMap(
            _ -> success(value),
            result -> Validation.combine(result).fold(() -> success(value), Program::failure));
  }

  /**
   * Catches all errors during the execution of the program and recovers using the provided function.
   *
   * @param program the program to be executed
   * @param recover the function used to recover from errors
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <F> the type of the new error
   * @param <T> the type of the result
   * @return a new program representing the computation with error handling
   */
  static <S, E, F, T> Program<S, F, T> recover(Program<S, E, T> program,
      Function<? super E, ? extends Program<S, F, T>> recover) {
    return program.flatMapError(recover);
  }

  /** Creates a function that branches the program based on a condition.
   *
   * @param condition the condition used to branch the program
   * @param onTrue the program to be executed if the condition is true
   * @param otherwise the program to be executed if the condition is false
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the input value
   * @param <R> the type of the result
   * @return a function that branches the program based on a condition
   */
  static <S, E, T, R> Function<T, Program<S, E, R>> branch(
      Predicate<? super T> condition, Supplier<? extends Program<S, E, R>> onTrue, Supplier<? extends Program<S, E, R>> otherwise) {
    return branch(onTrue, otherwise).compose(condition::test);
  }

  /** Creates a function that branches the program based on a boolean condition.
   *
   * @param onTrue the program to be executed if the condition is true
   * @param otherwise the program to be executed if the condition is false
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   * @return a function that branches the program based on a boolean condition
   */
  static <S, E, T> Function<Boolean, Program<S, E, T>> branch(
      Supplier<? extends Program<S, E, T>> onTrue, Supplier<? extends Program<S, E, T>> otherwise) {
    return result -> {
      if (result) {
        return onTrue.get();
      }
      return otherwise.get();
    };
  }

  /**
   * Creates a new program that represents a computation that acquires a resource, uses it, and then releases it.
   *
   * @param acquire the supplier of the resource to be acquired
   * @param use the function used to use the acquired resource
   * @param <S> the type of the state
   * @param <T> the type of the resource
   * @param <R> the type of the result
   * @return a new program representing a computation that acquires a resource, uses it, and then releases it
   */
  static <S, T extends AutoCloseable, R> Program<S, Throwable, R> bracket(
      Supplier<? extends T> acquire, Function<? super T, ? extends Program<S, Throwable, R>> use) {
    return bracket(
        attempt(acquire),
        use,
        resource -> task(() -> {
          try {
            resource.close();
          } catch (Exception e) {
            throw new UndeclaredThrowableException(e);
          }
        }));
  }

  /**
   * Creates a new program that represents a computation that acquires a resource, uses it, and then releases it
   *
   * <p>Acquiring and releasing the resource can't be cancelled, and once acquired the resource is
   * released whether {@code use} succeeds, fails, throws an exception or is cancelled.
   *
   * @param acquire the supplier of the resource to be acquired
   * @param use the function used to use the acquired resource
   * @param release the function used to release the acquired resource
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the resource
   * @param <R> the type of the result
   * @return a new program representing a computation that acquires a resource, uses it, and then releases it
   */
  static <S, E, T, R> Program<S, E, R> bracket(
      Program<S, E, T> acquire,
      Function<? super T, ? extends Program<S, E, R>> use,
      Function<? super T, ? extends Program<S, E, Void>> release) {
    // installing the finalizer never observes a cancellation, so there is no gap between
    // acquiring the resource and guaranteeing its release
    return Combine.pipe(
        new Uncancelable<>(acquire),
        resource -> use.apply(resource).ensuring(release.apply(resource))
        );
  }

  // start generated code

  // end generated code

  /**
   * The result of a timed program, with the time it took to execute and its result.
   *
   * @param duration the time it took to execute the program
   * @param value the result of the program
   * @param <T> the type of the result
   */
  record ElapsedTime<T>(Duration duration, T value) {}

  private static <S, E> Program<S, E, Long> start() {
    return supply(System::nanoTime);
  }

  private static <T> ElapsedTime<T> end(long start, T value) {
    return new ElapsedTime<>(Duration.ofNanos(System.nanoTime() - start), value);
  }
}
