/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Program.success;
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
 * @param <A> type of stored value
 */
public final class Ref<A> {

  private final AtomicReference<A> value;

  private Ref(A value) {
    this.value = new AtomicReference<>(value);
  }

  /**
   * Returns the stored value.
   *
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @return the value
   */
  public <S, E> Program<S, E, A> get() {
    return supply(value::get);
  }

  /**
   * Updates the stored value.
   *
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param newValue new value to store
   */
  public <S, E> Program<S, E, Void> set(A newValue) {
    return task(() -> value.set(newValue));
  }

  public <S, E> Program<S, E, Void> lazySet(A newValue) {
    return task(() -> value.lazySet(newValue));
  }

  public <S, E> Program<S, E, A> getAndSet(A newValue) {
    return supply(() -> value.getAndSet(newValue));
  }

  public <S, E> Program<S, E, A> updateAndGet(UnaryOperator<A> update) {
    return supply(() -> value.updateAndGet(update));
  }

  public <S, E> Program<S, E, A> getAndUpdate(UnaryOperator<A> update) {
    return supply(() -> value.getAndUpdate(update));
  }

  public <S, E, B> Program<S, E, B> modify(Function<? super A, Tuple2<B, A>> change) {
    return supply(() -> {
      var loop = true;
      B result = null;
      while (loop) {
        A current = value.get();
        var tuple = change.apply(current);
        result = tuple.first();
        loop = !value.compareAndSet(current, tuple.second());
      }
      return result;
    });
  }

  public static <S, E, A> Program<S, E, Ref<A>> make(A value) {
    return success(of(value));
  }

  public static <A> Ref<A> of(A value) {
    return new Ref<>(value);
  }

  @Override
  public String toString() {
    return String.format("Ref(%s)", value.get());
  }
}
