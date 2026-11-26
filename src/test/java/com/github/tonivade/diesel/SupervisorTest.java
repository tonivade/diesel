/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Combine.delayed;
import static com.github.tonivade.diesel.Combine.ensuring;
import static com.github.tonivade.diesel.Combine.pipe;
import static com.github.tonivade.diesel.Program.never;
import static com.github.tonivade.diesel.Program.success;
import static com.github.tonivade.diesel.Program.task;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tonivade.diesel.Supervisor.SupervisorClosedException;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 10, unit = TimeUnit.SECONDS)
class SupervisorTest {

  @Test
  void joinsTheResultOfASupervisedProgram() {
    var program = Supervisor.make().use(
        supervisor -> pipe(supervisor.supervise(success(42)), Fiber::join));

    assertThat(program.evalOrElseThrow()).isEqualTo(42);
  }

  @Test
  void releaseCancelsTheRunningProgramsAndWaitsForTheirFinalizers() {
    var started = new CountDownLatch(1);
    var finalized = new AtomicBoolean();
    var running = ensuring(
        pipe(task(started::countDown), _ -> never()),
        task(() -> finalized.set(true)));

    var program = Supervisor.make().use(
        supervisor -> pipe(supervisor.supervise(running), _ -> task(() -> await(started))));

    program.evalOrElseThrow();

    assertThat(finalized).isTrue();
  }

  @Test
  void pollReturnsCanceledWhenTheReleaseCancelsTheProgram() {
    var started = new CountDownLatch(1);
    var fiber = Supervisor.make().use(
        supervisor -> pipe(
            supervisor.supervise(pipe(task(started::countDown), _ -> never())),
            running -> pipe(task(() -> await(started)), _ -> success(running))))
        .evalOrElseThrow();

    assertThat(fiber.poll().evalOrElseThrow()).isEqualTo(Optional.of(Outcome.canceled()));
  }

  @Test
  void releaseWaitsForTheRunningProgramsWithAwait() {
    var done = new AtomicBoolean();
    var running = delayed(Delay.ofMillis(200), task(() -> done.set(true)));

    var program = Supervisor.make(true).use(
        supervisor -> pipe(supervisor.supervise(running), _ -> Program.unit()));

    program.evalOrElseThrow();

    assertThat(done).isTrue();
  }

  @Test
  void aSupervisedProgramOutlivesTheCancelledProgramThatStartedIt() {
    var started = new CountDownLatch(1);
    var supervised = new AtomicReference<Fiber<Void, Unit>>();
    Program<Void, Void, Unit> running = never();

    var program = Supervisor.<Void, Void>make().use(
        supervisor -> pipe(
            pipe(
                supervisor.supervise(running),
                fiber -> pipe(task(() -> supervised.set(fiber)), _ -> task(started::countDown), _ -> never())).fork(),
            caller -> pipe(
                task(() -> await(started)),
                _ -> caller.cancel(),
                _ -> requireNonNull(supervised.get()).poll())));

    assertThat(program.evalOrElseThrow()).isEmpty();
  }

  @Test
  void superviseFailsAfterTheRelease() {
    var leaked = new AtomicReference<Supervisor>();
    Supervisor.make().use(supervisor -> task(() -> leaked.set(supervisor))).evalOrElseThrow();

    var program = requireNonNull(leaked.get()).supervise(Program.unit());

    assertThatThrownBy(program::evalOrElseThrow).isInstanceOf(SupervisorClosedException.class);
  }

  @Test
  void runsTheProgramInTheGivenExecutor() {
    try (var executor = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "custom"))) {
      var program = Supervisor.make().use(
          supervisor -> pipe(
              supervisor.supervise(Program.supply(() -> Thread.currentThread().getName()), executor),
              Fiber::join));

      assertThat(program.evalOrElseThrow()).isEqualTo("custom");
    }
  }

  @Test
  void theFiberFailsIfTheGivenExecutorRejectsTheProgram() {
    Executor rejecting = _ -> {
      throw new RejectedExecutionException();
    };

    var program = Supervisor.make().use(
        supervisor -> pipe(supervisor.supervise(Program.unit(), rejecting), Fiber::join));

    assertThatThrownBy(program::evalOrElseThrow).isInstanceOf(RejectedExecutionException.class);
  }

  @Test
  void releaseWithAwaitWaitsForEveryProgramAdmittedWhileItCloses() throws InterruptedException {
    for (int i = 0; i < 200; i++) {
      var admitted = new AtomicInteger();
      var completed = new AtomicInteger();
      var cancelled = new AtomicInteger();
      var supervising = new AtomicReference<Thread>();
      var unexpected = new AtomicReference<Throwable>();
      var work = Combine.onCancel(
          delayed(Delay.ofMillis(1), task(completed::incrementAndGet)),
          task(cancelled::incrementAndGet));

      Supervisor.make(true).use(supervisor -> task(() -> {
        // supervises from another thread until the release rejects it, racing with the release
        supervising.set(Thread.ofPlatform().start(() -> {
          try {
            while (true) {
              supervisor.supervise(work).evalOrElseThrow();
              admitted.incrementAndGet();
            }
          } catch (SupervisorClosedException e) {
            // released: no more admissions
          } catch (Throwable e) {
            unexpected.set(e);
          }
        }));
      })).evalOrElseThrow();
      requireNonNull(supervising.get()).join();

      assertThat(unexpected).as("unexpected error in iteration %s", i).hasNullValue();

      assertThat(cancelled).as("cancelled in iteration %s", i).hasValue(0);
      assertThat(completed).as("completed in iteration %s", i).hasValue(admitted.get());
    }
  }

  private static void await(CountDownLatch latch) {
    try {
      latch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
