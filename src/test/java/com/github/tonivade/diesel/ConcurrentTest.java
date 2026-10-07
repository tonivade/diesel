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
import static com.github.tonivade.diesel.Program.delayed;
import static com.github.tonivade.diesel.Program.failure;
import static com.github.tonivade.diesel.Program.never;
import static com.github.tonivade.diesel.Program.raise;
import static com.github.tonivade.diesel.Program.sleep;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.tonivade.diesel.ProgramTest.Tuple;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

@MockitoSettings
class ConcurrentTest {

  Executor singleThreadExecutor = Executors.newSingleThreadExecutor();

  @Test
  void shouldParallelize() {
    var p1 = delayed(Duration.ofSeconds(2), () -> 10);
    var p2 = delayed(Duration.ofSeconds(2), () -> "hello");

    var result = parZip(p1, p2, Tuple::new).timed().evalOrElseThrow();

    assertThat(result.duration())
      .isCloseTo(Duration.ofSeconds(2), Duration.ofMillis(100));
    assertThat(result.value())
      .isEqualTo(new Tuple<>(10, "hello"));
  }

  @Test
  void shouldParallelizeWithSingleThreadExcecutor() {
    var p1 = delayed(Duration.ofSeconds(2), () -> 10);
    var p2 = delayed(Duration.ofSeconds(2), () -> "hello");

    var result = parZip(p1, p2, Tuple::new, singleThreadExecutor).timed().evalOrElseThrow();

    assertThat(result.duration())
      .isCloseTo(Duration.ofSeconds(4), Duration.ofMillis(100));
    assertThat(result.value())
      .isEqualTo(new Tuple<>(10, "hello"));
  }

  @Test
  void shouldFailFast() throws Exception {
    var p1 = delayed(Duration.ofSeconds(5), () -> 10);
    var p2 = sleep(Duration.ofSeconds(2)).andThen(failure("error"));

    var start = System.nanoTime();
    var result = parZip(p1, p2, Tuple::new).eval();
    var duration = Duration.ofNanos(System.nanoTime() - start);

    assertThat(duration)
      .isCloseTo(Duration.ofSeconds(2), Duration.ofMillis(100));
    assertThat(result)
      .isEqualTo(Result.failure("error"));
  }

  @Test
  void shouldRace() {
    var p1 = never();
    var p2 = delayed(Duration.ofSeconds(2), () -> "hello");

    var result = either(p1, p2).timed().evalOrElseThrow();

    assertThat(result.duration())
      .isCloseTo(Duration.ofSeconds(2), Duration.ofMillis(100));
    assertThat(result.value())
      .isEqualTo(Either.right("hello"));
  }

  @Test
  void shouldBothFail() {
    var p1 = sleep(Duration.ofSeconds(3)).andThen(failure("error 1"));
    var p2 = sleep(Duration.ofSeconds(2)).andThen(raise(UnsupportedOperationException::new));

    var start = System.nanoTime();
    var result = either(p1, p2).eval();
    var duration = Duration.ofNanos(System.nanoTime() - start);

    assertThat(duration)
      .isCloseTo(Duration.ofSeconds(3), Duration.ofMillis(100));
    assertThat(result)
      .isEqualTo(Result.failure("error 1"));
  }

  @Test
  void shouldRaceWhenOneReturnFailure() {
    var p1 = Program.<Void, String, Integer>delayed(Duration.ofSeconds(3), () -> 10);
    var p2 = Program.<Void, String>sleep(Duration.ofSeconds(1)).andThen(failure("error"));

    var result = either(p1, p2).timed().evalOrElseThrow();

    assertThat(result.duration())
      .isCloseTo(Duration.ofSeconds(3), Duration.ofMillis(100));
    assertThat(result.value())
      .isEqualTo(Either.left(10));
  }

  @Test
  void shouldRaceWhenOneThrowsException() {
    var p1 = Program.<Void, String, Integer>delayed(Duration.ofSeconds(3), () -> 10);
    var p2 = Program.<Void, String>sleep(Duration.ofSeconds(1)).andThen(raise(UnsupportedOperationException::new));

    var result = either(p1, p2).timed().evalOrElseThrow();

    assertThat(result.duration())
      .isCloseTo(Duration.ofSeconds(3), Duration.ofMillis(100));
    assertThat(result.value())
      .isEqualTo(Either.left(10));
  }

  @Test
  void shouldReturnFirstSuccess() {
    var p1 = Program.<Void, String, Integer>delayed(Duration.ofSeconds(3), () -> 10);
    var p2 = Program.<Void, String, Integer>delayed(Duration.ofSeconds(2), () -> 20);
    var p3 = Program.<Void, String>sleep(Duration.ofSeconds(1)).<Integer>andThen(failure("error"));
    var p4 = Program.<Void, String>sleep(Duration.ofSeconds(1)).<Integer>andThen(raise(UnsupportedOperationException::new));

    var result = parAny(p1, p2, p3, p4).timed().evalOrElseThrow();

    assertThat(result.duration())
      .isCloseTo(Duration.ofSeconds(2), Duration.ofMillis(100));
    assertThat(result.value())
      .isEqualTo(20);
  }

  @Test
  void shouldFailWhenAllFail() {
    var p1 = Program.<Void, String>sleep(Duration.ofSeconds(1)).<Integer>andThen(failure("error1"));
    var p2 = Program.<Void, String>sleep(Duration.ofSeconds(2)).<Integer>andThen(failure("error2"));

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
        delayed(Duration.ofSeconds(1), supplier),
        delayed(Duration.ofSeconds(2), supplier),
        delayed(Duration.ofSeconds(3), supplier)).timed().evalOrElseThrow();

    assertThat(result.duration())
      .isCloseTo(Duration.ofSeconds(3), Duration.ofMillis(100));
    verify(supplier, times(3)).get();
  }

  @Test
  void shouldExecuteAllProgramsWithSingleThreadExecutor(@Mock Supplier<String> supplier) {
    when(supplier.get()).thenReturn("hi!");

    var result = parAll(singleThreadExecutor,
        delayed(Duration.ofSeconds(1), supplier),
        delayed(Duration.ofSeconds(2), supplier),
        delayed(Duration.ofSeconds(3), supplier)).timed().evalOrElseThrow();

    assertThat(result.duration())
      .isCloseTo(Duration.ofSeconds(6), Duration.ofMillis(100));
    verify(supplier, times(3)).get();
  }

  @Test
  void shouldExecuteAllProgramsInParallelAndCollectsResult(@Mock Supplier<String> supplier) {
    when(supplier.get()).thenReturn("1", "2", "3");

    var result = parSequence(
        delayed(Duration.ofSeconds(1), supplier),
        delayed(Duration.ofSeconds(2), supplier),
        delayed(Duration.ofSeconds(3), supplier)).timed().evalOrElseThrow();

    assertThat(result.duration())
      .isCloseTo(Duration.ofSeconds(3), Duration.ofMillis(100));
    assertThat(result.value()).isEqualTo(List.of("1", "2", "3"));
    verify(supplier, times(3)).get();
  }

  @Test
  void shouldExecuteAllProgramsWithSingleThreadExecutorAndCollectsResult(@Mock Supplier<String> supplier) {
    when(supplier.get()).thenReturn("1", "2", "3");

    var result = parSequence(singleThreadExecutor,
        delayed(Duration.ofSeconds(1), supplier),
        delayed(Duration.ofSeconds(2), supplier),
        delayed(Duration.ofSeconds(3), supplier)).timed().evalOrElseThrow();

    assertThat(result.duration())
      .isCloseTo(Duration.ofSeconds(6), Duration.ofMillis(100));
    assertThat(result.value()).isEqualTo(List.of("1", "2", "3"));
    verify(supplier, times(3)).get();
  }
}
