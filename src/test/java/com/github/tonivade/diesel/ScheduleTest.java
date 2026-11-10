/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Program.success;
import static com.github.tonivade.diesel.Schedule.Decision.done;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.github.tonivade.diesel.Schedule.Decision.More;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoSettings;

@MockitoSettings
class ScheduleTest {

  @Test
  void stopCompletesImmediately() {
    assertThat(Schedule.<Void, Integer>stop().next(1).evalOrElseThrow())
        .isEqualTo(done());
  }

  @Test
  void foreverContinuesWithoutDelay() {
    var first = continueWith(Schedule.<Void, Integer>forever(), 1);
    var second = continueWith(first.next(), 2);

    assertThat(first.delay()).isEqualTo(Duration.ZERO);
    assertThat(second.delay()).isEqualTo(Duration.ZERO);
  }

  @Test
  void spacedUsesTheSameDelayForEveryExecution() {
    var delay = Duration.ofMillis(250);
    var first = continueWith(Schedule.<Void, Integer>spaced(delay), 1);
    var second = continueWith(first.next(), 2);

    assertThat(first.delay()).isEqualTo(delay);
    assertThat(second.delay()).isEqualTo(delay);
  }

  @Test
  void exponentialContract() {
    assertThatThrownBy(() -> Schedule.exponential(Duration.ofMillis(-1), 1))
      .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Schedule.exponential(Duration.ofMillis(1), Double.NaN))
      .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Schedule.exponential(Duration.ofMillis(1), Double.POSITIVE_INFINITY))
      .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Schedule.exponential(Duration.ofMillis(1), Double.NEGATIVE_INFINITY))
      .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Schedule.exponential(Duration.ofMillis(1), -1))
      .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void exponentialDoublesTheDelayAfterEveryExecution() {
    var first = continueWith(Schedule.<Void, Integer>exponential(Duration.ofMillis(100), 2), 1);
    var second = continueWith(first.next(), 2);
    var third = continueWith(second.next(), 3);

    assertThat(first.delay()).isEqualTo(Duration.ofMillis(100));
    assertThat(second.delay()).isEqualTo(Duration.ofMillis(200));
    assertThat(third.delay()).isEqualTo(Duration.ofMillis(400));
  }

  @Test
  void exponentialSupportsFractionalFactors() {
    var first = continueWith(Schedule.<Void, Integer>exponential(Duration.ofMillis(100), 1.5), 1);
    var second = continueWith(first.next(), 2);
    var third = continueWith(second.next(), 3);

    assertThat(first.delay()).isEqualTo(Duration.ofMillis(100));
    assertThat(second.delay()).isEqualTo(Duration.ofMillis(150));
    assertThat(third.delay()).isEqualTo(Duration.ofMillis(225));
  }

  @Test
  void recursStopsAfterTheConfiguredNumberOfContinuations() {
    var first = continueWith(Schedule.<Void, Integer>recurs(2), 1);
    var second = continueWith(first.next(), 2);

    assertThat(first.delay()).isEqualTo(Duration.ZERO);
    assertThat(second.delay()).isEqualTo(Duration.ZERO);
    assertThat(second.next().next(3).evalOrElseThrow())
        .isEqualTo(done());
  }

  @Test
  void recursWithZeroOrNegativeCountStopsImmediately() {
    assertThat(Schedule.<Void, Integer>recurs(0).next(1).evalOrElseThrow())
        .isEqualTo(done());
    assertThat(Schedule.<Void, Integer>recurs(-1).next(1).evalOrElseThrow())
        .isEqualTo(done());
  }

  @Test
  void andUsesTheLongestDelayWhenBothSchedulesContinue() {
    var schedule = Schedule.<Void, Integer>spaced(Duration.ofMillis(100))
        .and(Schedule.spaced(Duration.ofMillis(250)));

    var decision = continueWith(schedule, 1);

    assertThat(decision.delay()).isEqualTo(Duration.ofMillis(250));
    assertThat(continueWith(decision.next(), 2).delay()).isEqualTo(Duration.ofMillis(250));
  }

  @Test
  void andStopsWhenTheFirstScheduleIsDone() {
    var schedule = Schedule.<Void, Integer>stop()
        .and(Schedule.spaced(Duration.ofMillis(100)));

    assertThat(schedule.next(1).evalOrElseThrow()).isEqualTo(done());
  }

  @Test
  void andStopsWhenTheSecondScheduleIsDone() {
    var schedule = Schedule.<Void, Integer>spaced(Duration.ofMillis(100))
        .and(Schedule.stop());

    assertThat(schedule.next(1).evalOrElseThrow()).isEqualTo(done());
  }

  @Test
  void orUsesTheShortestDelayWhenBothSchedulesContinue() {
    var schedule = Schedule.<Void, Integer>spaced(Duration.ofMillis(100))
        .or(Schedule.spaced(Duration.ofMillis(250)));

    var decision = continueWith(schedule, 1);

    assertThat(decision.delay()).isEqualTo(Duration.ofMillis(100));
    assertThat(continueWith(decision.next(), 2).delay()).isEqualTo(Duration.ofMillis(100));
  }

  @Test
  void orContinuesWhenTheFirstScheduleIsDone() {
    var schedule = Schedule.<Void, Integer>stop()
        .or(Schedule.spaced(Duration.ofMillis(100)));

    var decision = continueWith(schedule, 1);

    assertThat(decision.delay()).isEqualTo(Duration.ofMillis(100));
  }

  @Test
  void orContinuesWhenTheSecondScheduleIsDone() {
    var schedule = Schedule.<Void, Integer>spaced(Duration.ofMillis(100))
        .or(Schedule.stop());

    var decision = continueWith(schedule, 1);

    assertThat(decision.delay()).isEqualTo(Duration.ofMillis(100));
  }

  @Test
  void orStopsWhenBothAreDone() {
    var schedule = Schedule.<Void, Integer>stop()
        .or(Schedule.stop());

    assertThat(schedule.next(1).evalOrElseThrow()).isEqualTo(done());
  }

  @Test
  void jitteredContract() {
    assertThatThrownBy(() -> Schedule.<Random.Service, Integer>stop().jittered(-0.2, Random.nextDouble()))
      .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Schedule.<Random.Service, Integer>stop().jittered(1.2, Random.nextDouble()))
      .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Schedule.<Random.Service, Integer>stop().jittered(Double.NaN, Random.nextDouble()))
      .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Schedule.<Random.Service, Integer>stop().jittered(Double.POSITIVE_INFINITY, Random.nextDouble()))
      .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Schedule.<Random.Service, Integer>stop().jittered(Double.NEGATIVE_INFINITY, Random.nextDouble()))
      .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void jitteredScalesDelayWithinTheConfiguredRange(@Mock Random.Service random) {
    var schedule = Schedule.<Random.Service, Integer>spaced(Duration.ofMillis(100))
        .jittered(0.2, Random.nextDouble());
    when(random.nextDouble()).thenReturn(0.0, 1.0);

    var first = continueWith(schedule, 1, random);
    var second = continueWith(first.next(), 2, random);

    assertThat(first.delay()).isEqualTo(Duration.ofMillis(80));
    assertThat(second.delay()).isEqualTo(Duration.ofMillis(120));
    verify(random, Mockito.times(2)).nextDouble();
  }

  @Test
  void jitteredRejectsARandomValueOutOfRange(@Mock Random.Service random) {
    var schedule = Schedule.<Random.Service, Integer>spaced(Duration.ofMillis(100))
        .jittered(1.0, Random.nextDouble());
    when(random.nextDouble()).thenReturn(
        -0.5, 1.5, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY);

    for (int i = 0; i < 5; i++) {
      assertThatThrownBy(() -> schedule.next(1).eval(random))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageStartingWith("the random value must be between 0 and 1");
    }
  }

  @Test
  void jitteredDoesNotRequestRandomnessWhenScheduleIsDone(@Mock Random.Service random) {
    var schedule = Schedule.<Random.Service, Integer>stop().jittered(0.2, Random.nextDouble());

    assertThat(schedule.next(1).eval(random).getOrElseThrow())
        .isEqualTo(done());

    verifyNoInteractions(random);
  }

  @Test
  void nextReceivesTheInput() {
    Schedule<Void, Integer> schedule = input -> success(
        input == 42
            ? done()
            : new More<>(Duration.ZERO, Schedule.stop()));

    assertThat(schedule.next(42).evalOrElseThrow()).isEqualTo(done());
    assertThat(continueWith(schedule, 1).delay()).isEqualTo(Duration.ZERO);
  }

  @Test
  void whileInputContinuesWhilePredicateMatches() {
    var schedule = Schedule.<Void, Integer>spaced(Duration.ofMillis(100))
        .whileInput(input -> input < 3);

    var first = continueWith(schedule, 1);
    var second = continueWith(first.next(), 2);

    assertThat(first.delay()).isEqualTo(Duration.ofMillis(100));
    assertThat(second.delay()).isEqualTo(Duration.ofMillis(100));
    assertThat(second.next().next(3).evalOrElseThrow())
        .isEqualTo(done());
  }

  @Test
  void whileInputStopsWhenPredicateDoesNotMatch() {
    var schedule = Schedule.<Void, Integer>spaced(Duration.ofMillis(100))
        .whileInput(input -> input < 2);

    var first = continueWith(schedule, 1);

    assertThat(first.next().next(2).evalOrElseThrow())
        .isEqualTo(done());
  }

  @Test
  void whileInputDoesNotEvaluateUnderlyingScheduleWhenPredicateFails() {
    var evaluations = new AtomicInteger();

    Schedule<Void, Integer> underlying = _ -> {
      evaluations.incrementAndGet();
      return success(new More<>(Duration.ZERO, Schedule.stop()));
    };

    var schedule = underlying.whileInput(input -> input > 0);

    assertThat(schedule.next(0).evalOrElseThrow())
        .isEqualTo(done());

    assertThat(evaluations.get()).isZero();
  }

  @Test
  void untilInputStopsWhenPredicateMatches() {
    var schedule = Schedule.<Void, Integer>forever()
        .untilInput(input -> input == 3);

    var first = continueWith(schedule, 1);
    var second = continueWith(first.next(), 2);

    assertThat(first.delay()).isEqualTo(Duration.ZERO);
    assertThat(second.delay()).isEqualTo(Duration.ZERO);
    assertThat(second.next().next(3).evalOrElseThrow())
        .isEqualTo(done());
  }

  @Test
  @SuppressWarnings("NullAway")
  void whileInputRejectsNullPredicate() {
    assertThatThrownBy(() -> Schedule.<Void, Integer>forever().whileInput(null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @SuppressWarnings("NullAway")
  void untilInputRejectsNullPredicate() {
    assertThatThrownBy(() -> Schedule.<Void, Integer>forever().untilInput(null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void cappedLimitsDelaysToTheMaximum() {
    var max = Duration.ofMillis(200);
    var schedule = Schedule.<Void, Integer>spaced(Duration.ofMillis(500))
        .capped(max);

    var first = continueWith(schedule, 1);
    var second = continueWith(first.next(), 2);

    assertThat(first.delay()).isEqualTo(max);
    assertThat(second.delay()).isEqualTo(max);
  }

  @Test
  void cappedPreservesDelaysBelowTheMaximum() {
    var delay = Duration.ofMillis(100);
    var schedule = Schedule.<Void, Integer>spaced(delay)
        .capped(Duration.ofMillis(200));

    var decision = continueWith(schedule, 1);

    assertThat(decision.delay()).isEqualTo(delay);
  }

  @Test
  void cappedPreservesDelaysEqualToTheMaximum() {
    var max = Duration.ofMillis(200);
    var schedule = Schedule.<Void, Integer>spaced(max)
        .capped(max);

    var decision = continueWith(schedule, 1);

    assertThat(decision.delay()).isEqualTo(max);
  }

  @Test
  void cappedAcceptsZeroAsMaximum() {
    var schedule = Schedule.<Void, Integer>spaced(Duration.ofMillis(100))
        .capped(Duration.ZERO);

    var decision = continueWith(schedule, 1);

    assertThat(decision.delay()).isEqualTo(Duration.ZERO);
  }

  @Test
  void cappedDoesNotChangeDoneDecision() {
    var schedule = Schedule.<Void, Integer>stop()
        .capped(Duration.ofMillis(100));

    assertThat(schedule.next(1).evalOrElseThrow())
        .isEqualTo(done());
  }

  @Test
  void cappedRejectsNegativeMaximum() {
    assertThatThrownBy(() ->
        Schedule.<Void, Integer>forever().capped(Duration.ofMillis(-1)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @SuppressWarnings("NullAway")
  void cappedRejectsNullMaximum() {
    assertThatThrownBy(() ->
        Schedule.<Void, Integer>forever().capped(null))
        .isInstanceOf(NullPointerException.class);
  }

  private static <I> More<Void, I> continueWith(Schedule<Void, I> schedule, I input) {
    return (More<Void, I>) schedule.next(input).evalOrElseThrow();
  }

  private static <I> More<Random.Service, I> continueWith(
      Schedule<Random.Service, I> schedule, I input, Random.Service random) {
    return (More<Random.Service, I>) schedule.next(input).eval(random).getOrElseThrow();
  }
}
