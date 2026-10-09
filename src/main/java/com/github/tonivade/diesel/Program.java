/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Combine.pipe;
import static com.github.tonivade.diesel.Combine.traverse;
import static com.github.tonivade.diesel.Concurrent.race;

import com.github.tonivade.diesel.Frame.CatchFrame;
import com.github.tonivade.diesel.Frame.FinalizerFrame;
import com.github.tonivade.diesel.Frame.FoldFrame;
import com.github.tonivade.diesel.Frame.OnCancelFrame;
import com.github.tonivade.diesel.Frame.UnmaskFrame;
import com.github.tonivade.purefun.Kind;

import java.lang.reflect.UndeclaredThrowableException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
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
  record Access<S, E, T>(Function<? super S, ? extends Program<S, E, T>> mapper) implements Program<S, E, T> {}

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
   * Represents a computation that runs a finalizer only when the program is cancelled. The
   * finalizer can't be cancelled.
   *
   * @param current the current program
   * @param finalizer the program to be executed if the program is cancelled
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   */
  record OnCancel<S, E, T>(Program<S, E, T> current, Program<S, E, ?> finalizer) implements Program<S, E, T> {}

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
   * <p>If the program is cancelled while it waits, it stops waiting, but the asynchronous operation
   * keeps running. Use {@link #asyncCancelable(BiFunction)} to stop the operation too.
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
   * Creates a new program that represents an asynchronous computation that can be cancelled.
   *
   * <p>The register function starts the asynchronous operation, completes the future when it
   * finishes, and returns a canceler: a program that stops the operation. If the program is
   * cancelled while it waits for the operation, the canceler runs before the cancellation goes on,
   * and it can't be cancelled itself. It doesn't run when the operation completes, nor when the
   * program runs in an uncancelable region, which waits for the operation to finish instead.
   *
   * <p>Use it when cancelling the program has to stop the operation too, like a scheduled task or a
   * request to an external system. With {@link #async(BiConsumer)} the program stops waiting, but
   * the operation keeps running.
   *
   * <p>If {@code register} throws, the program fails with that exception and no canceler is
   * installed: if the operation was already started, {@code register} has to stop it before
   * throwing. {@code register} must not return {@code null}.
   *
   * @param register the function that starts the operation and returns the canceler
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   * @return a new program representing a cancelable asynchronous computation
   */
  static <S, E, T> Program<S, E, T> asyncCancelable(
      BiFunction<? super S, ? super CompletableFuture<Result<E, T>>, ? extends Program<S, E, Void>> register) {
    // effectP defers all this to evaluation time: the operation has to start when the program
    // runs, not when it's built, with the state of that evaluation, and once per evaluation, so
    // each one has its own future and canceler. suspend would defer it too, but without the state.
    // The canceler is installed in the step right after the operation starts: installing an
    // onCancel never stops for a cancellation, so a started operation always has its canceler
    return accessProgram(state -> {
      var future = new CompletableFuture<Result<E, T>>();
      Program<S, E, Void> canceler = register.apply(state, future);
      return Program.<S, E, T>from(future).onCancel(canceler);
    });
  }

  /**
   * Creates a program that never completes.
   *
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   * @return a program that never completes
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
  static <S, E, T> Program<S, E, T> access(Function<? super S, ? extends T> mapper) {
    return accessResult(mapper.andThen(Result::success));
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
    return accessResult(state -> {
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
  static <S, E, T> Program<S, E, T> accessResult(Function<? super S, ? extends Result<E, T>> mapper) {
    return accessProgram(mapper.andThen(Program::from));
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
  static <S, E, T> Program<S, E, T> accessProgram(Function<? super S, ? extends Program<S, E, T>> mapper) {
    return new Access<>(mapper);
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
        return sneakyThrow(throwable);
      }
      return sneakyThrow(new NoSuchElementException());
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
    return run(state, CancelToken.current());
  }

  @SuppressWarnings("unchecked")
  private Result<E, T> run(@Nullable S state, CancelToken token) {
    Program<S, ?, ?> current = this;
    Deque<Frame<S>> stack = new ArrayDeque<>();
    // depth of nested uncancelable regions, a cancellation only takes effect outside of them
    int masked = 0;

    while (true) {
      try {
        // a cancelled program stops at the start of its next step, unwinding the stack like an
        // exception so the finalizers run
        if (shouldStop(token, masked, current)) {
          throw new CancelToken.Cancelled();
        }
        if (current instanceof Pure(var result)) {
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
                // run the finalizer and then continue with the result, unless the finalizer fails
                stack.push(Frame.fold(Program::failure, _ -> from(result)));
                masked = enterUncancelable(stack, masked);
                current = finalizer;
                resumed = true;
              }
              case UnmaskFrame<S> _ -> masked--;
              case OnCancelFrame<S> _ -> {
                // the program completed, so it wasn't cancelled
              }
              case CatchFrame<S> _ -> {
                // leaving a catchAll scope normally, its handler no longer applies
              }
            }
          }
        } else if (current instanceof Access(var mapper)) {
          current = mapper.apply(state);
        } else if (current instanceof Async(var callback)) {
          var async = (BiConsumer<S, CompletableFuture<?>>) callback;
          var result = masked == 0 ? awaitCancelable(state, async, token) : awaitUncancelable(state, async);
          current = from(result);
        } else if (current instanceof Ensuring(var source, var finalizer)) {
          stack.push(Frame.finalizer(finalizer));
          current = source;
        } else if (current instanceof OnCancel(var source, var finalizer)) {
          stack.push(Frame.onCancel(finalizer));
          current = source;
        } else if (current instanceof Uncancelable(var source)) {
          masked = enterUncancelable(stack, masked);
          current = source;
        } else if (current instanceof Forked forked) {
          // a fork started in an uncancelable region, like a finalizer, can't be cancelled either.
          // It isn't tracked by the parent either, so a cancelled parent doesn't wait for it: the
          // programs that wait for their forks, like timeout or par*, are not affected
          var parent = masked == 0 ? token : CancelToken.NONE;
          current = success(startFork(state, parent, forked.current, forked.executor));
        } else if (current instanceof FoldMap(var source, var onFailure, var onSuccess)) {
          stack.push(Frame.fold(onFailure, onSuccess));
          current = source;
        } else if (current instanceof Raise(var throwable)) {
          return sneakyThrow(throwable.get());
        } else if (current instanceof Catch(var source, var recover)) {
          stack.push(Frame.catch_(recover));
          current = source;
        } else if (current instanceof Suspend(var supplier)) {
          current = supplier.get();
        } else if (current instanceof Memoized memoized) {
          var result = memoized.get();
          if (result != null) {
            current = from(result);
          } else {
            stack.push(Frame.fold(
                error -> {
                  memoized.set(Result.failure(error));
                  return failure(error);
                },
                value -> {
                  memoized.set(Result.success(value));
                  return success(value);
                }));
            current = memoized.current;
          }
        } else {
          // every subtype is handled above, so only a null program can reach here
          throw new NullPointerException("program cannot be null");
        }
      } catch (Throwable e) {
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
              current = suspend(() -> recover.apply(e));
              resumed = true;
            }
            case FinalizerFrame(var finalizer) -> {
              // keep unwinding with the same exception once the finalizer is done
              stack.push(Frame.fold(
                  _ -> raise(() -> e),
                  _ -> raise(() -> e)));
              masked = enterUncancelable(stack, masked);
              current = finalizer;
              resumed = true;
            }
            case OnCancelFrame(var finalizer) -> {
              // only when this program is being cancelled, not for an ordinary exception or a
              // cancelled fork joined by this program
              if (masked == 0 && token.isCancelled()) {
                // run the finalizer, then keep unwinding with the same exception
                stack.push(Frame.fold(
                    _ -> raise(() -> e),
                    _ -> raise(() -> e)));
                masked = enterUncancelable(stack, masked);
                current = finalizer;
                resumed = true;
              }
            }
            case UnmaskFrame<S> _ -> masked--;
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
   *   <li>When the step installs a finalizer ({@link Ensuring} or {@link OnCancel}). {@code bracket} acquires the resource
   *   in an uncancelable region, and the step right after it installs the release. Stopping there
   *   would leave the resource acquired with no release to run, so a finalizer is always installed
   *   and the program stops at the step after it, running the finalizer while it unwinds.</li>
   * </ul>
   */
  private static boolean shouldStop(CancelToken token, int masked, Program<?, ?, ?> current) {
    if (masked > 0 || current instanceof Ensuring || current instanceof OnCancel) {
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
      return join(future);
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
  private static <S> Result<?, ?> awaitUncancelable(@Nullable S state, BiConsumer<S, CompletableFuture<?>> async) {
    var future = new CompletableFuture<Result<?, ?>>();
    ScopedValue.where(CancelToken.CURRENT, CancelToken.NONE).run(() -> async.accept(state, future));
    return join(future);
  }

  /**
   * Waits for the future and throws the original exception if it fails.
   *
   * <p>The futures of the forked programs are combined with methods like {@code thenApply}, which
   * wrap the exception of a failed future in a {@link CompletionException}. Without unwrapping it,
   * a {@code catchAll} would receive the wrapper instead of the exception the program raised.
   */
  private static Result<?, ?> join(CompletableFuture<Result<?, ?>> future) {
    try {
      return future.join();
    } catch (CompletionException e) {
      return sneakyThrow(e.getCause() != null ? e.getCause() : e);
    }
  }

  // the region ends when the UnmaskFrame is popped
  private static <S> int enterUncancelable(Deque<Frame<S>> stack, int masked) {
    stack.push(Frame.unmask());
    return masked + 1;
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
   * to stop. If the executor refuses to run the program, the future fails with the same error
   * that is thrown, otherwise the parent would wait for it forever.
   */
  private static <S, E, T> CompletableFuture<Result<E, T>> startFork(
      @Nullable S state, CancelToken parent, Program<S, E, T> program, Executor executor) {
    var token = parent.newChild();
    var future = new CancelableFuture<Result<E, T>>(token);
    parent.trackFork(future);
    try {
      executor.execute(() -> runForked(state, token, program, future));
    } catch (RuntimeException | Error e) {
      token.detachFromParent();
      future.completeExceptionally(e);
      throw e;
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
      result = ScopedValue.where(CancelToken.CURRENT, token).call(() -> program.run(state, token));
    } catch (Throwable e) {
      error = e;
    }
    if (token.isCancelled()) {
      // only when cancelled: a program that finishes normally can leave forks running, and they
      // are not awaited even if a cancellation arrives right after the program has finished
      token.awaitForks();
    }
    // otherwise the parent keeps a callback for each program it has ever forked
    token.detachFromParent();
    if (error != null) {
      future.completeExceptionally(error);
    } else {
      future.complete(result);
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
   *
   * <p>The lifetime of the forked program depends on how the program that forked it ends:
   * <ul>
   *   <li>If that program is cancelled, the forked program is cancelled too, and the cancelled
   *   program only completes once the forked program has stopped.</li>
   *   <li>If that program completes on its own, the forked program keeps running and nobody waits
   *   for it. To stop it, cancel the returned future, or join it before completing.</li>
   *   <li>If it's forked in an uncancelable region, like a finalizer, it can't be cancelled, and
   *   nobody waits for it either.</li>
   * </ul>
   *
   * <p>The concurrent combinators, like {@code parZip} or {@code race}, always join or cancel the
   * programs they fork, so this only matters for programs forked directly.
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
   *
   * <p>The lifetime of the forked program depends on how the program that forked it ends:
   * <ul>
   *   <li>If that program is cancelled, the forked program is cancelled too, and the cancelled
   *   program only completes once the forked program has stopped.</li>
   *   <li>If that program completes on its own, the forked program keeps running and nobody waits
   *   for it. To stop it, cancel the returned future, or join it before completing.</li>
   *   <li>If it's forked in an uncancelable region, like a finalizer, it can't be cancelled, and
   *   nobody waits for it either.</li>
   * </ul>
   *
   * <p>The concurrent combinators, like {@code parZip} or {@code race}, always join or cancel the
   * programs they fork, so this only matters for programs forked directly.
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
    return race(sleep(duration, executor), this, executor)
        .flatMap(either -> either.fold(_ -> raise(TimeoutException::new), Program::success));
  }

  /**
   * Makes the program uncancelable: it always runs to completion.
   *
   * <p>A cancellation requested while the program runs is not ignored, it is deferred: it takes
   * effect at the first step after the program finishes. Use it for steps that must not be
   * interrupted half-way, like committing a transaction.
   *
   * <p>Keep in mind that:
   * <ul>
   *   <li>The concurrent combinators, like {@code parZip}, {@code either}, {@code race} or
   *   {@link #timeout(Duration)}, complete only once their cancelled programs have stopped, so they
   *   wait for an uncancelable program to finish. For example, {@code commit.uncancelable().timeout(d)}
   *   fails with a timeout, but only once the commit is done.</li>
   *   <li>The programs forked while it runs can't be cancelled either, and a cancelled program doesn't
   *   wait for them.</li>
   * </ul>
   *
   * @return a new program representing the uncancelable computation
   */
  default Program<S, E, T> uncancelable() {
    if (this instanceof Uncancelable) {
      return this;
    }
    return new Uncancelable<>(this);
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
   * Runs the finalizer only when the program is cancelled, before the cancellation goes on.
   *
   * <p>The finalizer doesn't run when the program completes, fails or throws an exception, and it
   * can't be cancelled itself. Use it to undo the work of a program that was interrupted, like
   * rolling back a transaction. To run it in every case, use {@link #ensuring(Program)}.
   *
   * <p>A program is cancelled when its own cancellation is requested: joining a forked program that
   * was cancelled is an ordinary exception for the program that joins it, and doesn't run the
   * finalizer.
   *
   * @param finalizer the program to be executed if the program is cancelled
   * @return a new program representing the computation with the finalizer
   */
  default Program<S, E, T> onCancel(Program<S, E, ?> finalizer) {
    return new OnCancel<>(this, finalizer);
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
    return pipe(
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
   * <p>Cancelling the sleep cancels its delay too, so a cancelled sleep, like the one of a
   * {@link #timeout(Duration)} that didn't expire, doesn't stay scheduled until the delay expires.
   *
   * @param duration the duration of the sleep
   * @param executor the executor used to execute the sleep
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @return a new program representing a sleep
   */
  static <S, E> Program<S, E, Void> sleep(Duration duration, Executor executor) {
    return asyncCancelable((_, callback) -> {
      // the scheduler only waits for the delay, the program continues on the executor
      var delay = DelayScheduler.schedule(duration, () -> {
        try {
          executor.execute(() -> callback.complete(Result.unit()));
        } catch (RuntimeException e) {
          // the executor refused it, fail the sleep instead of leaving it waiting forever
          callback.completeExceptionally(e);
        }
      });
      return task(() -> delay.cancel(false));
    });
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
    return pipe(
        acquire.uncancelable(),
        // use and release are called inside suspend, after the finalizer is installed: if they
        // throw while building their programs, the resource is released anyway
        resource -> Program.<S, E, R>suspend(() -> use.apply(resource))
            .ensuring(suspend(() -> release.apply(resource)))
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

  // XXX: https://www.baeldung.com/java-sneaky-throws
  @SuppressWarnings("unchecked")
  private static <X extends Throwable, R> R sneakyThrow(Throwable t) throws X {
    throw (X) t;
  }
}
