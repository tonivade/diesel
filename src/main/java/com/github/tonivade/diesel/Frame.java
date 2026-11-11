/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import java.util.function.Function;

/**
 * Continuation frames used by {@link Program#eval(Object)}.
 */
sealed interface Frame<S> {

  final class Region {}

  record FoldFrame<S>(
    Function<Object, Program<S, ?, ?>> onFailure,
    Function<Object, Program<S, ?, ?>> onSuccess) implements Frame<S> {
  }

  record CatchFrame<S>(Function<Throwable, Program<S, ?, ?>> recover) implements Frame<S> {}

  record FinalizerFrame<S>(Function<Outcome<?, ?>, Program<S, Void, ?>> finalizer) implements Frame<S> {}

  // marks the end of an uncancelable region
  record UnmaskFrame<S>(Region region) implements Frame<S> {}

  record RemaskFrame<S>(Region region) implements Frame<S> {}

  static <S> FoldFrame<S> fold(
      Function<Object, Program<S, ?, ?>> onFailure,
      Function<Object, Program<S, ?, ?>> onSuccess) {
    return new FoldFrame<>(onFailure, onSuccess);
  }

  static <S> CatchFrame<S> catch_(Function<Throwable, Program<S, ?, ?>> recover) {
    return new CatchFrame<>(recover);
  }

  static <S> FinalizerFrame<S> finalizer(Function<Outcome<?, ?>, Program<S, Void, ?>> finalizer) {
    return new FinalizerFrame<>(finalizer);
  }

  static <S> UnmaskFrame<S> unmask(Region region) {
    return new UnmaskFrame<>(region);
  }

  static <S> RemaskFrame<S> remask(Region region) {
    return new RemaskFrame<>(region);
  }
}
