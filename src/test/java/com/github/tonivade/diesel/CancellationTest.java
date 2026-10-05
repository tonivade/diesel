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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * The tests don't depend on timing: the programs that must be cancelled never complete on their
 * own, so a broken cancellation makes the test hang until the timeout instead of passing by luck,
 * and the failing programs only fail once all of them have started.
 *
 * <p>The cancelled programs have stopped, finalizers included, when the combinators return, so
 * the side effects can be checked right after evaluating the program.
 */
@Timeout(value = 10, unit = TimeUnit.SECONDS)
class CancellationTest {

  private final AtomicInteger executed = new AtomicInteger();
  private final AtomicInteger released = new AtomicInteger();
  private final AtomicInteger started = new AtomicInteger();
  private final CompletableFuture<Result<String, Void>> allStarted = new CompletableFuture<>();
  private int expected;

  @Test
  void parZipCancelsTheOtherProgramsOnFailure() {
    var result = parZip(blocked(), failingWhenStarted(1), (_, _) -> "done").eval();

    assertThat(result).isEqualTo(Result.failure("error"));
    assertThat(released).hasValue(1);
  }

  @Test
  void parAllCancelsTheOtherProgramsOnFailure() {
    var result = parAll(blocked(), blocked(), failingWhenStarted(2)).eval();

    assertThat(result).isEqualTo(Result.failure("error"));
    assertThat(released).hasValue(2);
  }

  @Test
  void parSequenceCancelsTheOtherProgramsOnFailure() {
    var result = parSequence(blocked(), blocked(), failingWhenStarted(2)).eval();

    assertThat(result).isEqualTo(Result.failure("error"));
    assertThat(released).hasValue(2);
  }

  @Test
  void eitherCancelsTheLoser() {
    var winner = whenStarted(1).andThen(success("winner"));

    var result = either(blocked(), winner).eval();

    assertThat(result).isEqualTo(Result.success(Either.right("winner")));
    assertThat(released).hasValue(1);
  }

  @Test
  void timeoutCancelsTheProgram() {
    var program = blocked().timeout(Duration.ofMillis(50));

    assertThatThrownBy(program::eval).isInstanceOf(TimeoutException.class);
    // the timeout may expire before the program starts, but a started program is always released
    assertThat(released).hasValue(started.get());
  }

  @Test
  void cancellingAForkedProgramStopsIt() {
    expected = 1;
    var future = blocked().fork().evalOrElseThrow();
    allStarted.join();

    assertThat(future.cancel(true)).isTrue();

    assertThatThrownBy(future::join).isInstanceOf(CancellationException.class);
    assertThat(future.isCancelled()).isTrue();
    assertThat(released).hasValue(1);
  }

  @Test
  void cancellationPropagatesToNestedForks() {
    var nested = parZip(blocked(), blocked(), (_, _) -> "done");

    var result = parZip(nested, failingWhenStarted(2), (_, _) -> "done").eval();

    // the nested programs have stopped too when the outer parZip returns
    assertThat(result).isEqualTo(Result.failure("error"));
    assertThat(released).hasValue(2);
  }

  @Test
  void catchAllDoesNotStopACancellation() {
    var program = start().andThen(never())
        .catchAll(_ -> run(executed::incrementAndGet))
        .ensuring(run(released::incrementAndGet));

    var result = parZip(program, failingWhenStarted(1), (_, _) -> "done").eval();

    assertThat(result).isEqualTo(Result.failure("error"));
    assertThat(executed).hasValue(0);
    assertThat(released).hasValue(1);
  }

  @Test
  void finalizerCannotBeCancelled() {
    var finalizer = Program.<Void, String>sleep(Duration.ofMillis(50)).andThen(run(released::incrementAndGet));
    var program = start().andThen(never()).ensuring(finalizer);

    var result = parZip(program, failingWhenStarted(1), (_, _) -> "done").eval();

    assertThat(result).isEqualTo(Result.failure("error"));
    assertThat(released).hasValue(1);
  }

  @Test
  void bracketReleasesTheResourceWhenCancelled() {
    var program = bracket(
        success("resource"),
        _ -> start().andThen(never()),
        _ -> run(released::incrementAndGet));

    var result = parZip(program, failingWhenStarted(1), (_, _) -> "done").eval();

    assertThat(result).isEqualTo(Result.failure("error"));
    assertThat(released).hasValue(1);
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
    var program = run(executed::incrementAndGet).ensuring(run(released::incrementAndGet));

    var result = parZip(program, program, (_, _) -> "done").eval();

    assertThat(result).isEqualTo(Result.success("done"));
    assertThat(executed).hasValue(2);
    assertThat(released).hasValue(2);
  }

  /**
   * A program that installs its finalizer, signals that it has started and then waits forever,
   * so it only finishes when it is cancelled.
   */
  private Program<Void, String, Void> blocked() {
    return start().andThen(never()).ensuring(run(released::incrementAndGet));
  }

  /**
   * A program that fails once the given number of programs have started.
   */
  private Program<Void, String, Void> failingWhenStarted(int count) {
    return whenStarted(count).andThen(failure("error"));
  }

  private Program<Void, String, Void> whenStarted(int count) {
    expected = count;
    return Program.from(allStarted);
  }

  private Program<Void, String, Void> start() {
    return run(() -> {
      if (started.incrementAndGet() == expected) {
        allStarted.complete(Result.unit());
      }
    });
  }

  private static Program<Void, String, Void> never() {
    return Program.async((_, _) -> {});
  }

  private static Program<Void, String, Void> run(Runnable runnable) {
    return task(runnable);
  }
}
