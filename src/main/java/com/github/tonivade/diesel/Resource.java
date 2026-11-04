/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Combine.bracket;
import static com.github.tonivade.diesel.Combine.catchAll;
import static com.github.tonivade.diesel.Combine.chain;
import static com.github.tonivade.diesel.Combine.pipe;
import static com.github.tonivade.diesel.Program.access;
import static com.github.tonivade.diesel.Program.failure;
import static com.github.tonivade.diesel.Program.raise;
import static com.github.tonivade.diesel.Program.success;
import static com.github.tonivade.diesel.Program.suspend;
import static com.github.tonivade.diesel.Program.task;
import static com.github.tonivade.diesel.Program.unit;
import static com.github.tonivade.diesel.Tuple2.tuple;
import static java.util.Objects.requireNonNull;

import com.github.tonivade.purefun.Kind;

import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Function;

public final class Resource<S, E, A> implements Kind<Resource<S, E, ?>, A> {

  // the acquired value and the program that releases it. The release can't fail with an error, like
  // the programs returned by fold, so changing the error type of the resource doesn't affect it
  private final Program<S, E, Tuple2<A, Program<S, Void, Unit>>> resource;

  private Resource(Program<S, E, Tuple2<A, Program<S, Void, Unit>>> resource) {
    this.resource = requireNonNull(resource);
  }

  public <B> Resource<S, E, B> map(Function<? super A, ? extends B> mapper) {
    return flatMap(a -> of(success(mapper.apply(a))));
  }

  public <F> Resource<S, F, A> mapError(Function<? super E, ? extends F> mapper) {
    return new Resource<>(resource.mapError(mapper));
  }

  public <B> Resource<S, E, B> flatMap(Function<? super A, ? extends Resource<S, E, B>> mapper) {
    return new Resource<>(resource.flatMap(ta -> acquireNext(ta, mapper)));
  }

  public <F> Resource<S, F, A> flatMapError(Function<? super E, ? extends Resource<S, F, A>> mapper) {
    return new Resource<>(resource.flatMapError(e -> {
      var apply = mapper.apply(e);
      return apply.resource;
    }));
  }

  public <B> Program<S, E, B> use(Function<? super A, ? extends Program<S, E, B>> use) {
    return bracket(
        resource,
        ta -> use.apply(ta.first()),
        Resource::release);
  }

  public Resource<S, E, A> orElse(Resource<S, E, A> other) {
    return flatMapError(_ -> other);
  }

  public <F, B> Resource<S, F, B> foldMap(
      Function<? super E, ? extends Resource<S, F, B>> mapError,
      Function<? super A, ? extends Resource<S, F, B>> mapper) {
    return new Resource<>(resource.foldMap(
        error -> {
          var apply = mapError.apply(error);
          return apply.resource;
        },
        ta -> acquireNext(ta, mapper)));
  }

  public <B> Resource<S, E, Tuple2<A, B>> combine(Resource<S, E, B> other) {
    return flatMap(a -> other.map(b -> tuple(a, b)));
  }

  public <B> Resource<S, E, Either<A, B>> either(Resource<S, E, B> other) {
    Program<S, E, Either<Tuple2<A, Program<S, Void, Unit>>, Tuple2<B, Program<S, Void, Unit>>>> foldM =
        this.resource.foldMap(
            _ -> other.resource.map(Either::right),
            success -> success(Either.left(success)));

    return new Resource<>(foldM.map(
        e -> e.fold(
            a -> tuple(Either.left(a.first()), a.second()),
            b -> tuple(Either.right(b.first()), b.second()))));
  }

  public Resource<S, E, A> retry() {
    return retry(1);
  }

  public Resource<S, E, A> retry(int maxRetries) {
    return new Resource<>(resource.retry(maxRetries));
  }

  public Resource<S, E, A> retry(Duration delay) {
    return retry(1, delay);
  }

  public Resource<S, E, A> retry(int maxRetries, Duration delay) {
    return new Resource<>(resource.retry(maxRetries, delay));
  }

  public static <S, E, A> Resource<S, E, A> of(A resource) {
    return of(success(resource));
  }

  public static <S, E, A> Resource<S, E, A> of(Program<S, E, A> resource) {
    return make(resource, _ -> unit());
  }

  public static <S, E, A> Resource<S, E, A> make(
      Program<S, E, A> acquire, Function<? super A, ? extends Program<S, Void, ?>> release) {
    return new Resource<>(chain(acquire, a -> tuple(a, release(a, release))));
  }

  public static <S, E, A> Resource<S, E, A> make(
      Function<? super S, ? extends A> acquire, Function<? super A, ? extends Program<S, Void, ?>> release) {
    return make(access(acquire), release);
  }

  public static <S, E, A extends AutoCloseable> Resource<S, E, A> from(Program<S, E, A> resource) {
    return from(resource, Resource::close);
  }

  public static <S, E, A> Resource<S, E, A> from(Program<S, E, A> resource, Consumer<? super A> release) {
    return make(resource, a -> task(() -> release.accept(a)));
  }

  public static <S, E, A extends AutoCloseable> Resource<S, E, A> from(Function<? super S, ? extends A> mapper) {
    return from(mapper, Resource::close);
  }

  public static <S, E, A> Resource<S, E, A> from(Function<? super S, ? extends A> mapper, Consumer<? super A> release) {
    return from(access(mapper), release);
  }

  // acquires the next resource once the previous one is acquired: if it fails, throws or the
  // mapper throws, the previous resource is released before the error goes on, so nothing leaks.
  // Once both are acquired, the inner one is released first, and the outer one even if it fails
  private static <S, F, T, R> Program<S, F, Tuple2<R, Program<S, Void, Unit>>> acquireNext(
      Tuple2<T, Program<S, Void, Unit>> ta,
      Function<? super T, ? extends Resource<S, F, R>> mapper) {
    return catchAll(suspend(() -> {
          var next = mapper.apply(ta.first());
          return next.resource;
        }),
        error -> pipe(
            release(ta),
            _ -> raise(() -> error)))
        .foldMap(
            error -> pipe(
                release(ta),
                _ -> failure(error)),
            tb -> success(tuple(tb.first(), tb.second().ensuring(ta.second()))));
  }

  // the release program is built when it runs, so if building it throws, it happens inside the
  // finalizer of use, like any other failure of the release
  private static <S, A> Program<S, Void, Unit> release(
      A value, Function<? super A, ? extends Program<S, Void, ?>> release) {
    return suspend(() -> release.apply(value).discard());
  }

  private static <S, E> Program<S, E, Unit> release(Tuple2<?, Program<S, Void, Unit>> tuple) {
    // the release can't fail with an error, see resource, so the mapper is never called
    return tuple.second().mapError(_ -> {
      throw new IllegalStateException("a release can't fail with an error");
    });
  }

  private static void close(AutoCloseable resource) {
    try {
      resource.close();
    } catch (Exception e) {
      Fatal.sneakyThrow(e);
    }
  }
}
