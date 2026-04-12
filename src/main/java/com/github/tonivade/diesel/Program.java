/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static java.util.function.Function.identity;

import java.lang.reflect.UndeclaredThrowableException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

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

  Program<?, ?, Void> UNIT = from(Result.UNIT);

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
      BiConsumer<S, ? extends BiConsumer<? super Result<E, T>, ? super Throwable>> callback) implements Program<S, E, T> {
  }

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
   * Creates a new program that represents an asynchronous computation.
   *
   * @param future the CompletableFuture representing the asynchronous computation
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   * @return a new program representing an asynchronous computation
   */
  static <S, E, T> Program<S, E, T> from(CompletableFuture<? extends Result<E, T>> future) {
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
    return new Pure<>(Result.success(value));
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
    return new Pure<>(Result.failure(error));
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
   * Creates a new program that represents a computation that attempts to execute the given supplier and maps any exceptions to errors using the provided function.
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
   * @param supplier the supplier of the program to be executed
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   * @return a new program representing a computation that suspends execution
   */
  static <S, E, T> Program<S, E, T> suspend(Supplier<Program<S, E, T>> supplier) {
    return pipe(unit(), _ -> supplier.get());
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
  static <S, E, T> Program<S, E, T> async(
      BiConsumer<S, ? extends BiConsumer<? super Result<E, T>, ? super Throwable>> callback) {
    return new Async<>(callback);
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
   * Creates a new program that represents an either of two programs executed in parallel using the provided executor.
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
      Program<S, E, T> p1,
      Program<S, E, U> p2,
      Executor executor) {
    return zip(p1.fork(executor), p2.fork(executor), Fiber::either).flatMap(Fiber::join);
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
   * Evaluates the program using the provided state.
   *
   * @param state the state used to evaluate the program
   * @return the result of the evaluation
   */
  @SuppressWarnings("unchecked")
  default Result<E, T> eval(@Nullable S state) {
    Program<S, ?, ?> current = this;
    Deque<Function<Object, Program<S, ?, ?>>> failureStack = new ArrayDeque<>();
    Deque<Function<Object, Program<S, ?, ?>>> successStack = new ArrayDeque<>();
    Deque<Function<Throwable, Program<S, ?, ?>>> catchStack = new ArrayDeque<>();

    while (true) {
      try {
        if (current instanceof Pure(var result)) {
          if (successStack.isEmpty() && failureStack.isEmpty()) {
            return (Result<E, T>) result;
          }
          current = result.fold(failureStack.pop(), successStack.pop());
        } else if (current instanceof Effect(var mapper)) {
          current = mapper.apply(state);
        } else if (current instanceof Async(var callback)) {
          var future = new CompletableFuture<Result<?, ?>>();
          ((BiConsumer<S, BiConsumer<Result<?, ?>, Throwable>>) callback).accept(state, (result, error) -> {
            if (error != null) {
              future.completeExceptionally(error);
            } else {
              future.complete(result);
            }
          });
          current = from(future.join());
        } else if (current instanceof FoldMap(var source, var onFailure, var onSuccess)) {
          successStack.push((Function<Object, Program<S, ? ,?>>) onSuccess);
          failureStack.push((Function<Object, Program<S, ?, ?>>) onFailure);
          current = source;
        } else if (current instanceof Raise(var throwable)) {
          return sneakyThrow(throwable.get());
        } else if (current instanceof Catch(var source, var recover)) {
          catchStack.push((Function<Throwable, Program<S, ?, ?>>) recover);
          current = source;
        }
      } catch (Throwable e) {
        if (catchStack.isEmpty()) {
          return sneakyThrow(e);
        }
        current = catchStack.pop().apply(e);
      }
    }
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
   * Measures the time taken to execute the program and returns the elapsed time along with the result.
   *
   * @return a new program representing the computation with elapsed time measurement
   */
  default Program<S, E, ElapsedTime<T>> timed() {
    return pipe(
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
    return retry(retries, sleep(delay, ForkJoinPool.commonPool()));
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
    return repeat(times, sleep(delay, ForkJoinPool.commonPool()));
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
   * Forks the program to be executed asynchronously using the provided executor.
   *
   * @param executor the executor used to execute the program asynchronously
   * @return a new program representing the forked computation
   */
  default Program<S, E, Fiber<E, T>> fork(Executor executor) {
    return async((state, callback) -> {
      var future = CompletableFuture.supplyAsync(() -> eval(state), executor);
      callback.accept(Result.success(new Fiber<>(future)), null);
    });
  }

  /**
   * Adds a timeout to the program using the provided duration and executor.
   *
   * @param duration the duration of the timeout
   * @param executor the executor used to execute the timeout
   * @return a new program representing the computation with timeout
   */
  default Program<S, E, T> timeout(Duration duration, Executor executor) {
    return either(sleep(duration, executor), this, executor)
      .flatMap(either -> either.fold(_ -> raise(TimeoutException::new), Program::success));
  }

  /**
   * Delays the execution of the program using the provided duration, supplier, and executor.
   *
   * @param duration the duration of the delay
   * @param supplier the supplier of the value to be returned after the delay
   * @param executor the executor used to execute the delay
   * @return a new program representing the delayed computation
   */
  static <S, E, T> Program<S, E, T> delay(Duration duration, Supplier<T> supplier, Executor executor) {
    return delay(duration, supply(supplier), executor);
  }

  /**
   * Delays the execution of the program using the provided duration, next program, and executor.
   *
   * @param duration the duration of the delay
   * @param andThen the next program to be executed after the delay
   * @param executor the executor used to execute the delay
   * @return a new program representing the delayed computation
   */
  static <S, E, T> Program<S, E, T> delay(Duration duration, Program<S, E, T> andThen, Executor executor) {
    return pipe(
        sleep(duration, executor),
        _ -> andThen
      );
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
    return async((_, callback) -> {
      var delayed = CompletableFuture.delayedExecutor(duration.toMillis(), TimeUnit.MILLISECONDS, executor);
      var future = CompletableFuture.runAsync(() -> {}, delayed);
      future.whenCompleteAsync((_, _) -> callback.accept(Result.success(null), null));
    });
  }

  /**
   * Chains all the given programs sequentially.
   *
   * @param programs the programs to be chained
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @return a new program representing the chained computation
   */
  @SafeVarargs
  static <S, E> Program<S, E, Void> chainAll(Program<S, E, ?>... programs) {
    if (programs.length == 0) {
      return unit();
    }
    return programs[0].andThen(chainAll(Arrays.copyOfRange(programs, 1, programs.length)));
  }

  /**
   * Executes all the given programs in parallel using the provided executor.
   *
   * @param executor the executor used to execute the programs in parallel
   * @param programs the programs to be executed
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @return a new program representing the parallel computation
   */
  @SafeVarargs
  static <S, E> Program<S, E, Void> parAll(Executor executor, Program<S, E, ?>... programs) {
    if (programs.length == 0) {
      return unit();
    }

    var forked = forkAll(executor, programs);

    return Program.<S, E, Fiber<E, Void>>async(
        (state, callback) -> {
          try {
            var result = evalAll(state, forked).map(Fiber::all);
            callback.accept(result, null);
          } catch (RuntimeException e) {
            callback.accept(null, e);
          }
        })
        .flatMap(Fiber::join);
  }

  /**
   * Sequences a collection of programs into a single program containing a collection of success values.
   *
   * @param programs the programs to be forked
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   * @return a collection of forked programs
   */
  @SafeVarargs
  static <S, E, T> Program<S, E, Collection<T>> sequence(Program<S, E, T>... programs) {
    return sequence(List.of(programs));
  }

  /**
   * Executes a collection of programs in parallel using the provided executor
   * and sequences their results into a single program containing a collection of success values.
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
    if (programs.length == 0) {
      return success(List.of());
    }

    var forked = forkAll(executor, programs);

    return Program.<S, E, Fiber<E, Collection<T>>>async(
        (state, callback) -> {
          try {
            var result = evalAll(state, forked).map(Fiber::sequence);
            callback.accept(result, null);
          } catch (RuntimeException e) {
            callback.accept(null, e);
          }
        })
        .flatMap(Fiber::join);
  }

  /**
   * Sequences a collection of programs into a single program containing a collection of success values.
   *
   * @param programs the programs to be forked
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   * @return a collection of forked programs
   */
  static <S, E, T> Program<S, E, Collection<T>> sequence(Collection<Program<S, E, T>> programs) {
    return traverse(identity(), programs);
  }

  /**
   * Traverses a collection of values, applying the provided function to each value
   * and sequencing the results into a single program containing a collection of success values.
   *
   * @param function the function used to map each value to a program
   * @param values the values to be traversed
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the input values
   * @param <R> the type of the result
   * @return a new program representing the traversed computation with sequenced results
   */
  @SafeVarargs
  static <S, E, T, R> Program<S, E, Collection<R>> traverse(Function<? super T, ? extends Program<S, E, R>> function, T... values) {
    return traverse(function, List.of(values));
  }

  /**
   * Traverses a collection of values, applying the provided function to each value
   * and sequencing the results into a single program containing a collection of success values.
   *
   * @param function the function used to map each value to a program
   * @param values the values to be traversed
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the input values
   * @param <R> the type of the result
   * @return a new program representing the traversed computation with sequenced results
   */
  static <S, E, T, R> Program<S, E, Collection<R>> traverse(Function<? super T, ? extends Program<S, E, R>> function, Collection<T> values) {
    Program<S, E, Collection<R>> initial = success(new ArrayList<>());
    return values.stream().reduce(
        initial,
        (acc, s) -> append(acc, function.apply(s)),
        (_, _) -> {
          throw new UnsupportedOperationException("Parallel stream not supported");
        });
  }

  private static <S, E, R> Program<S, E, Collection<R>> append(Program<S, E, Collection<R>> acc, Program<S, E, R> value) {
    return zip(acc, value, Program::append);
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
    return traverse(v -> v.apply(value), validators)
        .foldMap(_ -> success(value), result -> Validation.combine(result).fold(() -> success(value), Program::failure));
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
  static <S, E, F, T> Program<S, F, T> recover(Program<S, E, T> program, Function<? super E, ? extends Program<S, F, T>> recover) {
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
  static <S, E, T, R> Function<T, Program<S, E, R>> branch(Predicate<? super T> condition, Supplier<? extends Program<S, E, R>> onTrue, Supplier<? extends Program<S, E, R>> otherwise) {
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
  static <S, E, T> Function<Boolean, Program<S, E, T>> branch(Supplier<? extends Program<S, E, T>> onTrue, Supplier<? extends Program<S, E, T>> otherwise) {
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
    return acquire.flatMap(resource ->
        use.apply(resource).flatMap(result ->
            release.apply(resource).andThen(success(result))
        ).flatMapError(e -> release.apply(resource).andThen(failure(e))
        )
    );
  }

  // start generated code

  static <S, E, T0, T1> Program<S, E, T1> pipe(
      Program<S, E, T0> p0,
      Function<? super T0, ? extends Program<S, E, T1>> p1) {
    return p0.flatMap(p1);
  }

  static <S, E, T0, T1, T2> Program<S, E, T2> pipe(
      Program<S, E, T0> p0,
      Function<? super T0, ? extends Program<S, E, T1>> p1,
      Function<? super T1, ? extends Program<S, E, T2>> p2) {
    return p0.flatMap(p1).flatMap(p2);
  }

  static <S, E, T0, T1, T2, T3> Program<S, E, T3> pipe(
      Program<S, E, T0> p0,
      Function<? super T0, ? extends Program<S, E, T1>> p1,
      Function<? super T1, ? extends Program<S, E, T2>> p2,
      Function<? super T2, ? extends Program<S, E, T3>> p3) {
    return p0.flatMap(p1).flatMap(p2).flatMap(p3);
  }

  static <S, E, T0, T1, T2, T3, T4> Program<S, E, T4> pipe(
      Program<S, E, T0> p0,
      Function<? super T0, ? extends Program<S, E, T1>> p1,
      Function<? super T1, ? extends Program<S, E, T2>> p2,
      Function<? super T2, ? extends Program<S, E, T3>> p3,
      Function<? super T3, ? extends Program<S, E, T4>> p4) {
    return p0.flatMap(p1).flatMap(p2).flatMap(p3).flatMap(p4);
  }

  static <S, E, T0, T1, T2, T3, T4, T5> Program<S, E, T5> pipe(
      Program<S, E, T0> p0,
      Function<? super T0, ? extends Program<S, E, T1>> p1,
      Function<? super T1, ? extends Program<S, E, T2>> p2,
      Function<? super T2, ? extends Program<S, E, T3>> p3,
      Function<? super T3, ? extends Program<S, E, T4>> p4,
      Function<? super T4, ? extends Program<S, E, T5>> p5) {
    return p0.flatMap(p1).flatMap(p2).flatMap(p3).flatMap(p4).flatMap(p5);
  }

  static <S, E, T0, T1, T2, T3, T4, T5, T6> Program<S, E, T6> pipe(
      Program<S, E, T0> p0,
      Function<? super T0, ? extends Program<S, E, T1>> p1,
      Function<? super T1, ? extends Program<S, E, T2>> p2,
      Function<? super T2, ? extends Program<S, E, T3>> p3,
      Function<? super T3, ? extends Program<S, E, T4>> p4,
      Function<? super T4, ? extends Program<S, E, T5>> p5,
      Function<? super T5, ? extends Program<S, E, T6>> p6) {
    return p0.flatMap(p1).flatMap(p2).flatMap(p3).flatMap(p4).flatMap(p5).flatMap(p6);
  }

  static <S, E, T0, T1, T2, T3, T4, T5, T6, T7> Program<S, E, T7> pipe(
      Program<S, E, T0> p0,
      Function<? super T0, ? extends Program<S, E, T1>> p1,
      Function<? super T1, ? extends Program<S, E, T2>> p2,
      Function<? super T2, ? extends Program<S, E, T3>> p3,
      Function<? super T3, ? extends Program<S, E, T4>> p4,
      Function<? super T4, ? extends Program<S, E, T5>> p5,
      Function<? super T5, ? extends Program<S, E, T6>> p6,
      Function<? super T6, ? extends Program<S, E, T7>> p7) {
    return p0.flatMap(p1).flatMap(p2).flatMap(p3).flatMap(p4).flatMap(p5).flatMap(p6).flatMap(p7);
  }

  static <S, E, T0, T1, T2, T3, T4, T5, T6, T7, T8> Program<S, E, T8> pipe(
      Program<S, E, T0> p0,
      Function<? super T0, ? extends Program<S, E, T1>> p1,
      Function<? super T1, ? extends Program<S, E, T2>> p2,
      Function<? super T2, ? extends Program<S, E, T3>> p3,
      Function<? super T3, ? extends Program<S, E, T4>> p4,
      Function<? super T4, ? extends Program<S, E, T5>> p5,
      Function<? super T5, ? extends Program<S, E, T6>> p6,
      Function<? super T6, ? extends Program<S, E, T7>> p7,
      Function<? super T7, ? extends Program<S, E, T8>> p8) {
    return p0.flatMap(p1).flatMap(p2).flatMap(p3).flatMap(p4).flatMap(p5).flatMap(p6).flatMap(p7).flatMap(p8);
  }

  static <S, E, T0, T1> Program<S, E, T1> chain(
      Program<S, E, T0> p0,
      Function<? super T0, ? extends T1> p1) {
    return p0.map(p1);
  }

  static <S, E, T0, T1, T2> Program<S, E, T2> chain(
      Program<S, E, T0> p0,
      Function<? super T0, ? extends T1> p1,
      Function<? super T1, ? extends T2> p2) {
    return p0.map(p1).map(p2);
  }

  static <S, E, T0, T1, T2, T3> Program<S, E, T3> chain(
      Program<S, E, T0> p0,
      Function<? super T0, ? extends T1> p1,
      Function<? super T1, ? extends T2> p2,
      Function<? super T2, ? extends T3> p3) {
    return p0.map(p1).map(p2).map(p3);
  }

  static <S, E, T0, T1, T2, T3, T4> Program<S, E, T4> chain(
      Program<S, E, T0> p0,
      Function<? super T0, ? extends T1> p1,
      Function<? super T1, ? extends T2> p2,
      Function<? super T2, ? extends T3> p3,
      Function<? super T3, ? extends T4> p4) {
    return p0.map(p1).map(p2).map(p3).map(p4);
  }

  static <S, E, T0, T1, T2, T3, T4, T5> Program<S, E, T5> chain(
      Program<S, E, T0> p0,
      Function<? super T0, ? extends T1> p1,
      Function<? super T1, ? extends T2> p2,
      Function<? super T2, ? extends T3> p3,
      Function<? super T3, ? extends T4> p4,
      Function<? super T4, ? extends T5> p5) {
    return p0.map(p1).map(p2).map(p3).map(p4).map(p5);
  }

  static <S, E, T0, T1, T2, T3, T4, T5, T6> Program<S, E, T6> chain(
      Program<S, E, T0> p0,
      Function<? super T0, ? extends T1> p1,
      Function<? super T1, ? extends T2> p2,
      Function<? super T2, ? extends T3> p3,
      Function<? super T3, ? extends T4> p4,
      Function<? super T4, ? extends T5> p5,
      Function<? super T5, ? extends T6> p6) {
    return p0.map(p1).map(p2).map(p3).map(p4).map(p5).map(p6);
  }

  static <S, E, T0, T1, T2, T3, T4, T5, T6, T7> Program<S, E, T7> chain(
      Program<S, E, T0> p0,
      Function<? super T0, ? extends T1> p1,
      Function<? super T1, ? extends T2> p2,
      Function<? super T2, ? extends T3> p3,
      Function<? super T3, ? extends T4> p4,
      Function<? super T4, ? extends T5> p5,
      Function<? super T5, ? extends T6> p6,
      Function<? super T6, ? extends T7> p7) {
    return p0.map(p1).map(p2).map(p3).map(p4).map(p5).map(p6).map(p7);
  }

  static <S, E, T0, T1, T2, T3, T4, T5, T6, T7, T8> Program<S, E, T8> chain(
      Program<S, E, T0> p0,
      Function<? super T0, ? extends T1> p1,
      Function<? super T1, ? extends T2> p2,
      Function<? super T2, ? extends T3> p3,
      Function<? super T3, ? extends T4> p4,
      Function<? super T4, ? extends T5> p5,
      Function<? super T5, ? extends T6> p6,
      Function<? super T6, ? extends T7> p7,
      Function<? super T7, ? extends T8> p8) {
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

  record ElapsedTime<T>(Duration duration, T value) {}

  private static <S, E> Program<S, E, Long> start() {
    return supply(System::nanoTime);
  }

  private static <T> ElapsedTime<T> end(long start, T value) {
    return new ElapsedTime<>(Duration.ofNanos(System.nanoTime() - start), value);
  }

  @SafeVarargs
  private static <S, E, T> Collection<Program<S, E, Fiber<E, T>>> forkAll(
      Executor executor, Program<S, E, ? extends T>... programs) {
    return Stream.of(programs)
        .map(Program::<S, E, T>narrow)
        .map(p -> p.fork(executor))
        .toList();
  }

  @SuppressWarnings("unchecked")
  private static <S, E, T> Program<S, E, T> narrow(Program<S, E, ? extends T> program) {
    return (Program<S, E, T>) program;
  }

  private static <T> Collection<T> append(Collection<T> list, T value) {
    list.add(value);
    return list;
  }

  // XXX: https://www.baeldung.com/java-sneaky-throws
  @SuppressWarnings("unchecked")
  private static <X extends Throwable, R> R sneakyThrow(Throwable t) throws X {
    throw (X) t;
  }
}
