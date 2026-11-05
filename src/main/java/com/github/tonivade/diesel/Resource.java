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
import static com.github.tonivade.diesel.Program.widen;
import static com.github.tonivade.diesel.Tuple2.tuple;
import static java.util.Objects.requireNonNull;

import com.github.tonivade.purefun.Kind;

import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A resource whose acquisition and release are managed together.
 *
 * <p>A resource describes how to acquire a value and how to release it when it is no longer
 * needed. Acquisition is deferred until {@link #use(Function)} is executed, and each execution
 * runs the acquisition again, so the same resource can be used more than once.
 *
 * <p>Resources can be composed using {@link #map(Function)} and {@link #flatMap(Function)}.
 * When resources are composed, they are released in reverse order of acquisition, ensuring that
 * dependent resources are released before the resources they depend on.
 *
 * <p>Resources are always released after use, including when the computation fails, throws an
 * exception, or is cancelled.
 *
 * <p>Acquisition can't be cancelled: once {@link #use(Function)} starts acquiring, the whole
 * acquisition runs to the end, including the delays of {@link #retry(int, Duration)}, so that
 * whatever is acquired is also released.
 *
 * <p>A release action cannot fail through the program's error channel, but it may throw an
 * exception. If a release action throws, that exception replaces the result of the computation
 * being finalized.
 *
 * @param <S> the type of the state
 * @param <E> the type of the error
 * @param <A> the type of the acquired resource
 */
public final class Resource<S, E, A> implements Kind<Resource<S, E, ?>, A> {

  // the acquired value and the program that releases it. The release can't fail with an error, like
  // the programs returned by fold, so changing the error type of the resource doesn't affect it
  private final Program<S, E, Tuple2<A, Program<S, Void, Unit>>> resource;

  private Resource(Program<S, E, Tuple2<A, Program<S, Void, Unit>>> resource) {
    this.resource = requireNonNull(resource);
  }

  /**
   * Maps the acquired value while preserving its release action.
   *
   * <p>The mapper is applied after acquisition. The original resource is still released when
   * the resulting resource is used.
   *
   * @param mapper the function used to transform the acquired value
   * @param <B> the type of the mapped value
   * @return a resource containing the mapped value
   */
  public <B> Resource<S, E, B> map(Function<? super A, ? extends B> mapper) {
    return flatMap(a -> of(success(mapper.apply(a))));
  }

  /**
   * Maps the error produced during resource acquisition.
   *
   * <p>The release action is preserved unchanged.
   *
   * @param mapper the function used to transform the error
   * @param <F> the type of the mapped error
   * @return a resource with the mapped error type
   */
  public <F> Resource<S, F, A> mapError(Function<? super E, ? extends F> mapper) {
    return new Resource<>(resource.mapError(mapper));
  }

  /**
   * Composes this resource with another resource that depends on its acquired value.
   *
   * <p>The mapper is applied after the first resource has been acquired. If acquiring the next
   * resource fails, throws an exception, or the mapper itself throws, the first resource is
   * released before the failure is propagated.
   *
   * <p>When both resources are acquired successfully, the second resource is released before
   * the first one.
   *
   * @param mapper the function used to create the next resource
   * @param <B> the type of the resulting resource
   * @return a resource representing the composition of both resources
   */
  public <B> Resource<S, E, B> flatMap(Function<? super A, ? extends Resource<S, E, B>> mapper) {
    return new Resource<>(resource.flatMap(ta -> acquireNext(ta, mapper)));
  }

  /**
   * Recovers from an acquisition error by creating another resource.
   *
   * <p>If this resource is acquired successfully, the mapper is not evaluated. If acquisition
   * fails, the mapper is applied to the error and the resulting resource is acquired instead.
   *
   * @param mapper the function used to create a resource from the acquisition error
   * @param <F> the error type of the resulting resource
   * @return a resource that falls back to another resource when acquisition fails
   */
  public <F> Resource<S, F, A> flatMapError(Function<? super E, ? extends Resource<S, F, A>> mapper) {
    return new Resource<>(resource.flatMapError(e -> {
      var apply = mapper.apply(e);
      return apply.resource;
    }));
  }

  /**
   * Acquires the resource, uses it, and releases it when the computation finishes.
   *
   * <p>The release action is executed regardless of whether the use computation succeeds,
   * fails, throws an exception, or is cancelled.
   *
   * <p>The resource is acquired each time the returned program is evaluated.
   *
   * @param use the function used to create a program from the acquired value
   * @param <B> the type of the result
   * @return a program that manages the complete resource lifecycle
   */
  public <B> Program<S, E, B> use(Function<? super A, ? extends Program<S, E, B>> use) {
    return bracket(
        resource,
        ta -> use.apply(ta.first()),
        Tuple2::second);
  }

  /**
   * Uses this resource if its acquisition succeeds, or falls back to another resource otherwise.
   *
   * <p>The alternative resource is acquired only when this resource fails during acquisition.
   * Errors from the alternative resource are propagated normally.
   *
   * @param other the alternative resource to acquire when this resource fails
   * @return a resource that falls back to the given alternative
   */
  public Resource<S, E, A> orElse(Resource<S, E, A> other) {
    return flatMapError(_ -> other);
  }

  /**
   * Folds both the acquisition error and the acquired value into a new resource.
   *
   * <p>If acquisition fails, {@code mapError} is applied to the error and the resource it returns
   * is acquired instead. If acquisition succeeds, {@code mapper} is applied to the acquired value,
   * like {@link #flatMap(Function)}: both resources are released, the new one first.
   *
   * @param mapError the function used to handle an acquisition error
   * @param mapper the function used to transform an acquired value into another resource
   * @param <F> the error type of the resulting resource
   * @param <B> the value type of the resulting resource
   * @return a resource representing the folded computation
   */
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

  /**
   * Combines this resource with another resource.
   *
   * <p>Both resources are acquired before the resulting resource is used. The second resource
   * is released before the first one.
   *
   * @param other the resource to combine with this one
   * @param <B> the type of the other resource
   * @return a resource containing both acquired values as a tuple
   */
  public <B> Resource<S, E, Tuple2<A, B>> combine(Resource<S, E, B> other) {
    return flatMap(a -> other.map(b -> tuple(a, b)));
  }

  /**
   * Tries this resource first and falls back to another resource if acquisition fails.
   *
   * <p>The resulting resource contains an {@link Either#left(Object)} when this resource
   * succeeds, or an {@link Either#right(Object)} when the alternative resource succeeds.
   *
   * <p>Only the successfully acquired resource is released.
   *
   * @param other the alternative resource to acquire when this resource fails
   * @param <B> the type of the alternative resource
   * @return a resource containing the value of the successful acquisition
   */
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

  /**
   * Retries resource acquisition once when it fails with an error.
   *
   * <p>Each retry performs a new acquisition attempt. Any resources acquired during a failed
   * attempt are released before the next attempt begins. Exceptions thrown during acquisition
   * are not retried.
   *
   * @return a resource that retries acquisition once
   */
  public Resource<S, E, A> retry() {
    return retry(1);
  }

  /**
   * Retries resource acquisition when it fails with an error, up to {@code maxRetries} times.
   *
   * <p>The initial acquisition is followed by at most {@code maxRetries} additional attempts.
   * Any resources acquired during a failed attempt are released before the next attempt begins.
   * Exceptions thrown during acquisition are not retried.
   *
   * @param maxRetries the maximum number of additional acquisition attempts
   * @return a resource that retries acquisition up to the specified number of times
   */
  public Resource<S, E, A> retry(int maxRetries) {
    return new Resource<>(resource.retry(maxRetries));
  }

  /**
   * Retries resource acquisition once when it fails with an error, waiting for the specified
   * duration between attempts.
   *
   * <p>Any resources acquired during the failed attempt are released before the next attempt
   * begins. Exceptions thrown during acquisition are not retried.
   *
   * @param delay the duration to wait before retrying
   * @return a resource that retries acquisition once after the specified delay
   */
  public Resource<S, E, A> retry(Duration delay) {
    return retry(1, delay);
  }

  /**
   * Retries resource acquisition when it fails with an error, up to {@code maxRetries} times,
   * waiting for the specified duration between attempts.
   *
   * <p>The initial acquisition is followed by at most {@code maxRetries} additional attempts.
   * Any resources acquired during a failed attempt are released before the next attempt begins.
   * Exceptions thrown during acquisition are not retried.
   *
   * @param maxRetries the maximum number of additional acquisition attempts
   * @param delay the duration to wait between attempts
   * @return a resource that retries acquisition up to the specified number of times
   */
  public Resource<S, E, A> retry(int maxRetries, Duration delay) {
    return new Resource<>(resource.retry(maxRetries, delay));
  }

  /**
   * Creates a resource from an already available value.
   *
   * <p>The value is not acquired through a program and no release action is performed.
   *
   * @param resource the value to wrap
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <A> the type of the value
   * @return a resource containing the given value
   */
  public static <S, E, A> Resource<S, E, A> of(A resource) {
    return of(success(resource));
  }

  /**
   * Creates a resource from a program that acquires its value.
   *
   * <p>No release action is performed when the resource is used.
   *
   * @param resource the program that acquires the value
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <A> the type of the acquired value
   * @return a resource wrapping the given acquisition program
   */
  public static <S, E, A> Resource<S, E, A> of(Program<S, E, A> resource) {
    return make(resource, _ -> unit());
  }

  /**
   * Creates a resource from an acquisition program and a release function.
   *
   * <p>The release function is evaluated when the resource is released, not when this method
   * is called. The resulting release program cannot fail through its error channel, but it may
   * throw an exception.
   *
   * @param acquire the program that acquires the resource
   * @param release the function used to release the acquired value
   * @param <S> the type of the state
   * @param <E> the type of the acquisition error
   * @param <A> the type of the acquired value
   * @return a resource that manages the acquired value and its release
   */
  public static <S, E, A> Resource<S, E, A> make(
      Program<S, E, A> acquire, Function<? super A, ? extends Program<S, Void, ?>> release) {
    return new Resource<>(chain(acquire, a -> tuple(a, release(a, release))));
  }

  /**
   * Creates a resource using the current program state to acquire its value, and a release
   * function.
   *
   * <p>The acquire function is evaluated when the resource is used. The release function is
   * evaluated when the resource is released, and the program it returns can't fail with an error,
   * only throw an exception.
   *
   * @param acquire the function used to acquire the value from the current state
   * @param release the function used to release the acquired value
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <A> the type of the acquired value
   * @return a resource that manages the acquired value and its release
   */
  public static <S, E, A> Resource<S, E, A> make(
      Function<? super S, ? extends A> acquire, Function<? super A, ? extends Program<S, Void, ?>> release) {
    return make(access(acquire), release);
  }

  /**
   * Creates a resource from a program that acquires an {@link AutoCloseable} value.
   *
   * <p>The acquired value is automatically closed when the resource is released.
   *
   * @param resource the program that acquires the closeable value
   * @param <S> the type of the state
   * @param <E> the type of the acquisition error
   * @param <A> the type of the acquired value
   * @return a resource that closes the acquired value when released
   */
  public static <S, E, A extends AutoCloseable> Resource<S, E, A> from(Program<S, E, A> resource) {
    return from(resource, Resource::close);
  }

  /**
   * Creates a resource from an acquisition program and a release consumer.
   *
   * <p>The consumer is invoked with the acquired value when the resource is released.
   * Exceptions thrown by the consumer are propagated as exceptions from the finalizer.
   *
   * @param resource the program that acquires the value
   * @param release the consumer used to release the acquired value
   * @param <S> the type of the state
   * @param <E> the type of the acquisition error
   * @param <A> the type of the acquired value
   * @return a resource that manages the acquired value and its release
   */
  public static <S, E, A> Resource<S, E, A> from(Program<S, E, A> resource, Consumer<? super A> release) {
    return make(resource, a -> task(() -> release.accept(a)));
  }

  /**
   * Creates a resource using the current program state to acquire an {@link AutoCloseable} value.
   *
   * <p>The acquired value is automatically closed when the resource is released.
   *
   * @param mapper the function used to acquire the closeable value from the current state
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <A> the type of the acquired value
   * @return a resource that closes the acquired value when released
   */
  public static <S, E, A extends AutoCloseable> Resource<S, E, A> from(Function<? super S, ? extends A> mapper) {
    return from(mapper, Resource::close);
  }

  /**
   * Creates a resource using the current program state to acquire its value.
   *
   * <p>The mapper is evaluated when the resource is used, and the release consumer is invoked
   * with the acquired value when the resource is released.
   *
   * @param mapper the function used to acquire the value from the current state
   * @param release the consumer used to release the acquired value
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <A> the type of the acquired value
   * @return a resource that manages the acquired value and its release
   */
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
            widen(ta.second()),
            _ -> raise(() -> error)))
        .foldMap(
            error -> pipe(
                widen(ta.second()),
                _ -> failure(error)),
            tb -> success(tuple(tb.first(), tb.second().ensuring(ta.second()))));
  }

  // the release program is built when it runs, so if building it throws, it happens inside the
  // finalizer of use, like any other failure of the release
  private static <S, A> Program<S, Void, Unit> release(
      A value, Function<? super A, ? extends Program<S, Void, ?>> release) {
    return suspend(() -> release.apply(value).discard());
  }

  private static void close(AutoCloseable resource) {
    try {
      resource.close();
    } catch (Exception e) {
      Fatal.sneakyThrow(e);
    }
  }
}
