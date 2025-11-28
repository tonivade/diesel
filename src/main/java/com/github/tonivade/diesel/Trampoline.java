/*
 * Copyright (c) 2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A Trampoline is a data structure used to implement tail recursion in Java.
 * It represents a computation that may yield a result or continue with another
 * computation. This allows for efficient and stack-safe recursive computations.
 *
 * @param <T> the type of the result of the computation
 */
sealed interface Trampoline<T> {

  /**
   * A Done Trampoline represents a successful computation with a given result.
   *
   * @param <T> the type of the result
   */
  record Done<T>(T value) implements Trampoline<T> {}

  /**
   * A More Trampoline represents a computation that continues with another
   * computation based on its result.
   *
   * @param <T> the type of the result of this computation
   */
  record More<T>(Supplier<Trampoline<T>> next) implements Trampoline<T> {}

  /**
   * Creates a Done Trampoline with a given value.
   *
   * @param <T> the type of the value
   * @param value the value of the computation
   * @return a Done Trampoline with the given value
   */
  static <T> Trampoline<T> done(T value) {
    return new Done<>(value);
  }

  /**
   * Creates a Trampoline that continues with a supplier of another Trampoline.
   *
   * @param <T> the type of the result of the computation
   * @param next a supplier of another Trampoline
   * @return a Trampoline that continues with the supplier of another Trampoline
   */
  static <T> Trampoline<T> more(Supplier<Trampoline<T>> next) {
    return new More<>(next);
  }

  /**
   * Maps the result of this computation using a given function.
   *
   * @param <R> the type of the result of the function
   * @param mapper the function to apply to the result
   * @return a new Trampoline with the mapped result
   */
  default <R> Trampoline<R> map(Function<T, R> mapper) {
    return flatMap(mapper.andThen(Trampoline::done));
  }

  /**
   * Chains two Trampolines together, discarding the result of the first one.
   *
   * @param <R> the type of the result of the next Trampoline
   * @param next the next Trampoline
   * @return a new Trampoline that chains the two computations together
   */
  default <R> Trampoline<R> andThen(Trampoline<R> next) {
    return flatMap(_ -> next);
  }

  /**
   * Flat maps the result of this computation using a given function.
   *
   * @param <R> the type of the result of the function
   * @param mapper the function to apply to the result
   * @return a new Trampoline with the flat mapped result
   */
  default <R> Trampoline<R> flatMap(Function<T, Trampoline<R>> mapper) {
    return more(() -> step(mapper));
  }

  /**
   * Runs the computation and returns the final result.
   *
   * @return the final result of the computation
   */
  default T run() {
    var current = this;

    while (current instanceof More<T> more) {
      current = more.next.get();
    }

    return ((Done<T>) current).value();
  }

  private <R> Trampoline<R> step(Function<T, Trampoline<R>> mapper) {
    var current = this;

    while (true) {
      if (current instanceof Done<T> done) {
        return mapper.apply(done.value);
      }

      if (current instanceof More<T> more) {
        current = more.next.get();
      }
    }
  }
}