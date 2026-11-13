/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Combine.delayed;
import static com.github.tonivade.diesel.Combine.ensuring;
import static com.github.tonivade.diesel.Combine.pipe;
import static com.github.tonivade.diesel.Concurrent.parAll;
import static com.github.tonivade.diesel.Concurrent.parZip;
import static com.github.tonivade.diesel.Concurrent.race;
import static com.github.tonivade.diesel.Program.failure;
import static com.github.tonivade.diesel.Program.never;
import static com.github.tonivade.diesel.Program.raise;
import static com.github.tonivade.diesel.Program.sleep;
import static com.github.tonivade.diesel.Program.success;
import static com.github.tonivade.diesel.Program.task;
import static com.github.tonivade.diesel.Program.uncancelable;
import static com.github.tonivade.diesel.Program.unit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;
import java.util.stream.IntStream;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

// a regression in the cancellation would leave a waiter waiting forever instead of failing
@Timeout(value = 10, unit = TimeUnit.SECONDS)
class SemaphoreTest {

  private static final Delay PAUSE = Delay.ofMillis(50);

  // ---------------------------------------------------------------------------------------------
  // creation and basic behaviour
  // ---------------------------------------------------------------------------------------------

  @Test
  void ofRejectsNegativePermits() {
    assertThatThrownBy(() -> Semaphore.of(-1)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void makeValidatesWhenEvaluated() {
    var program = Semaphore.<Void, Void>make(-1);

    assertThatThrownBy(program::evalOrElseThrow).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void makeCreatesANewSemaphoreForEachEvaluation() {
    var program = Semaphore.<Void, Void>make(1);

    var first = program.evalOrElseThrow();
    var second = program.evalOrElseThrow();

    first.acquire().evalOrElseThrow();

    assertThat(first.available().evalOrElseThrow()).isZero();
    assertThat(second.available().evalOrElseThrow()).isEqualTo(1L);
  }

  @Test
  void makeWithAHugeNumberOfPermitsIsCheap() {
    var semaphore = Semaphore.make(Integer.MAX_VALUE).evalOrElseThrow();

    assertThat(semaphore.available().evalOrElseThrow()).isEqualTo(Integer.MAX_VALUE);
  }

  @Test
  void nonPositiveAmountsAreRejected() {
    var semaphore = Semaphore.of(1);

    assertThatThrownBy(() -> semaphore.acquire(0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> semaphore.acquire(-1)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> semaphore.tryAcquire(0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> semaphore.release(0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> semaphore.withPermits(0, success(1)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void acquireAndReleaseChangeTheAvailablePermits() {
    var semaphore = Semaphore.of(2);

    semaphore.acquire().evalOrElseThrow();
    assertThat(semaphore.available().evalOrElseThrow()).isEqualTo(1L);

    semaphore.acquire().evalOrElseThrow();
    assertThat(semaphore.available().evalOrElseThrow()).isZero();

    semaphore.release(2).evalOrElseThrow();
    assertThat(semaphore.available().evalOrElseThrow()).isEqualTo(2L);
  }

  @Test
  void releasingMoreThanTheInitialPermitsIsAllowed() {
    var semaphore = Semaphore.of(1);

    semaphore.release(3).evalOrElseThrow();

    assertThat(semaphore.available().evalOrElseThrow()).isEqualTo(4L);
  }

  @Test
  void acquireWaitsUntilThereIsAPermit() {
    var semaphore = Semaphore.of(0);

    var program = parZip(
        semaphore.acquire().andThen(success("acquired")),
        delayed(PAUSE, semaphore.release()),
        (value, _) -> value);

    assertThat(program.evalOrElseThrow()).isEqualTo("acquired");
    assertThat(semaphore.available().evalOrElseThrow()).isZero();
    assertThat(semaphore.waiting()).isZero();
  }

  @Test
  void acquireManyWaitsUntilThereAreEnoughPermits() {
    var semaphore = Semaphore.of(1);

    var program = parZip(
        semaphore.acquire(3).andThen(success("acquired")),
        delayed(PAUSE, semaphore.release(2)),
        (value, _) -> value);

    assertThat(program.evalOrElseThrow()).isEqualTo("acquired");
    assertThat(semaphore.available().evalOrElseThrow()).isZero();
  }

  // ---------------------------------------------------------------------------------------------
  // tryAcquire
  // ---------------------------------------------------------------------------------------------

  @Test
  void tryAcquireDoesNotWait() {
    var semaphore = Semaphore.of(1);

    assertThat(semaphore.tryAcquire().evalOrElseThrow()).isTrue();
    assertThat(semaphore.tryAcquire().evalOrElseThrow()).isFalse();
    assertThat(semaphore.available().evalOrElseThrow()).isZero();
  }

  @Test
  void tryAcquireManyIsAllOrNothing() {
    var semaphore = Semaphore.of(2);

    assertThat(semaphore.tryAcquire(3).evalOrElseThrow()).isFalse();
    assertThat(semaphore.available().evalOrElseThrow()).isEqualTo(2L);
    assertThat(semaphore.tryAcquire(2).evalOrElseThrow()).isTrue();
  }

  @Test
  void tryAcquireDoesNotOvertakeTheWaiters() throws Exception {
    var semaphore = Semaphore.of(1);
    var waiter = semaphore.acquire(2).fork().evalOrElseThrow();
    awaitWaiting(semaphore, 1);

    try {
      // there is a permit, but the waiter is first in line
      assertThat(semaphore.tryAcquire().evalOrElseThrow()).isFalse();
      assertThat(semaphore.available().evalOrElseThrow()).isEqualTo(1L);
    } finally {
      waiter.<Void, Void>cancel().evalOrElseThrow();
    }
  }

  // ---------------------------------------------------------------------------------------------
  // withPermit(s)
  // ---------------------------------------------------------------------------------------------

  @Test
  void withPermitHoldsThePermitWhileRunningAndReleasesIt() {
    var semaphore = Semaphore.of(1);

    var program = semaphore.withPermit(
        pipe(semaphore.available(), available -> success("held:" + available)));

    assertThat(program.evalOrElseThrow()).isEqualTo("held:0");
    assertThat(semaphore.available().evalOrElseThrow()).isEqualTo(1L);
  }

  @Test
  void withPermitsHoldsAndReleasesSeveralPermits() {
    var semaphore = Semaphore.of(3);

    var program = semaphore.withPermits(2,
        pipe(semaphore.available(), available -> success(available)));

    assertThat(program.evalOrElseThrow()).isEqualTo(1L);
    assertThat(semaphore.available().evalOrElseThrow()).isEqualTo(3L);
  }

  @Test
  void withPermitReleasesWhenTheProgramFails() {
    var semaphore = Semaphore.of(1);

    var result = semaphore.withPermit(failure("boom")).eval();

    assertThat(result).isEqualTo(Result.failure("boom"));
    assertThat(semaphore.available().evalOrElseThrow()).isEqualTo(1L);
  }

  @Test
  void withPermitReleasesWhenTheProgramThrows() {
    var semaphore = Semaphore.of(1);

    var program = semaphore.withPermit(raise(IllegalStateException::new));

    assertThatThrownBy(program::eval).isInstanceOf(IllegalStateException.class);
    assertThat(semaphore.available().evalOrElseThrow()).isEqualTo(1L);
  }

  @Test
  void withPermitReleasesWhenTheProgramIsCancelled() throws Exception {
    var semaphore = Semaphore.of(1);
    var started = new CompletableFuture<Void>();
    var fiber = semaphore.withPermit(
            pipe(task(() -> started.complete(null)), _ -> never()))
        .fork().evalOrElseThrow();
    started.get(5, TimeUnit.SECONDS);
    assertThat(semaphore.available().evalOrElseThrow()).isZero();

    fiber.cancel().evalOrElseThrow();

    assertThat(fiber.poll().evalOrElseThrow()).contains(Outcome.canceled());
    assertThat(semaphore.available().evalOrElseThrow()).isEqualTo(1L);
  }

  @Test
  void permitHoldsThePermitWhileTheResourceIsUsed() {
    var semaphore = Semaphore.of(1);

    var result = semaphore.<Void, String>permit()
        .use(_ -> semaphore.available())
        .eval();

    assertThat(result).isEqualTo(Result.success(0L));
    assertThat(semaphore.available().evalOrElseThrow()).isEqualTo(1L);
  }

  @Test
  void permitsAreReleasedAfterTheResourcesAcquiredAfterThem() {
    var semaphore = Semaphore.of(3);
    var seen = new ConcurrentLinkedQueue<Long>();
    // the inner resource sees the permits still taken when it's released
    var inner = Resource.<Void, String, String>make(
        success("inner"),
        _ -> task(() -> seen.add(semaphore.available().evalOrElseThrow())));

    var result = semaphore.<Void, String>permits(2)
        .flatMap(_ -> inner)
        .use(_ -> failure("boom"))
        .eval();

    assertThat(result).isEqualTo(Result.failure("boom"));
    assertThat(seen).containsExactly(1L);
    assertThat(semaphore.available().evalOrElseThrow()).isEqualTo(3L);
  }

  @Test
  void aCancelledWaitForAPermitResourceLeavesTheQueue() {
    var semaphore = Semaphore.of(0);

    var program = semaphore.<Void, String>permit()
        .use(_ -> success("never"))
        .timeout(PAUSE);

    assertThatThrownBy(program::eval).isInstanceOf(TimeoutException.class);
    assertThat(semaphore.waiting()).isZero();
    assertThat(semaphore.available().evalOrElseThrow()).isZero();
  }

  // ---------------------------------------------------------------------------------------------
  // cancellation while waiting (the bug of the Queue based version)
  // ---------------------------------------------------------------------------------------------

  @Test
  void withPermitCanTimeOutWhileWaiting() {
    var semaphore = Semaphore.of(0);

    var program = semaphore.withPermit(success("never")).timeout(PAUSE);

    assertThatThrownBy(program::eval).isInstanceOf(TimeoutException.class);
    assertThat(semaphore.waiting()).isZero();
    assertThat(semaphore.available().evalOrElseThrow()).isZero();
  }

  @Test
  void acquireCanTimeOutWhileWaiting() {
    var semaphore = Semaphore.of(0);

    var program = semaphore.acquire().timeout(PAUSE);

    assertThatThrownBy(program::eval).isInstanceOf(TimeoutException.class);
    assertThat(semaphore.waiting()).isZero();
  }

  @Test
  void parZipCancelsAWaitingWithPermit() {
    var semaphore = Semaphore.of(0);
    var waiting = semaphore.withPermit(Program.<Void, String, Integer>success(1));
    var failing = pipe(sleep(PAUSE), _ -> Program.<Void, String, Integer>failure("error"));

    var result = parZip(waiting, failing, Integer::sum).eval();

    assertThat(result).isEqualTo(Result.failure("error"));
    assertThat(semaphore.waiting()).isZero();
    assertThat(semaphore.available().evalOrElseThrow()).isZero();
  }

  @Test
  void raceCancelsAWaitingWithPermit() {
    var semaphore = Semaphore.of(0);
    var waiting = semaphore.withPermit(success("permit"));
    var fast = delayed(PAUSE, success("fast"));

    var result = race(waiting, fast).evalOrElseThrow();

    assertThat(result).isEqualTo(Either.right("fast"));
    assertThat(semaphore.waiting()).isZero();
  }

  @Test
  void aTimedOutWaiterLeavesTheQueue() {
    var semaphore = Semaphore.of(0);
    var cancelled = semaphore.acquire()
        .timeout(Delay.ofMillis(1))
        .catchAll(_ -> unit());

    for (int i = 0; i < 100; i++) {
      cancelled.evalOrElseThrow();
    }

    assertThat(semaphore.waiting()).isZero();
    // the permit released afterwards is not handed to a dead waiter
    semaphore.release().evalOrElseThrow();
    assertThat(semaphore.available().evalOrElseThrow()).isEqualTo(1L);
  }

  // ---------------------------------------------------------------------------------------------
  // fairness
  // ---------------------------------------------------------------------------------------------

  @Test
  void waitersAreServedInFifoOrder() throws Exception {
    var semaphore = Semaphore.of(0);
    var order = new ConcurrentLinkedQueue<Integer>();
    var fibers = new ArrayList<Fiber<Void, Unit>>();

    for (int i = 0; i < 3; i++) {
      final int id = i;
      fibers.add(pipe(
              semaphore.<Void, Void>acquire(),
              _ -> task(() -> order.add(id)))
          .fork().evalOrElseThrow());
      awaitWaiting(semaphore, i + 1);
    }

    for (var fiber : fibers) {
      semaphore.release().evalOrElseThrow();
      // one at a time, so the order of the list is the order of the wake ups
      fiber.<Void>join().evalOrElseThrow();
    }

    assertThat(order).containsExactly(0, 1, 2);
  }

  @Test
  void aSmallRequestDoesNotOvertakeALargeOne() throws Exception {
    var semaphore = Semaphore.of(2);
    var large = semaphore.acquire(5).fork().evalOrElseThrow();
    awaitWaiting(semaphore, 1);
    var small = semaphore.acquire(1).fork().evalOrElseThrow();
    awaitWaiting(semaphore, 2);

    try {
      // 2 permits would be enough for the small one, but it queues behind the large one
      assertThat(small.poll().evalOrElseThrow()).isEmpty();
      assertThat(semaphore.available().evalOrElseThrow()).isEqualTo(2L);
    } finally {
      large.cancel().evalOrElseThrow();
      small.join().evalOrElseThrow();
    }
  }

  @Test
  void cancellingTheHeadOfTheLineWakesUpTheOnesBehindIt() throws Exception {
    var semaphore = Semaphore.of(2);
    var large = semaphore.acquire(5).fork().evalOrElseThrow();
    awaitWaiting(semaphore, 1);
    var small = semaphore.acquire(1).fork().evalOrElseThrow();
    awaitWaiting(semaphore, 2);

    large.cancel().evalOrElseThrow();

    small.join().evalOrElseThrow();
    assertThat(semaphore.waiting()).isZero();
    assertThat(semaphore.available().evalOrElseThrow()).isEqualTo(1L);
  }

  @Test
  void cancellingAWaiterInTheMiddleKeepsTheOrderOfTheOthers() throws Exception {
    var semaphore = Semaphore.of(0);
    var order = new ConcurrentLinkedQueue<String>();
    var first = semaphore.acquire(2).andThen(task(() -> order.add("first"))).fork().evalOrElseThrow();
    awaitWaiting(semaphore, 1);
    var middle = semaphore.acquire(3).andThen(task(() -> order.add("middle"))).fork().evalOrElseThrow();
    awaitWaiting(semaphore, 2);
    var last = semaphore.acquire(1).andThen(task(() -> order.add("last"))).fork().evalOrElseThrow();
    awaitWaiting(semaphore, 3);

    middle.cancel().evalOrElseThrow();
    semaphore.release(3).evalOrElseThrow();

    first.join().evalOrElseThrow();
    last.join().evalOrElseThrow();
    assertThat(order).containsExactlyInAnyOrder("first", "last");
    assertThat(middle.poll().evalOrElseThrow()).contains(Outcome.canceled());
    assertThat(semaphore.waiting()).isZero();
    assertThat(semaphore.available().evalOrElseThrow()).isZero();
  }

  @Test
  void aCancellationAfterTheGrantGivesThePermitsBack() {
    // the interleaving of a cancellation that arrives once a release has granted the permits to
    // the waiter: the release takes it out of the queue, and then the cancellation sees it's gone
    var semaphore = Semaphore.of(0);
    var done = Deferred.<Unit>create();

    assertThat(semaphore.register(2, done).evalOrElseThrow()).isFalse();
    semaphore.release(2).evalOrElseThrow();
    assertThat(semaphore.waiting()).isZero();
    assertThat(semaphore.available().evalOrElseThrow()).isZero();

    semaphore.cancelAcquire(2, done).evalOrElseThrow();

    assertThat(semaphore.available().evalOrElseThrow()).isEqualTo(2L);
    assertThat(semaphore.waiting()).isZero();
  }

  @Test
  void aCancellationBeforeTheGrantLeavesTheQueueWithoutTakingPermits() {
    var semaphore = Semaphore.of(1);
    var done = Deferred.<Unit>create();

    assertThat(semaphore.register(2, done).evalOrElseThrow()).isFalse();
    semaphore.cancelAcquire(2, done).evalOrElseThrow();

    assertThat(semaphore.waiting()).isZero();
    assertThat(semaphore.available().evalOrElseThrow()).isEqualTo(1L);
  }

  @Test
  void aReleaseThatOverflowsLeavesThePermitsUnchanged() {
    var semaphore = Semaphore.of(1);

    assertThatThrownBy(() -> semaphore.release(Long.MAX_VALUE).evalOrElseThrow())
        .isInstanceOf(ArithmeticException.class);
    assertThat(semaphore.available().evalOrElseThrow()).isEqualTo(1L);
  }

  @Test
  void aFailingProgramWithAPermitWakesUpTheNextWaiter() throws Exception {
    var semaphore = Semaphore.of(1);
    var holding = new CompletableFuture<Void>();
    var fail = new CompletableFuture<Void>();
    var first = semaphore.withPermit(
            pipe(task(() -> holding.complete(null)), _ -> pipe(task(fail::join), _ -> failure("boom"))))
        .fork().evalOrElseThrow();
    holding.get(5, TimeUnit.SECONDS);
    var second = semaphore.withPermit(success("second")).fork().evalOrElseThrow();
    awaitWaiting(semaphore, 1);

    fail.complete(null);

    assertThat(first.join().eval()).isEqualTo(Result.failure("boom"));
    assertThat(second.join().eval()).isEqualTo(Result.success("second"));
    assertThat(semaphore.available().evalOrElseThrow()).isEqualTo(1L);
  }

  @Test
  void aReleaseWakesUpSeveralWaitersAtOnce() throws Exception {
    var semaphore = Semaphore.of(0);
    var fibers = new ArrayList<Fiber<Void, Unit>>();
    for (int i = 0; i < 3; i++) {
      fibers.add(semaphore.<Void, Void>acquire().fork().evalOrElseThrow());
      awaitWaiting(semaphore, i + 1);
    }

    semaphore.release(3).evalOrElseThrow();

    for (var fiber : fibers) {
      fiber.<Void>join().evalOrElseThrow();
    }
    assertThat(semaphore.available().evalOrElseThrow()).isZero();
    assertThat(semaphore.waiting()).isZero();
  }

  // ---------------------------------------------------------------------------------------------
  // races and stress
  // ---------------------------------------------------------------------------------------------

  @RepeatedTest(50)
  void aReleaseRacingWithACancellationNeverLosesThePermit() throws Exception {
    var semaphore = Semaphore.of(0);
    var waiter = semaphore.acquire().fork().evalOrElseThrow();
    awaitWaiting(semaphore, 1);

    parAll(
        semaphore.release(),
        waiter.cancel()).evalOrElseThrow();

    var outcome = waiter.poll().evalOrElseThrow().orElseThrow();
    if (outcome instanceof Outcome.Canceled) {
      // the cancellation won, or the permit was handed over and given back
      assertThat(semaphore.available().evalOrElseThrow()).isEqualTo(1L);
    } else {
      // the waiter got the permit and keeps it
      assertThat(outcome).isInstanceOf(Outcome.Succeeded.class);
      assertThat(semaphore.available().evalOrElseThrow()).isZero();
    }
    assertThat(semaphore.waiting()).isZero();
  }

  @Test
  void neverMoreThanNProgramsRunAtTheSameTime() {
    var semaphore = Semaphore.of(3);
    var running = new AtomicInteger();
    var max = new AtomicInteger();

    var programs = IntStream.range(0, 50)
        .mapToObj(_ -> semaphore.withPermit(guarded(running, max)))
        .toList();

    parAll(programs).evalOrElseThrow();

    assertThat(max.get()).isBetween(1, 3);
    assertThat(semaphore.available().evalOrElseThrow()).isEqualTo(3L);
    assertThat(semaphore.waiting()).isZero();
  }

  @RepeatedTest(20)
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  void stressWithTimeoutsNeverLosesOrDuplicatesPermits() {
    var semaphore = Semaphore.of(3);
    var running = new AtomicInteger();
    var max = new AtomicInteger();

    // short timeouts, so some waits are cancelled just when a release hands them a permit
    var programs = IntStream.range(0, 300)
        .mapToObj(_ -> semaphore.withPermit(guarded(running, max))
            .timeout(Delay.ofNanos(200_000))
            .catchAll(e -> e instanceof TimeoutException ? unit() : raise(() -> e)))
        .toList();

    parAll(programs).evalOrElseThrow();

    assertThat(max.get()).isLessThanOrEqualTo(3);
    assertThat(running).hasValue(0);
    assertThat(semaphore.available().evalOrElseThrow()).isEqualTo(3L);
    assertThat(semaphore.waiting()).isZero();
  }

  @RepeatedTest(20)
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  void stressWithDifferentAmountsNeverLosesPermits() {
    var semaphore = Semaphore.of(5);

    var programs = IntStream.range(0, 300)
        .mapToObj(i -> semaphore.withPermits(1 + (i % 5), sleep(Delay.ofNanos(50_000)))
            .timeout(Delay.ofNanos(300_000))
            .catchAll(e -> e instanceof TimeoutException ? unit() : raise(() -> e)))
        .toList();

    parAll(programs).evalOrElseThrow();

    var state = semaphore.available().evalOrElseThrow() + " waiting=" + semaphore.waiting();
    assertThat(semaphore.available().evalOrElseThrow()).as(state).isEqualTo(5L);
    assertThat(semaphore.waiting()).isZero();
  }

  // ---------------------------------------------------------------------------------------------
  // helpers
  // ---------------------------------------------------------------------------------------------

  /** Tracks how many programs are inside at the same time, and the maximum seen. */
  private static Program<Void, Void, Unit> guarded(AtomicInteger running, AtomicInteger max) {
    return uncancelable(poll -> ensuring(
        pipe(
            task(() -> max.accumulateAndGet(running.incrementAndGet(), Math::max)),
            _ -> poll.apply(sleep(Delay.ofNanos(100_000)))),
        task(running::decrementAndGet)));
  }

  private static void awaitWaiting(Semaphore semaphore, int expected) {
    await(semaphore::waiting, expected);
  }

  private static void await(IntSupplier count, int expected) {
    var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (count.getAsInt() < expected) {
      if (System.nanoTime() > deadline) {
        throw new AssertionError("the programs never started waiting");
      }
      Thread.onSpinWait();
    }
  }
}
