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
import static com.github.tonivade.diesel.Program.bracket;
import static com.github.tonivade.diesel.Program.failure;
import static com.github.tonivade.diesel.Program.raise;
import static com.github.tonivade.diesel.Program.success;
import static com.github.tonivade.diesel.Program.task;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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
  // keeps a program waiting until the test opens it
  private final CompletableFuture<Result<String, Void>> gate = new CompletableFuture<>();

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
  void raceCancelsTheLoser() {
    var winner = whenStarted(1).andThen(success("winner"));

    var result = race(blocked(), winner).eval();

    assertThat(result).isEqualTo(Result.success(Either.right("winner")));
    assertThat(released).hasValue(1);
  }

  @Test
  void parAnyCancelsTheOtherProgramsOnSuccess() {
    var winner = whenStarted(2).andThen(Program.<Void, String>unit());

    var result = parAny(blocked(), blocked(), winner).eval();

    assertThat(result).isEqualTo(Result.success(null));
    assertThat(released).hasValue(2);
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
  void finalizerThatForksCannotBeCancelled() {
    // timeout forks the program and a sleep
    var finalizer = Program.<Void, String>sleep(Duration.ofMillis(50))
        .timeout(Duration.ofSeconds(5))
        .andThen(run(released::incrementAndGet));
    var program = start().andThen(never()).ensuring(finalizer);

    var result = parZip(program, failingWhenStarted(1), (_, _) -> "done").eval();

    assertThat(result).isEqualTo(Result.failure("error"));
    assertThat(released).hasValue(1);
  }

  @Test
  void finalizerThatRunsInParallelCannotBeCancelled() {
    var finalizer = parAll(run(released::incrementAndGet), run(released::incrementAndGet));
    var program = start().andThen(never()).ensuring(finalizer);

    var result = parZip(program, failingWhenStarted(1), (_, _) -> "done").eval();

    assertThat(result).isEqualTo(Result.failure("error"));
    assertThat(released).hasValue(2);
  }

  @Test
  void finalizerCanRecoverFromAnExceptionWhenCancelled() {
    var finalizer = Program.<Void, String, Void>raise(IllegalStateException::new)
        .catchAll(_ -> run(released::incrementAndGet));
    var program = start().andThen(never()).ensuring(finalizer);

    var result = parZip(program, failingWhenStarted(1), (_, _) -> "done").eval();

    assertThat(result).isEqualTo(Result.failure("error"));
    assertThat(released).hasValue(1);
  }

  @Test
  void joiningACancelledForkIsAnOrdinaryException() {
    // the program that joins is not cancelled, so it can recover from it
    var program = never().fork()
        .flatMap(fork -> run(() -> fork.cancel(true)).andThen(Program.from(fork)))
        .map(_ -> "joined")
        .catchAll(_ -> success("recovered"));

    var result = program.eval();

    assertThat(result).isEqualTo(Result.success("recovered"));
  }

  @Test
  void rejectedForkDoesNotBlockTheCancellation() throws Exception {
    expected = 1;
    var error = new AtomicReference<Throwable>();
    Executor rejecting = _ -> {
      throw new RejectedExecutionException();
    };
    var program = never().fork(rejecting).andThen(never())
        .catchAll(e -> run(() -> error.set(e)).andThen(start()).andThen(never()));

    var future = program.fork().evalOrElseThrow();
    allStarted.get(5, TimeUnit.SECONDS);
    future.cancel(true);

    // get with a timeout: join can't be interrupted, so a regression would hang instead of failing
    assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS)).isInstanceOf(CancellationException.class);
    assertThat(error.get()).isInstanceOf(RejectedExecutionException.class);
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

  @Test
  void uncancelableDefersTheCancellation() throws Exception {
    expected = 1;
    var after = new AtomicInteger();
    var program = start().andThen(Program.from(gate)).andThen(run(executed::incrementAndGet))
        .uncancelable()
        .andThen(run(after::incrementAndGet));

    var future = program.fork().evalOrElseThrow();
    allStarted.get(5, TimeUnit.SECONDS);
    future.cancel(true);
    gate.complete(Result.unit());

    // the uncancelable part runs to completion, and the cancellation stops the program right after
    assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS)).isInstanceOf(CancellationException.class);
    assertThat(executed).hasValue(1);
    assertThat(after).hasValue(0);
  }

  @Test
  void combinatorsWaitForUncancelablePrograms() throws Exception {
    var failed = new CompletableFuture<Void>();
    var program = start().andThen(Program.from(gate)).andThen(run(executed::incrementAndGet)).uncancelable();
    var failing = whenStarted(1).andThen(run(() -> failed.complete(null))).andThen(Program.<Void, String, Void>failure("error"));

    var future = parZip(program, failing, (_, _) -> "done").fork().evalOrElseThrow();
    failed.get(5, TimeUnit.SECONDS);

    // parZip can't complete while the uncancelable program is still running
    assertThatThrownBy(() -> future.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
    gate.complete(Result.unit());
    assertThat(future.get(5, TimeUnit.SECONDS)).isEqualTo(Result.failure("error"));
    assertThat(executed).hasValue(1);
  }

  @Test
  void forksInsideUncancelableAreNotCancelled() throws Exception {
    var forked = new CompletableFuture<CompletableFuture<Result<String, Void>>>();
    var child = Program.<Void, String, Void>from(gate).andThen(run(executed::incrementAndGet));
    var program = child.fork().flatMap(f -> run(() -> forked.complete(f))).uncancelable()
        .andThen(start()).andThen(never());

    var result = parZip(program, failingWhenStarted(1), (_, _) -> "done").eval();

    // the parent has been cancelled, but the fork it started in the uncancelable part is still running
    var fork = forked.get(5, TimeUnit.SECONDS);
    assertThat(result).isEqualTo(Result.failure("error"));
    assertThat(fork.isDone()).isFalse();
    gate.complete(Result.unit());
    assertThat(fork.get(5, TimeUnit.SECONDS)).isEqualTo(Result.success(null));
    assertThat(executed).hasValue(1);
  }

  @Test
  void onCancelRunsWhenTheProgramIsCancelled() {
    var program = start().andThen(never()).onCancel(run(released::incrementAndGet));

    var result = parZip(program, failingWhenStarted(1), (_, _) -> "done").eval();

    assertThat(result).isEqualTo(Result.failure("error"));
    assertThat(released).hasValue(1);
  }

  @Test
  void onCancelDoesNotRunWhenTheProgramCompletes() {
    var program = run(executed::incrementAndGet).onCancel(run(released::incrementAndGet));

    var result = program.eval();

    assertThat(result).isEqualTo(Result.success(null));
    assertThat(executed).hasValue(1);
    assertThat(released).hasValue(0);
  }

  @Test
  void onCancelDoesNotRunWhenTheProgramFails() {
    var program = Program.<Void, String, Void>failure("error").onCancel(run(released::incrementAndGet));

    var result = program.eval();

    assertThat(result).isEqualTo(Result.failure("error"));
    assertThat(released).hasValue(0);
  }

  @Test
  void onCancelDoesNotRunWhenTheProgramThrows() {
    var program = Program.<Void, String, Void>raise(IllegalStateException::new)
        .onCancel(run(released::incrementAndGet));

    assertThatThrownBy(program::eval).isInstanceOf(IllegalStateException.class);
    assertThat(released).hasValue(0);
  }

  @Test
  void onCancelDoesNotRunWhenJoiningACancelledFork() {
    // the program that joins is not cancelled, the fork is
    var program = never().fork()
        .flatMap(fork -> run(() -> fork.cancel(true)).andThen(Program.from(fork)))
        .onCancel(run(released::incrementAndGet))
        .map(_ -> "joined")
        .catchAll(_ -> success("recovered"));

    var result = program.eval();

    assertThat(result).isEqualTo(Result.success("recovered"));
    assertThat(released).hasValue(0);
  }

  @Test
  void onCancelFinalizerCannotBeCancelled() {
    var finalizer = Program.<Void, String>sleep(Duration.ofMillis(50)).andThen(run(released::incrementAndGet));
    var program = start().andThen(never()).onCancel(finalizer);

    var result = parZip(program, failingWhenStarted(1), (_, _) -> "done").eval();

    assertThat(result).isEqualTo(Result.failure("error"));
    assertThat(released).hasValue(1);
  }

  @Test
  void onCancelRunsTogetherWithEnsuring() {
    var program = start().andThen(never())
        .onCancel(run(released::incrementAndGet))
        .ensuring(run(executed::incrementAndGet));

    var result = parZip(program, failingWhenStarted(1), (_, _) -> "done").eval();

    assertThat(result).isEqualTo(Result.failure("error"));
    assertThat(released).hasValue(1);
    assertThat(executed).hasValue(1);
  }

  @Test
  void asyncCancelableStopsTheOperationWhenCancelled() throws Exception {
    var scheduler = Executors.newSingleThreadScheduledExecutor();
    try {
      var scheduled = new CompletableFuture<ScheduledFuture<?>>();
      var operation = Program.<Void, String, String>asyncCancelable((_, future) -> {
        var task = scheduler.schedule(() -> future.complete(Result.success("late")), 1, TimeUnit.HOURS);
        scheduled.complete(task);
        signalStarted();
        return run(() -> task.cancel(false));
      });

      var result = parZip(operation, failingWhenStarted(1), (_, _) -> "done").eval();

      assertThat(result).isEqualTo(Result.failure("error"));
      assertThat(scheduled.get(5, TimeUnit.SECONDS).isCancelled()).isTrue();
    } finally {
      scheduler.shutdownNow();
    }
  }

  @Test
  void asyncCancelableDoesNotRunTheCancelerWhenTheOperationCompletes() {
    var operation = Program.<Void, String, String>asyncCancelable((_, future) -> {
      future.complete(Result.success("done"));
      return run(released::incrementAndGet);
    });

    var result = operation.eval();

    assertThat(result).isEqualTo(Result.success("done"));
    assertThat(released).hasValue(0);
  }

  @Test
  void asyncCancelableDoesNotRunTheCancelerWhenTheOperationFails() {
    var failing = Program.<Void, String, String>asyncCancelable((_, future) -> {
      future.complete(Result.failure("error"));
      return run(released::incrementAndGet);
    });
    var throwing = Program.<Void, String, String>asyncCancelable((_, future) -> {
      future.completeExceptionally(new IllegalStateException());
      return run(released::incrementAndGet);
    });

    assertThat(failing.eval()).isEqualTo(Result.failure("error"));
    assertThatThrownBy(throwing::eval).isInstanceOf(IllegalStateException.class);
    assertThat(released).hasValue(0);
  }

  @Test
  void asyncCancelableCancelerCannotBeCancelled() {
    var canceler = Program.<Void, String>sleep(Duration.ofMillis(50)).andThen(run(released::incrementAndGet));
    var operation = Program.<Void, String, String>asyncCancelable((_, _) -> {
      signalStarted();
      return canceler;
    });

    var result = parZip(operation, failingWhenStarted(1), (_, _) -> "done").eval();

    assertThat(result).isEqualTo(Result.failure("error"));
    assertThat(released).hasValue(1);
  }

  @Test
  void asyncCancelableWaitsForTheOperationInAnUncancelableRegion() throws Exception {
    expected = 1;
    var operation = Program.<Void, String, Void>asyncCancelable((_, future) -> {
      var _ = gate.whenComplete((_, _) -> future.complete(Result.unit()));
      signalStarted();
      return run(released::incrementAndGet);
    });
    var program = operation.andThen(run(executed::incrementAndGet)).uncancelable();

    var future = program.fork().evalOrElseThrow();
    allStarted.get(5, TimeUnit.SECONDS);
    future.cancel(true);
    gate.complete(Result.unit());

    // the operation isn't cancelled, the region waits for it instead. The whole program is the
    // region, so there is no step left where the cancellation could stop it: it completes normally
    assertThat(future.get(5, TimeUnit.SECONDS)).isEqualTo(Result.success(null));
    assertThat(executed).hasValue(1);
    assertThat(released).hasValue(0);
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
    return run(this::signalStarted);
  }

  private void signalStarted() {
    if (started.incrementAndGet() == expected) {
      allStarted.complete(Result.unit());
    }
  }

  private static Program<Void, String, Void> never() {
    return Program.never();
  }

  private static Program<Void, String, Void> run(Runnable runnable) {
    return task(runnable);
  }
}
