/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Combine.zip;
import static com.github.tonivade.diesel.Concurrent.parSequence;
import static com.github.tonivade.diesel.Concurrent.parZip;
import static com.github.tonivade.diesel.Program.success;
import static com.github.tonivade.diesel.Tuple2.tuple;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
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
  void makeCreatesANewDeferredForEachEvaluation() {
    var program = Deferred.<Void, Void, String>make();

    var first = program.evalOrElseThrow();
    var second = program.evalOrElseThrow();

    first.<Void, Void>complete("first").evalOrElseThrow();

    assertThat(first.<Void, Void>tryGet().evalOrElseThrow()).contains("first");
    assertThat(second.<Void, Void>tryGet().evalOrElseThrow()).isEmpty();
  }

  @Test
  void getWaitsUntilItIsCompleted() {
    var program = parZip(
        deferred.<Void, Void>get(),
        Program.<Void, Void>sleep(Duration.ofMillis(50)).andThen(deferred.complete("hello")),
        (value, _) -> value);

    assertThat(program.evalOrElseThrow()).isEqualTo("hello");
  }

  @Test
  void getReturnsAtOnceWhenItIsAlreadyCompleted() {
    var program = deferred.<Void, Void>complete("hello").andThen(deferred.get());

    assertThat(program.evalOrElseThrow()).isEqualTo("hello");
  }

  @Test
  void completeOnlySetsTheValueTheFirstTime() {
    var program = zip(
        deferred.<Void, Void>complete("first"),
        deferred.complete("second"),
        deferred.get(),
        (first, second, value) -> List.of(first, second, value));

    assertThat(program.evalOrElseThrow()).containsExactly(true, false, "first");
  }

  @Test
  void tryGetDoesNotWait() {
    var program = zip(
        deferred.<Void, Void>tryGet(),
        deferred.complete("hello"),
        deferred.tryGet(),
        (before, _, after) -> tuple(before, after));

    assertThat(program.evalOrElseThrow()).isEqualTo(tuple(Optional.empty(), Optional.of("hello")));
  }

  @Test
  void everyWaiterGetsTheValue() {
    var waiters = IntStream.range(0, 100).mapToObj(_ -> deferred.<Void, Void>get()).toList();

    var program = parZip(
        parSequence(waiters),
        Program.<Void, Void>sleep(Duration.ofMillis(50)).andThen(deferred.complete("hello")),
        (values, _) -> values);

    assertThat(program.evalOrElseThrow()).hasSize(100).containsOnly("hello");
  }

  @Test
  void aCancelledWaiterDoesNotAffectTheOthers() {
    var cancelled = deferred.<Void, Void>get()
        .timeout(Duration.ofMillis(50))
        .catchAll(_ -> success("timeout"));
    var other = Program.<Void, Void>sleep(Duration.ofMillis(200))
        .andThen(deferred.complete("hello"))
        .andThen(deferred.get());

    var program = parZip(cancelled, other, Tuple2::tuple);

    assertThat(program.evalOrElseThrow()).isEqualTo(tuple("timeout", "hello"));
  }

  @Test
  void onlyOneConcurrentCompleteSetsTheValue() {
    var deferred = Deferred.<Integer>create();
    var completes = IntStream.range(0, 100).mapToObj(i -> deferred.<Void, Void>complete(i)).toList();

    var results = new ArrayList<>(parSequence(completes).evalOrElseThrow());

    assertThat(results).containsOnlyOnce(true);
    assertThat(deferred.<Void, Void>get().evalOrElseThrow()).isEqualTo(results.indexOf(true));
  }

  @Test
  void toStringShowsTheValueWhenItIsSet() {
    assertThat(deferred).hasToString("Deferred(<empty>)");

    deferred.<Void, Void>complete("hello").evalOrElseThrow();

    assertThat(deferred).hasToString("Deferred(hello)");
  }
}
