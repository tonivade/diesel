/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 10, unit = TimeUnit.SECONDS)
class QueueTest {

  @Test
  void keepsFifoOrder() {
    var queue = Queue.<Void, Void, Integer>unbounded();

    var program = queue.offer(1).andThen(queue.offer(2)).andThen(queue.offer(3))
        .andThen(Combine.sequence(queue.take(), queue.take(), queue.take()));

    assertThat(program.evalOrElseThrow()).containsExactly(1, 2, 3);
  }

  @Test
  void takeWaitsUntilThereIsAnElement() {
    var queue = Queue.<Void, Void, String>unbounded();

    var program = Concurrent.parZip(
        queue.take(),
        Program.<Void, Void>sleep(Duration.ofMillis(50)).andThen(queue.offer("hello")),
        (value, _) -> value);

    assertThat(program.evalOrElseThrow()).isEqualTo("hello");
  }

  @Test
  void boundedOfferWaitsUntilThereIsRoom() {
    var queue = Queue.<Void, Void, Integer>bounded(1);

    var program = Concurrent.parZip(
        queue.offer(1).andThen(queue.offer(2)),
        Program.<Void, Void>sleep(Duration.ofMillis(50)).andThen(queue.take()),
        (_, taken) -> taken);

    assertThat(program.evalOrElseThrow()).isEqualTo(1);
    assertThat(queue.<Void, Void>size().evalOrElseThrow()).isEqualTo(1);
  }

  @Test
  void cancelledTakeDoesNotLoseTheNextElement() {
    var queue = Queue.<Void, Void, Integer>unbounded();

    var timedOut = queue.<Void, Void>take().timeout(Duration.ofMillis(50))
        .catchAll(_ -> Program.success(-1));
    var program = timedOut.andThen(queue.offer(7)).andThen(queue.take());

    assertThat(program.evalOrElseThrow()).isEqualTo(7);
  }

  @Test
  void cancelledOfferIsRemoved() {
    var queue = Queue.<Void, Void, Integer>bounded(1);

    var program = queue.<Void, Void>offer(1)
        .andThen(queue.<Void, Void>offer(2).timeout(Duration.ofMillis(50))
            .catchAll(_ -> Program.unit()))
        .andThen(Combine.sequence(queue.take(), queue.tryTake().map(o -> o.orElse(-1))));

    assertThat(program.evalOrElseThrow()).containsExactly(1, -1);
  }

  @Test
  void stressNothingIsLostOrDuplicated() {
    // N productores y M consumidores, con timeouts aleatorios cancelando takes.
    // Invariante: entregados + size() == ofrecidos, sin duplicados.
  }
}