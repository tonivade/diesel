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

  record FoldFrame<S>(
    Function<Object, Program<S, ?, ?>> onFailure,
    Function<Object, Program<S, ?, ?>> onSuccess) implements Frame<S> {
  }

  record CatchFrame<S>(Function<Throwable, Program<S, ?, ?>> recover) implements Frame<S> {}

  record FinalizerFrame<S>(Program<S, ?, ?> finalizer) implements Frame<S> {}

  // marks the end of an uncancelable region
  record UnmaskFrame<S>() implements Frame<S> {}

  @SuppressWarnings("unchecked")
  static <S> FoldFrame<S> fold(Function<?, ? extends Program<S, ?, ?>> onFailure,
      Function<?, ? extends Program<S, ?, ?>> onSuccess) {
    return new FoldFrame<>(
        (Function<Object, Program<S, ?, ?>>) onFailure,
        (Function<Object, Program<S, ?, ?>>) onSuccess);
  }

  @SuppressWarnings("unchecked")
  static <S> CatchFrame<S> catch_(Function<? super Throwable, ? extends Program<S, ?, ?>> recover) {
    return new CatchFrame<>((Function<Throwable, Program<S, ?, ?>>) recover);
  }

  static <S> FinalizerFrame<S> finalizer(Program<S, ?, ?> finalizer) {
    return new FinalizerFrame<>(finalizer);
  }

  static <S> UnmaskFrame<S> unmask() {
    return new UnmaskFrame<>();
  }
}
