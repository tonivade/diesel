/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Combine.bracket;
import static com.github.tonivade.diesel.Combine.catchAll;
import static com.github.tonivade.diesel.Combine.pipe;
import static com.github.tonivade.diesel.Program.failure;
import static com.github.tonivade.diesel.Program.raise;
import static com.github.tonivade.diesel.Program.success;
import static com.github.tonivade.diesel.Program.suspend;
import static com.github.tonivade.diesel.Program.task;
import static com.github.tonivade.diesel.Tuple2.tuple;
import static java.util.Objects.requireNonNull;

import com.github.tonivade.purefun.Kind;

import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Function;

public final class Resource<S, E, A> implements Kind<Resource<S, E, ?>, A> {

  private final Program<S, E, Tuple2<A, Consumer<? super A>>> resource;

  private Resource(Program<S, E, Tuple2<A, Consumer<? super A>>> resource) {
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
        ta -> task(() -> release(ta)));
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
    Program<S, E, Either<Tuple2<A, Consumer<? super A>>, Tuple2<B, Consumer<? super B>>>> foldM =
        this.resource.foldMap(
            _ -> other.resource.map(Either::right),
            success -> success(Either.left(success)));

    return new Resource<>(foldM.map(
        e -> e.fold(
            a -> tuple(Either.left(a.first()), _ -> release(a)),
            b -> tuple(Either.right(b.first()), _ -> release(b)))));
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
    return from(resource, _ -> {});
  }

  public static <S, E, A extends AutoCloseable> Resource<S, E, A> from(Program<S, E, A> resource) {
    return from(resource, Resource::close);
  }

  public static <S, E, A> Resource<S, E, A> from(Program<S, E, A> resource, Consumer<? super A> release) {
    return new Resource<>(resource.map(a -> tuple(a, release)));
  }

  public static <S, E, A extends AutoCloseable> Resource<S, E, A> from(Function<? super S, ? extends A> mapper) {
    return from(mapper, Resource::close);
  }

  public static <S, E, A> Resource<S, E, A> from(Function<? super S, ? extends A> mapper, Consumer<? super A> release) {
    return new Resource<>(Program.<S, E, A>access(mapper).map(y -> tuple(y, release)));
  }

  // acquires the next resource once the previous one is acquired: if it fails, throws or the
  // mapper throws, the previous resource is released before the error goes on, so nothing leaks
  private static <S, F, T, R> Program<S, F, Tuple2<R, Consumer<? super R>>> acquireNext(
      Tuple2<T, Consumer<? super T>> ta,
      Function<? super T, ? extends Resource<S, F, R>> mapper) {
    return catchAll(suspend(() -> {
          var next = mapper.apply(ta.first());
          return next.resource;
        }),
        error -> pipe(
            task(() -> release(ta)),
            _ -> raise(() -> error)))
        .foldMap(
            error -> pipe(
                task(() -> release(ta)),
                _ -> failure(error)),
            tb -> success(tuple(tb.first(), releaseAndThen(ta, tb))));
  }

  private static <X, T, R> Consumer<X> releaseAndThen(
      Tuple2<T, Consumer<? super T>> outter, Tuple2<R, Consumer<? super R>> inner) {
    return _ -> {
      try {
        release(inner);
      } finally {
        release(outter);
      }
    };
  }

  private static void close(AutoCloseable resource) {
    try {
      resource.close();
    } catch (Exception e) {
      Fatal.sneakyThrow(e);
    }
  }

  private static <T> void release(Tuple2<T, Consumer<? super T>> tuple) {
    tuple.second().accept(tuple.first());
  }
}
