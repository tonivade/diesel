/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import java.util.function.Function;

/**
 * Continuation frames used by the {@link Interpreter}.
 */
sealed interface Frame<S> {

  record FoldFrame<S>(
    Function<Object, Program<S, ?, ?>> onFailure,
    Function<Object, Program<S, ?, ?>> onSuccess) implements Frame<S> {
  }

  record CatchFrame<S>(Function<Throwable, Program<S, ?, ?>> recover) implements Frame<S> {
  }

  record FinalizerFrame<S>(Program<S, ?, ?> finalizer) implements Frame<S> {
  }

  // marks the end of an uncancelable region
  record UnmaskFrame<S>() implements Frame<S> {
  }
}
