/*
 * Copyright (c) 2025, Antonio Gabriel Muñoz Conejo <me at tonivade es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import java.util.ArrayDeque;
import java.util.Deque;
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
  record More<T>(Supplier<? extends Trampoline<T>> next) implements Trampoline<T> {}

  /**
   * A FlatMap Trampoline represents a computation that continues with another
   * computation based on the result of the current computation.
   *
   * @param <T> the type of the result of the current computation
   * @param <R> the type of the result of the next computation
   */
  record FlatMap<T, R>(Trampoline<T> current, Function<? super T, ? extends Trampoline<R>> mapper) implements Trampoline<R> {}

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
  static <T> Trampoline<T> more(Supplier<? extends Trampoline<T>> next) {
    return new More<>(next);
  }

  /**
   * Maps the result of this computation using a given function.
   *
   * @param <R> the type of the result of the function
   * @param mapper the function to apply to the result
   * @return a new Trampoline with the mapped result
   */
  default <R> Trampoline<R> map(Function<? super T, ? extends R> mapper) {
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
  default <R> Trampoline<R> flatMap(Function<? super T, ? extends Trampoline<R>> mapper) {
    return new FlatMap<>(this, mapper);
  }

  /**
   * Runs the computation and returns the final result.
   *
   * This implementation is stack-safe (no Java stack growth) because it uses
   * an explicit loop and an explicit Deque for continuations instead of
   * recursive calls or per-step lambda chaining. It also reduces heap
   * allocations compared to the previous chained-lambda approach by
   * keeping continuations in an ArrayDeque (which grows its internal array,
   * avoiding one-object-per-step allocations).
   *
   * Note: heap usage is still O(n) in the number of pending flatMap
   * continuations: the Deque stores references to the mappers. This is an
   * improvement in allocation churn but not in asymptotic retained memory.
   *
   * @return the final result of the computation
   */
  @SuppressWarnings("unchecked")
  default T run() {
    Trampoline<?> current = this;
    Deque<Function<Object, Trampoline<?>>> stack = new ArrayDeque<>();

    while (true) {
      if (current instanceof Done<?> done) {
        var value = done.value();

        if (stack.isEmpty()) {
          return (T) value; // end of program
        }

        Function<Object, Trampoline<?>> k = stack.pop();
        current = k.apply(value);
      } else if (current instanceof More<?> more) {
        current = more.next().get();
      } else if (current instanceof FlatMap<?, ?> flatMap) {
        Trampoline<Object> source = (Trampoline<Object>) flatMap.current();
        Function<Object, Trampoline<?>> nextFn = (Function<Object, Trampoline<?>>) flatMap.mapper();

        // Push the mapper and continue with the source. Using an explicit
        // stack avoids allocating a new closure for each chained flatMap step.
        stack.push(nextFn);
        current = source;
      }
    }
  }
}