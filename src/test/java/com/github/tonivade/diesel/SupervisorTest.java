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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
    var stillRunning = new CountDownLatch(1);
    var running = pipe(task(started::countDown), _ -> task(() -> await(stillRunning)));

    var program = Supervisor.make(true).use(
        supervisor -> pipe(
            pipe(supervisor.supervise(running), _ -> never()).fork(),
            caller -> pipe(
                task(() -> await(started)),
                _ -> pipe(
                    caller.cancel(),
                    _ -> pipe(
                        task(stillRunning::countDown),
                        _ -> success(true))))));

    assertThat(program.evalOrElseThrow()).isTrue();
  }

  @Test
  void superviseFailsAfterTheRelease() {
    var leaked = new AtomicReference<Supervisor>();
    Supervisor.make().use(supervisor -> task(() -> leaked.set(supervisor))).evalOrElseThrow();

    var program = Objects.requireNonNull(leaked.get()).supervise(Program.unit());

    assertThatThrownBy(program::evalOrElseThrow).isInstanceOf(IllegalStateException.class);
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

  private static void await(CountDownLatch latch) {
    try {
      latch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
