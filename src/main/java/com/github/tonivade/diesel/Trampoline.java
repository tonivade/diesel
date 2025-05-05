/*
 * Copyright (c) 2024-2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static java.util.function.Function.identity;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

public sealed interface Trampoline<T> {

  record Done<T>(T value) implements Trampoline<T> {}
  record More<T>(Supplier<Trampoline<T>> next) implements Trampoline<T> {}

  static <T> Trampoline<T> done(T value) {
    return new Done<>(value);
  }

  static <T> Trampoline<T> more(Supplier<Trampoline<T>> next) {
    return new More<>(next);
  }

  default <R> Trampoline<R> map(Function<T, R> mapper) {
    return flatMap(mapper.andThen(Trampoline::done));
  }

  default <R> Trampoline<R> flatMap(Function<T, Trampoline<R>> mapper) {
    return fold(next -> more(() -> next.flatMap(mapper)), mapper);
  }

  default <R> Trampoline<R> andThen(Trampoline<R> next) {
    return flatMap(__ -> next);
  }

  default <R> R fold(Function<Trampoline<T>, R> moreMapper, Function<T, R> doneMapper) {
    return switch (this) {
      case Done<T>(var value) -> doneMapper.apply(value);
      case More<T>(var next) -> moreMapper.apply(next.get());
    };
  }

  default T run() {
    return iterate().fold(__ -> {
      throw new IllegalStateException();
    }, identity());
  }

  private Trampoline<T> iterate() {
    return Stream.iterate(this, t -> t.fold(identity(), __ -> t))
        .dropWhile(t -> t instanceof More).findFirst().orElseThrow();
  }

  static <A, B, R> Trampoline<R> map2(Trampoline<A> ta, Trampoline<B> tb, BiFunction<A, B, R> mapper) {
    return ta.flatMap(a -> tb.map(b -> mapper.apply(a, b)));
  }
}