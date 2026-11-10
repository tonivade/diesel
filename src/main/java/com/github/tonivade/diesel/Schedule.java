/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Combine.pipe;
import static com.github.tonivade.diesel.Program.success;
import static java.util.Objects.requireNonNull;

import com.github.tonivade.diesel.Schedule.Decision;
import com.github.tonivade.diesel.Schedule.Decision.More;

import java.time.Duration;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;

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
     * Returns the decision that completes the schedule.
     *
     * @param <S> the type of the state used by the schedule
     * @param <I> the type of the input received by the schedule
     * @return the shared {@link Done} decision
     */
    @SuppressWarnings("unchecked")
    static <S, I> Decision<S, I> done() {
      return (Decision<S, I>) Done.DONE;
    }

    /**
     * Applies {@code onDone} if the schedule is complete, or {@code onMore} with the delay and the
     * next schedule if it continues.
     *
     * @param onDone the function used when the schedule is complete
     * @param onMore the function used with the delay and the next schedule when it continues
     * @param <A> the type of the result
     * @return the result of the function that matches the decision
     */
    default <A> A fold(
        Supplier<? extends A> onDone,
        BiFunction<? super Duration, ? super Schedule<S, I>, ? extends A> onMore) {
      return switch (this) {
        case Done<S, I> _ -> onDone.get();
        case More<S, I>(var delay, var next) -> onMore.apply(delay, next);
      };
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
    record More<S, I>(Duration delay, Schedule<S, I> next) implements Decision<S, I> {

      /**
       * Creates a decision to continue after the given delay.
       *
       * @param delay the duration to wait before the next execution
       * @param next the schedule to evaluate after the next execution
       * @throws NullPointerException if delay or next is {@code null}
       * @throws IllegalArgumentException if delay is negative
       */
      public More {
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
   * Creates a program that yields the decision to complete the schedule, useful to implement
   * {@link #next(Object)}.
   *
   * @param <S> the type of the state used by the schedule
   * @param <I> the type of the input received by the schedule
   * @return a program that yields {@link Decision#done()}
   */
  static <S, I> Program<S, Void, Decision<S, I>> done() {
    return success(Decision.done());
  }

  /**
   * Creates a program that yields the decision to continue after a delay, useful to implement
   * {@link #next(Object)}.
   *
   * @param delay the duration to wait before the next execution
   * @param next the schedule to evaluate after the next execution
   * @param <S> the type of the state used by the schedule
   * @param <I> the type of the input received by the schedule
   * @return a program that yields a {@link Decision.More} decision
   * @throws NullPointerException if delay or next is {@code null}
   * @throws IllegalArgumentException if delay is negative
   */
  static <S, I> Program<S, Void, Decision<S, I>> more(Duration delay, Schedule<S, I> next) {
    return success(new More<>(delay, next));
  }

  /**
   * Creates a schedule that completes without allowing another execution.
   *
   * @param <S> the type of the state used by the schedule
   * @param <I> the type of the input received by the schedule
   * @return a schedule that always returns {@link Decision.Done}
   */
  static <S, I> Schedule<S, I> stop() {
    return _ -> done();
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
    if (delay.isNegative()) {
      throw new IllegalArgumentException("delay must be a positive duration");
    }
    return _ -> more(delay, spaced(delay));
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
    if (!Double.isFinite(factor) || factor <= 0) {
      throw new IllegalArgumentException("factor must be a positive value");
    }
    return _ -> more(base, exponential(scale(base, factor), factor));
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
    return _ -> more(Duration.ZERO, recurs(n - 1));
  }

  /**
   * Decorates this schedule so that it continues only while the given predicate
   * matches the input.
   *
   * <p>The predicate is evaluated before the underlying schedule. If it returns
   * {@code false}, the schedule completes immediately without evaluating the
   * underlying schedule.
   *
   * <p>The predicate is evaluated again for every subsequent input.
   *
   * @param predicate the condition that must match for the schedule to continue
   * @return a schedule that continues while the predicate matches the input
   * @throws NullPointerException if predicate is {@code null}
   */
  default Schedule<S, I> whileInput(Predicate<? super I> predicate) {
    requireNonNull(predicate);
    return input -> predicate.test(input)
        ? next(input).flatMap(decision ->
            decision.fold(
              Schedule::done,
              (delay, next) -> more(delay, next.whileInput(predicate))))
        : done();
  }

  /**
   * Decorates this schedule so that it completes when the given predicate
   * matches the input.
   *
   * <p>The predicate is evaluated before the underlying schedule. If it returns
   * {@code true}, the schedule completes immediately without evaluating the
   * underlying schedule.
   *
   * <p>The predicate is evaluated again for every subsequent input.
   *
   * @param predicate the condition that causes the schedule to complete
   * @return a schedule that continues until the predicate matches the input
   * @throws NullPointerException if predicate is {@code null}
   */
  default Schedule<S, I> untilInput(Predicate<? super I> predicate) {
    requireNonNull(predicate);
    return whileInput(predicate.negate());
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
    requireNonNull(other);
    return input -> next(input).flatMap(first ->
      first.fold(
        Schedule::done,
        (firstDelay, firstNext) ->
          other.next(input).flatMap(second ->
            second.fold(
              Schedule::done,
              (var secondDelay, var secondNext) ->
                firstDelay.compareTo(secondDelay) >= 0
                  ? more(firstDelay, firstNext.and(secondNext))
                  : more(secondDelay, firstNext.and(secondNext))
            )
          )
      )
    );
  }

  /**
   * Combines this schedule with another, continuing while at least one of the schedules continues.
   *
   * <p>If both schedules continue, the resulting delay is the shorter
   * of the two delays. Both schedules receive the same input and advance
   * on every evaluation, regardless of which delay is selected.
   *
   * @param other the schedule to combine with this one
   * @return a schedule that completes only when both schedules complete
   */
  default Schedule<S, I> or(Schedule<S, I> other) {
    requireNonNull(other);
    return input -> next(input).flatMap(first ->
      first.fold(
        () -> other.next(input),
        (firstDelay, firstNext) ->
          other.next(input).flatMap(second ->
            second.fold(
              () -> more(firstDelay, firstNext),
              (secondDelay, secondNext) ->
                firstDelay.compareTo(secondDelay) <= 0
                  ? more(firstDelay, firstNext.or(secondNext))
                  : more(secondDelay, firstNext.or(secondNext)))
        )
      )
    );
  }

  /**
   * Adds random jitter to the delays produced by this schedule.
   *
   * <p>Each delay is multiplied by a random value in the range
   * {@code [1 - factor, 1 + factor]}.
   *
   * <p>The value factor must be between 0 and 1.
   *
   * <p>The random program must produce a value between 0 and 1, like {@link Random#nextDouble()}.
   * If it produces any other value, the evaluation of the schedule throws an
   * {@link IllegalArgumentException}.
   *
   * @param factor the maximum relative variation applied to each delay
   * @param random the program that produces a random value between 0 and 1 for each delay
   * @return a schedule with randomized delays
   * @throws NullPointerException if random is {@code null}
   * @throws IllegalArgumentException if factor is not between 0 and 1
   */
  default Schedule<S, I> jittered(double factor, Program<S, Void, Double> random) {
    requireNonNull(random);
    if (Double.isNaN(factor) || factor < 0 || factor > 1) {
      throw new IllegalArgumentException("factor must be a value between 0 to 1");
    }
    return input -> next(input).flatMap(decision ->
      decision.fold(
        Schedule::done,
        (var delay, var next) ->
          pipe(
              random,
              r -> {
                // otherwise the multiplier, and so the delay, could be negative. A NaN fails every
                // comparison, so it has to be checked on its own, or it would become a zero delay
                if (Double.isNaN(r) || r < 0 || r > 1) {
                  throw new IllegalArgumentException(
                      "the random value must be between 0 and 1, but it was " + r);
                }
                var multiplier = 1 + (r * 2 - 1) * factor;
                return more(scale(delay, multiplier), next.jittered(factor, random));
              })
      )
    );
  }

  /**
   * Decorates this schedule by limiting the maximum delay between executions.
   *
   * <p>If the underlying schedule produces a delay greater than {@code max},
   * the resulting schedule uses {@code max} instead. Delays less than or equal
   * to {@code max} remain unchanged.
   *
   * <p>This operation does not limit the number of executions. It only limits
   * the delay produced by the schedule.
   *
   * <p>A zero maximum is valid and causes all delays to be zero.
   *
   * @param max the maximum delay allowed between executions
   * @return a schedule whose delays never exceed {@code max}
   * @throws NullPointerException if max is {@code null}
   * @throws IllegalArgumentException if max is negative
   */
  default Schedule<S, I> capped(Duration max) {
    if (max.isNegative()) {
      throw new IllegalArgumentException("max cannot be negative");
    }
    return input -> next(input).flatMap(decision ->
      decision.fold(
        Schedule::done,
        (delay, next) -> more(delay.compareTo(max) > 0 ? max : delay, next.capped(max)))
    );
  }

  private static Duration scale(Duration duration, double factor) {
    if (duration.compareTo(DelayScheduler.FOREVER) >= 0) {
      return DelayScheduler.FOREVER;
    }
    return Duration.ofNanos(Math.round(duration.toNanos() * factor));
  }
}
