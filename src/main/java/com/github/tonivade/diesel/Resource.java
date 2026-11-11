/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Combine.bracketFull;
import static com.github.tonivade.diesel.Combine.catchAll;
import static com.github.tonivade.diesel.Combine.chain;
import static com.github.tonivade.diesel.Combine.ensuring;
import static com.github.tonivade.diesel.Combine.pipe;
import static com.github.tonivade.diesel.Program.access;
import static com.github.tonivade.diesel.Program.failure;
import static com.github.tonivade.diesel.Program.join;
import static com.github.tonivade.diesel.Program.never;
import static com.github.tonivade.diesel.Program.raise;
import static com.github.tonivade.diesel.Program.sleep;
import static com.github.tonivade.diesel.Program.success;
import static com.github.tonivade.diesel.Program.supply;
import static com.github.tonivade.diesel.Program.suspend;
import static com.github.tonivade.diesel.Program.task;
import static com.github.tonivade.diesel.Program.unit;
import static com.github.tonivade.diesel.Program.widen;
import static com.github.tonivade.diesel.Tuple2.tuple;
import static java.util.Objects.requireNonNull;

import com.github.tonivade.diesel.Program.Poll;
import com.github.tonivade.purefun.Kind;

import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A resource whose acquisition and release are managed together.
 *
 * <p>A resource describes how to acquire a value and how to release it when it is no longer
 * needed. Acquisition is deferred until {@link #use(Function)} is executed. Each execution
 * evaluates the acquisition program again.
 *
 * <p>Resources can be composed using {@link #map(Function)} and {@link #flatMap(Function)}.
 * When resources are composed, they are released in reverse order of acquisition, ensuring that
 * dependent resources are released before the resources they depend on.
 *
 * <p>Resources are always released after use, including when the computation fails, throws an
 * exception, or is cancelled.
 *
 * <p>Acquisition can't be cancelled: once {@link #use(Function)} starts acquiring, the
 * acquisition runs to the end, so that whatever is acquired is also released. The only exceptions
 * are the waits that a resource made with {@link #makeFull(Function, Function)} wraps with its
 * {@link Poll}, and the delays of {@link #retry(int, Delay)}: a cancellation during them stops
 * the acquisition, and the resources already acquired are released.
 *
 * <p>A release action cannot fail through the program's error channel, but it may throw an
 * exception. If a release action throws, that exception replaces the result of the computation
 * being finalized. Like in try-with-resources, if the computation already threw an exception, or
 * an inner release threw one, that one goes on and the next ones are added to it as suppressed.
 * If the computation failed with an error instead, the exception of the release replaces that
 * error, which is lost, because an error can't be added as suppressed.
 *
 * <p>A program forked during the acquisition isn't cancelled with the program that uses the
 * resource, because it's forked in an uncancelable region. If the resource starts a background
 * program, its release must cancel it.
 *
 * @param <S> the type of the state
 * @param <E> the type of the error
 * @param <A> the type of the acquired resource
 */
public final class Resource<S, E, A> implements Kind<Resource<S, E, ?>, A> {

  // builds the acquisition with the Poll of the uncancelable region that use opens: it returns the
  // acquired value and the program that releases it. The release can't fail with an error, like
  // the programs returned by fold, so changing the error type of the resource doesn't affect it
  private final Function<Poll, Program<S, E, Tuple2<A, Program<S, Void, Unit>>>> acquire;

  // the acquisition is built lazily, when it runs: combinators like flatMap call the acquisition of
  // the previous resource, so building it eagerly would nest a Java call for each step of a long
  // chain, like a map in a loop, and overflow the stack
  private Resource(Function<Poll, Program<S, E, Tuple2<A, Program<S, Void, Unit>>>> acquire) {
    requireNonNull(acquire);
    this.acquire = poll -> suspend(() -> acquire.apply(poll));
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
    return evalMap(a -> supply(() -> mapper.apply(a)));
  }

  /**
   * Runs a program with the acquired value, as part of the acquisition, while preserving its
   * release action.
   *
   * <p>Like the rest of the acquisition, the program can't be cancelled. If it fails, or throws
   * an exception, the original resource is released before the failure is propagated.
   *
   * @param mapper the function used to create a program from the acquired value
   * @param <B> the type of the result of the program
   * @return a resource containing the result of the program
   */
  public <B> Resource<S, E, B> evalMap(Function<? super A, ? extends Program<S, E, B>> mapper) {
    // keeps the same release instead of adding a new resource with an empty one, but the release
    // isn't installed until the whole acquisition returns, so it must run here if the program fails
    return new Resource<>(poll -> pipe(
        acquire.apply(poll),
        ta -> releaseOnFailure(
            suspend(() -> mapper.apply(ta.first()).map(b -> tuple(b, ta.second()))),
            ta.second())));
  }

  /**
   * Runs a program with the acquired value, as part of the acquisition, and keeps the acquired
   * value, like {@link #evalMap(Function)} but discarding the result of the program.
   *
   * @param mapper the function used to create a program from the acquired value
   * @return a resource containing the same value
   */
  public Resource<S, E, A> evalTap(Function<? super A, ? extends Program<S, E, ?>> mapper) {
    return evalMap(a -> mapper.apply(a).map(_ -> a));
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
    return new Resource<>(poll -> acquire.apply(poll).mapError(mapper));
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
    return new Resource<>(poll -> acquire.apply(poll).flatMap(ta -> acquireNext(poll, ta, mapper)));
  }

  /**
   * Recovers from an acquisition error by creating another resource.
   *
   * <p>If this resource is acquired successfully, the mapper is not evaluated. If acquisition
   * fails, the mapper is applied to the error and the resulting resource is acquired instead.
   *
   * <p>Only an error of the acquisition is handled: an exception thrown during the acquisition is
   * propagated as it is.
   *
   * @param mapper the function used to create a resource from the acquisition error
   * @param <F> the error type of the resulting resource
   * @return a resource that falls back to another resource when acquisition fails
   */
  public <F> Resource<S, F, A> flatMapError(Function<? super E, ? extends Resource<S, F, A>> mapper) {
    return new Resource<>(poll -> acquire.apply(poll).flatMapError(e -> {
      var next = mapper.apply(e);
      return next.acquire.apply(poll);
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
    return bracketFull(
        acquire,
        ta -> use.apply(ta.first()),
        Tuple2::second);
  }

  /**
   * Acquires the resource and keeps it until the program is cancelled, then releases it.
   *
   * <p>It's useful for resources that do their work by themselves, like a server or a
   * {@link #background(Program) background} program, that must run until the application stops.
   *
   * @param <B> the type of the result, which is never produced
   * @return a program that never completes successfully
   */
  public <B> Program<S, E, B> useForever() {
    return use(_ -> never());
  }

  /**
   * Uses this resource if its acquisition succeeds, or falls back to another resource otherwise.
   *
   * <p>The alternative resource is acquired only when this resource fails during acquisition.
   * Errors from the alternative resource are propagated normally.
   *
   * <p>Only an error of the acquisition is handled: an exception thrown during the acquisition is
   * propagated as it is.
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
   * <p>Only an error of the acquisition is handled: an exception thrown during the acquisition is
   * propagated as it is.
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
    return new Resource<>(poll -> acquire.apply(poll).foldMap(
        error -> {
          var next = mapError.apply(error);
          return next.acquire.apply(poll);
        },
        ta -> acquireNext(poll, ta, mapper)));
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
   * <p>If both acquisitions fail, the resulting resource fails with the error of the alternative
   * one, and the error of this resource is discarded.
   *
   * <p>Only an error of the acquisition is handled: an exception thrown during the acquisition is
   * propagated as it is.
   *
   * @param other the alternative resource to acquire when this resource fails
   * @param <B> the type of the alternative resource
   * @return a resource containing the value of the successful acquisition
   */
  public <B> Resource<S, E, Either<A, B>> either(Resource<S, E, B> other) {
    return new Resource<>(poll -> {
      Program<S, E, Either<Tuple2<A, Program<S, Void, Unit>>, Tuple2<B, Program<S, Void, Unit>>>> foldM =
          acquire.apply(poll).foldMap(
              _ -> other.acquire.apply(poll).map(Either::right),
              acquired -> success(Either.left(acquired)));

      return foldM.map(
          e -> e.fold(
              a -> tuple(Either.left(a.first()), a.second()),
              b -> tuple(Either.right(b.first()), b.second())));
    });
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
    return retry(Schedule.recurs(maxRetries));
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
  public Resource<S, E, A> retry(Delay delay) {
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
   * <p>Unlike the rest of the acquisition, the delays can be cancelled.
   *
   * @param maxRetries the maximum number of additional acquisition attempts
   * @param delay the duration to wait between attempts
   * @return a resource that retries acquisition up to the specified number of times
   */
  public Resource<S, E, A> retry(int maxRetries, Delay delay) {
    return retry(Schedule.<S, E>recurs(maxRetries).and(Schedule.spaced(delay)));
  }

  /**
   * Retries resource acquisition according to the specified schedule whenever
   * acquisition fails with a typed error.
   *
   * <p>After a failed acquisition, the schedule is evaluated with the error.
   * If the schedule is done, the acquisition fails with the original error.
   * If the schedule continues, its delay is applied before attempting to
   * acquire the resource again.
   *
   * <p>Any resources acquired during a failed attempt are released before
   * the next attempt begins. Exceptions thrown during acquisition are not
   * retried.
   *
   * <p>Unlike the rest of the acquisition, the delays between attempts can
   * be cancelled.
   *
   * @param schedule the policy that determines whether to retry and the delay
   *        before the next acquisition attempt
   * @return a resource that retries acquisition according to the specified schedule
   */
  public Resource<S, E, A> retry(Schedule<S, E> schedule) {
    return new Resource<>(poll -> retryLoop(poll, schedule));
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
    return eval(success(resource));
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
  public static <S, E, A> Resource<S, E, A> eval(Program<S, E, A> resource) {
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
    return makeFull(_ -> acquire, release);
  }

  /**
   * Creates a resource whose acquisition can make some of its steps cancelable, and a release
   * function.
   *
   * <p>The acquisition runs in an uncancelable region, so once the value is acquired there is no
   * window where it could be lost before its release is guaranteed. The acquire function receives
   * a {@link Poll}: only the programs wrapped with it can be cancelled, like waiting for a
   * connection from an exhausted pool.
   *
   * <p>Wrap only the steps that wait, never the one that actually obtains the value: if a
   * cancellation arrives inside a polled program, the value isn't acquired yet, so it isn't
   * released either. So don't write {@code poll.apply(pool.open())}: a connection opened just when
   * the cancellation arrives would be lost without being released. If the resource is composed with
   * others, the resources already acquired are released.
   *
   * <p>A program forked during the acquisition, even inside a polled program, isn't cancelled with
   * the program that uses the resource, so the release must cancel it.
   *
   * <pre>{@code
   * Resource<S, E, Connection> connection = Resource.makeFull(
   *     poll -> pipe(
   *         poll.apply(pool.awaitAvailable()),  // only the wait is cancelable
   *         _ -> pool.open()),
   *     conn -> task(conn::close));
   * }</pre>
   *
   * @param acquire the function that receives the {@link Poll} and returns the acquisition
   * @param release the function used to release the acquired value
   * @param <S> the type of the state
   * @param <E> the type of the acquisition error
   * @param <A> the type of the acquired value
   * @return a resource that manages the acquired value and its release
   */
  public static <S, E, A> Resource<S, E, A> makeFull(
      Function<? super Poll, ? extends Program<S, E, A>> acquire,
      Function<? super A, ? extends Program<S, Void, ?>> release) {
    return new Resource<>(poll -> acquire(poll, acquire, release));
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

  /**
   * Creates a resource that runs a program in the background while the resource is used.
   *
   * <p>The program is forked when the resource is acquired, using virtual threads, and it's
   * cancelled when the resource is released, whether {@code use} succeeds, fails, throws an
   * exception or is cancelled. The release waits until the program has stopped and its finalizers
   * have run, so the resources released after it, like the ones it depends on when it's composed
   * with {@link #flatMap(Function)}, can't be used by it anymore. If the program has an uncancelable
   * part, the release waits for it to finish.
   *
   * <p>The acquired value is a program that waits for the background program and returns its
   * result. The release ignores how the background program ended, so a failure or an exception
   * of it is only seen by joining it.
   *
   * <pre>{@code
   * Resource<S, E, Connection> resource = connection.flatMap(
   *     conn -> Resource.background(heartbeat(conn)).map(_ -> conn));
   * // when it's released, the heartbeat is cancelled and stops before the connection is closed
   * }</pre>
   *
   * @param program the program to run in the background
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result of the program
   * @return a resource whose value is a program that joins the background program
   * @see Program#fork()
   */
  public static <S, E, T> Resource<S, E, Program<S, E, T>> background(Program<S, E, T> program) {
    return background(program, Concurrent.DEFAULT_EXECUTOR);
  }

  /**
   * Creates a resource that runs a program in the background, using the given executor, while the
   * resource is used.
   *
   * <p>Like {@link #background(Program)}, but the program is forked using the given executor.
   *
   * @param program the program to run in the background
   * @param executor the executor used to fork the program
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result of the program
   * @return a resource whose value is a program that joins the background program
   * @see Program#fork(Executor)
   */
  public static <S, E, T> Resource<S, E, Program<S, E, T>> background(Program<S, E, T> program, Executor executor) {
    return new Resource<>(_ -> chain(
        program.fork(executor),
        fiber -> tuple(fiber.<S>join(), fiber.<S>cancel())));
  }

  private Program<S, E, Tuple2<A, Program<S, Void, Unit>>> retryLoop(Poll poll, Schedule<S, E> schedule) {
    // only the wait between attempts is cancelable: a failed attempt has already released what
    // it acquired, so nothing is lost if it's cancelled
    return suspend(
      () -> acquire.apply(poll).flatMapError(error ->
        pipe(
          widen(schedule.next(error)),
          decision -> decision.fold(
            () -> failure(error),
            (delay, next) ->
              pipe(
                poll.apply(sleep(delay)),
                _ -> retryLoop(poll, next))
              )
        )
      )
    );
  }

  // acquires the next resource once the previous one is acquired: if it fails, throws or the
  // mapper throws, the previous resource is released before the error goes on, so nothing leaks.
  // Once both are acquired, the inner one is released first, and the outer one even if it fails
  // A cancellation from a poll inside the next acquisition is handled the same way: it's raised
  // in the closed part of the region, so the catchAll can release the previous resource
  private static <S, F, T, R> Program<S, F, Tuple2<R, Program<S, Void, Unit>>> acquireNext(
      Poll poll,
      Tuple2<T, Program<S, Void, Unit>> ta,
      Function<? super T, ? extends Resource<S, F, R>> mapper) {
    return releaseOnFailure(suspend(() -> {
          var next = mapper.apply(ta.first());
          return next.acquire.apply(poll);
        }),
        ta.second())
        .map(tb -> tuple(tb.first(), tb.second().ensuring(ta.second())));
  }

  // like releaseOnException, but it also releases when the program fails with an error
  private static <S, E, T> Program<S, E, T> releaseOnFailure(
      Program<S, E, T> program, Program<S, Void, Unit> release) {
    return releaseOnException(program, release)
        .flatMapError(error -> pipe(widen(release), _ -> failure(error)));
  }

  // if the program throws, runs the release of the resource already acquired, and the exception
  // goes on, with the one of the release added as suppressed if it throws too
  private static <S, E, T> Program<S, E, T> releaseOnException(
      Program<S, E, T> program, Program<S, Void, Unit> release) {
    return catchAll(
        program,
        error -> ensuring(raise(() -> error), release)
      );
  }

  // the acquisition is built when it runs, inside the region, so if building it throws, it's like
  // any other failure of the acquisition
  private static <S, E, A> Program<S, E, Tuple2<A, Program<S, Void, Unit>>> acquire(
      Poll poll,
      Function<? super Poll, ? extends Program<S, E, A>> acquire,
      Function<? super A, ? extends Program<S, Void, ?>> release) {
    return chain(
        suspend(() -> acquire.apply(poll)),
        a -> tuple(a, release(a, release)));
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
