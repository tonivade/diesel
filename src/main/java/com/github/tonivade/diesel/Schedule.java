/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Combine.chain;
import static com.github.tonivade.diesel.Program.success;
import static java.util.Objects.requireNonNull;

import com.github.tonivade.diesel.Schedule.Decision.Continue;
import com.github.tonivade.diesel.Schedule.Decision.Done;

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

    @SuppressWarnings("unchecked")
    static <I, S> Decision<S, I> done() {
      return (Decision<S, I>) Done.DONE;
    }

    /**
     * Indicates that the schedule is complete and no further execution should occur.
     *
     * @param <S> the type of the state used by the schedule
     * @param <I> the type of the input received by the schedule
     */
    final class Done<S, I> implements Decision<S, I> {

      private static final Decision<?, ?> DONE = new Done<>();

      private Done() {
        // nothing to do
      }
    }

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
        if (delay.isNegative()) {
          throw new IllegalArgumentException("delay cannot be negative");
        }
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
    return _ -> success(Decision.done());
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
   * <p>The value of factor must be greater than 0.
   * <ul>
   * <li>with a factor &gt; 0 and  &lt; 1, the delay will decrese with retries
   * <li>with a factor equals to 1, the delay will always the same
   * <li>with a factor &gt; 1, the delay will increase with retries
   * </ul>
   *
   * @param base the delay before the first subsequent execution
   * @param factor the multiplier applied to each delay to obtain the next one
   * @param <S> the type of the state used by the schedule
   * @param <I> the type of the input received by the schedule
   * @return a schedule with an exponentially increasing delay
   */
  static <S, I> Schedule<S, I> exponential(Duration base, double factor) {
    if (base.isNegative()) {
      throw new IllegalArgumentException("delay cannot be negative");
    }
    if (!Double.isFinite(factor)) {
      throw new IllegalArgumentException("factor must be a positive value");
    }
    if (factor <= 0) {
      throw new IllegalArgumentException("factor must be a positive value");
    }
    return _ -> success(new Continue<>(base, exponential(scale(base, factor), factor)));
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
    return input -> next(input).flatMap(first ->
      switch (first) {
        case Done<S, I> done -> success(done);
        case Continue<S, I>(var firstDelay, var firstNext) ->
          other.next(input).map(second ->
            switch (second) {
              case Done<S, I> done -> done;
              case Continue<S, I>(var secondDelay, var secondNext) ->
                firstDelay.compareTo(secondDelay) >= 0
                  ? new Continue<>(firstDelay, firstNext.and(secondNext))
                  : new Continue<>(secondDelay, firstNext.and(secondNext));
            }
          );
      }
    );
  }

  /**
   * Combines this schedule with another, if the first one finish, it will continue with the second one.
   *
   * <p>The resulting delay is the lower of the two delays. Both schedules receive the same
   * input, and their next schedules are combined in the same way.
   *
   * @param other the schedule to combine with this one
   * @return a schedule that completes when both schedules finish
   */
  default Schedule<S, I> or(Schedule<S, I> other) {
    return input -> next(input).flatMap(first ->
      switch (first) {
        case Done<S, I> _ -> other.next(input);
        case Continue<S, I>(var firstDelay, var firstNext) ->
          other.next(input).map(second ->
            switch (second) {
              case Done<S, I> _ -> new Continue<>(firstDelay, firstNext);
              case Continue<S, I>(var secondDelay, var secondNext) ->
                firstDelay.compareTo(secondDelay) <= 0
                  ? new Continue<>(firstDelay, firstNext.or(secondNext))
                  : new Continue<>(secondDelay, firstNext.or(secondNext));
            }
          );
      }
    );
  }

  /**
   * Adds random jitter to the delays produced by the given schedule.
   *
   * <p>Each delay is multiplied by a random value in the range
   * {@code [1 - factor, 1 + factor]}.
   *
   * <p>The value factor must be between 0 and 1.
   *
   * @param factor the maximum relative variation applied to each delay
   * @return a schedule with randomized delays
   */
  default Schedule<S, I> jittered(double factor, Program<S, Void, Double> random) {
    if (!Double.isFinite(factor)) {
      throw new IllegalArgumentException("factor must be a value between 0 to 1");
    }
    if (factor < 0 || factor > 1) {
      throw new IllegalArgumentException("factor must be a value between 0 to 1");
    }
    return input -> next(input).flatMap(decision -> switch (decision) {
      case Done<S, I> done -> success(done);
      case Continue<S, I>(var delay, var next) ->
        chain(
            random,
            r -> {
              var multiplier = 1 + (r * 2 - 1) * factor;
              return new Continue<>(scale(delay, multiplier), next.jittered(factor, random));
            });
    });
  }

  private static Duration scale(Duration duration, double factor) {
    if (duration.compareTo(DelayScheduler.FOREVER) >= 0) {
      return DelayScheduler.FOREVER;
    }
    return Duration.ofNanos(Math.round(duration.toNanos() * factor));
  }
}
