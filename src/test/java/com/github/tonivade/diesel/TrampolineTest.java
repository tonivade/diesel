/*
 * Copyright (c) 2018-2024, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TrampolineTest {

  @Test
  void done() {
    var done = Trampoline.done("done");

    assertEquals("done", done.run());
  }

  @Test
  void more() {
    var more = Trampoline.more(() -> Trampoline.done("done"));

    assertEquals("done", more.run());
  }

  @Test
  void sum() {
    assertAll(
        () -> assertEquals(5_050, sum(100, 0).run()),
        () -> assertEquals(20_100, sum(200, 0).run()),
        () -> assertEquals(45_150, sum(300, 0).run()),
        () -> assertEquals(705_082_704, sum(100_000, 0).run())
      );
  }

  @Test
  void fib() {
    assertAll(
        () -> assertEquals(1, fib(1).run()),
        () -> assertEquals(1, fib(2).run()),
        () -> assertEquals(2, fib(3).run()),
        () -> assertEquals(3, fib(4).run()),
        () -> assertEquals(5, fib(5).run()),
        () -> assertEquals(8, fib(6).run()),
        () -> assertEquals(13, fib(7).run()),
        () -> assertEquals(21, fib(8).run()),
        () -> assertEquals(55, fib(10).run()),
        () -> assertEquals(317_811, fib(28).run()),
        () -> assertEquals(832_040, fib(30).run()),
        () -> assertEquals(9_227_465, fib(35).run())
      );
  }

  @Test
  void heapSafe() {
    var t = Trampoline.done(0);

    for (var i = 0; i < 10_000_000; i++) {
      t = t.flatMap(x -> Trampoline.done(x + 1));
    }

    assertEquals(10_000_000, t.run());
  }

  private Trampoline<Integer> sum(Integer counter, Integer sum) {
    if (counter == 0) {
      return Trampoline.done(sum);
    }
    return Trampoline.more(() -> sum(counter - 1, sum + counter));
  }

  private Trampoline<Integer> fib(Integer n) {
    if (n < 2) {
      return Trampoline.done(n);
    }
    return Trampoline.more(() -> fib(n - 1).flatMap(x -> fib(n - 2).map(y -> x + y)));
  }
}
