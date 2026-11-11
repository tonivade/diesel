/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Combine.pipe;
import static com.github.tonivade.diesel.Combine.sequence;
import static com.github.tonivade.diesel.Combine.traverse;
import static com.github.tonivade.diesel.Concurrent.parAll;
import static com.github.tonivade.diesel.Concurrent.parSequence;
import static com.github.tonivade.diesel.Concurrent.parZip;
import static com.github.tonivade.diesel.Program.delayed;
import static com.github.tonivade.diesel.Program.raise;
import static com.github.tonivade.diesel.Program.success;
import static com.github.tonivade.diesel.Program.suspend;
import static com.github.tonivade.diesel.Program.task;
import static com.github.tonivade.diesel.Program.unit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tonivade.diesel.Queue.QueueClosedException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.IntSupplier;
import java.util.stream.IntStream;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 10, unit = TimeUnit.SECONDS)
class QueueTest {

  private static final Delay PAUSE = Delay.ofMillis(50);

  @Test
  void canBeUsedInMoreSpecificProgramContext() {
    Queue<Integer> queue = Queue.<Integer>bounded(1);

    Program<String, RuntimeException, Optional<Integer>> program =
        pipe(queue.offer(1), _ -> queue.tryTake());

    assertThat(program.evalOrElseThrow()).contains(1);
  }

  // ---------------------------------------------------------------------------------------------
  // basic behaviour (open queue)
  // ---------------------------------------------------------------------------------------------

  @Test
  void keepsFifoOrder() {
    var queue = Queue.<Integer>unbounded();

    var program = queue.offer(1)
        .andThen(queue.offer(2))
        .andThen(queue.offer(3))
        .andThen(sequence(queue.take(), queue.take(), queue.take()));

    assertThat(program.evalOrElseThrow()).containsExactly(1, 2, 3);
    assertThat(queue.isEmpty().evalOrElseThrow()).isTrue();
    assertThat(queue.isFull().evalOrElseThrow()).isFalse();
  }

  @Test
  void takeWaitsUntilThereIsAnElement() {
    var queue = Queue.<String>unbounded();

    var program = parZip(
        queue.take(),
        delayed(Delay.ofMillis(50), queue.offer("hello")),
        (value, _) -> value);

    assertThat(program.evalOrElseThrow()).isEqualTo("hello");
  }

  @Test
  void boundedOfferWaitsUntilThereIsRoom() {
    var queue = Queue.<Integer>bounded(1);

    var program = parZip(
        queue.offer(1).andThen(queue.offer(2)),
        delayed(Delay.ofMillis(50), queue.take()),
        (_, taken) -> taken);

    assertThat(program.evalOrElseThrow()).isEqualTo(1);
    assertThat(queue.size().evalOrElseThrow()).isEqualTo(1);
    assertThat(queue.isEmpty().evalOrElseThrow()).isFalse();
    assertThat(queue.isFull().evalOrElseThrow()).isTrue();
  }

  @Test
  void cancelledTakeDoesNotLoseTheNextElement() {
    var queue = Queue.<Integer>unbounded();

    var timedOut = queue.take()
        .timeout(Delay.ofMillis(50))
        .catchAll(_ -> success(-1));
    var program = timedOut
        .andThen(queue.offer(7))
        .andThen(queue.take());

    assertThat(program.evalOrElseThrow()).isEqualTo(7);
  }

  @Test
  void cancelledOfferIsRemoved() {
    var queue = Queue.<Integer>bounded(1);

    var program = queue.offer(1)
        .andThen(queue.offer(2).timeout(Delay.ofMillis(50))
            .catchAll(_ -> unit()))
        .andThen(sequence(queue.take(), queue.tryTake().map(o -> o.orElse(-1))));

    assertThat(program.evalOrElseThrow()).containsExactly(1, -1);
  }

  @Test
  void tryOfferReturnsFalseWhenFull() {
    var queue = Queue.<Integer>bounded(1);

    var program = queue.offer(1)
        .andThen(queue.tryOffer(2));

    assertThat(program.evalOrElseThrow()).isFalse();
  }

  @Test
  void tryOfferReturnsTrueWhenThereIsRoom() {
    var queue = Queue.<Integer>bounded(2);

    var program = sequence(queue.tryOffer(1), queue.tryOffer(2));

    assertThat(program.evalOrElseThrow()).containsExactly(true, true);
    assertThat(queue.size().evalOrElseThrow()).isEqualTo(2);
  }

  @Test
  void tryOfferDoesNotAddTheValueWhenFull() {
    var queue = Queue.<Integer>bounded(1);

    queue.offer(1).andThen(queue.tryOffer(2)).evalOrElseThrow();

    assertThat(queue.size().evalOrElseThrow()).isEqualTo(1);
    assertThat(queue.tryTake().evalOrElseThrow()).contains(1);
    assertThat(queue.tryTake().evalOrElseThrow()).isEmpty();
  }

  @Test
  void tryTakeReturnsEmptyWhenEmpty() {
    var queue = Queue.<Integer>unbounded();

    assertThat(queue.tryTake().evalOrElseThrow()).isEmpty();
  }

  @Test
  void tryTakeReturnsValueWhenNotEmpty() {
    var queue = Queue.<Integer>unbounded();

    assertThat(queue.offer(1).andThen(queue.tryTake()).evalOrElseThrow()).contains(1);
  }

  @Test
  void sizeCountsTheElements() {
    var queue = Queue.<Integer>unbounded();

    assertThat(queue.size().evalOrElseThrow()).isZero();

    queue.offer(1).andThen(queue.offer(2)).evalOrElseThrow();
    assertThat(queue.size().evalOrElseThrow()).isEqualTo(2);

    queue.take().evalOrElseThrow();
    assertThat(queue.size().evalOrElseThrow()).isEqualTo(1);
    assertThat(queue.isEmpty().evalOrElseThrow()).isFalse();
    assertThat(queue.isFull().evalOrElseThrow()).isFalse();
    assertThat(queue.capacity()).isEqualTo(Integer.MAX_VALUE);
  }

  @Test
  void sizeDoesNotCountTheWaitingOffers() {
    var queue = Queue.<Integer>bounded(1);

    var program = queue.offer(1)
        .andThen(queue.offer(2).timeout(PAUSE).catchAll(_ -> unit()))
        .andThen(queue.size());

    assertThat(program.evalOrElseThrow()).isEqualTo(1);
    assertThat(queue.isEmpty().evalOrElseThrow()).isFalse();
    assertThat(queue.isFull().evalOrElseThrow()).isTrue();
    assertThat(queue.capacity()).isEqualTo(1);
  }

  @Test
  void handoffToAWaitingTakerDoesNotStoreTheElement() {
    var queue = Queue.<Integer>unbounded();

    var program = parZip(
        queue.take(),
        delayed(PAUSE, queue.offer(5)),
        (value, _) -> value);

    assertThat(program.evalOrElseThrow()).isEqualTo(5);
    assertThat(queue.size().evalOrElseThrow()).isZero();
  }

  @Test
  void unboundedAcceptsManyElements() {
    var queue = Queue.<Integer>unbounded();
    var values = IntStream.range(0, 10_000).boxed().toList();

    produce(queue, values).andThen(queue.close()).evalOrElseThrow();

    assertThat(drain(queue).evalOrElseThrow()).containsExactlyElementsOf(values);
  }

  @Test
  void boundedRejectsAnInvalidCapacity() {
    assertThatThrownBy(() -> Queue.bounded(0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Queue.bounded(-1)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void makeBoundedRejectsAnInvalidCapacityWhenEvaluated() {
    var program = Queue.<Void, Void, Integer>makeBounded(0);

    assertThatThrownBy(program::evalOrElseThrow).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void makeCreatesANewQueueForEachEvaluation() {
    var bounded = Queue.<Void, Void, Integer>makeBounded(2);
    var unbounded = Queue.<Void, Void, Integer>makeUnbounded();

    var firstBounded = bounded.evalOrElseThrow();
    var secondBounded = bounded.evalOrElseThrow();
    var firstUnbounded = unbounded.evalOrElseThrow();
    var secondUnbounded = unbounded.evalOrElseThrow();

    firstBounded.offer(1).evalOrElseThrow();
    firstUnbounded.offer(1).evalOrElseThrow();

    assertThat(firstBounded.size().evalOrElseThrow()).isEqualTo(1);
    assertThat(secondBounded.size().evalOrElseThrow()).isZero();
    assertThat(firstUnbounded.size().evalOrElseThrow()).isEqualTo(1);
    assertThat(secondUnbounded.size().evalOrElseThrow()).isZero();
  }

  @Test
  @SuppressWarnings("NullAway") // NullAway already rejects it at compile time, but not every caller uses it
  void offerRejectsANullValue() {
    var queue = Queue.<Integer>unbounded();

    assertThatThrownBy(() -> queue.offer(null)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> queue.tryOffer(null)).isInstanceOf(NullPointerException.class);
  }

  // ---------------------------------------------------------------------------------------------
  // close: state
  // ---------------------------------------------------------------------------------------------

  @Test
  void aNewQueueIsNotClosed() {
    assertThat(Queue.<Integer>unbounded().isClosed().evalOrElseThrow()).isFalse();
    assertThat(Queue.<Integer>bounded(1).isClosed().evalOrElseThrow()).isFalse();
  }

  @Test
  void closeMarksTheQueueAsClosed() {
    var queue = Queue.<Integer>unbounded();

    queue.close().evalOrElseThrow();

    assertThat(queue.isClosed().evalOrElseThrow()).isTrue();
  }

  @Test
  void closeIsIdempotent() {
    var queue = Queue.<Integer>unbounded();

    var program = queue.offer(1)
        .andThen(queue.close())
        .andThen(queue.close())
        .andThen(queue.close());

    program.evalOrElseThrow();

    assertThat(queue.isClosed().evalOrElseThrow()).isTrue();
    assertThat(drain(queue).evalOrElseThrow()).containsExactly(1);
  }

  @Test
  void closeKeepsTheElements() {
    var queue = Queue.<Integer>unbounded();

    queue.offer(1).andThen(queue.offer(2)).andThen(queue.close()).evalOrElseThrow();

    assertThat(queue.size().evalOrElseThrow()).isEqualTo(2);
  }

  @Test
  void closeOnAnEmptyQueue() {
    var queue = Queue.<Integer>unbounded();

    queue.close().evalOrElseThrow();

    assertThat(queue.isClosed().evalOrElseThrow()).isTrue();
    assertThat(queue.size().evalOrElseThrow()).isZero();
  }

  @Test
  void closeCanBeEvaluatedMoreThanOnce() {
    var queue = Queue.<Integer>unbounded();
    var close = queue.close();

    close.evalOrElseThrow();
    close.evalOrElseThrow();

    assertThat(queue.isClosed().evalOrElseThrow()).isTrue();
  }

  @Test
  void isFullWhenQueueIsOverCapacityAfterPuttingBackACancelledTake() {
    var queue = Queue.<Integer>bounded(1);
    Deferred<Optional<Integer>> waiter = Deferred.create();

    var program = queue.claim(waiter, true)
        .discard()
        .andThen(queue.offer(1))
        .andThen(queue.offer(2))
        .andThen(queue.releaseTaker(waiter)) // requeue first element
        .andThen(queue.isFull());

    assertThat(program.evalOrElseThrow()).isTrue();
    assertThat(queue.size().evalOrElseThrow()).isEqualTo(2);
  }

  @Test
  void aTakeFromAQueueOverCapacityDoesNotAdmitABlockedOffer() {
    var queue = Queue.<Integer>bounded(1);
    Deferred<Optional<Integer>> waiter = Deferred.create();
    Deferred<Boolean> done = Deferred.create();

    var first = queue.claim(waiter, true).discard()
        .andThen(queue.offer(1))                            // handed to the waiting take
        .andThen(queue.offer(2))                            // stored: the queue is full
        .andThen(queue.register(3, done, Queue.Mode.WAIT))  // blocked
        .andThen(queue.releaseTaker(waiter))                // 1 is put back: over capacity
        .andThen(queue.take());

    assertThat(first.evalOrElseThrow()).isEqualTo(1);
    // still full after the take: the blocked offer has to keep waiting
    assertThat(queue.size().evalOrElseThrow()).isEqualTo(1);
    assertThat(queue.waitingOfferers()).isEqualTo(1);
    assertThat(done.tryGet().evalOrElseThrow()).isEmpty();

    // the next take makes room for it
    assertThat(queue.take().evalOrElseThrow()).isEqualTo(2);
    assertThat(queue.size().evalOrElseThrow()).isEqualTo(1);
    assertThat(queue.waitingOfferers()).isZero();
    assertThat(done.tryGet().evalOrElseThrow()).contains(true);
    assertThat(queue.take().evalOrElseThrow()).isEqualTo(3);
  }

  // ---------------------------------------------------------------------------------------------
  // close: offers
  // ---------------------------------------------------------------------------------------------

  @Test
  void offerFailsAfterClose() {
    var queue = Queue.<Integer>unbounded();

    var program = queue.close().andThen(queue.offer(1));

    assertThatThrownBy(program::evalOrElseThrow).isExactlyInstanceOf(QueueClosedException.class);
  }

  @Test
  void tryOfferFailsAfterClose() {
    var queue = Queue.<Integer>unbounded();

    var program = queue.close().andThen(queue.tryOffer(1));

    assertThatThrownBy(program::evalOrElseThrow).isExactlyInstanceOf(QueueClosedException.class);
  }

  @Test
  void tryOfferOnAClosedFullQueueFailsInsteadOfReturningFalse() {
    var queue = Queue.<Integer>bounded(1);

    var program = queue.offer(1).andThen(queue.close()).andThen(queue.tryOffer(2));

    assertThatThrownBy(program::evalOrElseThrow).isExactlyInstanceOf(QueueClosedException.class);
  }

  @Test
  void aRejectedOfferDoesNotAddTheValue() {
    var queue = Queue.<Integer>unbounded();

    queue.offer(1).andThen(queue.close()).evalOrElseThrow();
    assertThatThrownBy(queue.offer(2)::evalOrElseThrow).isInstanceOf(QueueClosedException.class);
    assertThatThrownBy(queue.tryOffer(3)::evalOrElseThrow).isInstanceOf(QueueClosedException.class);

    assertThat(queue.size().evalOrElseThrow()).isEqualTo(1);
    assertThat(drain(queue).evalOrElseThrow()).containsExactly(1);
  }

  @Test
  void theClosedExceptionCanBeRecoveredWithCatchAll() {
    var queue = Queue.<Integer>unbounded();

    var program = queue.close()
        .andThen(queue.offer(1).map(_ -> "accepted"))
        .catchAll(e -> success(e.getClass().getSimpleName()));

    assertThat(program.evalOrElseThrow()).isEqualTo("QueueClosedException");
  }

  // ---------------------------------------------------------------------------------------------
  // close: drain
  // ---------------------------------------------------------------------------------------------

  @Test
  void takeDeliversTheRemainingElementsInOrderAndThenFails() {
    var queue = Queue.<Integer>unbounded();

    queue.offer(1).andThen(queue.offer(2)).andThen(queue.offer(3)).andThen(queue.close()).evalOrElseThrow();

    assertThat(sequence(queue.take(), queue.take(), queue.take()).evalOrElseThrow()).containsExactly(1, 2, 3);
    assertThatThrownBy(queue.take()::evalOrElseThrow).isExactlyInstanceOf(QueueClosedException.class);
  }

  @Test
  void takeFailsOnAClosedEmptyQueue() {
    var queue = Queue.<Integer>unbounded();

    var program = queue.close().andThen(queue.take());

    assertThatThrownBy(program::evalOrElseThrow).isExactlyInstanceOf(QueueClosedException.class);
  }

  @Test
  void takeKeepsFailingOnceTheQueueIsDrained() {
    var queue = Queue.<Integer>unbounded();

    queue.close().evalOrElseThrow();

    for (int i = 0; i < 3; i++) {
      assertThatThrownBy(queue.take()::evalOrElseThrow).isExactlyInstanceOf(QueueClosedException.class);
    }
  }

  @Test
  void takeOrClosedDeliversTheRemainingElementsAndThenReturnsEmpty() {
    var queue = Queue.<Integer>unbounded();

    queue.offer(1).andThen(queue.offer(2)).andThen(queue.close()).evalOrElseThrow();

    var program = sequence(
        queue.takeOrClosed(), queue.takeOrClosed(), queue.takeOrClosed(), queue.takeOrClosed());

    assertThat(program.evalOrElseThrow())
        .containsExactly(Optional.of(1), Optional.of(2), Optional.empty(), Optional.empty());
  }

  @Test
  void takeOrClosedOnAnOpenQueueReturnsTheValue() {
    var queue = Queue.<Integer>unbounded();

    assertThat(queue.offer(4).andThen(queue.takeOrClosed()).evalOrElseThrow()).contains(4);
  }

  @Test
  void takeOrClosedWaitsOnAnOpenEmptyQueue() {
    var queue = Queue.<Integer>unbounded();

    var program = parZip(
        queue.takeOrClosed(),
        delayed(PAUSE, queue.offer(9)),
        (value, _) -> value);

    assertThat(program.evalOrElseThrow()).contains(9);
  }

  @Test
  void tryTakeDeliversTheRemainingElementsAndThenFails() {
    var queue = Queue.<Integer>unbounded();

    queue.offer(1).andThen(queue.offer(2)).andThen(queue.close()).evalOrElseThrow();

    assertThat(queue.tryTake().evalOrElseThrow()).contains(1);
    assertThat(queue.tryTake().evalOrElseThrow()).contains(2);
    assertThatThrownBy(queue.tryTake()::evalOrElseThrow).isExactlyInstanceOf(QueueClosedException.class);
  }

  @Test
  void tryTakeOnAnOpenEmptyQueueReturnsEmptyButOnAClosedOneFails() {
    var queue = Queue.<Integer>unbounded();

    assertThat(queue.tryTake().evalOrElseThrow()).isEmpty();

    queue.close().evalOrElseThrow();

    assertThatThrownBy(queue.tryTake()::evalOrElseThrow).isExactlyInstanceOf(QueueClosedException.class);
  }

  @Test
  void sizeIsZeroOnceTheClosedQueueIsDrained() {
    var queue = Queue.<Integer>unbounded();

    queue.offer(1).andThen(queue.close()).andThen(queue.take()).evalOrElseThrow();

    assertThat(queue.size().evalOrElseThrow()).isZero();
    assertThat(queue.isEmpty().evalOrElseThrow()).isTrue();
    assertThat(queue.isFull().evalOrElseThrow()).isFalse();
  }

  @Test
  void aClosedFullBoundedQueueCanBeDrained() {
    var queue = Queue.<Integer>bounded(2);

    queue.offer(1).andThen(queue.offer(2)).andThen(queue.close()).evalOrElseThrow();

    assertThat(drain(queue).evalOrElseThrow()).containsExactly(1, 2);
  }

  @Test
  void theClosedExceptionOfTakeCanBeRecoveredWithCatchAll() {
    var queue = Queue.<Integer>unbounded();

    var program = queue.close().andThen(queue.take()).catchAll(_ -> success(-1));

    assertThat(program.evalOrElseThrow()).isEqualTo(-1);
  }

  // ---------------------------------------------------------------------------------------------
  // close: waiting programs
  // ---------------------------------------------------------------------------------------------

  @Test
  void closeWakesUpAWaitingTakeOrClosedWithEmpty() {
    var queue = Queue.<Integer>unbounded();

    var program = pipe(
        queue.takeOrClosed().fork(),
        future -> pipe(
            task(() -> await(queue::waitingTakers, 1)),
            _ -> queue.close(),
            _ -> future.join()));

    assertThat(program.evalOrElseThrow()).isEmpty();
  }

  @Test
  void closeWakesUpAWaitingTakeWithTheClosedException() {
    var queue = Queue.<Integer>unbounded();

    var program = pipe(
        outcome(queue.take()).fork(),
        future -> pipe(
            task(() -> await(queue::waitingTakers, 1)),
            _ -> queue.close(),
            _ -> future.join()));

    assertThat(program.evalOrElseThrow()).isEqualTo("QueueClosedException");
  }

  @Test
  void closeWakesUpAllTheWaitingTakers() {
    var queue = Queue.<Integer>unbounded();
    List<Program<Void, Void, Optional<Integer>>> takers = IntStream.range(0, 20)
        .mapToObj(_ -> queue.<Void, Void>takeOrClosed())
        .toList();

    var program = pipe(
        parSequence(takers).fork(),
        future -> pipe(
            task(() -> await(queue::waitingTakers, 20)),
            _ -> queue.close(),
            _ -> future.join()));

    assertThat(program.evalOrElseThrow()).hasSize(20).allMatch(Optional::isEmpty);
  }

  @Test
  void closeWakesUpABlockedOfferWithTheClosedException() {
    var queue = Queue.<Integer>bounded(1);

    var program = pipe(
        queue.offer(1),
        _ -> outcome(queue.offer(2)).fork(),
        future -> pipe(
            task(() -> await(queue::waitingOfferers, 1)),
            _ -> queue.close(),
            _ -> future.join()));

    assertThat(program.evalOrElseThrow()).isEqualTo("QueueClosedException");
  }

  @Test
  void theValueOfAnOfferWokenUpByCloseIsDiscarded() {
    var queue = Queue.<Integer>bounded(1);

    pipe(
        queue.offer(1),
        _ -> outcome(queue.offer(2)).fork(),
        future -> pipe(
            task(() -> await(queue::waitingOfferers, 1)),
            _ -> queue.close(),
            _ -> future.join()))
        .evalOrElseThrow();

    assertThat(drain(queue).evalOrElseThrow()).containsExactly(1);
  }

  @Test
  void closeWakesUpAllTheBlockedOffers() {
    var queue = Queue.<Integer>bounded(1);
    List<Program<Void, Void, String>> offers = IntStream.range(0, 10)
        .mapToObj(i -> outcome(queue.offer(i + 100)))
        .toList();

    var program = pipe(
        queue.offer(1),
        _ -> parSequence(offers).fork(),
        future -> pipe(
            task(() -> await(queue::waitingOfferers, 10)),
            _ -> queue.close(),
            _ -> future.join()));

    assertThat(program.evalOrElseThrow()).hasSize(10).containsOnly("QueueClosedException");
    assertThat(drain(queue).evalOrElseThrow()).containsExactly(1);
  }

  @Test
  void anOfferAdmittedBeforeCloseIsDeliveredAfterIt() {
    var queue = Queue.<Integer>bounded(1);

    // the take frees room, so the blocked offer(2) enters the queue before the close
    var program = queue.offer(1)
        .andThen(parZip(
            queue.offer(2),
            delayed(PAUSE, queue.take()),
            (_, taken) -> taken))
        .andThen(queue.close())
        .andThen(sequence(queue.takeOrClosed(), queue.takeOrClosed()));

    assertThat(program.evalOrElseThrow()).containsExactly(Optional.of(2), Optional.empty());
  }

  // ---------------------------------------------------------------------------------------------
  // cancellation and close
  // ---------------------------------------------------------------------------------------------

  @Test
  void aCancelledTakeIsNotWokenUpByClose() {
    var queue = Queue.<Integer>unbounded();

    var program = queue.take()
        .timeout(PAUSE)
        .catchAll(e -> e instanceof TimeoutException ? success(-1) : raise(() -> e))
        .andThen(queue.close())
        .andThen(queue.takeOrClosed());

    assertThat(program.evalOrElseThrow()).isEmpty();
  }

  @Test
  void aCancelledTakeDoesNotLoseTheNextElementEvenIfTheQueueIsClosedAfter() {
    var queue = Queue.<Integer>unbounded();

    var program = queue.take()
        .timeout(PAUSE)
        .catchAll(_ -> success(-1))
        .andThen(queue.offer(7))
        .andThen(queue.close())
        .andThen(sequence(queue.takeOrClosed(), queue.takeOrClosed()));

    assertThat(program.evalOrElseThrow()).containsExactly(Optional.of(7), Optional.empty());
  }

  @Test
  void aCancelledOfferDoesNotAddItsValueAfterClose() {
    var queue = Queue.<Integer>bounded(1);

    var program = queue.offer(1)
        .andThen(queue.offer(2).timeout(PAUSE).catchAll(_ -> unit()))
        .andThen(queue.close())
        .andThen(sequence(queue.takeOrClosed(), queue.takeOrClosed()));

    assertThat(program.evalOrElseThrow()).containsExactly(Optional.of(1), Optional.empty());
  }

  @Test
  void manyCancelledTakesDoNotBlockTheClose() {
    var queue = Queue.<Integer>unbounded();
    var cancelled = queue.take().timeout(Delay.ofMillis(1)).catchAll(_ -> success(-1));

    for (int i = 0; i < 50; i++) {
      assertThat(cancelled.evalOrElseThrow()).isEqualTo(-1);
    }

    assertThat(queue.close().andThen(queue.takeOrClosed()).evalOrElseThrow()).isEmpty();
  }

  @Test
  void closeCompletesEvenWhenTheProgramThatCalledItIsCancelled() {
    var queue = Queue.<Integer>unbounded();
    var blocked = Program.<Void, Void, Optional<Integer>>never();

    // close is uncancelable: it has already woken up the waiters when the cancellation arrives
    var program = parZip(
        queue.takeOrClosed(),
        closeLater(queue).andThen(blocked.timeout(Delay.ofMillis(10)).catchAll(_ -> success(Optional.empty()))),
        (value, _) -> value);

    assertThat(program.evalOrElseThrow()).isEmpty();
    assertThat(queue.isClosed().evalOrElseThrow()).isTrue();
  }

  // ---------------------------------------------------------------------------------------------
  // concurrency
  // ---------------------------------------------------------------------------------------------

  @Test
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  void stressNothingIsLostOrDuplicated() {
    var queue = Queue.<Integer>bounded(8);
    var sink = new ConcurrentLinkedQueue<Integer>();
    var producers = 4;
    var perProducer = 500;
    var expected = IntStream.range(0, producers * perProducer).boxed().toList();

    List<Program<Void, Void, Unit>> produce = IntStream.range(0, producers)
        .mapToObj(p -> produce(queue, expected.subList(p * perProducer, (p + 1) * perProducer)))
        .toList();

    var program = parAll(
        pipe(parAll(produce), _ -> queue.close()),
        consume(queue, sink),
        consume(queue, sink),
        consume(queue, sink),
        consume(queue, sink));

    program.evalOrElseThrow();

    assertThat(sink).containsExactlyInAnyOrderElementsOf(expected);
    assertThat(queue.size().evalOrElseThrow()).isZero();
  }

  @Test
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  void stressWithCancelledTakesNothingIsLostOrDuplicated() {
    var queue = Queue.<Integer>bounded(4);
    var sink = new ConcurrentLinkedQueue<Integer>();
    var expected = IntStream.range(0, 1_000).boxed().toList();

    var program = parAll(
        pipe(produce(queue, expected), _ -> queue.close()),
        consumeWithTimeouts(queue, sink),
        consumeWithTimeouts(queue, sink),
        consumeWithTimeouts(queue, sink));

    program.evalOrElseThrow();

    // a take cancelled while an offer was handing it a value must give the value back
    assertThat(sink).containsExactlyInAnyOrderElementsOf(expected);
  }

  @RepeatedTest(10)
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  void closeRacingWithOffersKeepsEverythingAccepted() {
    var queue = Queue.<Integer>bounded(4);
    var accepted = new ConcurrentLinkedQueue<Integer>();
    var consumed = new ConcurrentLinkedQueue<Integer>();

    var program = parAll(
        produceUntilClosed(queue, range(0, 500), accepted),
        produceUntilClosed(queue, range(500, 1_000), accepted),
        produceUntilClosed(queue, range(1_000, 1_500), accepted),
        delayed(Delay.ofMillis(1), queue.close()),
        consume(queue, consumed),
        consume(queue, consumed));

    program.evalOrElseThrow();

    // every offer that returned successfully is consumed once, and the rest never shows up
    assertThat(consumed).containsExactlyInAnyOrderElementsOf(accepted);
    assertThat(queue.isClosed().evalOrElseThrow()).isTrue();
    assertThat(queue.size().evalOrElseThrow()).isZero();
  }

  @Test
  void releaseTakerPutsBackAValueAlreadyHandedOff() {
    var queue = Queue.<Integer>unbounded();
    Deferred<Optional<Integer>> waiter = Deferred.create();

    var program = queue.claim(waiter, true).discard() // the taker is waiting
        .andThen(queue.offer(5))             // handoff: it leaves takers and gets the value
        .andThen(queue.releaseTaker(waiter)) // it is cancelled after that: the value must come back
        .andThen(queue.size());

    assertThat(program.evalOrElseThrow()).isEqualTo(1);
    assertThat(queue.tryTake().evalOrElseThrow()).contains(5);
  }

  @Test
  void aValuePutBackAfterCloseIsNotLost() {
    var queue = Queue.<Integer>unbounded();
    Deferred<Optional<Integer>> waiter = Deferred.create();

    queue.claim(waiter, true).discard()
        .andThen(queue.offer(5))
        .andThen(queue.close())
        .andThen(queue.releaseTaker(waiter))
        .evalOrElseThrow();

    // it was accepted before the close, so draining still delivers it
    assertThat(drain(queue).evalOrElseThrow()).containsExactly(5);
  }

  @Test
  void releaseTakerDoesNotPutBackAnythingWhenTheTakerWasWokenUpByClose() {
    var queue = Queue.<Integer>unbounded();
    Deferred<Optional<Integer>> waiter = Deferred.create();

    queue.claim(waiter, true).discard()
        .andThen(queue.close())
        .andThen(queue.releaseTaker(waiter))
        .evalOrElseThrow();

    assertThat(waiter.get().evalOrElseThrow()).isEmpty();
    assertThat(queue.size().evalOrElseThrow()).isZero();
    assertThat(drain(queue).evalOrElseThrow()).isEmpty();
  }

  @Test
  void aValuePutBackIsHandedOffToAnotherWaitingTaker() {
    var queue = Queue.<Integer>unbounded();
    Deferred<Optional<Integer>> cancelled = Deferred.create();
    Deferred<Optional<Integer>> other = Deferred.create();

    queue.claim(cancelled, true).discard()
        .andThen(queue.offer(5))                          // handed to the first taker
        .andThen(queue.claim(other, true).discard()) // a second one starts waiting
        .andThen(queue.releaseTaker(cancelled))           // the first is cancelled: the value goes to the second
        .evalOrElseThrow();

    assertThat(other.get().evalOrElseThrow()).contains(5);
    assertThat(queue.size().evalOrElseThrow()).isZero();
  }

  @Test
  void anOfferAdmittedByATakeCompletesEvenIfItIsCancelledAfter() {
    var queue = Queue.<Integer>bounded(1);
    Deferred<Optional<Integer>> taker = Deferred.create();
    Deferred<Boolean> done = Deferred.create();

    var program = queue.offer(1)
        .andThen(queue.register(2, done, Queue.Mode.WAIT)) // the queue is full: the offer waits
        .andThen(queue.claim(taker, true))                 // a take makes room: 2 enters the queue
        .andThen(queue.cancelOffer(done, new CancelToken.Cancelled())); // the cancellation arrives after it

    assertThat(program.evalOrElseThrow()).isTrue();
    assertThat(queue.tryTake().evalOrElseThrow()).contains(2);
  }

  @Test
  void anOfferCancelledWhileWaitingIsRemovedAndStaysCancelled() {
    var queue = Queue.<Integer>bounded(1);
    Deferred<Boolean> done = Deferred.create();

    var program = queue.offer(1)
        .andThen(queue.register(2, done, Queue.Mode.WAIT))
        .andThen(queue.cancelOffer(done, new CancelToken.Cancelled()));

    assertThatThrownBy(program::evalOrElseThrow).isInstanceOf(CancellationException.class);
    assertThat(queue.waitingOfferers()).isZero();
    assertThat(queue.size().evalOrElseThrow()).isEqualTo(1);
  }

  @RepeatedTest(20)
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  void aTimedOutOfferIsNeverDelivered() {
    var queue = Queue.<Integer>bounded(1);
    var timedOut = new ConcurrentLinkedQueue<Integer>();
    var consumed = new ConcurrentLinkedQueue<Integer>();

    // short timeouts so some offers are cancelled just when a take makes room for them
    Program<Void, Void, Unit> producer = IntStream.range(0, 3_000).boxed()
        .map(i -> queue.<Void, Void>offer(i)
            .timeout(Delay.ofNanos(20_000))
            .catchAll(error -> {
              if (error instanceof TimeoutException) {
                timedOut.add(i);
                return unit();
              }
              return raise(() -> error);
            }))
        .reduce(unit(), Program::andThen);

    parAll(
        pipe(producer, _ -> queue.close()),
        consume(queue, consumed),
        consume(queue, consumed)).evalOrElseThrow();

    // an offer that failed with a timeout must not have added its value, and every other one did
    assertThat(consumed).filteredOn(timedOut::contains).isEmpty();
    assertThat(consumed).hasSize(3_000 - timedOut.size());
  }

  // ---------------------------------------------------------------------------------------------
  // helpers
  // ---------------------------------------------------------------------------------------------

  private static List<Integer> range(int from, int to) {
    return IntStream.range(from, to).boxed().toList();
  }

  /** Sleeps a bit and then closes the queue, so the programs run in parallel have time to wait. */
  private static Program<Void, Void, Unit> closeLater(Queue<Integer> queue) {
    return delayed(PAUSE, queue.close());
  }

  /** The result of the program, or the simple name of the exception it raised. */
  private static <T> Program<Void, Void, String> outcome(Program<Void, Void, T> program) {
    return program
        .map(value -> "ok:" + value)
        .catchAll(e -> success(e.getClass().getSimpleName()));
  }

  private static Program<Void, Void, Unit> produce(Queue<Integer> queue, List<Integer> values) {
    return traverse(queue::<Void, Void>offer, values).discard();
  }

  /** Offers the values in order until the queue is closed, saving the ones that were accepted. */
  private static Program<Void, Void, Unit> produceUntilClosed(
      Queue<Integer> queue, List<Integer> values, Collection<Integer> accepted) {
    return traverse(
            value -> queue.<Void, Void>offer(value)
                .andThen(task(() -> accepted.add(value))),
            values)
        .discard()
        .catchAll(e -> e instanceof QueueClosedException
            ? unit()
            : raise(() -> e));
  }

  /** Takes until the queue is closed and drained. */
  private static Program<Void, Void, Unit> consume(Queue<Integer> queue, Collection<Integer> sink) {
    return suspend(() -> pipe(
        queue.takeOrClosed(),
        value -> {
          if (value.isEmpty()) {
            return unit();
          }
          return pipe(
              task(() -> sink.add(value.get())),
              _ -> suspend(() -> consume(queue, sink)));
        }));
  }

  /** Like consume, but each wait is bounded by a short timeout, to race with the offers. */
  private static Program<Void, Void, Unit> consumeWithTimeouts(
      Queue<Integer> queue, Collection<Integer> sink) {
    return suspend(() -> pipe(
        takeWithTimeout(queue),
        value -> {
          if (value.isEmpty()) {
            return unit();
          }
          return pipe(
              task(() -> {
                if (value.get() >= 0) {
                  sink.add(value.get());
                }
              }),
              _ -> suspend(() -> consumeWithTimeouts(queue, sink)));
        }));
  }

  /**
   * A take bounded by a timeout. Returns -1 if the timeout expired, which is never a produced
   * value, and empty if the queue is closed and drained.
   */
  private static Program<Void, Void, Optional<Integer>> takeWithTimeout(Queue<Integer> queue) {
    return queue.<Void, Void>takeOrClosed()
        .timeout(Delay.ofMillis(1))
        .catchAll(e -> e instanceof TimeoutException
            ? success(Optional.of(-1))
            : raise(() -> e));
  }

  /** Drains a closed queue. It would wait forever on an open one. */
  private static Program<Void, Void, List<Integer>> drain(Queue<Integer> queue) {
    return drain(queue, List.of());
  }

  private static Program<Void, Void, List<Integer>> drain(Queue<Integer> queue, List<Integer> acc) {
    return pipe(
        queue.takeOrClosed(),
        value -> {
          if (value.isEmpty()) {
            return success(acc);
          }
          var next = new ArrayList<>(acc);
          next.add(value.get());
          return suspend(() -> drain(queue, next));
        });
  }

  private static void await(IntSupplier count, int expected) {
    var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (count.getAsInt() < expected) {
      if (System.nanoTime() > deadline) {
        throw new AssertionError("the programs never started waiting");
      }
      Thread.onSpinWait();
    }
  }
}