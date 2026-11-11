/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Combine.pipe;
import static com.github.tonivade.diesel.Program.success;
import static com.github.tonivade.diesel.Program.task;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 10, unit = TimeUnit.SECONDS)
class FiberTest {

  @Test
  void joinReturnsTheValue() {
    var program = pipe(
        Program.<Void, String, String>success("a").fork(),
        Fiber::join);

    assertThat(program.eval()).isEqualTo(Result.success("a"));
  }

  @Test
  void joinFailsWithTheError() {
    var program = pipe(
        Program.<Void, String, String>failure("boom").fork(),
        Fiber::join);

    assertThat(program.eval()).isEqualTo(Result.failure("boom"));
  }

  @Test
  void joinThrowsTheException() {
    var program = pipe(
        Program.<Void, String, String>raise(IllegalStateException::new).fork(),
        Fiber::join);

    assertThatThrownBy(program::eval).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void cancelWaitsUntilTheFinalizersHaveRun() {
    var started = new CompletableFuture<Void>();
    var released = new AtomicInteger();
    var program = pipe(
        Program.<Void, String>task(() -> started.complete(null))
            .andThen(Program.<Void, String, Unit>never())
            .ensuring(task(released::incrementAndGet))
            .fork(),
        fiber -> pipe(
            task(started::join),
            _ -> Program.<Void, String, Unit>widen(fiber.cancel()),
            // the finalizer has run by the time cancel returns
            _ -> Program.<Void, String, Integer>supply(released::get)));

    assertThat(program.eval()).isEqualTo(Result.success(1));
  }

  @Test
  void joiningACancelledFiberThrowsACancellationException() {
    var program = pipe(
        Program.<Void, String, Unit>never().fork(),
        fiber -> Program.<Void, String, Unit>widen(fiber.cancel()).andThen(fiber.join()));

    assertThatThrownBy(program::eval).isInstanceOf(CancellationException.class);
  }

  @Test
  void cancelDoesNotFailWhenTheFiberHasAlreadyFinished() {
    var program = pipe(
        Program.<Void, String, String>failure("boom").fork(),
        fiber -> pipe(
            Program.<Void, String, String>join(fiber.future()).foldMap(_ -> success("done"), _ -> success("done")),
            _ -> Program.<Void, String, Unit>widen(fiber.cancel())));

    assertThat(program.eval()).isEqualTo(Result.success(Unit.unit()));
  }

  @Test
  void pollIsEmptyWhileTheFiberIsRunning() {
    var fiber = Program.<Void, String, Unit>never().fork().evalOrElseThrow();
    try {
      assertThat(fiber.<Void>poll().evalOrElseThrow()).isEmpty();
    } finally {
      fiber.<Void>cancel().evalOrElseThrow();
    }
  }

  @Test
  void pollReturnsSucceededOnceTheFiberHasFinished() {
    var fiber = finished(Program.<Void, String, String>success("a"));

    assertThat(fiber.<Void>poll().evalOrElseThrow()).isEqualTo(Optional.of(Outcome.succeeded("a")));
  }

  @Test
  void pollReturnsFailedWhenTheFiberFailedWithAnError() {
    var fiber = finished(Program.<Void, String, String>failure("boom"));

    assertThat(fiber.<Void>poll().evalOrElseThrow()).isEqualTo(Optional.of(Outcome.failed("boom")));
  }

  @Test
  void pollReturnsErroredWhenTheFiberThrew() {
    var error = new IllegalStateException();
    var fiber = finished(Program.<Void, String, String>raise(() -> error));

    assertThat(fiber.<Void>poll().evalOrElseThrow()).isEqualTo(Optional.of(Outcome.errored(error)));
  }

  @Test
  void pollReturnsCanceledWhenTheFiberWasCancelled() {
    var fiber = Program.<Void, String, Unit>never().fork().evalOrElseThrow();
    fiber.<Void>cancel().evalOrElseThrow();

    assertThat(fiber.<Void>poll().evalOrElseThrow()).isEqualTo(Optional.of(Outcome.canceled()));
  }

  @Test
  void pollReturnsErroredWhenTheFiberThrowsACancellationExceptionItself() {
    // the fiber joins another one that was cancelled: it fails, but it wasn't cancelled
    var fiber = finished(pipe(
        Program.<Void, String, Unit>never().fork(),
        inner -> Program.<Void, String, Unit>widen(inner.cancel()).andThen(inner.join())));

    assertThat(fiber.<Void>poll().evalOrElseThrow())
        .hasValueSatisfying(outcome -> assertThat(outcome)
            .isInstanceOfSatisfying(Outcome.Errored.class, errored ->
                assertThat(errored.exception()).isInstanceOf(CancellationException.class)));
  }

  // forks the program and waits until it has finished, whatever its outcome
  private static <T> Fiber<String, T> finished(Program<Void, String, T> program) {
    var fiber = program.fork().evalOrElseThrow();
    fiber.future().handle((_, _) -> null).join();
    return fiber;
  }
}
