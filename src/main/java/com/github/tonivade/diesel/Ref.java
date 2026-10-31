/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Program.supply;
import static com.github.tonivade.diesel.Program.task;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * Models a mutable reference to a value of type {@code A} in which we can store immutable data.
 * Two basic operations {@link #set(Object)}, to set the value, and {@link #get()} to retrieve
 * the value.
 *
 * <p>It provides a way to functionally manage in-memory state. All operations are atomic and thread-safe,
 * giving a reliable foundation for synchronizing concurrent programs.
 *
 * @param <S> the type of the state
 * @param <E> the type of the error
 * @param <A> type of stored value
 */
public final class Ref<S, E, A> {

  private final AtomicReference<A> value;

  private Ref(A value) {
    this.value = new AtomicReference<>(value);
  }

  /**
   * Returns the currently stored value.
   *
   * @param <S1> the type of the state
   * @param <E1> the type of the error
   * @return a program that reads the current value when evaluated
   */
  public <S1 extends S, E1 extends E> Program<S1, E1, A> get() {
    return supply(value::get);
  }

  /**
   * Atomically replaces the stored value.
   *
   * @param <S1> the type of the state
   * @param <E1> the type of the error
   * @param newValue the value to store
   * @return a program that sets the value and returns {@code null}
   */
  public <S1 extends S, E1 extends E> Program<S1, E1, Unit> set(A newValue) {
    return task(() -> value.set(newValue));
  }

  /**
   * Atomically updates the value using the supplied function.
   *
   * <p>The function may be applied more than once if concurrent updates contend; it should
   * therefore be side-effect-free.
   *
   * @param <S1> the type of the state
   * @param <E1> the type of the error
   * @param update the function used to compute the new value
   * @return a program that performs the update and returns {@code null}
   */
  public <S1 extends S, E1 extends E> Program<S1, E1, Unit> update(UnaryOperator<A> update) {
    return task(() -> value.updateAndGet(update));
  }

  /**
   * Atomically replaces the stored value and returns the previous value.
   *
   * @param <S1> the type of the state
   * @param <E1> the type of the error
   * @param newValue the value to store
   * @return a program that returns the value held before the update
   */
  public <S1 extends S, E1 extends E> Program<S1, E1, A> getAndSet(A newValue) {
    return supply(() -> value.getAndSet(newValue));
  }

  /**
   * Atomically updates the value using the supplied function and returns the updated value.
   *
   * <p>The function may be applied more than once if concurrent updates contend; it should
   * therefore be side-effect-free.
   *
   * @param <S1> the type of the state
   * @param <E1> the type of the error
   * @param update the function used to compute the new value
   * @return a program that returns the updated value
   */
  public <S1 extends S, E1 extends E> Program<S1, E1, A> updateAndGet(UnaryOperator<A> update) {
    return supply(() -> value.updateAndGet(update));
  }

  /**
   * Atomically updates the value using the supplied function and returns the previous value.
   *
   * <p>The function may be applied more than once if concurrent updates contend; it should
   * therefore be side-effect-free.
   *
   * @param <S1> the type of the state
   * @param <E1> the type of the error
   * @param update the function used to compute the new value
   * @return a program that returns the value held before the update
   */
  public <S1 extends S, E1 extends E> Program<S1, E1, A> getAndUpdate(UnaryOperator<A> update) {
    return supply(() -> value.getAndUpdate(update));
  }

  /**
   * Atomically computes and applies a change based on the current value.
   *
   * <p>The function returns a pair whose first element is the result to return and whose
   * second element is the new value to store. If another thread updates the reference before
   * the change can be applied, the function may be invoked again with the newer value.
   * The function should therefore be side-effect-free.
   *
   * @param <S1> the type of the state
   * @param <E1> the type of the error
   * @param <B> the type of the result produced by the change
   * @param change a function from the current value to the result and replacement value
   * @return a program that returns the result associated with the successfully applied change
   */
  public <S1 extends S, E1 extends E, B> Program<S1, E1, B> modify(Function<? super A, Tuple2<B, A>> change) {
    return supply(() -> {
      while (true) {
        A current = value.get();
        var tuple = change.apply(current);
        if (value.compareAndSet(current, tuple.second())) {
          return tuple.first();
        }
      }
    });
  }

  /**
   * Creates a program that allocates a new reference initialized with the supplied value
   * each time the program is evaluated.
   *
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <A> the type of the stored value
   * @param value the initial value
   * @return a program that creates a fresh reference when evaluated
   */
  public static <S, E, A> Program<S, E, Ref<S, E, A>> make(A value) {
    return supply(() -> of(value));
  }

  /**
   * Creates a reference initialized with the supplied value immediately.
   *
   * @param <A> the type of the stored value
   * @param value the initial value
   * @return a new reference
   */
  public static <S, E, A> Ref<S, E, A> of(A value) {
    return new Ref<>(value);
  }

  /**
   * Returns a string representation containing the current value.
   *
   * @return a string representation of this reference
   */
  @Override
  public String toString() {
    return "Ref(%s)".formatted(value.get());
  }
}
