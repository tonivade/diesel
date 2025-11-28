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
        () -> assertEquals(5050, sum(100)),
        () -> assertEquals(20100, sum(200)),
        () -> assertEquals(45150, sum(300)),
        () -> assertEquals(705082704, sum(100000))
      );
  }

  @Test
  void fib() {
    assertAll(
        () -> assertEquals(1, fib(1)),
        () -> assertEquals(1, fib(2)),
        () -> assertEquals(2, fib(3)),
        () -> assertEquals(3, fib(4)),
        () -> assertEquals(5, fib(5)),
        () -> assertEquals(8, fib(6)),
        () -> assertEquals(13, fib(7)),
        () -> assertEquals(21, fib(8)),
        () -> assertEquals(55, fib(10)),
        () -> assertEquals(317811, fib(28))
      );
  }

  @Test
  void heapSafe() {
    var t = Trampoline.done(0);

    for (int i = 0; i < 10_000_000; i++) {
      t = t.flatMap(x -> Trampoline.done(x + 1));
    }

    System.out.println(t.run());
  }

  private int fib(int n) {
    return fibLoop(n).run();
  }

  private int sum(int n) {
    return sumLoop(n, 0).run();
  }

  private Trampoline<Integer> sumLoop(Integer counter, Integer sum) {
    if (counter == 0) {
      return Trampoline.done(sum);
    }
    return Trampoline.more(() -> sumLoop(counter - 1, sum + counter));
  }

  private Trampoline<Integer> fibLoop(Integer n) {
    if (n < 2) {
      return Trampoline.done(n);
    }
    return Trampoline.more(() -> fibLoop(n - 1)).flatMap(x -> fibLoop(n - 2).map(y -> x + y));
  }
}
