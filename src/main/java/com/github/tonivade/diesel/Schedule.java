/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Combine.chain;
import static com.github.tonivade.diesel.Combine.zip;
import static com.github.tonivade.diesel.Program.success;
import static java.util.Objects.requireNonNull;

import com.github.tonivade.diesel.Schedule.Decision.Continue;
import com.github.tonivade.diesel.Schedule.Decision.Done;
import com.github.tonivade.diesel.impl.Random;

import java.time.Duration;

/**
 * A policy that determines whether a computation should continue and how long
 * it should wait before the next execution.
 *
 * <p>A schedule receives an input value and produces a {@link Decision}
 * describing what should happen next. If the schedule continues, it provides
 * the delay before the next execution and the next schedule to evaluate.
 *
 * <p>Schedules are effectful: evaluating a schedule returns a {@link Program}
 * that can access the computation's state. The schedule itself cannot fail
 * with a typed error.
 *
 * <p>Schedules can be composed to build more complex policies, for example
 * combining exponential backoff with a maximum number of retries.
 *
 * @param <S> the type of the state used by the schedule
 * @param <I> the type of the input received by the schedule
 * @see Program#retry(Schedule)
 * @see Program#repeat(Schedule)
 * @see Resource#retry(Schedule)
 */
@FunctionalInterface
public interface Schedule<S, I> {

  /**
   * Describes whether a schedule is complete or should continue after a delay.
   *
   * @param <S> the type of the state used by the schedule
   * @param <I> the type of the input received by the schedule
   */
  sealed interface Decision<S, I> {

    /**
     * Indicates that the schedule is complete and no further execution should occur.
     *
     * @param <S> the type of the state used by the schedule
     * @param <I> the type of the input received by the schedule
     */
    record Done<S, I>() implements Decision<S, I> {}

    /**
     * Indicates that the computation should continue after a delay, using the next schedule.
     *
     * @param delay the duration to wait before the next execution
     * @param next the schedule to evaluate after the next execution
     * @param <S> the type of the state used by the schedule
     * @param <I> the type of the input received by the schedule
     */
    record Continue<S, I>(Duration delay, Schedule<S, I> next) implements Decision<S, I> {

      public Continue {
        requireNonNull(delay);
        requireNonNull(next);
      }
    }
  }

  /**
   * Evaluates this schedule for the given input.
   *
   * <p>The returned program may access the state but cannot fail through its typed error channel.
   *
   * @param input the result or error from the computation being scheduled
   * @return a program that produces the next scheduling decision
   */
  Program<S, Void, Decision<S, I>> next(I input);

  /**
   * Creates a schedule that completes without allowing another execution.
   *
   * @param <S> the type of the state used by the schedule
   * @param <I> the type of the input received by the schedule
   * @return a schedule that always returns {@link Decision.Done}
   */
  static <S, I> Schedule<S, I> stop() {
    return _ -> success(new Done<>());
  }

  /**
   * Creates a schedule that continues indefinitely without delaying between executions.
   *
   * @param <S> the type of the state used by the schedule
   * @param <I> the type of the input received by the schedule
   * @return a schedule that continues forever with zero delay
   */
  static <S, I> Schedule<S, I> forever() {
    return spaced(Duration.ZERO);
  }

  /**
   * Creates a schedule that continues indefinitely, waiting the same duration between executions.
   *
   * @param delay the duration to wait before each subsequent execution
   * @param <S> the type of the state used by the schedule
   * @param <I> the type of the input received by the schedule
   * @return a schedule with a constant delay between executions
   */
  static <S, I> Schedule<S, I> spaced(Duration delay) {
    return _ -> success(new Continue<>(delay, spaced(delay)));
  }

  /**
   * Creates a schedule with an exponentially increasing delay.
   *
   * <p>The first delay is {@code base}; each subsequent delay is the previous delay multiplied
   * by {@code factor}.
   *
   * @param base the delay before the first subsequent execution
   * @param factor the multiplier applied to each delay to obtain the next one
   * @param <S> the type of the state used by the schedule
   * @param <I> the type of the input received by the schedule
   * @return a schedule with an exponentially increasing delay
   */
  static <S, I> Schedule<S, I> exponential(Duration base, double factor) {
    return _ -> success(new Continue<>(base, exponential(scale(base, factor), factor)));
  }

  /**
   * Adds random jitter to the delays produced by the given schedule.
   *
   * <p>Each delay is multiplied by a random value in the range
   * {@code [1 - factor, 1 + factor]}. Evaluating the resulting schedule requires the state to
   * implement {@link Random.Service}.
   *
   * @param schedule the schedule whose delays are adjusted
   * @param factor the maximum relative variation applied to each delay
   * @param <S> the type of the state, which must implement {@link Random.Service}
   * @param <I> the type of the input received by the schedule
   * @return a schedule with randomized delays
   */
  static <S extends Random.Service, I> Schedule<S, I> jittered(Schedule<S, I> schedule, double factor) {
    return input -> schedule.next(input).flatMap(decision -> switch (decision) {
      case Done<S, I> done -> success(done);
      case Continue<S, I>(var delay, var next) ->
        chain(
            Random.nextDouble(),
            random -> {
              var multiplier = 1 + (random * 2 - 1) * factor;
              return new Continue<>(
                  scale(delay, multiplier),
                  jittered(next, factor));
            });
    });
  }

  /**
   * Creates a schedule that continues at most the given number of times, with no delay.
   *
   * <p>A non-positive number produces a schedule that is immediately complete.
   *
   * @param n the maximum number of subsequent executions
   * @param <S> the type of the state used by the schedule
   * @param <I> the type of the input received by the schedule
   * @return a schedule that continues at most {@code n} times
   */
  static <S, I> Schedule<S, I> recurs(int n) {
    if (n <= 0) {
      return stop();
    }
    return _ -> success(new Continue<>(Duration.ZERO, recurs(n - 1)));
  }

  /**
   * Combines this schedule with another, continuing only while both schedules continue.
   *
   * <p>The resulting delay is the greater of the two delays. Both schedules receive the same
   * input, and their next schedules are combined in the same way.
   *
   * @param other the schedule to combine with this one
   * @return a schedule that completes as soon as either constituent schedule completes
   */
  default Schedule<S, I> and(Schedule<S, I> other) {
    return input ->
      zip(
        this.next(input),
        other.next(input),
        (decision1, decision2) -> switch (decision1) {
          case Done<S, I> done -> done;
          case Continue<S, I>(var d1, var n1) -> switch (decision2) {
            case Done<S, I> done -> done;
            case Continue<S, I>(var d2, var n2) -> new Continue<>(d1.compareTo(d2) >= 0 ? d1 : d2, n1.and(n2));
          };
        }
      );
  }

  private static Duration scale(Duration d, double f) {
    return Duration.ofNanos(Math.round(d.toNanos() * f));
  }
}
