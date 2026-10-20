/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Concurrent.parAll;
import static com.github.tonivade.diesel.Concurrent.parZip;
import static com.github.tonivade.diesel.Program.success;
import static com.github.tonivade.diesel.Program.supply;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

// a regression in the forks would hang instead of failing
@Timeout(value = 10, unit = TimeUnit.SECONDS)
class FatalTest {

  private final AtomicInteger recovered = new AtomicInteger();

  @Test
  void catchAllRecoversFromANonFatalError() {
    var program = Program.<Void, Void, String>supply(() -> {
      throw new AssertionError();
    }).catchAll(_ -> recover());

    assertThat(program.eval()).isEqualTo(Result.success("recovered"));
    assertThat(recovered).hasValue(1);
  }

  @Test
  void catchAllDoesNotRecoverFromAFatalError() {
    var program = Program.<Void, Void, String>supply(() -> {
      throw new StackOverflowError();
    }).catchAll(_ -> recover());

    assertThatThrownBy(program::eval).isInstanceOf(StackOverflowError.class);
    assertThat(recovered).hasValue(0);
  }

  @Test
  void attemptCapturesANonFatalError() {
    var error = new AssertionError();

    var result = Result.attempt(() -> {
      throw error;
    });

    assertThat(result).isEqualTo(Result.failure(error));
  }

  @Test
  void attemptDoesNotCaptureAFatalError() {
    assertThatThrownBy(() -> Result.attempt(() -> {
      throw new StackOverflowError();
    })).isInstanceOf(StackOverflowError.class);
  }

  @Test
  void aFatalErrorInParZipReachesTheParent() {
    var program = parZip(fatal(), Program.<Void, Void, String>success("other"), (a, b) -> a + b);

    assertThatThrownBy(program::eval).isInstanceOf(StackOverflowError.class);
  }

  @Test
  void aFatalErrorInParAllReachesTheParent() {
    var program = parAll(fatal(), Program.<Void, Void, String>success("other"));

    assertThatThrownBy(program::eval).isInstanceOf(StackOverflowError.class);
  }

  private Program<Void, Void, String> recover() {
    return Program.<Void, Void>task(recovered::incrementAndGet).andThen(success("recovered"));
  }

  private static Program<Void, Void, String> fatal() {
    return supply(() -> {
      throw new StackOverflowError();
    });
  }
}
