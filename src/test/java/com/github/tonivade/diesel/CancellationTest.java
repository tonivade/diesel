/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Concurrent.either;
import static com.github.tonivade.diesel.Concurrent.parAll;
import static com.github.tonivade.diesel.Concurrent.parSequence;
import static com.github.tonivade.diesel.Concurrent.parZip;
import static com.github.tonivade.diesel.Program.bracket;
import static com.github.tonivade.diesel.Program.failure;
import static com.github.tonivade.diesel.Program.raise;
import static com.github.tonivade.diesel.Program.sleep;
import static com.github.tonivade.diesel.Program.success;
import static com.github.tonivade.diesel.Program.task;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class CancellationTest {

  private static final Duration FAST = Duration.ofMillis(50);
  private static final Duration SLOW = Duration.ofMillis(500);
  // longer than SLOW, to check that the cancelled programs never complete
  private static final Duration AFTER_SLOW = Duration.ofMillis(700);

  private final AtomicInteger executed = new AtomicInteger();
  private final AtomicInteger released = new AtomicInteger();

  @Test
  void parZipCancelsTheOtherProgramsOnFailure() throws InterruptedException {
    var start = System.nanoTime();
    var result = parZip(slow(), failing(), (a, _) -> a).eval();
    var duration = Duration.ofNanos(System.nanoTime() - start);

    assertThat(result).isEqualTo(Result.failure("error"));
    assertThat(duration).isLessThan(SLOW);
    Thread.sleep(AFTER_SLOW);
    assertThat(executed).hasValue(0);
  }

  @Test
  void parAllCancelsTheOtherProgramsOnFailure() throws InterruptedException {
    var result = parAll(slow(), slow(), failing()).eval();

    assertThat(result).isEqualTo(Result.failure("error"));
    Thread.sleep(AFTER_SLOW);
    assertThat(executed).hasValue(0);
  }

  @Test
  void parSequenceCancelsTheOtherProgramsOnFailure() throws InterruptedException {
    var result = parSequence(slow(), slow(), failing()).eval();

    assertThat(result).isEqualTo(Result.failure("error"));
    Thread.sleep(AFTER_SLOW);
    assertThat(executed).hasValue(0);
  }

  @Test
  void eitherCancelsTheLoser() throws InterruptedException {
    var result = either(slow(), pause(FAST).andThen(success("fast"))).eval();

    assertThat(result).isEqualTo(Result.success(Either.right("fast")));
    Thread.sleep(AFTER_SLOW);
    assertThat(executed).hasValue(0);
  }

  @Test
  void timeoutCancelsTheProgram() throws InterruptedException {
    var program = slow().timeout(FAST);

    assertThatThrownBy(program::eval).isInstanceOf(TimeoutException.class);
    Thread.sleep(AFTER_SLOW);
    assertThat(executed).hasValue(0);
  }

  @Test
  void cancellingAForkedProgramStopsIt() throws InterruptedException {
    var future = slow().fork().evalOrElseThrow();

    assertThat(future.cancel(true)).isTrue();

    assertThatThrownBy(future::join).isInstanceOf(CancellationException.class);
    assertThat(future.isCancelled()).isTrue();
    Thread.sleep(AFTER_SLOW);
    assertThat(executed).hasValue(0);
  }

  @Test
  void cancellationPropagatesToNestedForks() throws InterruptedException {
    var nested = parZip(slow(), slow(), (a, _) -> a);

    var result = parZip(nested, failing(), (a, _) -> a).eval();

    assertThat(result).isEqualTo(Result.failure("error"));
    Thread.sleep(AFTER_SLOW);
    assertThat(executed).hasValue(0);
  }

  @Test
  void catchAllDoesNotStopACancellation() throws InterruptedException {
    var program = slow().catchAll(_ -> run(executed::incrementAndGet));

    var result = parZip(program, failing(), (a, _) -> a).eval();

    assertThat(result).isEqualTo(Result.failure("error"));
    Thread.sleep(AFTER_SLOW);
    assertThat(executed).hasValue(0);
  }

  @Test
  void ensuringRunsTheFinalizerWhenCancelled() {
    var program = slow().ensuring(run(released::incrementAndGet));

    var result = parZip(program, failing(), (a, _) -> a).eval();

    // the cancelled programs have finished, finalizers included, when parZip returns
    assertThat(result).isEqualTo(Result.failure("error"));
    assertThat(released).hasValue(1);
    assertThat(executed).hasValue(0);
  }

  @Test
  void finalizerCannotBeCancelled() {
    var finalizer = pause(FAST).andThen(run(released::incrementAndGet));
    var program = slow().ensuring(finalizer);

    var result = parZip(program, failing(), (a, _) -> a).eval();

    assertThat(result).isEqualTo(Result.failure("error"));
    assertThat(released).hasValue(1);
  }

  @Test
  void bracketReleasesTheResourceWhenCancelled() {
    var program = bracket(
        success("resource"),
        _ -> slow(),
        _ -> run(released::incrementAndGet));

    var result = parZip(program, failing(), (a, _) -> a).eval();

    assertThat(result).isEqualTo(Result.failure("error"));
    assertThat(released).hasValue(1);
    assertThat(executed).hasValue(0);
  }

  @Test
  void bracketReleasesTheResourceWhenUseThrows() {
    var program = Program.<Void, String, String, Void>bracket(
        success("resource"),
        _ -> raise(IllegalStateException::new),
        _ -> run(released::incrementAndGet));

    assertThatThrownBy(program::eval).isInstanceOf(IllegalStateException.class);
    assertThat(released).hasValue(1);
  }

  @Test
  void ensuringRunsTheFinalizerWhenThrows() {
    var program = Program.<Void, String, Void>raise(IllegalStateException::new)
        .ensuring(run(released::incrementAndGet));

    assertThatThrownBy(program::eval).isInstanceOf(IllegalStateException.class);
    assertThat(released).hasValue(1);
  }

  @Test
  void programsNotCancelledAreNotAffected() {
    var program = pause(FAST).andThen(run(executed::incrementAndGet))
        .ensuring(run(released::incrementAndGet));

    var result = parZip(program, program, (a, _) -> a).eval();

    assertThat(result).isEqualTo(Result.success(null));
    assertThat(executed).hasValue(2);
    assertThat(released).hasValue(2);
  }

  private Program<Void, String, Void> slow() {
    return pause(SLOW).andThen(run(executed::incrementAndGet));
  }

  private static Program<Void, String, Void> failing() {
    return pause(FAST).andThen(failure("error"));
  }

  private static Program<Void, String, Void> pause(Duration duration) {
    return sleep(duration);
  }

  private static Program<Void, String, Void> run(Runnable runnable) {
    return task(runnable);
  }
}
