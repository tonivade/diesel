/*
 * Copyright (c) 2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Trampoline.done;
import static com.github.tonivade.diesel.Trampoline.more;
import static java.util.concurrent.CompletableFuture.supplyAsync;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

public sealed interface Program<S, E, T> {

  Program<Void, Void, Void> UNIT = success(null);

  @SuppressWarnings("unchecked")
  static <S, E> Program<S, E, Void> unit() {
    return (Program<S, E, Void>) UNIT;
  }

  static <S, E, T> Program<S, E, T> success(@Nullable T value) {
    return new Success<>(value);
  }

  static <S, E, T> Program<S, E, T> failure(E error) {
    return new Failure<>(error);
  }

  static <S, E, T> Program<S, E, T> suspend(Supplier<T> supplier) {
    return Program.<S, E>unit().map(_ -> supplier.get());
  }

  static <S, E, T> Program<S, E, T> task(Runnable runnable) {
    return Program.<S, E>unit().map(_ -> {
      runnable.run();
      return null;
    });
  }

  static <S, E, T> Program<S, E, T> async(BiConsumer<S, BiConsumer<Result<E, T>, Throwable>> callback) {
    return new Async<>(callback);
  }

  record Success<S, E, T>(@Nullable T value) implements Program<S, E, T> {
    @Override public Trampoline<Result<E, T>> safeEval(S state) {
      return done(Result.success(value));
    }
  }

  record Failure<S, E, T>(E error) implements Program<S, E, T> {
    @Override public Trampoline<Result<E, T>> safeEval(S state) {
      return done(Result.failure(error));
    }
  }

  record FoldMap<S, E, F, T, R>(
      Program<S, E, T> current,
      Function<E, Program<S, F, R>> onFailure,
      Function<T, Program<S, F, R>> onSuccess) implements Program<S, F, R> {
    @Override public Trampoline<Result<F, R>> safeEval(S state) {
      return more(() -> current.safeEval(state))
          .flatMap(result -> more(() -> result.fold(onFailure, onSuccess).safeEval(state)));
    }
  }

  record Async<S, E, T>(BiConsumer<S, BiConsumer<Result<E, T>, Throwable>> callback) implements Program<S, E, T> {
    @Override public Trampoline<Result<E, T>> safeEval(S state) {
      var promise = new CompletableFuture<Result<E, T>>();
      callback.accept(state, (result, error) -> {
        if (error != null) {
          promise.completeExceptionally(error);
        } else {
          promise.complete(result);
        }
      });
      return done(promise.join());
    }
  }

  non-sealed interface Dsl<S, E, T> extends Program<S, E, T> {
    @Override default Trampoline<Result<E, T>> safeEval(S state) {
      return done(eval(state));
    }
  }

  default Result<E, T> eval(S state) {
    return safeEval(state).run();
  }

  Trampoline<Result<E, T>> safeEval(S state);

  default <R> Program<S, E, R> map(Function<T, R> mapper) {
    return flatMap(mapper.andThen(Program::success));
  }

  default Program<S, E, T> redeem(Function<E, T> mapper) {
    return recover(mapper.andThen(Program::success));
  }

  default Program<S, E, T> redeemWith(T value) {
    return recoverWith(success(value));
  }

  default <F> Program<S, F, T> mapError(Function<E, F> mapper) {
    return flatMapError(mapper.andThen(Program::failure));
  }

  default Program<S, E, T> recover(Function<E, Program<S, E, T>> mapper) {
    return flatMapError(mapper);
  }

  default Program<S, E, T> recoverWith(Program<S, E, T> value) {
    return flatMapError(_ -> value);
  }

  default <R> Program<S, E, R> andThen(Program<S, E, R> next) {
    return flatMap(_ -> next);
  }

  default <R> Program<S, E, R> flatMap(Function<T, Program<S, E, R>> next) {
    return foldMap(Program::failure, next);
  }

  default <F> Program<S, F, T> flatMapError(Function<E, Program<S, F, T>> next) {
    return foldMap(next, Program::success);
  }

  default <F, R> Program<S, F, R> foldMap(
      Function<E, Program<S, F, R>> onFailure,
      Function<T, Program<S, F, R>> onSuccess) {
    return new FoldMap<>(this, onFailure, onSuccess);
  }

  default Program<S, E, ElapsedTime<T>> timed() {
    return Program.<S, E>start()
      .flatMap(start -> map(value -> end(start, value)));
  }

  default Program<S, E, T> retry(int retries) {
    return retry(retries, unit());
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
      var promise = supplyAsync(() -> eval(state), executor);
      callback.accept(Result.success(new Fiber<>(promise)), null);
    });
  }

  static <S, E, T> Program<S, E, T> delay(Duration duration, Supplier<T> supplier, Executor executor) {
    return Program.<S, E>sleep(duration, executor).flatMap(_ -> success(supplier.get()));
  }

  static <S, E> Program<S, E, Void> sleep(Duration duration, Executor executor) {
    return async((_, callback) -> {
      var delayed = CompletableFuture.delayedExecutor(duration.toMillis(), TimeUnit.MILLISECONDS, executor);
      var promise = CompletableFuture.runAsync(() -> {}, delayed);
      promise.whenComplete((_, _) -> callback.accept(Result.success(null), null));
    });
  }

  static <S, E, T, U, R> Program<S, E, R> map2(
      Program<S, E, T> pt,
      Program<S, E, U> pu,
      BiFunction<T, U, R> mapper) {
    return async((state, callback) -> {
      try {
        callback.accept(Result.map2(pt.eval(state), pu.eval(state), mapper), null);
      } catch (RuntimeException e) {
        callback.accept(null, e);
      }
    });
  }

  static <S, E, T, V, R> Program<S, E, R> parallel(
      Program<S, E, T> pt,
      Program<S, E, V> pv,
      BiFunction<T, V, R> mapper,
      Executor executor) {
    return async((state, callback) -> {
      try {
        var result = map2(pt.fork(executor), pv.fork(executor), (f1, f2) -> Fiber.combine(f1, f2, mapper))
          .flatMap(Fiber::join);
        callback.accept(result.eval(state), null);
      } catch (RuntimeException e) {
        callback.accept(null, e);
      }
    });
  }

  static <S, E, T, U> Program<S, E, Either<T, U>> either(
      Program<S, E, T> pt,
      Program<S, E, U> pu,
      Executor executor) {
    return async((state, callback) -> {
      try {
        var result = map2(pt.fork(executor), pu.fork(executor), Fiber::either)
          .flatMap(Fiber::join);
        callback.accept(result.eval(state), null);
      } catch (RuntimeException e) {
        callback.accept(null, e);
      }
    });
  }

  record ElapsedTime<T>(Duration duration, T value) {}

  record Fiber<E, T>(CompletableFuture<Result<E, T>> promise) {

    @SuppressWarnings("unchecked")
    public <S, F extends E> Program<S, F, T> join() {
      return (Program<S, F, T>) Program.from(promise);
    }

    public <S, F extends E> Program<S, F, Void> cancel() {
      return task(() -> promise.cancel(true));
    }

    public boolean isCompleted() {
      return promise.isDone();
    }

    public boolean isCancelled() {
      return promise.isCancelled();
    }

    public static <E, T, U, R> Fiber<E, R> combine(Fiber<E, T> f1, Fiber<E, U> f2, BiFunction<T, U, R> mapper) {
      return new Fiber<>(f1.promise.thenCombineAsync(f2.promise, (a, b) -> Result.map2(a, b, mapper)));
    }

    public static <E, T, U> Fiber<E, Either<T, U>> either(Fiber<E, T> f1, Fiber<E, U> f2) {
      return new Fiber<>(f1.promise.thenApplyAsync(t -> t.map(Either::<T, U>left))
        .applyToEitherAsync(f2.promise.thenApplyAsync(u -> u.map(Either::<T, U>right)), result -> {
          cancelBoth(f1, f2);
          return result;
        }));
    }

    private static <E, T, U> void cancelBoth(Fiber<E, T> f1, Fiber<E, U> f2) {
      try {
        if (!f1.isCompleted()) {
          f1.promise.cancel(true);
        }
      } finally {
        if (!f2.isCompleted()) {
          f2.promise.cancel(true);
        }
      }
    }
  }

  private static <S, E, T> Program<S, E, T> from(CompletableFuture<Result<E, T>> promise) {
    return new Async<S, E, T>((_, callback) -> promise.whenComplete(callback));
  }

  private static <S, E> Program<S, E, Long> start() {
    return suspend(System::nanoTime);
  }

  private static <T> ElapsedTime<T> end(Long start, T value) {
    return new ElapsedTime<>(Duration.ofNanos(System.nanoTime() - start), value);
  }
}
