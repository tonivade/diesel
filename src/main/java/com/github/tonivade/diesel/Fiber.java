/*
 * Copyright (c) 2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import com.github.tonivade.diesel.function.Finisher2;
import com.github.tonivade.diesel.function.Finisher3;
import com.github.tonivade.diesel.function.Finisher4;
import com.github.tonivade.diesel.function.Finisher5;
import com.github.tonivade.diesel.function.Finisher6;
import com.github.tonivade.diesel.function.Finisher7;
import com.github.tonivade.diesel.function.Finisher8;
import com.github.tonivade.diesel.function.Finisher9;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * A Fiber is a lightweight, non-blocking computation that can be cancelled.
 * It is used to represent a computation that may not have completed yet.
 * <p>
 * The Fiber class is a wrapper around CompletableFuture and provides
 * additional functionality for working with asynchronous computations.
 *
 * @param <E> the type of the error
 * @param <T> the type of the result
 */
public record Fiber<E, T>(CompletableFuture<Result<E, T>> future) {

  public <S> Program<S, E, T> join() {
    return Program.from(future);
  }

  public <S> Program<S, E, Void> cancel() {
    return Program.task(() -> future.cancel(true));
  }

  public boolean isCompleted() {
    return future.isDone();
  }

  public boolean isCancelled() {
    return future.isCancelled();
  }

  public <R> Fiber<E, R> map(Function<T, R> mapper) {
    var mapped = future.thenApply(result -> result.map(mapper));
    return new Fiber<>(mapped);
  }

  public static <E> Fiber<E, Void> all(Collection<? extends Fiber<E, ?>> fibers) {
    var all = CompletableFuture.allOf(fibers.stream().map(Fiber::future).toArray(CompletableFuture[]::new))
        .thenApply(_ -> Result.<E, Void>success(null));
    return new Fiber<E, Void>(all);
  }

  // start generated code

  public static <E, T0, T1, R> Fiber<E, R> zip(
     Fiber<E, T0> f0,
     Fiber<E, T1> f1,
     Finisher2<T0, T1, R> finisher) {
    var result = f0.future.thenComposeAsync(_0 ->
      f1.future.thenApplyAsync(_1 -> Result.zip(_0, _1, finisher))
      );
    return new Fiber<>(result);
  }

  public static <E, T0, T1, T2, R> Fiber<E, R> zip(
     Fiber<E, T0> f0,
     Fiber<E, T1> f1,
     Fiber<E, T2> f2,
     Finisher3<T0, T1, T2, R> finisher) {
    var result = f0.future.thenComposeAsync(_0 ->
      f1.future.thenComposeAsync(_1 ->
      f2.future.thenApplyAsync(_2 -> Result.zip(_0, _1, _2, finisher))
      ));
    return new Fiber<>(result);
  }

  public static <E, T0, T1, T2, T3, R> Fiber<E, R> zip(
     Fiber<E, T0> f0,
     Fiber<E, T1> f1,
     Fiber<E, T2> f2,
     Fiber<E, T3> f3,
     Finisher4<T0, T1, T2, T3, R> finisher) {
    var result = f0.future.thenComposeAsync(_0 ->
      f1.future.thenComposeAsync(_1 ->
      f2.future.thenComposeAsync(_2 ->
      f3.future.thenApplyAsync(_3 -> Result.zip(_0, _1, _2, _3, finisher))
      )));
    return new Fiber<>(result);
  }

  public static <E, T0, T1, T2, T3, T4, R> Fiber<E, R> zip(
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
      f4.future.thenApplyAsync(_4 -> Result.zip(_0, _1, _2, _3, _4, finisher))
      ))));
    return new Fiber<>(result);
  }

  public static <E, T0, T1, T2, T3, T4, T5, R> Fiber<E, R> zip(
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
      f5.future.thenApplyAsync(_5 -> Result.zip(_0, _1, _2, _3, _4, _5, finisher))
      )))));
    return new Fiber<>(result);
  }

  public static <E, T0, T1, T2, T3, T4, T5, T6, R> Fiber<E, R> zip(
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
      f6.future.thenApplyAsync(_6 -> Result.zip(_0, _1, _2, _3, _4, _5, _6, finisher))
      ))))));
    return new Fiber<>(result);
  }

  public static <E, T0, T1, T2, T3, T4, T5, T6, T7, R> Fiber<E, R> zip(
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
      f7.future.thenApplyAsync(_7 -> Result.zip(_0, _1, _2, _3, _4, _5, _6, _7, finisher))
      )))))));
    return new Fiber<>(result);
  }

  public static <E, T0, T1, T2, T3, T4, T5, T6, T7, T8, R> Fiber<E, R> zip(
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
      f8.future.thenApplyAsync(_8 -> Result.zip(_0, _1, _2, _3, _4, _5, _6, _7, _8, finisher))
      ))))))));
    return new Fiber<>(result);
  }

  // end generated code

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