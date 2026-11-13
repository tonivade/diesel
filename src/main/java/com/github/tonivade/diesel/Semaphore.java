/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Combine.bracketFull;
import static com.github.tonivade.diesel.Combine.chain;
import static com.github.tonivade.diesel.Combine.onCancel;
import static com.github.tonivade.diesel.Combine.pipe;
import static com.github.tonivade.diesel.Combine.traverse;
import static com.github.tonivade.diesel.Program.supply;
import static com.github.tonivade.diesel.Program.uncancelable;
import static com.github.tonivade.diesel.Program.unit;
import static com.github.tonivade.diesel.Tuple2.tuple;
import static java.util.Objects.requireNonNull;

import com.github.tonivade.diesel.Program.Poll;

import java.util.ArrayList;
import java.util.List;

import org.pcollections.PVector;
import org.pcollections.TreePVector;

/**
 * A semaphore that controls concurrent access to a shared resource through a
 * fixed number of permits.
 *
 * <p>A semaphore maintains a number of available permits. Acquiring permits
 * decreases that number, while releasing permits increases it.
 *
 * <p>Acquisition is fair and follows a strict FIFO policy: waiting operations
 * are granted permits in the order in which they were queued. A waiter is
 * granted permits only when all the permits it requested are available.
 * Consequently, a waiter at the head of the queue may prevent later waiters
 * from acquiring permits, even when those later waiters request fewer permits.
 *
 * <p>Acquiring multiple permits is atomic: either all requested permits are
 * granted or none of them are.
 *
 * <p>Acquisition operations are cancelable. If a waiting acquisition is
 * cancelled, it is removed from the waiting queue. If its permits were already
 * granted concurrently with cancellation, those permits are returned to the
 * semaphore.
 *
 * <p>Releasing permits does not require the semaphore to have previously
 * acquired them. The number of available permits may therefore exceed the
 * initial number of permits.
 *
 * <p>The types of the state and the error aren't parameters of the class but of each operation,
 * so the same semaphore can be used in programs of any state and error type. Java infers them from
 * where the program is used, which a chained call doesn't tell: in
 * {@code semaphore.acquire().map(...)} they are inferred as {@code Object}. Pass the program to a
 * {@link Combine} helper instead, like {@code pipe(semaphore.acquire(), _ -> ...)}, or give the
 * types, like {@code semaphore.<Void, String>acquire().map(...)}.
 */
public final class Semaphore {

  private final Ref<State> state;

  private Semaphore(long permits) {
    this.state = Ref.of(new State(permits, TreePVector.empty()));
  }

  /**
   * Creates a semaphore with the given number of initially available permits.
   *
   * @param permits the initial number of available permits
   * @return a new semaphore
   * @throws IllegalArgumentException if {@code permits} is negative
   */
  public static Semaphore of(long permits) {
    if (permits < 0) {
      throw new IllegalArgumentException("permits must be non-negative");
    }
    return new Semaphore(permits);
  }

  /**
   * Creates a semaphore with the given number of initially available permits.
   *
   * <p>The semaphore is created when the returned program is evaluated.
   *
   * @param permits the initial number of available permits
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @return a program that creates a new semaphore
   * @throws IllegalArgumentException if {@code permits} is negative
   */
  public static <S, E> Program<S, E, Semaphore> make(long permits) {
    return supply(() -> of(permits));
  }

  /**
   * Acquires one permit.
   *
   * <p>If no permit is immediately available, the operation waits until one
   * can be granted. Waiting acquisitions are served in FIFO order.
   *
   * <p>The operation is cancelable while waiting. Cancelling it removes the
   * acquisition request from the waiting queue and ensures that no permit
   * remains consumed by the cancelled operation.
   *
   * <p>Prefer {@link #withPermit(Program)} or {@link #permit()}: with {@code acquire} and
   * {@code release} used by hand, a cancellation that arrives between them keeps the permit, like
   * with any resource acquired by hand.
   *
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @return a program that completes when one permit has been acquired
   */
  public <S, E> Program<S, E, Unit> acquire() {
    return acquire(1);
  }

  /**
   * Acquires the given number of permits atomically.
   *
   * <p>If fewer than {@code n} permits are available, the operation waits
   * until all requested permits can be granted. It never acquires only a
   * subset of the requested permits.
   *
   * <p>Waiting acquisitions are served in FIFO order. A request at the head
   * of the queue may therefore prevent smaller requests behind it from
   * proceeding.
   *
   * <p>The operation is cancelable while waiting. Cancelling it removes the
   * request from the waiting queue. If the permits have already been granted
   * concurrently with cancellation, they are returned to the semaphore.
   *
   * <p>Prefer {@link #withPermits(long, Program)} or {@link #permits(long)}: with {@code acquire}
   * and {@code release} used by hand, a cancellation that arrives between them keeps the permits,
   * like with any resource acquired by hand.
   *
   * @param n the number of permits to acquire
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @return a program that completes when all requested permits have been acquired
   * @throws IllegalArgumentException if {@code n} is not positive
   */
  public <S, E> Program<S, E, Unit> acquire(long n) {
    requirePositive(n);
    return uncancelable(poll -> acquireWith(n, poll));
  }

  /**
   * Attempts to acquire one permit without waiting.
   *
   * <p>The operation succeeds only if a permit is available and there are no
   * pending acquisition requests. This preserves the FIFO ordering of waiting
   * operations.
   *
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @return a program that evaluates to {@code true} if the permit was acquired,
   *         or {@code false} otherwise
   */
  public <S, E> Program<S, E, Boolean> tryAcquire() {
    return tryAcquire(1);
  }

  /**
   * Attempts to acquire the given number of permits atomically without waiting.
   *
   * <p>The operation succeeds only if all requested permits are available and
   * there are no pending acquisition requests. It never acquires a subset of
   * the requested permits.
   *
   * @param n the number of permits to acquire
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @return a program that evaluates to {@code true} if all permits were acquired,
   *         or {@code false} otherwise
   * @throws IllegalArgumentException if {@code n} is not positive
   */
  public <S, E> Program<S, E, Boolean> tryAcquire(long n) {
    requirePositive(n);
    return state.modify(s -> s.tryAcquire(n));
  }

  /**
   * Releases one permit.
   *
   * <p>Releasing a permit may allow one or more waiting acquisitions to proceed.
   * Waiting operations are granted permits according to the semaphore's FIFO
   * policy.
   *
   * <p>Releasing a permit does not require a previous successful acquisition.
   * The number of available permits may exceed the initial number of permits.
   *
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @return a program that completes when the permit has been released and
   *         eligible waiting acquisitions have been notified
   */
  public <S, E> Program<S, E, Unit> release() {
    return release(1);
  }

  /**
   * Releases the given number of permits.
   *
   * <p>Releasing permits may allow one or more waiting acquisitions to proceed.
   * Waiting operations are granted permits according to the semaphore's FIFO
   * policy.
   *
   * <p>Releasing permits does not require previous successful acquisitions.
   * The number of available permits may exceed the initial number of permits.
   *
   * @param n the number of permits to release
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @return a program that completes when the permits have been released and
   *         eligible waiting acquisitions have been notified
   * @throws IllegalArgumentException if {@code n} is not positive
   * @throws ArithmeticException if adding the permits would overflow {@code long}
   */
  public <S, E> Program<S, E, Unit> release(long n) {
    requirePositive(n);
    // uncancelable: once the permits are in the state, every woken waiter must be completed
    return uncancelable(_ -> releaseWith(n));
  }

  /**
   * Acquires one permit, evaluates the given program, and releases the permit
   * when that program terminates.
   *
   * <p>The permit is released whether the program succeeds, fails, or is
   * cancelled. Acquisition is cancelable while waiting, and cancellation before
   * the permit is granted does not consume a permit.
   *
   * @param program the program to evaluate while holding the permit
   * @param <S> the type of the state
   * @param <E> the error type
   * @param <T> the result type
   * @return a program that evaluates {@code program} while holding one permit
   */
  public <S, E, T> Program<S, E, T> withPermit(Program<S, E, T> program) {
    return withPermits(1, program);
  }

  /**
   * Atomically acquires the given number of permits, evaluates the given
   * program, and releases the permits when that program terminates.
   *
   * <p>All requested permits are acquired before the program starts. They are
   * released whether the program succeeds, fails, or is cancelled.
   *
   * <p>Acquisition is cancelable while waiting. If cancellation occurs before
   * the permits are granted, no permits are consumed.
   *
   * @param n the number of permits to acquire
   * @param program the program to evaluate while holding the permits
   * @param <S> the type of the state
   * @param <E> the error type
   * @param <T> the result type
   * @return a program that evaluates {@code program} while holding the
   *         requested permits
   * @throws IllegalArgumentException if {@code n} is not positive
   */
  public <S, E, T> Program<S, E, T> withPermits(long n, Program<S, E, T> program) {
    requirePositive(n);
    requireNonNull(program);
    return bracketFull(
        poll -> acquireWith(n, poll),   // only the wait is cancelable
        _ -> program,
        _ -> releaseWith(n));
  }

  /**
   * Returns a resource that holds one permit while it's used.
   *
   * <p>Like {@link #withPermit(Program)}, but as a {@link Resource}, so it can be composed with
   * other resources: the permit is acquired with them, and released after the ones acquired after
   * it. Only the wait for the permit can be cancelled.
   *
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @return a resource that holds one permit
   */
  public <S, E> Resource<S, E, Unit> permit() {
    return permits(1);
  }

  /**
   * Returns a resource that holds the given number of permits while it's used.
   *
   * <p>Like {@link #withPermits(long, Program)}, but as a {@link Resource}. All the permits are
   * acquired atomically, and only the wait for them can be cancelled.
   *
   * @param n the number of permits to hold
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @return a resource that holds the permits
   * @throws IllegalArgumentException if {@code n} is not positive
   */
  public <S, E> Resource<S, E, Unit> permits(long n) {
    requirePositive(n);
    return Resource.makeFull(poll -> acquireWith(n, poll), _ -> releaseWith(n));
  }

  /**
   * Returns the current number of available permits.
   *
   * <p>The returned value is a snapshot and may become outdated immediately
   * after it is read.
   *
   * <p>Because the semaphore enforces strict FIFO ordering, this value does
   * not necessarily indicate how many permits can be acquired immediately.
   * For example, permits may be available while the first waiting request
   * still requires more permits than are currently available.
   *
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @return a program that evaluates to the current number of available permits
   */
  public <S, E> Program<S, E, Long> available() {
    return chain(state.get(), State::permits);
  }

  // must run inside an uncancelable region, which is where the poll comes from
  private <S, E> Program<S, E, Unit> acquireWith(long n, Poll poll) {
    return pipe(
        newDone(),
        done -> pipe(
            register(n, done),
            granted -> {
              if (granted) {
                return unit();
              }
              // registered as a waiter in the closed part, so only the wait can be cancelled
              return onCancel(poll.apply(done.get()), cancelAcquire(n, done));
            }));
  }

  // takes the permits if they fit and nobody is waiting, otherwise queues the waiter.
  // visible for testing
  <S, E> Program<S, E, Boolean> register(long n, Deferred<Unit> done) {
    return state.modify(s -> s.acquire(n, done));
  }

  private <S, E> Program<S, E, Unit> releaseWith(long n) {
    return pipe(
        state.modify(s -> s.release(n)),
        this::wake);
  }

  // a cancelled wait: if it's still queued it leaves (and the ones behind it may now fit),
  // but if a release already granted it the permits, they are given back.
  // visible for testing
  <S, E> Program<S, E, Unit> cancelAcquire(long n, Deferred<Unit> done) {
    return pipe(
        state.modify(s -> s.cancel(done)),
        removal -> removal.removed()
            ? wake(removal.woken())
            : releaseWith(n));
  }

  private <S, E> Program<S, E, Unit> wake(List<Waiter> woken) {
    return traverse(w -> w.done().<S, E>complete(Unit.unit()), woken).discard();
  }

  private <S, E> Program<S, E, Deferred<Unit>> newDone() {
    return supply(Deferred::create);
  }

  private static void requirePositive(long n) {
    if (n <= 0) {
      throw new IllegalArgumentException("n must be positive");
    }
  }

  // visible for testing: it evaluates a program to read the state, only for the tests
  int waiting() {
    return state.get().evalOrElseThrow().waiters().size();
  }

  @Override
  public String toString() {
    return "Semaphore[permits=%s]".formatted(state.get().evalOrElseThrow().permits());
  }

  private record Waiter(long n, Deferred<Unit> done) {}

  private record Removal(boolean removed, List<Waiter> woken) {}

  private record State(long permits, PVector<Waiter> waiters) {

    Tuple2<Boolean, State> acquire(long n, Deferred<Unit> done) {
      if (waiters.isEmpty() && permits >= n) {
        return tuple(true, new State(permits - n, waiters));
      }
      return tuple(false, new State(permits, waiters.plus(new Waiter(n, done))));
    }

    Tuple2<Boolean, State> tryAcquire(long n) {
      if (waiters.isEmpty() && permits >= n) {
        return tuple(true, new State(permits - n, waiters));
      }
      return tuple(false, this);
    }

    Tuple2<List<Waiter>, State> release(long n) {
      return new State(Math.addExact(permits, n), waiters).grant();
    }

    @SuppressWarnings("ReferenceEquality")
    Tuple2<Removal, State> cancel(Deferred<Unit> done) {
      for (int i = 0; i < waiters.size(); i++) {
        if (waiters.get(i).done() == done) {
          var granted = new State(permits, waiters.minus(i)).grant();
          return tuple(new Removal(true, granted.first()), granted.second());
        }
      }
      // not queued: a release already granted it
      return tuple(new Removal(false, List.of()), this);
    }

    // strict FIFO: stops at the first waiter that doesn't fit
    private Tuple2<List<Waiter>, State> grant() {
      var woken = new ArrayList<Waiter>();
      var available = permits;
      int i = 0;
      while (i < waiters.size() && waiters.get(i).n() <= available) {
        available -= waiters.get(i).n();
        woken.add(waiters.get(i));
        i++;
      }
      return tuple(List.copyOf(woken), new State(available, waiters.subList(i, waiters.size())));
    }
  }
}
