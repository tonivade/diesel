/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel.impl;

import com.github.tonivade.diesel.Program;

/**
 * Represents a program that can be executed on a {@link Service} that provides
 * queue operations.
 * This interface extends {@link Program.Dsl} to provide a domain-specific
 * language (DSL) for working with queues.
 *
 * @param <V> the type of items in the queue
 * @param <T> the type of result returned by the queue operation
 */
public interface Queue<V, T> {

  /**
   * Defines the contract for a queue service.
   *
   * @param <V> the type of items in the queue
   */
  interface Service<V> {
    /**
     * Adds an item to the queue.
     *
     * @param item the item to add
     */
    void offer(V item);

    /**
     * Removes and returns the next item from the queue.
     *
     * @return the next item from the queue
     */
    V take();
  }

  /**
   * Creates a {@link Program} instance that adds an item to the queue.
   *
   * @param <V>  the type of items in the queue
   * @param <S>  the type of service
   * @param <E>  the type of error
   * @param item the item to add
   * @return a {@link Program} instance that adds an item to the queue
   */
  static <V, S extends Service<V>, E> Program<S, E, Void> offer(V item) {
    return Program.access(state -> {
      state.offer(item);
      return null;
    });
  }

  /**
   * Creates a {@link Program} instance that removes and returns the next item
   * from the queue.
   *
   * @param <V> the type of items in the queue
   * @param <S> the type of service
   * @param <E> the type of error
   * @return a {@link Program} instance that removes and returns the next item
   *         from the queue
   */
  static <V, S extends Service<V>, E> Program<S, E, V> take() {
    return Program.access(state -> state.take());
  }
}
