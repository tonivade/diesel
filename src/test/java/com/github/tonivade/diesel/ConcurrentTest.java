/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Concurrent.either;
import static com.github.tonivade.diesel.Concurrent.parAll;
import static com.github.tonivade.diesel.Concurrent.parAny;
import static com.github.tonivade.diesel.Concurrent.parSequence;
import static com.github.tonivade.diesel.Concurrent.parZip;
import static com.github.tonivade.diesel.Concurrent.race;
import static com.github.tonivade.diesel.Program.delayed;
import static com.github.tonivade.diesel.Program.failure;
import static com.github.tonivade.diesel.Program.never;
import static com.github.tonivade.diesel.Program.raise;
import static com.github.tonivade.diesel.Program.sleep;
import static com.github.tonivade.diesel.Program.success;
import static com.github.tonivade.diesel.Program.task;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.tonivade.diesel.ProgramTest.Tuple;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

@MockitoSettings
class ConcurrentTest {

  Executor singleThreadExecutor = Executors.newSingleThreadExecutor();

  @Test
  void shouldParallelize() {
    var p1 = delayed(Delay.ofSeconds(2), () -> 10);
    var p2 = delayed(Delay.ofSeconds(2), () -> "hello");

    var result = parZip(p1, p2, Tuple::new).timed().evalOrElseThrow();

    assertThat(result.duration())
      .isCloseTo(Duration.ofSeconds(2), Duration.ofMillis(100));
    assertThat(result.value())
      .isEqualTo(new Tuple<>(10, "hello"));
  }

  @Test
  void shouldParallelizeWithSingleThreadExcecutor() {
    var p1 = delayed(Delay.ofSeconds(2), () -> 10);
    var p2 = delayed(Delay.ofSeconds(2), () -> "hello");

    var result = parZip(p1, p2, Tuple::new, singleThreadExecutor).timed().evalOrElseThrow();

    assertThat(result.duration())
      .isCloseTo(Duration.ofSeconds(4), Duration.ofMillis(100));
    assertThat(result.value())
      .isEqualTo(new Tuple<>(10, "hello"));
  }

  @Test
  void shouldFailFast() throws Exception {
    var p1 = delayed(Delay.ofSeconds(5), () -> 10);
    var p2 = sleep(Delay.ofSeconds(2)).andThen(failure("error"));

    var result = parZip(p1, p2, Tuple::new).timedResult().evalOrElseThrow();

    assertThat(result.duration())
      .isCloseTo(Duration.ofSeconds(2), Duration.ofMillis(100));
    assertThat(result.value())
      .isEqualTo(Result.failure("error"));
  }

  @Test
  void shouldRace() {
    var p1 = never();
    var p2 = delayed(Delay.ofSeconds(2), () -> "hello");

    var result = either(p1, p2).timed().evalOrElseThrow();

    assertThat(result.duration())
      .isCloseTo(Duration.ofSeconds(2), Duration.ofMillis(100));
    assertThat(result.value())
      .isEqualTo(Either.right("hello"));
  }

  @Test
  void shouldBothFail() {
    var p1 = sleep(Delay.ofSeconds(3)).andThen(failure("error 1"));
    var p2 = sleep(Delay.ofSeconds(2)).andThen(raise(UnsupportedOperationException::new));

    var result = either(p1, p2).timedResult().evalOrElseThrow();

    assertThat(result.duration())
      .isCloseTo(Duration.ofSeconds(3), Duration.ofMillis(100));
    assertThat(result.value())
      .isEqualTo(Result.failure("error 1"));
  }

  @Test
  void shouldRaceWhenOneReturnFailure() {
    var p1 = delayed(Delay.ofSeconds(3), () -> 10);
    var p2 = sleep(Delay.ofSeconds(1)).andThen(failure("error"));

    var result = either(p1, p2).timed().evalOrElseThrow();

    assertThat(result.duration())
      .isCloseTo(Duration.ofSeconds(3), Duration.ofMillis(100));
    assertThat(result.value())
      .isEqualTo(Either.left(10));
  }

  @Test
  void shouldRaceWhenOneThrowsException() {
    var p1 = delayed(Delay.ofSeconds(3), () -> 10);
    var p2 = sleep(Delay.ofSeconds(1)).andThen(raise(UnsupportedOperationException::new));

    var result = either(p1, p2).timed().evalOrElseThrow();

    assertThat(result.duration())
      .isCloseTo(Duration.ofSeconds(3), Duration.ofMillis(100));
    assertThat(result.value())
      .isEqualTo(Either.left(10));
  }

  @Test
  void shouldReturnFirstSuccess() {
    var p1 = delayed(Delay.ofSeconds(3), () -> 10);
    var p2 = delayed(Delay.ofSeconds(2), () -> 20);
    var p3 = sleep(Delay.ofSeconds(1)).<Integer>andThen(failure("error"));
    var p4 = sleep(Delay.ofSeconds(1)).<Integer>andThen(raise(UnsupportedOperationException::new));

    var result = parAny(p1, p2, p3, p4).timed().evalOrElseThrow();

    assertThat(result.duration())
      .isCloseTo(Duration.ofSeconds(2), Duration.ofMillis(100));
    assertThat(result.value())
      .isEqualTo(20);
  }

  @Test
  void shouldFailWhenAllFail() {
    var p1 = sleep(Delay.ofSeconds(1)).<Integer>andThen(failure("error1"));
    var p2 = sleep(Delay.ofSeconds(2)).<Integer>andThen(failure("error2"));

    var start = System.nanoTime();
    var result = parAny(p1, p2).eval();
    var duration = Duration.ofNanos(System.nanoTime() - start);

    assertThat(duration)
      .isCloseTo(Duration.ofSeconds(2), Duration.ofMillis(100));
    assertThat(result)
      .isEqualTo(Result.failure("error2"));
  }

  @Test
  void shouldExecuteAllProgramsInParallel(@Mock Supplier<String> supplier) {
    when(supplier.get()).thenReturn("hi!");

    var result = parAll(
        delayed(Delay.ofSeconds(1), supplier),
        delayed(Delay.ofSeconds(2), supplier),
        delayed(Delay.ofSeconds(3), supplier)).timed().evalOrElseThrow();

    assertThat(result.duration())
      .isCloseTo(Duration.ofSeconds(3), Duration.ofMillis(100));
    verify(supplier, times(3)).get();
  }

  @Test
  void shouldExecuteAllProgramsWithSingleThreadExecutor(@Mock Supplier<String> supplier) {
    when(supplier.get()).thenReturn("hi!");

    var result = parAll(singleThreadExecutor,
        delayed(Delay.ofSeconds(1), supplier),
        delayed(Delay.ofSeconds(2), supplier),
        delayed(Delay.ofSeconds(3), supplier)).timed().evalOrElseThrow();

    assertThat(result.duration())
      .isCloseTo(Duration.ofSeconds(6), Duration.ofMillis(100));
    verify(supplier, times(3)).get();
  }

  @Test
  void shouldExecuteAllProgramsInParallelAndCollectsResult(@Mock Supplier<String> supplier) {
    when(supplier.get()).thenReturn("1", "2", "3");

    var result = parSequence(
        delayed(Delay.ofSeconds(1), supplier),
        delayed(Delay.ofSeconds(2), supplier),
        delayed(Delay.ofSeconds(3), supplier)).timed().evalOrElseThrow();

    assertThat(result.duration())
      .isCloseTo(Duration.ofSeconds(3), Duration.ofMillis(100));
    assertThat(result.value()).isEqualTo(List.of("1", "2", "3"));
    verify(supplier, times(3)).get();
  }

  @Test
  void shouldExecuteAllProgramsWithSingleThreadExecutorAndCollectsResult(@Mock Supplier<String> supplier) {
    when(supplier.get()).thenReturn("1", "2", "3");

    var result = parSequence(singleThreadExecutor,
        delayed(Delay.ofSeconds(1), supplier),
        delayed(Delay.ofSeconds(2), supplier),
        delayed(Delay.ofSeconds(3), supplier)).timed().evalOrElseThrow();

    assertThat(result.duration())
      .isCloseTo(Duration.ofSeconds(6), Duration.ofMillis(100));
    assertThat(result.value()).isEqualTo(List.of("1", "2", "3"));
    verify(supplier, times(3)).get();
  }

  @Test
  void forkedProgramThrowsTheOriginalException() {
    var program = boom().fork().flatMap(Fiber::join);

    assertThatThrownBy(program::evalOrElseThrow).isExactlyInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void raceThrowsTheOriginalException() {
    var program = race(boom(), never());

    assertThatThrownBy(program::evalOrElseThrow).isExactlyInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void eitherThrowsTheOriginalException() {
    var program = either(boom(), boom());

    assertThatThrownBy(program::evalOrElseThrow).isExactlyInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void parZipThrowsTheOriginalException() {
    var program = parZip(boom(), never(), (a, _) -> a);

    assertThatThrownBy(program::evalOrElseThrow).isExactlyInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void parAllThrowsTheOriginalException() {
    var program = parAll(boom(), never());

    assertThatThrownBy(program::evalOrElseThrow).isExactlyInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void parSequenceThrowsTheOriginalException() {
    var program = parSequence(boom(), never());

    assertThatThrownBy(program::evalOrElseThrow).isExactlyInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void parAnyThrowsTheOriginalException() {
    var program = parAny(boom(), boom());

    assertThatThrownBy(program::evalOrElseThrow).isExactlyInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void timeoutThrowsTheOriginalException() {
    var program = boom().timeout(Delay.ofSeconds(10));

    assertThatThrownBy(program::evalOrElseThrow).isExactlyInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void catchAllReceivesTheOriginalException() {
    var program = parZip(boom(), never(), (_, _) -> "done")
        .catchAll(e -> success(e.getClass().getSimpleName()));

    assertThat(program.evalOrElseThrow()).isEqualTo("UnsupportedOperationException");
  }

  @Test
  void emptyParAllCompletesWithUnit() {
    var result = parAll(
        List.<Program<Void, String, ?>>of())
        .evalOrElseThrow();

    assertThat(result).isEqualTo(Unit.unit());
  }

  @Test
  void parAllCompletesWithUnitAfterAllProgramsFinish() {
    var calls = new AtomicInteger();

    var first = task(calls::incrementAndGet);
    var second = task(calls::incrementAndGet);

    var result = parAll(first, second).evalOrElseThrow();

    assertThat(result).isEqualTo(Unit.unit());
    assertThat(calls).hasValue(2);
  }

  @Test
  void parZipFinisherRunsInTheThreadThatEvaluatesTheParent() {
    var finisherThread = new AtomicReference<Thread>();
    var program = parZip(
        delayed(Delay.ofMillis(50), () -> 1),
        delayed(Delay.ofMillis(100), () -> 2),
        (a, b) -> {
          finisherThread.set(Thread.currentThread());
          return a + b;
        });

    assertThat(program.evalOrElseThrow()).isEqualTo(3);
    assertThat(finisherThread.get()).isSameAs(Thread.currentThread());
  }

  @Test
  void anExceptionInTheFinisherIsRecoverableWithCatchAll() {
    var program = parZip(
        success(1),
        success(2),
        (_, _) -> {
          throw new IllegalStateException("boom");
        })
        .catchAll(e -> success(e.getClass().getSimpleName()));

    // the original exception, not a CompletionException
    assertThat(program.evalOrElseThrow()).isEqualTo("IllegalStateException");
  }

  @Test
  void theFinisherDoesNotOccupyTheThreadOfAChild() {
    var executor = Executors.newSingleThreadExecutor();
    try {
      var started = new CompletableFuture<Unit>();
      var release = new CompletableFuture<Unit>();
      var program = parZip(
          success(1),
          success(2),
          (a, b) -> {
            started.complete(Unit.unit());
            release.join();               // a finisher that blocks
            return a + b;
          },
          executor);

      var fiber = program.fork().evalOrElseThrow();
      started.join();

      // the only thread of the executor is free, so another program can run on it
      var other = success(7).fork(executor).evalOrElseThrow();
      assertThat(other.<Void>join().eval()).isEqualTo(Result.success(7));
      release.complete(Unit.unit());
      assertThat(fiber.<Void>join().eval()).isEqualTo(Result.success(3));
    } finally {
      executor.shutdownNow();
    }
  }

  private static Program<Void, String, Integer> boom() {
    return raise(UnsupportedOperationException::new);
  }
}
