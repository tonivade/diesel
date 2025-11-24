/*
 * Copyright (c) 2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel.impl;

import static com.github.tonivade.diesel.Result.success;

import com.github.tonivade.diesel.Program;
import com.github.tonivade.diesel.Result;

/**
 * Represents a program that can be executed on a {@link Service} that provides
 * queue operations.
 * This interface extends {@link Program.Dsl} to provide a domain-specific
 * language (DSL) for working with queues.
 *
 * @param <V> the type of items in the queue
 * @param <T> the type of result returned by the queue operation
 */
public sealed interface Queue<V, T> extends Program.Dsl<Queue.Service<V>, Void, T> {

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
   * Represents an operation to add an item to the queue.
   *
   * @param <V> the type of items in the queue
   */
  record Offer<V>(V item) implements Queue<V, Void> {}

  /**
   * Represents an operation to remove and return the next item from the queue.
   *
   * @param <V> the type of items in the queue
   */
  record Take<V>() implements Queue<V, V> {}

  /**
   * Creates a {@link Program} instance that adds an item to the queue.
   *
   * @param <V>  the type of items in the queue
   * @param <S>  the type of service
   * @param <E>  the type of error
   * @param item the item to add
   * @return a {@link Program} instance that adds an item to the queue
   */
  @SuppressWarnings("unchecked")
  static <V, S extends Service<V>, E> Program<S, E, Void> offer(V item) {
    return (Program<S, E, Void>) new Offer<>(item);
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
  @SuppressWarnings("unchecked")
  static <V, S extends Service<V>, E> Program<S, E, V> take() {
    return (Program<S, E, V>) new Take<>();
  }

  /**
   * Evaluates the queue program on a given service.
   *
   * @param service the service to evaluate the program on
   * @return the result of the evaluation
   */
  @Override
  @SuppressWarnings({ "rawtypes", "unchecked" })
  default Result<Void, T> handle(Service<V> service) {
    return success((T) switch (this) {
      case Offer offer -> {
        service.offer((V) offer.item());
        yield null;
      }
      case Take _ -> service.take();
    });
  }
}
