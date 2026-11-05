/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Combine.branch;
import static com.github.tonivade.diesel.Combine.chain;
import static com.github.tonivade.diesel.Combine.pipe;
import static com.github.tonivade.diesel.Combine.traverse;
import static com.github.tonivade.diesel.Program.raise;
import static com.github.tonivade.diesel.Program.success;
import static com.github.tonivade.diesel.Program.supply;
import static com.github.tonivade.diesel.Program.uncancelable;
import static com.github.tonivade.diesel.Program.unit;
import static com.github.tonivade.diesel.Tuple2.tuple;
import static java.util.Objects.requireNonNull;

import java.io.Serial;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.pcollections.ConsPStack;
import org.pcollections.PStack;
import org.pcollections.PVector;
import org.pcollections.TreePVector;

/**
 * A concurrent FIFO queue, bounded or unbounded, with semantic blocking: {@link #take()} waits
 * while the queue is empty and, if it's bounded, {@link #offer(Object)} waits while it's full.
 *
 * <p>Waiting programs can be cancelled, like by a {@code timeout} or a {@code race}, without
 * losing elements. A value that an offer had already handed to a take that is then cancelled is
 * put back in the queue.
 *
 * <p>A queue has a lifecycle with three states:
 * <ul>
 *   <li><b>Open</b>: it accepts offers and takes.</li>
 *   <li><b>Closed</b>, see {@link #close()}: it doesn't accept more elements, but the ones it
 *   already has can still be taken.</li>
 *   <li><b>Closed and empty</b>: the elements have been drained, so no more will ever arrive.
 *   {@link #takeOrClosed()} yields an empty optional, and {@link #take()}, {@link #tryTake()},
 *   {@link #offer(Object)} and {@link #tryOffer(Object)} raise a {@link QueueClosedException}.</li>
 * </ul>
 *
 * <p>After a cancelled {@code take} gives back a value that an offer had already handed to it,
 * the queue can temporarily exceed its capacity, until enough elements are taken: meanwhile, the
 * blocked offers keep waiting. That value is put back even if the queue has been closed in the
 * meantime, since it was accepted before the close.
 *
 * <p>Elements can't be {@code null}.
 *
 * @param <A> the type of the elements
 */
public final class Queue<A> {

  private final int capacity;
  private final Ref<State<A>> state;

  private Queue(int capacity) {
    this.capacity = capacity;
    this.state = Ref.of(State.create(capacity));
  }

  /**
   * Creates a bounded queue.
   *
   * @param capacity the maximum number of elements, at least 1
   * @param <A> the type of the elements
   * @return a new queue
   */
  public static <A> Queue<A> bounded(int capacity) {
    if (capacity < 1) {
      throw new IllegalArgumentException("capacity must be at least 1");
    }
    return new Queue<>(capacity);
  }

  /**
   * Creates an unbounded queue.
   *
   * @param <A> the type of the elements
   * @return a new queue
   */
  public static <A> Queue<A> unbounded() {
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
  public static <S, E, A> Program<S, E, Queue<A>> makeBounded(int capacity) {
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
  public static <S, E, A> Program<S, E, Queue<A>> makeUnbounded() {
    return supply(Queue::unbounded);
  }

  /**
   * Adds the value, waiting while the queue is full.
   *
   * <p>If the queue is closed, the program raises a {@link QueueClosedException}: it's thrown, so it
   * can be handled with {@code catchAll}, but it's not a failure of type {@code E}. An offer already
   * waiting for room when the queue is closed is woken up and raises it too: it was never accepted,
   * so its value is discarded and will not be delivered to any take.
   *
   * <p>The wait can be cancelled, like by a {@code timeout} or a {@code race}, and a cancelled offer
   * is removed from the queue without adding its value. If a take had already made room for it, the
   * value is in the queue, so the offer completes normally instead of being cancelled.
   *
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param value the value to add, not {@code null}
   * @return a program that completes once the value is in the queue
   * @throws QueueClosedException when evaluated, if the queue is closed
   */
  public <S, E> Program<S, E, Unit> offer(A value) {
    requireNonNull(value, "the value of a queue can't be null");
    return uncancelable(poll -> pipe(
        newDone(),
        done -> pipe(
            register(value, done, Mode.WAIT),
            result -> {
              if (result instanceof OfferClosed) {
                return closedError();
              }
              if (result instanceof Blocked) {
                return pipe(
                    poll.apply(done.<S, E>get())
                        .catchAll(error -> error instanceof CancelToken.Cancelled
                            ? cancelOffer(done, error)
                            : raise(() -> error)),
                    accepted -> accepted ? unit() : closedError());
              }
              return unit();
            })));
  }

  /**
   * Adds the value if there is room, without waiting.
   *
   * <p>If the queue is closed, the program raises a {@link QueueClosedException}, so the caller can
   * tell a full queue from one that will never accept more elements.
   *
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param value the value to add, not {@code null}
   * @return a program that returns {@code true} if the value was added, or {@code false} if the
   *     queue is full
   * @throws QueueClosedException when evaluated, if the queue is closed
   */
  public <S, E> Program<S, E, Boolean> tryOffer(A value) {
    requireNonNull(value, "the value of a queue can't be null");
    return uncancelable(_ -> pipe(
        newDone(),
        done -> pipe(
            register(value, done, Mode.TRY),
            result -> {
              if (result instanceof OfferClosed) {
                return closedError();
              }
              return success(!(result instanceof Full));
            })));
  }

  /**
   * Removes and returns the first value, waiting while the queue is empty, or an empty optional
   * when the queue is closed and has no more elements.
   *
   * <p>It's the way to consume a queue until the end without handling exceptions: an empty optional
   * means that the queue is closed and drained, so no more elements will ever arrive. While the queue
   * is closed but still has elements, they are delivered in FIFO order. A program already waiting
   * when the queue is closed is woken up with an empty optional.
   *
   * <p>The wait can be cancelled, like by a {@code timeout} or a {@code race}, without losing
   * elements: a value already handed to a cancelled take is put back in the queue, even if the queue
   * has been closed in the meantime.
   *
   * <pre>{@code
   * Program<S, E, Unit> consume(Queue<Integer> queue) {
   *   return pipe(
   *       queue.takeOrClosed(),
   *       value -> value.isPresent()
   *           ? process(value.get()).andThen(suspend(() -> consume(queue)))
   *           : Program.unit());
   * }
   * }</pre>
   *
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @return a program that yields the first value, or an empty optional if the queue is closed and empty
   */
  public <S, E> Program<S, E, Optional<A>> takeOrClosed() {
    return uncancelable(poll -> pipe(
        newWaiter(),
        waiter -> pipe(
            claim(waiter, true),
            result -> {
              if (result instanceof Taken<A>(var value, _)) {
                return success(Optional.of(value));
              }
              if (result instanceof TakeClosed) {
                return success(Optional.empty());
              }
              if (result instanceof Waiting) {
                return poll.apply(waiter.<S, E>get().onCancel(releaseTaker(waiter)));
              }
              return raise(IllegalStateException::new);
            })));
  }

  /**
   * Removes and returns the first value, waiting while the queue is empty.
   *
   * <p>If the queue is closed and still has elements, they are delivered in FIFO order. Once the
   * queue is closed and empty, the program raises a {@link QueueClosedException}: it's thrown, so it
   * can be handled with {@code catchAll}, but it's not a failure of type {@code E}. A program
   * already waiting when the queue is closed is woken up and raises it too. Use
   * {@link #takeOrClosed()} to detect the end of the queue without an exception.
   *
   * <p>The wait can be cancelled, like by a {@code timeout} or a {@code race}, without losing
   * elements: a value already handed to a cancelled take is put back in the queue, even if the queue
   * has been closed in the meantime.
   *
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @return a program that yields the first value
   * @throws QueueClosedException when evaluated, if the queue is closed and empty
   */
  public <S, E> Program<S, E, A> take() {
    return pipe(
        takeOrClosed(),
        value -> value.isPresent()
            ? success(value.get())
            : closedError());
  }

  /**
   * Removes and returns the first value if there is one, without waiting.
   *
   * <p>If the queue is open and empty, it returns an empty optional. If it's closed and empty, the
   * program raises a {@link QueueClosedException}, so the caller can tell an empty queue from one
   * that will never have more elements.
   *
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @return a program that yields the first value, or an empty optional
   * @throws QueueClosedException when evaluated, if the queue is closed and empty
   */
  public <S, E> Program<S, E, Optional<A>> tryTake() {
    return uncancelable(_ -> pipe(
        newWaiter(),
        waiter -> pipe(
            claim(waiter, false),
            result -> {
              if (result instanceof TakeClosed) {
                return closedError();
              }
              if (result instanceof Taken<A>(var value, _)) {
                return success(Optional.of(value));
              }
              return success(Optional.empty());
            })));
  }

  /**
   * Number of elements in the queue, not counting the waiting offers.
   *
   * <p>A closed queue keeps counting the elements that haven't been taken yet, until it's drained.
   *
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @return a program that yields the current size
   */
  public <S, E> Program<S, E, Integer> size() {
    return chain(
        state.get(),
        State::size);
  }

  /**
   * Capacity of the queue. In case of unbounded it returns {@link Integer#MAX_VALUE}.
   *
   * @return the queue capacity
   */
  public int capacity() {
    return capacity;
  }

  /**
   * Closes the queue: it doesn't accept more elements, but the ones it already has can still be
   * taken.
   *
   * <p>After it, {@link #offer(Object)} and {@link #tryOffer(Object)} raise a
   * {@link QueueClosedException}. {@link #take()} and {@link #tryTake()} keep delivering the
   * remaining elements in FIFO order, and raise it once the queue is empty. {@link #takeOrClosed()}
   * yields an empty optional instead.
   *
   * <p>The programs waiting when the queue is closed are woken up: the takes, which can only be
   * waiting because the queue is empty, see it closed and empty, and the offers waiting for room
   * fail, since they were never accepted.
   *
   * <p>It's idempotent: closing a closed queue does nothing. It can't be cancelled once it has
   * started, so every waiting program is woken up.
   *
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @return a program that closes the queue
   */
  public <S, E> Program<S, E, Unit> close() {
    return uncancelable(_ -> pipe(
        state.modify(State::close),
        pending -> pipe(
            traverse(t -> t.<S, E>complete(Optional.empty()), pending.takers()),
            _ -> traverse(o -> o.done().complete(false), pending.offerers())
        ).discard()));
  }

  /**
   * Whether the queue has been closed, see {@link #close()}.
   *
   * <p>A closed queue can still have elements to take: use {@link #takeOrClosed()} to detect when it
   * is drained.
   *
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @return a program that yields {@code true} if the queue is closed
   */
  public <S, E> Program<S, E, Boolean> isClosed() {
    return chain(state.get(), State::closed);
  }

  /**
   * Whether the queue is empty or not.
   *
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @return a program that yields {@code true} if the queue is empty
   */
  public <S, E> Program<S, E, Boolean> isEmpty() {
    return chain(size(), s -> s == 0);
  }

  /**
   * Whether the queue is full or not. In case of unbounded always return {@code false}.
   *
   * <p>A queue can be over capacity after a value is put back when a cancelled take puts back
   * a value that an offer had already handed to it.
   *
   * <p>The result of this method is a snapshot that may already be stale. Don't use {@code isFull()}
   * to decide whether to call {@link #offer(Object)}. Use {@link #tryOffer(Object)} instead,
   * since it checks and adds in one step.
   *
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @return a program that yields {@code true} if the queue is full
   */
  public <S, E> Program<S, E, Boolean> isFull() {
    return chain(state.get(), s -> s.size() >= s.capacity());
  }

  @Override
  public String toString() {
    return "Queue[capacity=%s]".formatted(capacity < Integer.MAX_VALUE ? capacity : "unbounded");
  }

  private static <S, E, T> Program<S, E, T> closedError() {
    return raise(QueueClosedException::new);
  }

  private <S, E> Program<S, E, Deferred<Boolean>> newDone() {
    return supply(Deferred::create);
  }

  private <S, E> Program<S, E, Deferred<Optional<A>>> newWaiter() {
    return supply(Deferred::create);
  }

  // visible for testing
  <S, E> Program<S, E, OfferResult<A>> register(
      A value, Deferred<Boolean> done, Mode mode) {
    return pipe(
        state.modify(s -> s.offer(value, done, mode)),
        result -> result instanceof Handoff<A>(var taker, var next)
            ? chain(taker.complete(Optional.of(next)), _ -> result)
            : success(result));
  }

  // visible for testing
  <S, E> Program<S, E, TakeResult<A>> claim(
      Deferred<Optional<A>> waiter, boolean wait) {
    return pipe(
        state.modify(s -> s.take(waiter, wait)),
        result -> result instanceof Taken<A>(_, var wake) && wake.isPresent()
            ? chain(wake.get().complete(true), _ -> result)
            : success(result));
  }

  // the cancellation reaches here inside the uncancelable region, so it can be handled: if the
  // offer is still waiting it's removed and the cancellation goes on, but if a take or a close got
  // it first, the offer was already decided and it waits for that outcome instead of losing it.
  // visible for testing
  <S, E> Program<S, E, Boolean> cancelOffer(
      Deferred<Boolean> done, Throwable cancellation) {
    return pipe(
        state.modify(s -> s.removeOfferer(done)),
        branch(
            () -> raise(() -> cancellation),
            done::get));
  }

  // visible for testing
  <S, E> Program<S, E, Unit> releaseTaker(Deferred<Optional<A>> waiter) {
    return pipe(
        state.modify(s -> s.removeTaker(waiter)),
        branch(
            Program::unit,
            // an offer already handed it a value: put it back instead of losing it
            () -> pipe(
                waiter.get(),
                value -> value.isPresent()
                    ? pipe(
                        newDone(),
                        done -> register(value.get(), done, Mode.REQUEUE),
                        _ -> unit())
                    : unit())));
  }

  // visible for the testing
  int waitingTakers() {
   return state.get().evalOrElseThrow().takers().size();
  }

  // visible for the testing
  int waitingOfferers() {
   return state.get().evalOrElseThrow().offerers().size();
  }

  // visible for testing
  enum Mode { WAIT, TRY, REQUEUE }

  private sealed interface OfferResult<A> {}
  private record Stored<A>() implements OfferResult<A> {}
  private record Handoff<A>(Deferred<Optional<A>> taker, A value) implements OfferResult<A> {}
  private record Blocked<A>(Deferred<Boolean> done) implements OfferResult<A> {}
  private record Full<A>() implements OfferResult<A> {}
  private record OfferClosed<A>() implements OfferResult<A> {}

  private sealed interface TakeResult<A> {}
  private record Taken<A>(A value, Optional<Deferred<Boolean>> wake) implements TakeResult<A> {}
  private record Waiting<A>() implements TakeResult<A> {}
  private record Empty<A>() implements TakeResult<A> {}
  private record TakeClosed<A>() implements TakeResult<A> {}

  private record Pending<A>(
      PVector<Deferred<Optional<A>>> takers,
      PVector<Offerer<A>> offerers) {}

  private record Offerer<A>(A value, Deferred<Boolean> done) {

    @Override
    public boolean equals(Object other) {
      return other instanceof Offerer<?> offerer && done == offerer.done;
    }

    @Override
    public int hashCode() {
      return done.hashCode();
    }
  }

  private record State<A>(
      int capacity,
      Fifo<A> items,
      PVector<Deferred<Optional<A>>> takers,
      PVector<Offerer<A>> offerers,
      boolean closed) {

    static <A> State<A> create(int capacity) {
      return new State<>(
          capacity, Fifo.empty(), TreePVector.empty(), TreePVector.empty(), false);
    }

    int size() {
      return items.size();
    }

    Tuple2<OfferResult<A>, State<A>> offer(A value, Deferred<Boolean> done, Mode mode) {
      if (closed && mode != Mode.REQUEUE) {
        return tuple(new OfferClosed<>(), this);
      }
      if (!takers.isEmpty()) {
        return tuple(new Handoff<>(takers.getFirst(), value), new State<>(capacity, items, takers.minus(0), offerers, closed));
      }
      if (mode == Mode.REQUEUE) {
        return tuple(new Stored<>(), new State<>(capacity, items.prepend(value), takers, offerers, closed));
      }
      if (size() < capacity) {
        return tuple(new Stored<>(), new State<>(capacity, items.append(value), takers, offerers, closed));
      }
      if (mode == Mode.TRY) {
        return tuple(new Full<>(), this);
      }
      return tuple(
          new Blocked<>(done),
          new State<>(capacity, items, takers, offerers.plus(new Offerer<>(value, done)), closed));
    }

    Tuple2<TakeResult<A>, State<A>> take(Deferred<Optional<A>> waiter, boolean wait) {
      if (!items.isEmpty()) {
        var head = items.poll();
        var rest = head.second();
        // a place was freed: the first blocked offer enters the queue and is woken up. A queue over
        // its capacity, after a value was put back, doesn't free a place until it's below it
        if (!offerers.isEmpty() && rest.size() < capacity) {
          return tuple(
              new Taken<>(head.first(), Optional.of(offerers.getFirst().done())),
              new State<>(capacity, rest.append(offerers.getFirst().value()), takers, offerers.minus(0), closed));
        }
        return tuple(
            new Taken<>(head.first(), Optional.empty()),
            new State<>(capacity, rest, takers, offerers, closed));
      }
      if (closed) {
        return tuple(new TakeClosed<>(), this);
      }
      if (!wait) {
        return tuple(new Empty<>(), this);
      }
      return tuple(new Waiting<>(), new State<>(capacity, items, takers.plus(waiter), offerers, closed));
    }

    Tuple2<Boolean, State<A>> removeTaker(Deferred<Optional<A>> waiter) {
      var rest = takers.minus(waiter);
      return tuple(rest.size() < takers.size(), new State<>(capacity, items, rest, offerers, closed));
    }

    Tuple2<Boolean, State<A>> removeOfferer(Deferred<Boolean> done) {
      var rest = offerers.minus(new Offerer<>(Unit.unit(), done));
      return tuple(rest.size() < offerers.size(), new State<>(capacity, items, takers, rest, closed));
    }

    Tuple2<Pending<A>, State<A>> close() {
      if (closed) {
        return tuple(new Pending<>(TreePVector.empty(), TreePVector.empty()), this);
      }
      return tuple(
          new Pending<>(takers, offerers),
          new State<>(capacity, items, TreePVector.empty(), TreePVector.empty(), true));
    }
  }

  /**
   * Raised by the operations of a {@link Queue} that can't complete because the queue is closed: an
   * offer to a closed queue, or a take from a closed and empty one.
   */
  public static final class QueueClosedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public QueueClosedException() {
      super("queue is closed");
    }
  }

  private static final class Fifo<T> {

    private static final Fifo<?> EMPTY = new Fifo<>(ConsPStack.empty(), ConsPStack.empty(), 0);

    private final PStack<T> front;
    private final PStack<T> back; // reversed
    private final int size;

    private Fifo(PStack<T> front, PStack<T> back, int size) {
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

    Fifo<T> append(T value) {
      return new Fifo<>(front, back.plus(value), size + 1);
    }

    Fifo<T> prepend(T value) {
      return new Fifo<>(front.plus(value), back, size + 1);
    }

    Tuple2<T, Fifo<T>> poll() {
      var f = front;
      var b = back;
      if (f.isEmpty()) {
        f = reverse(b);
        b = ConsPStack.empty();
      }
      if (f.isEmpty()) {
        throw new NoSuchElementException();
      }
      return tuple(f.getFirst(), new Fifo<>(f.minus(0), b, size - 1));
    }

    private static <T> PStack<T> reverse(PStack<T> list) {
      PStack<T> result = ConsPStack.empty();
      for (T t : list) {
        result = result.plus(t);
      }
      return result;
    }
  }
}