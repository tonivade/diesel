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

public sealed interface Program<S, E, T> {

  Program<?, ?, Void> UNIT = success(null);

  @SuppressWarnings("unchecked")
  static <S, E> Program<S, E, Void> unit() {
    return (Program<S, E, Void>) UNIT;
  }

  static <S, E, T> Program<S, E, T> from(Result<E, T> result) {
    return result.fold(Program::failure, Program::success);
  }

  static <S, E, T> Program<S, E, T> success(@Nullable T value) {
    return new Success<>(value);
  }

  static <S, E, T> Program<S, E, T> failure(E error) {
    return new Failure<>(error);
  }

  static <S, E, T, X extends Throwable> Program<S, E, T> raise(Supplier<X> throwable) {
    return supply(() -> sneakyThrow(throwable.get()));
  }

  static <S, E, T> Program<S, E, T> supply(Supplier<T> supplier) {
    return Program.<S, E>unit().map(__ -> supplier.get());
  }

  static <S, E, T> Program<S, E, T> suspend(Supplier<Program<S, E, T>> supplier) {
    return Program.<S, E>unit().flatMap(__ -> supplier.get());
  }

  static <S, E> Program<S, E, Void> task(Runnable runnable) {
    return Program.<S, E>unit().map(__ -> {
      runnable.run();
      return null;
    });
  }

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
      case Dsl<S, E, T> dsl -> done(dsl.handle(state));
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
    };
  }

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
    return flatMapError(__ -> value);
  }

  default <R> Program<S, E, R> andThen(Program<S, E, R> next) {
    return flatMap(__ -> next);
  }

  default Program<S, E, T> peek(Function<T, Program<S, E, Void>> insert) {
    return flatMap(value -> insert.apply(value).andThen(success(value)));
  }

  default Program<S, E, T> peekError(Function<E, Program<S, E, Void>> insert) {
    return flatMapError(error -> insert.apply(error).andThen(failure(error)));
  }

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
      var future = CompletableFuture.supplyAsync(() -> eval(state), executor);
      callback.accept(Result.success(new Fiber<>(future)), null);
    });
  }

  default Program<S, E, T> timeout(Duration duration, Executor executor) {
    return either(sleep(duration, executor), this, executor)
      .flatMap(either -> either.fold(__ -> raise(TimeoutException::new), Program::success));
  }

  static <S, E, T> Program<S, E, T> delay(Duration duration, Supplier<T> supplier, Executor executor) {
    return Program.<S, E>sleep(duration, executor).flatMap(__ -> success(supplier.get()));
  }

  static <S, E> Program<S, E, Void> sleep(Duration duration, Executor executor) {
    return async((__, callback) -> {
      var delayed = CompletableFuture.delayedExecutor(duration.toMillis(), TimeUnit.MILLISECONDS, executor);
      var future = CompletableFuture.runAsync(() -> {}, delayed);
      future.whenCompleteAsync((i, j) -> callback.accept(Result.success(null), null));
    });
  }

  static <S, E, T, U, R> Program<S, E, R> map2(
      Program<S, E, T> p1,
      Program<S, E, U> p2,
      Finisher2<T, U, R> mapper) {
    return async((state, callback) -> {
      try {
        callback.accept(Result.map2(p1.eval(state), p2.eval(state), mapper), null);
      } catch (RuntimeException e) {
        callback.accept(null, e);
      }
    });
  }

  static <S, E, T0, T1, T2, R> Program<S, E, R> map3(
      Program<S, E, T0> p0,
      Program<S, E, T1> p1,
      Program<S, E, T2> p2,
      Finisher3<T0, T1, T2, R> finisher) {
       return async((state, callback) -> {
         try {
           callback.accept(Result.map3(p0.eval(state), p1.eval(state), p2.eval(state), finisher), null);
         } catch (RuntimeException e) {
           callback.accept(null, e);
         }
       });
   }

   static <S, E, T0, T1, T2, T3, R> Program<S, E, R> map4(
      Program<S, E, T0> p0,
      Program<S, E, T1> p1,
      Program<S, E, T2> p2,
      Program<S, E, T3> p3,
      Finisher4<T0, T1, T2, T3, R> finisher) {
       return async((state, callback) -> {
         try {
           callback.accept(Result.map4(p0.eval(state), p1.eval(state), p2.eval(state), p3.eval(state), finisher), null);
         } catch (RuntimeException e) {
           callback.accept(null, e);
         }
       });
   }

   static <S, E, T0, T1, T2, T3, T4, R> Program<S, E, R> map5(
      Program<S, E, T0> p0,
      Program<S, E, T1> p1,
      Program<S, E, T2> p2,
      Program<S, E, T3> p3,
      Program<S, E, T4> p4,
      Finisher5<T0, T1, T2, T3, T4, R> finisher) {
       return async((state, callback) -> {
         try {
           callback.accept(Result.map5(p0.eval(state), p1.eval(state), p2.eval(state), p3.eval(state), p4.eval(state), finisher), null);
         } catch (RuntimeException e) {
           callback.accept(null, e);
         }
       });
   }

   static <S, E, T0, T1, T2, T3, T4, T5, R> Program<S, E, R> map6(
      Program<S, E, T0> p0,
      Program<S, E, T1> p1,
      Program<S, E, T2> p2,
      Program<S, E, T3> p3,
      Program<S, E, T4> p4,
      Program<S, E, T5> p5,
      Finisher6<T0, T1, T2, T3, T4, T5, R> finisher) {
       return async((state, callback) -> {
         try {
           callback.accept(Result.map6(p0.eval(state), p1.eval(state), p2.eval(state), p3.eval(state), p4.eval(state), p5.eval(state), finisher), null);
         } catch (RuntimeException e) {
           callback.accept(null, e);
         }
       });
   }

   static <S, E, T0, T1, T2, T3, T4, T5, T6, R> Program<S, E, R> map7(
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
           callback.accept(Result.map7(p0.eval(state), p1.eval(state), p2.eval(state), p3.eval(state), p4.eval(state), p5.eval(state), p6.eval(state), finisher), null);
         } catch (RuntimeException e) {
           callback.accept(null, e);
         }
       });
   }

   static <S, E, T0, T1, T2, T3, T4, T5, T6, T7, R> Program<S, E, R> map8(
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
           callback.accept(Result.map8(p0.eval(state), p1.eval(state), p2.eval(state), p3.eval(state), p4.eval(state), p5.eval(state), p6.eval(state), p7.eval(state), finisher), null);
         } catch (RuntimeException e) {
           callback.accept(null, e);
         }
       });
   }

   static <S, E, T0, T1, T2, T3, T4, T5, T6, T7, T8, R> Program<S, E, R> map9(
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
           callback.accept(Result.map9(p0.eval(state), p1.eval(state), p2.eval(state), p3.eval(state), p4.eval(state), p5.eval(state), p6.eval(state), p7.eval(state), p8.eval(state), finisher), null);
         } catch (RuntimeException e) {
           callback.accept(null, e);
         }
       });
   }

  static <S, E, T, V, R> Program<S, E, R> parMap2(
      Program<S, E, T> p1,
      Program<S, E, V> p2,
      Finisher2<T, V, R> mapper,
      Executor executor) {
    return map2(
        p1.fork(executor),
        p2.fork(executor),
        (f1, f2) -> Fiber.map2(f1, f2, mapper))
        .flatMap(Fiber::join);
  }

  static <S, E, T0, T1, T2, R> Program<S, E, R> parMap3(
      Program<S, E, T0> p0,
      Program<S, E, T1> p1,
      Program<S, E, T2> p2,
      Finisher3<T0, T1, T2, R> finisher,
      Executor executor) {
    return map3(
          p0.fork(executor),
          p1.fork(executor),
          p2.fork(executor),
          (f0, f1, f2) -> Fiber.map3(f0, f1, f2, finisher))
         .flatMap(Fiber::join);
   }

  static <S, E, T0, T1, T2, T3, R> Program<S, E, R> parMap4(
      Program<S, E, T0> p0,
      Program<S, E, T1> p1,
      Program<S, E, T2> p2,
      Program<S, E, T3> p3,
      Finisher4<T0, T1, T2, T3, R> finisher,
      Executor executor) {
    return map4(
          p0.fork(executor),
          p1.fork(executor),
          p2.fork(executor),
          p3.fork(executor),
          (f0, f1, f2, f3) -> Fiber.map4(f0, f1, f2, f3, finisher))
         .flatMap(Fiber::join);
   }

  static <S, E, T0, T1, T2, T3, T4, R> Program<S, E, R> parMap5(
      Program<S, E, T0> p0,
      Program<S, E, T1> p1,
      Program<S, E, T2> p2,
      Program<S, E, T3> p3,
      Program<S, E, T4> p4,
      Finisher5<T0, T1, T2, T3, T4, R> finisher,
      Executor executor) {
    return map5(
          p0.fork(executor),
          p1.fork(executor),
          p2.fork(executor),
          p3.fork(executor),
          p4.fork(executor),
          (f0, f1, f2, f3, f4) -> Fiber.map5(f0, f1, f2, f3, f4, finisher))
         .flatMap(Fiber::join);
   }

  static <S, E, T0, T1, T2, T3, T4, T5, R> Program<S, E, R> parMap6(
      Program<S, E, T0> p0,
      Program<S, E, T1> p1,
      Program<S, E, T2> p2,
      Program<S, E, T3> p3,
      Program<S, E, T4> p4,
      Program<S, E, T5> p5,
      Finisher6<T0, T1, T2, T3, T4, T5, R> finisher,
      Executor executor) {
    return map6(
          p0.fork(executor),
          p1.fork(executor),
          p2.fork(executor),
          p3.fork(executor),
          p4.fork(executor),
          p5.fork(executor),
          (f0, f1, f2, f3, f4, f5) -> Fiber.map6(f0, f1, f2, f3, f4, f5, finisher))
         .flatMap(Fiber::join);
   }

  static <S, E, T0, T1, T2, T3, T4, T5, T6, R> Program<S, E, R> parMap7(
      Program<S, E, T0> p0,
      Program<S, E, T1> p1,
      Program<S, E, T2> p2,
      Program<S, E, T3> p3,
      Program<S, E, T4> p4,
      Program<S, E, T5> p5,
      Program<S, E, T6> p6,
      Finisher7<T0, T1, T2, T3, T4, T5, T6, R> finisher,
      Executor executor) {
    return map7(
          p0.fork(executor),
          p1.fork(executor),
          p2.fork(executor),
          p3.fork(executor),
          p4.fork(executor),
          p5.fork(executor),
          p6.fork(executor),
          (f0, f1, f2, f3, f4, f5, f6) -> Fiber.map7(f0, f1, f2, f3, f4, f5, f6, finisher))
         .flatMap(Fiber::join);
   }

  static <S, E, T0, T1, T2, T3, T4, T5, T6, T7, R> Program<S, E, R> parMap8(
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
    return map8(
          p0.fork(executor),
          p1.fork(executor),
          p2.fork(executor),
          p3.fork(executor),
          p4.fork(executor),
          p5.fork(executor),
          p6.fork(executor),
          p7.fork(executor),
          (f0, f1, f2, f3, f4, f5, f6, f7) -> Fiber.map8(f0, f1, f2, f3, f4, f5, f6, f7, finisher))
         .flatMap(Fiber::join);
   }

  static <S, E, T0, T1, T2, T3, T4, T5, T6, T7, T8, R> Program<S, E, R> parMap9(
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
    return map9(
          p0.fork(executor),
          p1.fork(executor),
          p2.fork(executor),
          p3.fork(executor),
          p4.fork(executor),
          p5.fork(executor),
          p6.fork(executor),
          p7.fork(executor),
          p8.fork(executor),
          (f0, f1, f2, f3, f4, f5, f6, f7, f8) -> Fiber.map9(f0, f1, f2, f3, f4, f5, f6, f7, f8, finisher))
         .flatMap(Fiber::join);
   }

  static <S, E, T, U> Program<S, E, Either<T, U>> either(
      Program<S, E, T> p1,
      Program<S, E, U> p2,
      Executor executor) {
    return map2(p1.fork(executor), p2.fork(executor), Fiber::either).flatMap(Fiber::join);
  }

  record ElapsedTime<T>(Duration duration, T value) {}

  record Fiber<E, T>(CompletableFuture<Result<E, T>> future) {

    @SuppressWarnings("unchecked")
    public <S, F extends E> Program<S, F, T> join() {
      return (Program<S, F, T>) Program.from(future);
    }

    public <S, F extends E> Program<S, F, Void> cancel() {
      return task(() -> future.cancel(true));
    }

    public boolean isCompleted() {
      return future.isDone();
    }

    public boolean isCancelled() {
      return future.isCancelled();
    }

    public static <E, T, U, R> Fiber<E, R> map2(Fiber<E, T> f1, Fiber<E, U> f2, Finisher2<T, U, R> mapper) {
      var result = f1.future.thenComposeAsync(_1 -> f2.future.thenApplyAsync(_2 -> Result.map2(_1, _2, mapper)));
      return new Fiber<>(result);
    }

    public static <E, T0, T1, T2, R> Fiber<E, R> map3(
        Fiber<E, T0> f0,
        Fiber<E, T1> f1,
        Fiber<E, T2> f2,
        Finisher3<T0, T1, T2, R> finisher) {
      var result = f0.future.thenComposeAsync(_0 ->
          f1.future.thenComposeAsync(_1 ->
          f2.future.thenApplyAsync(_2 -> Result.map3(_0, _1, _2, finisher))
          ));
      return new Fiber<>(result);
    }

    public static <E, T0, T1, T2, T3, R> Fiber<E, R> map4(
        Fiber<E, T0> f0,
        Fiber<E, T1> f1,
        Fiber<E, T2> f2,
        Fiber<E, T3> f3,
        Finisher4<T0, T1, T2, T3, R> finisher) {
      var result = f0.future.thenComposeAsync(_0 ->
          f1.future.thenComposeAsync(_1 ->
          f2.future.thenComposeAsync(_2 ->
          f3.future.thenApplyAsync(_3 -> Result.map4(_0, _1, _2, _3, finisher))
          )));
      return new Fiber<>(result);
    }

    public static <E, T0, T1, T2, T3, T4, R> Fiber<E, R> map5(
        Fiber<E, T0> f0,
        Fiber<E, T1> f1,
        Fiber<E, T2> f2,
        Fiber<E, T3> f3,
        Fiber<E, T4> f4,
        Finisher5<T0, T1, T2, T3, T4, R> finisher) {
      var result = f0.future.thenComposeAsync(_0 ->
          f1.future.thenComposeAsync(_1 ->
          f2.future.thenComposeAsync(_2 ->
          f3.future.thenComposeAsync(_3 ->
          f4.future.thenApplyAsync(_4 -> Result.map5(_0, _1, _2, _3, _4, finisher))
          ))));
      return new Fiber<>(result);
    }

    public static <E, T0, T1, T2, T3, T4, T5, R> Fiber<E, R> map6(
        Fiber<E, T0> f0,
        Fiber<E, T1> f1,
        Fiber<E, T2> f2,
        Fiber<E, T3> f3,
        Fiber<E, T4> f4,
        Fiber<E, T5> f5,
        Finisher6<T0, T1, T2, T3, T4, T5, R> finisher) {
      var result = f0.future.thenComposeAsync(_0 ->
          f1.future.thenComposeAsync(_1 ->
          f2.future.thenComposeAsync(_2 ->
          f3.future.thenComposeAsync(_3 ->
          f4.future.thenComposeAsync(_4 ->
          f5.future.thenApplyAsync(_5 -> Result.map6(_0, _1, _2, _3, _4, _5, finisher))
          )))));
      return new Fiber<>(result);
    }

    public static <E, T0, T1, T2, T3, T4, T5, T6, R> Fiber<E, R> map7(
        Fiber<E, T0> f0,
        Fiber<E, T1> f1,
        Fiber<E, T2> f2,
        Fiber<E, T3> f3,
        Fiber<E, T4> f4,
        Fiber<E, T5> f5,
        Fiber<E, T6> f6,
        Finisher7<T0, T1, T2, T3, T4, T5, T6, R> finisher) {
      var result = f0.future.thenComposeAsync(_0 ->
          f1.future.thenComposeAsync(_1 ->
          f2.future.thenComposeAsync(_2 ->
          f3.future.thenComposeAsync(_3 ->
          f4.future.thenComposeAsync(_4 ->
          f5.future.thenComposeAsync(_5 ->
          f6.future.thenApplyAsync(_6 -> Result.map7(_0, _1, _2, _3, _4, _5, _6, finisher))
          ))))));
      return new Fiber<>(result);
    }

    public static <E, T0, T1, T2, T3, T4, T5, T6, T7, R> Fiber<E, R> map8(
        Fiber<E, T0> f0,
        Fiber<E, T1> f1,
        Fiber<E, T2> f2,
        Fiber<E, T3> f3,
        Fiber<E, T4> f4,
        Fiber<E, T5> f5,
        Fiber<E, T6> f6,
        Fiber<E, T7> f7,
        Finisher8<T0, T1, T2, T3, T4, T5, T6, T7, R> finisher) {
      var result = f0.future.thenComposeAsync(_0 ->
          f1.future.thenComposeAsync(_1 ->
          f2.future.thenComposeAsync(_2 ->
          f3.future.thenComposeAsync(_3 ->
          f4.future.thenComposeAsync(_4 ->
          f5.future.thenComposeAsync(_5 ->
          f6.future.thenComposeAsync(_6 ->
          f7.future.thenApplyAsync(_7 -> Result.map8(_0, _1, _2, _3, _4, _5, _6, _7, finisher))
          )))))));
      return new Fiber<>(result);
    }

    public static <E, T0, T1, T2, T3, T4, T5, T6, T7, T8, R> Fiber<E, R> map9(
        Fiber<E, T0> f0,
        Fiber<E, T1> f1,
        Fiber<E, T2> f2,
        Fiber<E, T3> f3,
        Fiber<E, T4> f4,
        Fiber<E, T5> f5,
        Fiber<E, T6> f6,
        Fiber<E, T7> f7,
        Fiber<E, T8> f8,
        Finisher9<T0, T1, T2, T3, T4, T5, T6, T7, T8, R> finisher) {
      var result = f0.future.thenComposeAsync(_0 ->
          f1.future.thenComposeAsync(_1 ->
          f2.future.thenComposeAsync(_2 ->
          f3.future.thenComposeAsync(_3 ->
          f4.future.thenComposeAsync(_4 ->
          f5.future.thenComposeAsync(_5 ->
          f6.future.thenComposeAsync(_6 ->
          f7.future.thenComposeAsync(_7 ->
          f8.future.thenApplyAsync(_8 -> Result.map9(_0, _1, _2, _3, _4, _5, _6, _7, _8, finisher))
          ))))))));
      return new Fiber<>(result);
    }

    public static <E, T, U> Fiber<E, Either<T, U>> either(Fiber<E, T> f1, Fiber<E, U> f2) {
      return new Fiber<>(f1.future.thenApplyAsync(t -> t.map(Either::<T, U>left))
        .applyToEitherAsync(f2.future.thenApplyAsync(u -> u.map(Either::<T, U>right)), result -> {
          cancelBoth(f1, f2);
          return result;
        }));
    }

    private static <E, T, U> void cancelBoth(Fiber<E, T> f1, Fiber<E, U> f2) {
      try {
        if (!f1.isCompleted()) {
          f1.future.cancel(true);
        }
      } finally {
        if (!f2.isCompleted()) {
          f2.future.cancel(true);
        }
      }
    }
  }

  private static <S, E, T> Program<S, E, T> from(CompletableFuture<Result<E, T>> future) {
    return new Async<>((__, callback) -> future.whenCompleteAsync(callback));
  }

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
