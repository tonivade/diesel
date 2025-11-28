/*
 * Copyright (c) 2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import java.util.function.Function;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

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
   * A FlatMap Trampoline represents a computation that continues with another
   * computation based on the result of the current computation.
   *
   * @param <T> the type of the result of the current computation
   * @param <R> the type of the result of the next computation
   */
  record FlatMap<T, R>(Trampoline<T> current, Function<T, Trampoline<R>> mapper) implements Trampoline<R> {}

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
    return new FlatMap<>(this, mapper);
  }

  /**
   * Runs the computation and returns the final result.
   *
   * @return the final result of the computation
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  default T run() {
    Trampoline<?> current = this;
    Function<Object, Trampoline<?>> continuation = null;

    while (true) {
      if (current instanceof Done<?> done) {
        Object value = done.value();

        if (continuation == null) {
          return (T) value;  // end of program
        }

        Function<Object, Trampoline<?>> k = continuation;
        continuation = null; // clear before applying
        current = k.apply(value);
      } else if (current instanceof More<?> more) {
        current = more.next().get();
      } else if (current instanceof FlatMap<?, ?> flatMap) {
        Trampoline<Object> source = (Trampoline<Object>) flatMap.current();
        Function<Object, Trampoline<?>> nextFn = (Function) flatMap.mapper();

        if (source instanceof FlatMap<?, ?> sourceFlatMap) {
          // Reassociate:
          // FlatMap(FlatMap(x, f), g)
          // becomes
          // FlatMap(x, v -> FlatMap(f(v), g))
          Trampoline<Object> inner = (Trampoline<Object>) sourceFlatMap.current();
          Function<Object, Trampoline<?>> innerFn = (Function) sourceFlatMap.mapper();

          Function<Object, Trampoline<?>> merged = v -> {
            Trampoline t = innerFn.apply(v);
            return t.flatMap(nextFn);
          };

          current = inner;
          continuation = chain(merged, continuation);
        } else {
          current = source;
          continuation = chain(nextFn, continuation);
        }
      }
    }
  }

  @SuppressWarnings({ "rawtypes", "unchecked" })
  private static Function<Object, Trampoline<?>> chain(
      Function<Object, Trampoline<?>> newK, @Nullable Function<Object, Trampoline<?>> existing) {
    if (existing == null) {
      return newK;
    }

    return value -> {
      Trampoline<?> next = newK.apply(value);
      return new FlatMap(next, existing);
    };
  }
}