/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Combine.chainAll;
import static com.github.tonivade.diesel.Combine.sequence;
import static com.github.tonivade.diesel.Combine.zip;
import static com.github.tonivade.diesel.Program.delayed;
import static com.github.tonivade.diesel.Program.success;
import static com.github.tonivade.diesel.Program.supply;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.tonivade.diesel.ProgramTest.Tuple;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

@MockitoSettings
class CombineTest {

  @Test
  void shouldSerialize() {
    var p1 = delayed(Duration.ofSeconds(2), () -> 10);
    var p2 = delayed(Duration.ofSeconds(2), () -> "hello");

    var result = zip(p1, p2, Tuple::new).timed().evalOrElseThrow();

    assertThat(result.duration())
      .isCloseTo(Duration.ofSeconds(4), Duration.ofMillis(100));
    assertThat(result.value())
      .isEqualTo(new Tuple<>(10, "hello"));
  }

  @Test
  void shouldExecuteAllPrograms(@Mock Supplier<String> supplier) {
    when(supplier.get()).thenReturn("hi!");

    chainAll(supply(supplier), supply(supplier), supply(supplier)).evalOrElseThrow();

    verify(supplier, times(3)).get();
  }

  @Test
  void shouldExecuteAllProgramsAndCollectsResult(@Mock Supplier<String> supplier) {
    when(supplier.get()).thenReturn("1", "2", "3");

    var result = sequence(supply(supplier), supply(supplier), supply(supplier)).evalOrElseThrow();

    assertThat(result).isEqualTo(List.of("1", "2", "3"));
    verify(supplier, times(3)).get();
  }

  @Test
  void shouldSequenceWhenEvaluatedTwice() {
    var program = Combine.<Void, Void, Integer>sequence(success(1), success(2));

    var first = program.evalOrElseThrow();
    var second = program.evalOrElseThrow();

    assertThat(first).containsExactly(1, 2);
    assertThat(second).containsExactly(1, 2);
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldChainAllWithoutStackOverflow() {
    Program<Void, Void, Integer>[] programs = new Program[100_000];
    Arrays.fill(programs, success(1));

    var result = chainAll(programs).evalOrElseThrow();

    assertThat(result).isNull();
  }
}
