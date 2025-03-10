package com.github.tonivade.diesel;

import com.github.tonivade.diesel.function.Finisher2;
import com.github.tonivade.diesel.function.Finisher3;
import com.github.tonivade.diesel.function.Finisher4;
import com.github.tonivade.diesel.function.Finisher5;
import com.github.tonivade.diesel.function.Finisher6;
import com.github.tonivade.diesel.function.Finisher7;
import com.github.tonivade.diesel.function.Finisher8;
import com.github.tonivade.diesel.function.Finisher9;
import java.util.concurrent.CompletableFuture;

public record Fiber<E, T>(CompletableFuture<Result<E, T>> future) {

  @SuppressWarnings("unchecked")
  public <S, F extends E> Program<S, F, T> join() {
    return (Program<S, F, T>) Program.from(future);
  }

  public <S, F extends E> Program<S, F, Void> cancel() {
    return Program.task(() -> future.cancel(true));
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