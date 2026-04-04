/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel.impl;

import static com.github.tonivade.diesel.Program.effect;
import static com.github.tonivade.diesel.Program.inspect;

import com.github.tonivade.diesel.Program;

/**
 * Represents a program that can be executed on a {@link Service} that provides
 * queue operations.
 *
 * @param <T> the type of items in the queue
 */
public interface Queue<T> {

  /**
   * Defines the contract for a queue service.
   *
   * @param <T> the type of items in the queue
   */
  interface Service<T> {
    /**
     * Adds an item to the queue.
     *
     * @param item the item to add
     */
    void offer(T item);

    /**
     * Removes and returns the next item from the queue.
     *
     * @return the next item from the queue
     */
    T take();
  }

  /**
   * Creates a {@link Program} instance that adds an item to the queue.
   *
   * @param <T>  the type of items in the queue
   * @param <S>  the type of service
   * @param <E>  the type of error
   * @param item the item to add
   * @return a {@link Program} instance that adds an item to the queue
   */
  static <T, S extends Service<T>, E> Program<S, E, Void> offer(T item) {
    return inspect(state -> state.offer(item));
  }

  /**
   * Creates a {@link Program} instance that removes and returns the next item
   * from the queue.
   *
   * @param <T> the type of items in the queue
   * @param <S> the type of service
   * @param <E> the type of error
   * @return a {@link Program} instance that removes and returns the next item
   *         from the queue
   */
  static <T, S extends Service<T>, E> Program<S, E, T> take() {
    return effect(Service::take);
  }
}
