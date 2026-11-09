/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Combine.chain;
import static com.github.tonivade.diesel.Combine.pipe;
import static com.github.tonivade.diesel.Combine.traverse;
import static com.github.tonivade.diesel.Combine.zip;
import static com.github.tonivade.diesel.Fatal.sneakyThrow;
import static com.github.tonivade.diesel.function.Finisher2.first;
import static java.util.function.Function.identity;

import com.github.tonivade.diesel.Frame.Region;
import com.github.tonivade.diesel.function.CheckedRunnable;
import com.github.tonivade.diesel.function.CheckedSupplier;
import com.github.tonivade.purefun.Kind;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
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
  Program<?, ?, Unit> UNIT = from(Result.UNIT);

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
  static <S, E> Program<S, E, Unit> unit() {
    return (Program<S, E, Unit>) UNIT;
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
      Function<? super Throwable, ? extends Program<S, E, T>> recover) implements Program<S, E, T> {

    @Override
    public final String toString() {
      return "Catch[current=%s, recover=...]".formatted(current);
    }
  }

  /**
   * Represents a computation that raises an exception.
   *
   * @param supplier the exception to be raised
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   */
  record Raise<S, E, T>(Supplier<? extends Throwable> supplier) implements Program<S, E, T> {

    @Override
    public final String toString() {
      return "Raise[supplier=...]";
    }
  }

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
      Function<? super T, ? extends Program<S, F, R>> onSuccess) implements Program<S, F, R> {

    @Override
    public final String toString() {
      return "FoldMap[current=%s, onFailure=..., onSuccess=...]".formatted(current);
    }
  }

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

    @Override
    public final String toString() {
      return "Async[callback=...]";
    }
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
  record Access<S, E, T>(Function<? super S, ? extends Program<S, E, T>> mapper) implements Program<S, E, T> {

    @Override
    public final String toString() {
      return "Access[mapper=...]";
    }
  }

  /**
   * Represents a new program that describes a computation that suspends its execution.
   *
   * @param supplier the supplier of the program to be executed
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   */
  record Suspend<S, E, T>(Supplier<? extends Program<S, E, T>> supplier) implements Program<S, E, T> {

    @Override
    public final String toString() {
      return "Suspend[supplier=...]";
    }
  }

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
  record Ensuring<S, E, T>(Program<S, E, T> current, Program<S, Void, ?> finalizer) implements Program<S, E, T> {}

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
  record OnCancel<S, E, T>(Program<S, E, T> current, Program<S, Void, ?> finalizer) implements Program<S, E, T> {}

  /**
   * Represents a memoized computation that caches the result of the program.
   *
   * <p>Concurrent evaluations are single-flight: the first one runs the program and the others wait
   * for its result, so the effect runs once. Only a success or a typed failure is cached; if the
   * running evaluation throws or is cancelled, nothing is cached and one of the waiting evaluations
   * runs the program again.
   *
   * <p>An evaluation that waits for itself, like a program that evaluates the same memoized
   * program recursively, waits forever.
   */
  final class Memoized<S, E, T> implements Program<S, E, T> {

    private sealed interface State<E, T> {}
    private record Idle<E, T>() implements State<E, T> {}
    private record Running<E, T>(Deferred<Optional<Result<E, T>>> done) implements State<E, T> {}
    private record Done<E, T>(Result<E, T> result) implements State<E, T> {}

    private final Program<S, E, T> current;
    private final AtomicReference<State<E, T>> state = new AtomicReference<>(new Idle<>());

    Memoized(Program<S, E, T> current) {
      this.current = current;
    }

    /** The program the interpreter runs in place of this one. */
    Program<S, E, T> run() {
      return suspend(() -> {
        var observed = state.get();
        return switch (observed) {
          case null -> throw new IllegalStateException("this should not happen");
          case Done<E, T>(var result) -> from(result);
          case Running<E, T>(var done) -> await(done);
          case Idle<E, T> _ -> {
            var running = new Running<E, T>(Deferred.create());
            // if another evaluation claimed it first, look at the state again
            yield state.compareAndSet(observed, running) ? lead(running) : run();
          }
        };
      });
    }

    private Program<S, E, T> lead(Running<E, T> running) {
      return current.foldMap(
          error -> pipe(
              publish(running, Result.failure(error)),
              _ -> failure(error)),
          value -> pipe(
              publish(running, Result.success(value)),
              _ -> success(value)))
          // always runs, also on exception or cancellation. After a normal publish it does nothing
          .ensuring(suspend(() -> release(running)));
    }

    private Program<S, E, T> await(Deferred<Optional<Result<E, T>>> done) {
      // the wait is cancelable, and a cancelled waiter doesn't affect the others
      return pipe(
          done.get(),
          outcome -> outcome
              .map(result -> Program.<S, E, T>from(result))
              .orElseGet(this::run)); // the leader gave up: try again
    }

    private <F> Program<S, F, Unit> publish(Running<E, T> running, Result<E, T> result) {
      return zip(
          task(() -> state.set(new Done<>(result))),
          running.done().complete(Optional.of(result)),
          first());
    }

    private Program<S, Void, Unit> release(Running<E, T> running) {
      return zip(
          task(() -> state.compareAndSet(running, new Idle<>())),
          running.done().complete(Optional.empty()),
          first());
    }

    @Override
    public String toString() {
      return "Memoized[current=%s]".formatted(current);
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
   * Represents a computation that can't be cancelled, a cancellation requested while it runs
   * takes effect after it finishes.
   *
   * <p>The body function receives a {@link Poll} object that can be used to create a cancellable
   * region inside this uncancelable region.
   *
   * @param body body of the region that is uncancelable
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   */
  record UncancelableWith<S, E, T>(Function<? super Poll, ? extends Program<S, E, T>> body) implements Program<S, E, T> {

    @Override
    public final String toString() {
      return "UncancelableWith[body=...]";
    }
  }

  /**
   * Represents a computation that can be cancelled inside an uncancelable region.
   *
   * @param current the current program
   * @param region the identifier of the cancellable region
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   */
  record Polled<S, E, T>(Program<S, E, T> current, Region region) implements Program<S, E, T> {}

  /**
   * Makes a program cancelable inside the uncancelable region it comes from, see
   * {@link Program#uncancelable(Function)}.
   */
  interface Poll {

    /**
     * Makes the given program cancelable.
     *
     * <p>If a cancellation was requested while the region was closed, the program doesn't start:
     * the cancellation takes effect before it. Once it finishes, the region is closed again.
     *
     * @param program the program to make cancelable
     * @param <S> the type of the state
     * @param <E> the type of the error
     * @param <T> the type of the result
     * @return a program that is cancelable even if it runs inside the region
     */
    <S, E, T> Program<S, E, T> apply(Program<S, E, T> program);
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
   * Creates a new program that represents a computation that can be executed in a specific context.
   *
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param validation the Validation representing the computation
   * @return a new program representing the computation
   */
  static <S, E> Program<S, E, Unit> from(Validation<E> validation) {
    return validation.fold(Program::unit, Program::failure);
  }

  /**
   * Creates a new program that will wait for the completion of a future.
   *
   * <p>If the program is cancelled, the original future is not cancelled
   *
   * @param future the CompletableFuture representing the asynchronous computation
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   * @return a new program that will wait for the future
   */
  static <S, E, T> Program<S, E, T> join(CompletableFuture<? extends Result<E, T>> future) {
    return async((_, callback) -> completeWith(callback, future, identity()));
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
  static <S, E, T> Program<S, E, T> success(T value) {
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
  static <S, E, T> Program<S, E, T> failure(E error) {
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
   * Widens a program that can't fail with an error to any error type.
   *
   * <p>A program with {@code Void} as error type, like the ones returned by {@link #fold}, never
   * fails with an error, so it can be used where a program with any error type is expected.
   *
   * @param program the program that can't fail with an error
   * @param <S> the type of the state
   * @param <E> the new type of the error
   * @param <T> the type of the result
   * @return the same computation with the new error type
   */
  static <S, E, T> Program<S, E, T> widen(Program<S, Void, T> program) {
    return program.mapError(_ -> {
      throw new IllegalStateException("a program with Void as error type can't fail with an error");
    });
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
  static <S, T> Program<S, Throwable, T> attempt(CheckedSupplier<? extends T> supplier) {
    return suspend(() -> from(Result.attempt(supplier)));
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
  static <S, E> Program<S, E, Unit> task(Runnable runnable) {
    return supply(() -> {
      runnable.run();
      return Unit.unit();
    });
  }

  /**
   * Creates a new program that represents an interruptible task, executed in the
   * {@link ForkJoinPool#commonPool()}. See {@link #interruptibleTask(Executor, CheckedRunnable)}.
   *
   * <p>The common pool suits CPU-intensive actions. An action that mostly blocks, like waiting for
   * I/O, holds a thread of the pool while it runs: pass an executor of its own, like one of
   * virtual threads.
   *
   * @param action the task to be executed
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @return a new program representing an interruptible task
   */
  static <S, E> Program<S, E, Unit> interruptibleTask(CheckedRunnable action) {
    return interruptibleTask(ForkJoinPool.commonPool(), action);
  }

  /**
   * Creates a new program that represents an interruptible task: when the program is cancelled,
   * the thread running the action is interrupted. A blocking call in the action, like
   * {@link Thread#sleep} or {@link Object#wait}, then throws {@link InterruptedException}, and a
   * loop can check {@code Thread.currentThread().isInterrupted()} to stop.
   *
   * <p>The cancellation waits for the action to return, so the finalizers of the program, like the
   * release of a {@code bracket} or an {@code ensuring}, run after it. An action that ignores the
   * interrupt delays the cancellation until it finishes.
   *
   * @param executor executor that will execute the action
   * @param action the task to be executed
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @return a new program representing an interruptible task
   */
  static <S, E> Program<S, E, Unit> interruptibleTask(Executor executor, CheckedRunnable action) {
    return asyncCancelable((_, callback) -> {
      var claimed = new AtomicBoolean();
      var finished = new CompletableFuture<Unit>();
      // a FutureTask interrupts the thread that runs it on cancel(true), whatever the executor: the
      // tasks of ForkJoinPool.submit don't when submitted from inside the pool, like from a fork
      var future = new FutureTask<Unit>(() -> {
        if (claimed.compareAndSet(false, true)) {
          try {
            action.run();
            callback.complete(Result.unit());
          } catch (Throwable error) {
            // a fatal error too: the waiting program rethrows it, and the FutureTask would swallow it
            callback.completeExceptionally(error);
          } finally {
            finished.complete(Unit.unit());
          }
        }
      }, null);
      executor.execute(future);
      return task(() -> {
        var started = !claimed.compareAndSet(false, true);
        future.cancel(true);
        if (started) {
          // wait for the task to finish
          finished.join();
        }
      });
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
   * program runs in an uncancelable region, which waits for the operation to finish instead. Like
   * any finalizer, the canceler can't fail with an error.
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
      BiFunction<? super S, ? super CompletableFuture<Result<E, T>>, ? extends Program<S, Void, Unit>> register) {
    // accessProgram defers all this to evaluation time: the operation has to start when the program
    // runs, not when it's built, with the state of that evaluation, and once per evaluation, so
    // each one has its own future and canceler. suspend would defer it too, but without the state.
    // The canceler is installed in the step right after the operation starts: installing an
    // onCancel never stops for a cancellation, so a started operation always has its canceler
    return accessProgram(state -> {
      var future = new CompletableFuture<Result<E, T>>();
      var canceler = register.apply(state, future);
      return Combine.onCancel(join(future), canceler);
    });
  }

  /**
   * Creates a program that runs in an uncancelable region, giving the body a {@link Poll} to make
   * parts of it cancelable again.
   *
   * <p>Everything the body does is uncancelable, like with {@link #uncancelable()}, except the
   * programs wrapped with {@link Poll#apply(Program)}: a cancellation requested while the region
   * runs is deferred until the body reaches one of them, and takes effect there, before it starts.
   * Use it for operations that have to acquire something and then wait for it, like taking an
   * element out of a queue: the element is taken in the closed part, so it can't be lost, and only
   * the wait is cancelable.
   *
   * <pre>{@code
   * Program<S, E, A> take = Program.uncancelable(poll ->
   *     pipe(
   *         acquire(),
   *         resource -> poll.apply(waitFor(resource))));
   * }</pre>
   *
   * <p>Keep in mind that:
   * <ul>
   *   <li>A {@code poll} only opens the region it comes from. Inside a nested {@code uncancelable},
   *   or inside a finalizer, a {@code poll} of an outer region doesn't make anything cancelable.</li>
   *   <li>Once the polled program finishes, whether it succeeds, fails or throws, the region is
   *   closed again.</li>
   *   <li>The finalizers installed inside a polled program, like {@link #ensuring(Program)} or
   *   {@link #onCancel(Program)}, run if it is cancelled. An {@code onCancel} placed outside the
   *   {@code poll}, in the closed part, doesn't run: the program is only cancelled inside the
   *   {@code poll}.</li>
   *   <li>A {@code poll} used outside of the region it comes from, or in a program that isn't in
   *   any uncancelable region, does nothing.</li>
   *   <li>As with {@link #uncancelable()}, the concurrent combinators, like {@code parZip} or
   *   {@link #timeout(Duration)}, wait for the closed part to finish before they complete. If the
   *   body finishes successfully, {@code timeout} returns its result even when it expired
   *   meanwhile.</li>
   * </ul>
   *
   * @param body the function that receives the {@link Poll} and returns the program to run
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   * @return a new program representing the computation, uncancelable except for the polled parts
   */
  static <S, E, T> Program<S, E, T> uncancelable(Function<? super Poll, ? extends Program<S, E, T>> body) {
    return new UncancelableWith<>(body);
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
   * using the provided consumer.
   *
   * @param consumer the consumer used to access the DSL computation
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @return a new program representing a DSL access
   */
  static <S, E> Program<S, E, Unit> inspect(Consumer<S> consumer) {
    return accessResult(state -> {
      consumer.accept(state);
      return Result.unit();
    });
  }

  /**
   * Creates a new program that represents an effectful computation that accesses a domain-specific language (DSL)
   * using the provided function.
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
   * Creates a new program that represents an effectful computation that accesses a domain-specific language (DSL)
   * using the provided function.
   *
   * <p>When the program is cancelled, the future returned by the mapper is cancelled too, so a
   * future that aborts its work on cancel, like the one of {@code HttpClient.sendAsync}, stops it.
   * A function that returns a future shared with others, like a cached one, must return a copy of
   * it with {@link CompletableFuture#copy()}, or the cancellation would reach every one using it.
   *
   * @param mapper the function used to access the DSL computation
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   * @return a new program representing a DSL access
   */
  static <S, E, T> Program<S, E, T> accessFuture(Function<? super S, ? extends CompletableFuture<T>> mapper) {
    return asyncCancelable((state, callback) -> {
      var future = mapper.apply(state);
      completeWith(callback, future, Result::success);
      return task(() -> future.cancel(true));
    });
  }

  /**
   * Creates a new program that represents an effectful computation that accesses a domain-specific language (DSL)
   * using the provided function.
   *
   * <p>When the program is cancelled, the future returned by the mapper is cancelled too, so a
   * future that aborts its work on cancel, like the one of {@code HttpClient.sendAsync}, stops it.
   * A function that returns a future shared with others, like a cached one, must return a copy of
   * it with {@link CompletableFuture#copy()}, or the cancellation would reach every one using it.
   *
   * @param mapper the function used to access the DSL computation
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   * @return a new program representing a DSL access
   */
  static <S, E, T> Program<S, E, T> accessFutureResult(
      Function<? super S, ? extends CompletableFuture<? extends Result<E, T>>> mapper) {
    return asyncCancelable((state, callback) -> {
      var future = mapper.apply(state);
      completeWith(callback, future, identity());
      return task(() -> future.cancel(true));
    });
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
    return Interpreter.run(this, state, CancelToken.current());
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
   * It just discard the result of the computation
   *
   * @return the program with the result discarded
   */
  default Program<S, E, Unit> discard() {
    return andThen(unit());
  }

  /**
   * Runs a plain Java side effect with the successful value, without modifying it.
   *
   * <p><b>The consumer is not a program</b>: if it returns a {@code Program}, for example
   * {@code peek(v -> Logger.info("..."))}, that program is created and discarded, so its effects
   * never run. Use {@link #tap(Function)} for effects described as programs.
   *
   * @param insert the side effect to run with the value
   * @return a program that runs the side effect and keeps the value
   */
  default Program<S, E, T> peek(Consumer<? super T> insert) {
    return flatMap(value -> {
      insert.accept(value);
      return success(value);
    });
  }

  /**
   * Runs a plain Java side effect with the error, without modifying it.
   *
   * <p>Like {@link #peek(Consumer)}, the consumer is not a program: use
   * {@link #tapError(Function)} for effects described as programs.
   *
   * @param insert the side effect to run with the error
   * @return a program that runs the side effect and keeps the error
   */
  default Program<S, E, T> peekError(Consumer<? super E> insert) {
    return flatMapError(error -> {
      insert.accept(error);
      return failure(error);
    });
  }

  /**
   * Evaluates the program returned by {@code effect} with the successful value, discards its
   * result and keeps the original value.
   *
   * <p>If the effect fails with an error, or throws, that failure replaces the result.
   *
   * @param effect the function that creates the program to run with the value
   * @return a program that runs the effect and keeps the value
   */
  default Program<S, E, T> tap(Function<? super T, ? extends Program<S, E, ?>> effect) {
    return flatMap(value -> effect.apply(value).andThen(success(value)));
  }

  /**
   * Evaluates the program returned by {@code effect} with the error, discards its result and
   * keeps the original error.
   *
   * <p>If the effect itself fails with an error, or throws, that failure replaces the original.
   *
   * @param effect the function that creates the program to run with the error
   * @return a program that runs the effect and keeps the error
   */
  default Program<S, E, T> tapError(Function<? super E, ? extends Program<S, E, ?>> effect) {
    return flatMapError(error -> effect.apply(error).andThen(failure(error)));
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
   * <p>Only an error of the program is handled: an exception is propagated as it is, use
   * {@link #catchAll(Function)} to recover from it.
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
   * <p>Only an error of the program is handled: an exception is propagated as it is, use
   * {@link #catchAll(Function)} to recover from it.
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
   * Measures the time taken to execute the program, using the system monotonic clock.
   *
   * @return a program that yields the result with the elapsed time
   */
  default Program<S, E, ElapsedTime<T>> timed() {
    return timed(supply(System::nanoTime));
  }

  /**
   * Measures the time taken to execute the program, preserving its result
   * (including typed failures) as a Result.
   *
   * @return a program that yields the elapsed time and the original result
   */
  default Program<S, Void, ElapsedTime<Result<E, T>>> timedResult() {
    return timedResult(supply(System::nanoTime));
  }

  /**
   * Measures the time taken to execute the program, reading the time from the given program.
   *
   * <p>The program must return a monotonic time in nanoseconds, like {@code System.nanoTime()}:
   * only the difference between two readings is used. It's read before and after this program.
   * If this program fails or throws, no elapsed time is produced.
   *
   * @param nanoTime a program that returns the current monotonic time in nanoseconds
   * @return a program that yields the result with the elapsed time
   */
  default Program<S, E, ElapsedTime<T>> timed(Program<S, E, Long> nanoTime) {
    return pipe(
        nanoTime,
        start -> flatMap(value -> chain(nanoTime, end -> elapsedTime(start, end, value))));
  }

  /**
   * Measures the time taken to execute the program, preserving its result
   * (including typed failures) as a Result.
   *
   * <p>The program used to read the clock must not fail with a typed error.
   * If reading the clock throws or is cancelled, no elapsed time is produced.
   *
   * @param nanoTime a program that returns the current monotonic time in nanoseconds
   * @return a program that yields the elapsed time and the original result
   */
  default Program<S, Void, ElapsedTime<Result<E, T>>> timedResult(Program<S, Void, Long> nanoTime) {
    return pipe(
        nanoTime,
        start -> foldMap(
            error -> chain(nanoTime, end -> elapsedTime(start, end, Result.failure(error))),
            value -> chain(nanoTime, end -> elapsedTime(start, end, Result.success(value))))
        );
  }

  /**
   * Retries the program a specified number of times in case of failure.
   *
   * @param retries the number of retries
   * @return a new program representing the computation with retries
   */
  default Program<S, E, T> retry(int retries) {
    return retry(Schedule.recurs(retries));
  }

  default Program<S, E, T> retryWhile(Predicate<? super E> condition) {
    return retry(Schedule.<S, E>forever().whileInput(condition));
  }

  default Program<S, E, T> retryUntil(Predicate<? super E> condition) {
    return retry(Schedule.<S, E>forever().untilInput(condition));
  }

  /**
   * Retries the program a specified number of times with a delay in case of failure.
   *
   * @param retries the number of retries
   * @param delay the delay between retries
   * @return a new program representing the computation with retries and delay
   */
  default Program<S, E, T> retry(int retries, Duration delay) {
    return retry(Schedule.<S, E>spaced(delay).and(Schedule.recurs(retries)));
  }

  /**
   * Retries the program a specified number of times depending on the schedule policy
   *
   * @param schedule the schedule policy to apply
   * @return a new program representing the computation with retries and delay
   */
  default Program<S, E, T> retry(Schedule<S, E> schedule) {
    return flatMapError(error ->
      pipe(
        widen(schedule.next(error)),
        decision -> decision.fold(
          () -> failure(error),
          (delay, next) -> delayed(delay, retry(next)))
      )
    );
  }

  /**
   * Repeats the program a specified number of times.
   *
   * @param times the number of times to repeat
   * @return a new program representing the computation repeated
   */
  default Program<S, E, T> repeat(int times) {
    return repeat(Schedule.recurs(times));
  }

  default Program<S, E, T> repeatWhile(Predicate<? super T> condition) {
    return repeat(Schedule.<S, T>forever().whileInput(condition));
  }

  default Program<S, E, T> repeatUntil(Predicate<? super T> condition) {
    return repeat(Schedule.<S, T>forever().untilInput(condition));
  }

  /**
   * Repeats the program a specified number of times with a delay.
   *
   * @param times the number of times to repeat
   * @param delay the delay between repetitions
   * @return a new program representing the computation repeated with delay
   */
  default Program<S, E, T> repeat(int times, Duration delay) {
    return repeat(Schedule.<S, T>spaced(delay).and(Schedule.recurs(times)));
  }

  /**
   * Repeats the program a specified number of times depending on the schedule policy
   *
   * @param schedule the schedule policy to apply
   * @return a new program representing the computation repeated
   */
  default Program<S, E, T> repeat(Schedule<S, T> schedule) {
    return flatMap(value ->
      pipe(
        widen(schedule.next(value)),
        decision -> decision.fold(
          () -> success(value),
          (delay, next) -> delayed(delay, repeat(next)))
      )
    );
  }

  /**
   * Forks the program to be executed asynchronously in a virtual thread.
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
    return fork(Concurrent.DEFAULT_EXECUTOR);
  }

  /**
   * Forks the program to be executed asynchronously using the provided executor.
   *
   * <p>Cancelling the returned future cancels the program: it stops at its next step, runs its
   * finalizers and then completes the future with a {@link java.util.concurrent.CancellationException}.
   *
   * <p>If the executor refuses to run the program, like a {@code RejectedExecutionException} of a
   * full or shut down executor, the fork doesn't fail: the returned future fails with that error.
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
   * Adds a timeout to the program using the provided duration and virtual threads.
   *
   * <p>If the timeout expires first, the program is cancelled, it stops at its next step and runs
   * its finalizers, and then the resulting program fails with a {@link TimeoutException}.
   *
   * <p>The cancellation is a request: if the program finishes successfully before it observes it,
   * its result is returned instead of the timeout, so a value that the program has already
   * consumed, like an element taken from a {@link Queue}, is not lost.
   *
   * @param duration the duration of the timeout
   * @return a new program representing the computation with timeout
   */
  default Program<S, E, T> timeout(Duration duration) {
    return timeout(duration, Concurrent.DEFAULT_EXECUTOR);
  }

  /**
   * Adds a timeout to the program using the provided duration and executor.
   *
   * <p>If the timeout expires first, the program is cancelled, it stops at its next step and runs
   * its finalizers, and then the resulting program fails with a {@link TimeoutException}.
   *
   * <p>The cancellation is a request: if the program finishes successfully before it observes it,
   * its result is returned instead of the timeout, so a value that the program has already
   * consumed, like an element taken from a {@link Queue}, is not lost.
   *
   * @param duration the duration of the timeout
   * @param executor the executor used to run the program and its timeout in parallel
   * @return a new program representing the computation with timeout
   */
  default Program<S, E, T> timeout(Duration duration, Executor executor) {
    return Concurrent.timeout(this, duration, executor);
  }

  /**
   * Makes the program uncancelable: it always runs to completion.
   *
   * <p>A cancellation requested while the program runs is not ignored, it is deferred: it takes
   * effect at the first step after the program finishes. If there is no step left, because the
   * program is the whole forked computation, it completes normally with its own result. Use it for
   * steps that must not be interrupted half-way, like committing a transaction.
   *
   * <p>Keep in mind that:
   * <ul>
   *   <li>The concurrent combinators, like {@code parZip}, {@code either}, {@code race} or
   *   {@link #timeout(Duration)}, complete only once their cancelled programs have stopped, so they
   *   wait for an uncancelable program to finish.</li>
   *   <li>{@link #timeout(Duration)} returns the result of an uncancelable program that finishes
   *   successfully, even if the timeout expired meanwhile: the work was done, so it is not reported
   *   as a failure. For example, {@code commit.uncancelable().timeout(d)} returns the result of the
   *   commit, only once it is done, however long it takes. It fails with a {@link TimeoutException}
   *   only if the program ends cancelled.</li>
   *   <li>{@code race}, {@code either} and {@code parAny} discard the result of the program that
   *   loses, even if it was uncancelable and completed successfully: its effects happened, but
   *   its value is lost.</li>
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
   * cancelled itself. The finalizer can't fail with an error, only throw an exception, which
   * replaces the result of the program. If the program threw an exception or was cancelled, that
   * exception goes on and the one of the finalizer is added to it as suppressed, like
   * try-with-resources does.
   *
   * @param finalizer the program to be executed as a finalizer
   * @return a new program representing the computation with the finalizer
   */
  default Program<S, E, T> ensuring(Program<S, Void, ?> finalizer) {
    return new Ensuring<>(this, finalizer);
  }

  /**
   * Runs the finalizer only when the program is cancelled, before the cancellation goes on.
   *
   * <p>The finalizer doesn't run when the program completes, fails or throws an exception, and it
   * can't be cancelled itself, nor fail with an error. Use it to undo the work of a program that
   * was interrupted, like rolling back a transaction. To run it in every case, use
   * {@link #ensuring(Program)}.
   *
   * <p>A program is cancelled when its own cancellation is requested: joining a forked program that
   * was cancelled is an ordinary exception for the program that joins it, and doesn't run the
   * finalizer.
   *
   * @param finalizer the program to be executed if the program is cancelled
   * @return a new program representing the computation with the finalizer
   */
  default Program<S, E, T> onCancel(Program<S, Void, ?> finalizer) {
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
   * Delays the execution of the program using the provided duration and supplier.
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
   * Delays the execution of the program using the provided duration.
   *
   * @param duration the duration of the delay
   * @param program the next program to be executed after the delay
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   * @return a new program representing the delayed computation
   */
  static <S, E, T> Program<S, E, T> delayed(Duration duration, Program<S, E, T> program) {
    return pipe(
        sleep(duration),
        _ -> program
        );
  }

  /**
   * Creates a new program that represents a sleep for the given duration.
   *
   * <p>Cancelling the sleep cancels its delay too, so a cancelled sleep, like the one of a
   * {@link #timeout(Duration)} that didn't expire, doesn't stay scheduled until the delay expires.
   *
   * <p>If duration is 0 or less, it will return {@link #unit()}.
   * <p>If duration in nanoseconds is greater than or equal to {@link Long#MAX_VALUE}, it will return {@link #never()}.
   *
   * @param duration the duration of the sleep
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @return a new program representing a sleep
   */
  static <S, E> Program<S, E, Unit> sleep(Duration duration) {
    if (duration.isNegative() || duration.isZero()) {
      return unit();
    }
    if (duration.compareTo(DelayScheduler.FOREVER) >= 0) {
      return never();
    }
    return asyncCancelable((_, callback) -> {
      // completed directly on the scheduler thread: the program continues on the thread that waits
      // for it, not on an executor, and handing the completion to the executor of a fork could
      // queue it behind that waiting thread forever
      var delay = DelayScheduler.schedule(duration, () -> callback.complete(Result.unit()));
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
   * The result of a timed program, with the time it took to execute and its result.
   *
   * @param duration the time it took to execute the program
   * @param value the result of the program
   * @param <T> the type of the result
   */
  record ElapsedTime<T>(Duration duration, T value) {}

  private static <E, T, A> void completeWith(CompletableFuture<Result<E, T>> callback,
      CompletableFuture<? extends A> future, Function<? super A, ? extends Result<E, T>> adapter) {
    var _ = future.whenComplete((result, error) -> {
      if (error != null) {
        callback.completeExceptionally(error);
      } else if (result != null) {
        callback.complete(adapter.apply(result));
      } else {
        callback.completeExceptionally(new NullPointerException());
      }
    });
  }

  private static <T> ElapsedTime<T> elapsedTime(Long start, Long end, T value) {
    return new ElapsedTime<>(Duration.ofNanos(end - start), value);
  }
}
