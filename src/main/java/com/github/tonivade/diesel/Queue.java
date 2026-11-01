/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Combine.chain;
import static com.github.tonivade.diesel.Combine.pipe;
import static com.github.tonivade.diesel.Program.raise;
import static com.github.tonivade.diesel.Program.success;
import static com.github.tonivade.diesel.Program.supply;
import static com.github.tonivade.diesel.Program.unit;
import static com.github.tonivade.diesel.Tuple2.tuple;
import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

/**
 * A concurrent FIFO queue, bounded or unbounded, with semantic blocking: {@link #take()} waits
 * while the queue is empty and, if it's bounded, {@link #offer(Object)} waits while it's full.
 *
 * <p>Waiting programs can be cancelled, like by a {@code timeout} or a {@code race}, without
 * losing elements. Elements can't be {@code null}.
 *
 * <p>After a cancelled {@code take} gives back a value that an offer had already handed to it,
 * the queue can temporarily exceed its capacity.
 *
 * @param <S> the type of the state
 * @param <E> the type of the error
 * @param <A> the type of the elements
 */
public final class Queue<S, E, A> {

  private final Ref<S, E, State<S, E, A>> state;

  private Queue(int capacity) {
    State<S, E, A> initial = new State<>(capacity, Fifo.empty(), Fifo.empty(), Fifo.empty());
    this.state = Ref.of(initial);
  }

  /**
   * Creates a bounded queue.
   *
   * @param capacity the maximum number of elements, at least 1
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <A> the type of the elements
   * @return a new queue
   */
  public static <S, E, A> Queue<S, E, A> bounded(int capacity) {
    if (capacity < 1) {
      throw new IllegalArgumentException("capacity must be at least 1");
    }
    return new Queue<>(capacity);
  }

  /**
   * Creates an unbounded queue.
   *
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <A> the type of the elements
   * @return a new queue
   */
  public static <S, E, A> Queue<S, E, A> unbounded() {
    return new Queue<>(Integer.MAX_VALUE);
  }

  /**
   * Creates a program that allocates a new bounded queue each time it is evaluated.
   *
   * @param capacity the maximum number of elements, at least 1
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <A> the type of the elements
   * @return a program that creates a fresh queue
   */
  public static <S, E, A> Program<S, E, Queue<S, E, A>> makeBounded(int capacity) {
    return supply(() -> bounded(capacity));
  }

  /**
   * Creates a program that allocates a new unbounded queue each time it is evaluated.
   *
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <A> the type of the elements
   * @return a program that creates a fresh queue
   */
  public static <S, E, A> Program<S, E, Queue<S, E, A>> makeUnbounded() {
    return supply(() -> unbounded());
  }

  // ---- offer ----

  /**
   * Adds the value, waiting while the queue is full.
   *
   * @param <S1> the type of the state
   * @param <E1> the type of the error
   * @param value the value to add, not {@code null}
   * @return a program that completes once the value is in the queue
   */
  public <S1 extends S, E1 extends E> Program<S1, E1, Unit> offer(A value) {
    requireNonNull(value, "the value of a queue can't be null");
    return pipe(
        newDone(),
        done -> pipe(
            register(value, done, Mode.WAIT),
            result -> result instanceof Blocked
                ? done.<S1, E1>get().onCancel(removeOfferer(done))
                : unit()));
  }

  /**
   * Adds the value if there is room, without waiting.
   *
   * @param <S1> the type of the state
   * @param <E1> the type of the error
   * @param value the value to add, not {@code null}
   * @return a program that returns {@code true} if the value was added
   */
  public <S1 extends S, E1 extends E> Program<S1, E1, Boolean> tryOffer(A value) {
    requireNonNull(value, "the value of a queue can't be null");
    return pipe(
        newDone(),
        done -> chain(
            register(value, done, Mode.TRY),
            result -> !(result instanceof Full)));
  }

  // ---- take ----

  /**
   * Removes and returns the first value, waiting while the queue is empty.
   *
   * @param <S1> the type of the state
   * @param <E1> the type of the error
   * @return a program that yields the first value
   */
  public <S1 extends S, E1 extends E> Program<S1, E1, A> take() {
    return pipe(
        newWaiter(),
        waiter -> pipe(
            claim(waiter, true),
            result -> {
              if (result instanceof Taken<S, E, A> taken) {
                return success(taken.value());
              }
              if (result instanceof Waiting) {
                return waiter.<S1, E1>get().onCancel(releaseTaker(waiter));
              }
              return raise(IllegalStateException::new);
            }));
  }

  /**
   * Removes and returns the first value if there is one, without waiting.
   *
   * @param <S1> the type of the state
   * @param <E1> the type of the error
   * @return a program that yields the first value, or an empty optional
   */
  public <S1 extends S, E1 extends E> Program<S1, E1, Optional<A>> tryTake() {
    return pipe(
        newWaiter(),
        waiter -> chain(
            claim(waiter, false),
            result -> result instanceof Taken<S, E, A> taken
                ? Optional.of(taken.value())
                : Optional.empty()));
  }

  /**
   * Number of elements in the queue, not counting the waiting offers.
   *
   * @param <S1> the type of the state
   * @param <E1> the type of the error
   * @return a program that yields the current size
   */
  public <S1 extends S, E1 extends E> Program<S1, E1, Integer> size() {
    return chain(
        state.get(),
        s -> s.items().size());
  }

  // ---- internals ----

  private <S1 extends S, E1 extends E> Program<S1, E1, Deferred<S, E, Unit>> newDone() {
    return supply(Deferred::create);
  }

  private <S1 extends S, E1 extends E> Program<S1, E1, Deferred<S, E, A>> newWaiter() {
    return supply(Deferred::create);
  }

  private <S1 extends S, E1 extends E> Program<S1, E1, OfferResult<S, E, A>> register(
      A value, Deferred<S, E, Unit> done, Mode mode) {
    return pipe(
        state.modify(s -> s.offer(value, done, mode)),
        result -> result instanceof Handoff<S, E, A> handoff
            ? chain(handoff.taker().<S1, E1>complete(handoff.value()), _ -> result)
            : success(result))
      .uncancelable();
  }

  private <S1 extends S, E1 extends E> Program<S1, E1, TakeResult<S, E, A>> claim(
      Deferred<S, E, A> waiter, boolean wait) {
    return pipe(
        state.modify(s -> s.take(waiter, wait)),
        result -> result instanceof Taken<S, E, A> taken && taken.wake().isPresent()
            ? chain(taken.wake().get().<S1, E1>complete(Unit.unit()), _ -> result)
            : success(result))
      .uncancelable();
  }

  private <S1 extends S, E1 extends E> Program<S1, E1, Unit> removeOfferer(Deferred<S, E, Unit> done) {
    return chain(
        state.modify(s -> s.removeOfferer(done)),
        _ -> Unit.unit());
  }

  private <S1 extends S, E1 extends E> Program<S1, E1, Unit> releaseTaker(Deferred<S, E, A> waiter) {
    return pipe(
        state.modify(s -> s.removeTaker(waiter)),
        removed -> removed
            ? unit()
            // an offer already handed it a value: put it back instead of losing it
            : pipe(
                waiter.get(),
                value -> pipe(newDone(), done -> register(value, done, Mode.FORCE)),
                _ -> unit()));
  }

  // ---- pure state, retried safely by Ref.modify ----

  private enum Mode { WAIT, TRY, FORCE }

  private sealed interface OfferResult<S, E, A> {}
  private record Stored<S, E, A>() implements OfferResult<S, E, A> {}
  private record Handoff<S, E, A>(Deferred<S, E, A> taker, A value) implements OfferResult<S, E, A> {}
  private record Blocked<S, E, A>(Deferred<S, E, Unit> done) implements OfferResult<S, E, A> {}
  private record Full<S, E, A>() implements OfferResult<S, E, A> {}

  private sealed interface TakeResult<S, E, A> {}
  private record Taken<S, E, A>(A value, Optional<Deferred<S, E, Unit>> wake) implements TakeResult<S, E, A> {}
  private record Waiting<S, E, A>() implements TakeResult<S, E, A> {}
  private record Empty<S, E, A>() implements TakeResult<S, E, A> {}

  private record Offerer<S, E, A>(A value, Deferred<S, E, Unit> done) {}

  private record State<S, E, A>(
      int capacity,
      Fifo<A> items,
      Fifo<Deferred<S, E, A>> takers,
      Fifo<Offerer<S, E, A>> offerers) {

    Tuple2<OfferResult<S, E, A>, State<S, E, A>> offer(A value, Deferred<S, E, Unit> done, Mode mode) {
      if (!takers.isEmpty()) {
        var next = takers.poll();
        return tuple(new Handoff<>(next.first(), value), new State<>(capacity, items, next.second(), offerers));
      }
      if (items.size() < capacity || mode == Mode.FORCE) {
        return tuple(new Stored<>(), new State<>(capacity, items.offer(value), takers, offerers));
      }
      if (mode == Mode.TRY) {
        return tuple(new Full<>(), this);
      }
      return tuple(
          new Blocked<>(done),
          new State<>(capacity, items, takers, offerers.offer(new Offerer<>(value, done))));
    }

    Tuple2<TakeResult<S, E, A>, State<S, E, A>> take(Deferred<S, E, A> waiter, boolean wait) {
      if (!items.isEmpty()) {
        var head = items.poll();
        if (!offerers.isEmpty()) {
          // a place was freed: the first blocked offer enters the queue and is woken up
          var offerer = offerers.poll();
          return tuple(
              new Taken<>(head.first(), Optional.of(offerer.first().done())),
              new State<>(capacity, head.second().offer(offerer.first().value()), takers, offerer.second()));
        }
        return tuple(
            new Taken<>(head.first(), Optional.empty()),
            new State<>(capacity, head.second(), takers, offerers));
      }
      if (!wait) {
        return tuple(new Empty<>(), this);
      }
      return tuple(new Waiting<>(), new State<>(capacity, items, takers.offer(waiter), offerers));
    }

    Tuple2<Boolean, State<S, E, A>> removeTaker(Deferred<S, E, A> waiter) {
      var rest = takers.removeIf(t -> t == waiter);
      return tuple(rest != takers, new State<>(capacity, items, rest, offerers));
    }

    Tuple2<Boolean, State<S, E, A>> removeOfferer(Deferred<S, E, Unit> done) {
      var rest = offerers.removeIf(o -> o.done() == done);
      return tuple(rest != offerers, new State<>(capacity, items, takers, rest));
    }
  }

  /** Immutable FIFO, amortized O(1) offer and poll. Removal is O(n), only used on cancellation. */
  private static final class Fifo<T> {

    private record Cons<T>(T head, @Nullable Cons<T> tail) {}

    private static final Fifo<Object> EMPTY = new Fifo<>(null, null, 0);

    private final @Nullable Cons<T> front;
    private final @Nullable Cons<T> back; // reversed
    private final int size;

    private Fifo(@Nullable Cons<T> front, @Nullable Cons<T> back, int size) {
      this.front = front;
      this.back = back;
      this.size = size;
    }

    @SuppressWarnings("unchecked")
    static <T> Fifo<T> empty() {
      return (Fifo<T>) EMPTY;
    }

    boolean isEmpty() {
      return size == 0;
    }

    int size() {
      return size;
    }

    Fifo<T> offer(T value) {
      return new Fifo<>(front, new Cons<>(value, back), size + 1);
    }

    Tuple2<T, Fifo<T>> poll() {
      var f = front;
      var b = back;
      if (f == null) {
        f = reverse(b);
        b = null;
      }
      if (f == null) {
        throw new NoSuchElementException();
      }
      return tuple(f.head(), new Fifo<>(f.tail(), b, size - 1));
    }

    /** Removes the first element that matches, returns {@code this} if none does. */
    Fifo<T> removeIf(Predicate<? super T> matcher) {
      var all = new ArrayList<T>(size);
      for (var c = front; c != null; c = c.tail()) {
        all.add(c.head());
      }
      var pending = new ArrayList<T>();
      for (var c = back; c != null; c = c.tail()) {
        pending.add(c.head());
      }
      Collections.reverse(pending);
      all.addAll(pending);

      var removed = false;
      Fifo<T> result = empty();
      for (var t : all) {
        if (!removed && matcher.test(t)) {
          removed = true;
        } else {
          result = result.offer(t);
        }
      }
      return removed ? result : this;
    }

    private static <T> @Nullable Cons<T> reverse(@Nullable Cons<T> list) {
      Cons<T> result = null;
      for (var c = list; c != null; c = c.tail()) {
        result = new Cons<>(c.head(), result);
      }
      return result;
    }
  }
}