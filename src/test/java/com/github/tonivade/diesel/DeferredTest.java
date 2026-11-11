/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Combine.zip;
import static com.github.tonivade.diesel.Concurrent.parSequence;
import static com.github.tonivade.diesel.Concurrent.parZip;
import static com.github.tonivade.diesel.Program.delayed;
import static com.github.tonivade.diesel.Program.sleep;
import static com.github.tonivade.diesel.Program.success;
import static com.github.tonivade.diesel.Tuple2.tuple;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

// a regression would leave a waiter waiting forever instead of failing
@Timeout(value = 10, unit = TimeUnit.SECONDS)
class DeferredTest {

  private final Deferred<String> deferred = Deferred.create();

  @Test
  void canBeUsedInMoreSpecificProgramContext() {
    Deferred<Integer> deferred = Deferred.create();

    Program<String, RuntimeException, Optional<Integer>> program = deferred.tryGet();

    assertThat(program.evalOrElseThrow()).isEmpty();
  }

  @Test
  void makeCreatesANewDeferredForEachEvaluation() {
    var program = Deferred.make();

    var first = program.evalOrElseThrow();
    var second = program.evalOrElseThrow();

    first.complete("first").evalOrElseThrow();

    assertThat(first.tryGet().evalOrElseThrow()).contains("first");
    assertThat(second.tryGet().evalOrElseThrow()).isEmpty();
  }

  @Test
  void getWaitsUntilItIsCompleted() {
    var program = parZip(
        deferred.get(),
        delayed(Delay.ofMillis(50), deferred.complete("hello")),
        (value, _) -> value);

    assertThat(program.evalOrElseThrow()).isEqualTo("hello");
  }

  @Test
  void getReturnsAtOnceWhenItIsAlreadyCompleted() {
    var program = deferred.complete("hello").andThen(deferred.get());

    assertThat(program.evalOrElseThrow()).isEqualTo("hello");
  }

  @Test
  void completeOnlySetsTheValueTheFirstTime() {
    var program = zip(
        deferred.complete("first"),
        deferred.complete("second"),
        deferred.get(),
        (first, second, value) -> List.of(first, second, value));

    assertThat(program.evalOrElseThrow()).containsExactly(true, false, "first");
  }

  @Test
  void tryGetDoesNotWait() {
    var program = zip(
        deferred.tryGet(),
        deferred.complete("hello"),
        deferred.tryGet(),
        (before, _, after) -> tuple(before, after));

    assertThat(program.evalOrElseThrow()).isEqualTo(tuple(Optional.empty(), Optional.of("hello")));
  }

  @Test
  void everyWaiterGetsTheValue() {
    var waiters = IntStream.range(0, 100).mapToObj(_ -> deferred.get()).toList();

    var program = parZip(
        parSequence(waiters),
        delayed(Delay.ofMillis(50), deferred.complete("hello")),
        (values, _) -> values);

    assertThat(program.evalOrElseThrow()).hasSize(100).containsOnly("hello");
  }

  @Test
  void aCancelledWaiterDoesNotAffectTheOthers() {
    var cancelled = deferred.get()
        .timeout(Delay.ofMillis(50))
        .catchAll(_ -> success("timeout"));
    var other = sleep(Delay.ofMillis(200))
        .andThen(deferred.complete("hello"))
        .andThen(deferred.get());

    var program = parZip(cancelled, other, Tuple2::tuple);

    assertThat(program.evalOrElseThrow()).isEqualTo(tuple("timeout", "hello"));
  }

  @Test
  void onlyOneConcurrentCompleteSetsTheValue() {
    var deferred = Deferred.create();
    var completes = IntStream.range(0, 100).mapToObj(i -> deferred.complete(i)).toList();

    var results = new ArrayList<>(parSequence(completes).evalOrElseThrow());

    assertThat(results).containsOnlyOnce(true);
    assertThat(deferred.get().evalOrElseThrow()).isEqualTo(results.indexOf(true));
  }

  @Test
  void toStringShowsTheValueWhenItIsSet() {
    assertThat(deferred).hasToString("Deferred(<empty>)");

    deferred.complete("hello").evalOrElseThrow();

    assertThat(deferred).hasToString("Deferred(hello)");
  }

  @Test
  void cancelledWaitersAreNotKept() {
    var waitWithTimeout = deferred.get()
        .timeout(Delay.ofMillis(1))
        .catchAll(_ -> success("timeout"));

    for (int i = 0; i < 100; i++) {
      assertThat(waitWithTimeout.evalOrElseThrow()).isEqualTo("timeout");
    }

    assertThat(deferred.waiting()).isZero();
  }

  @Test
  void wokenUpWaitersAreNotKept() {
    var program = parZip(
        deferred.get(),
        delayed(Delay.ofMillis(50), deferred.complete("hello")),
        (value, _) -> value);

    assertThat(program.evalOrElseThrow()).isEqualTo("hello");
    assertThat(deferred.waiting()).isZero();
  }

  @Test
  @SuppressWarnings("NullAway") // NullAway already rejects it at compile time, but not every caller uses it
  void completeRejectsANullValue() {
    assertThatThrownBy(() -> deferred.complete(null)).isInstanceOf(NullPointerException.class);
    assertThat(deferred.tryGet().evalOrElseThrow()).isEmpty();
  }

  @Test
  void getInAnUncancelableRegionWaitsForTheValue() throws Exception {
    var future = deferred.get().uncancelable().fork().evalOrElseThrow().future();
    awaitWaiting(1);

    // the future of a fork isn't completed by cancel: it only asks the program to stop
    future.cancel(true);
    deferred.complete("hello").evalOrElseThrow();

    // the whole program is the region, so the cancellation has no step left to stop it
    assertThat(future.get(5, TimeUnit.SECONDS)).isEqualTo(Result.success("hello"));
  }

  private void awaitWaiting(int count) {
    var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (deferred.waiting() < count) {
      if (System.nanoTime() > deadline) {
        throw new AssertionError("the program never started waiting");
      }
      Thread.onSpinWait();
    }
  }
}
