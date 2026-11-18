/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import java.util.function.Function;

import org.jspecify.annotations.Nullable;

/**
 * Continuation frames used by {@link Interpreter#run(Program, Object, CancelToken)}.
 */
sealed interface Frame<S> {

  sealed interface Region {}

  non-sealed interface TransformFrame<S> extends Frame<S> {
    Function<Result<?, ?>, Result<?, ?>> frameTransform();
  }

  // a null branch means that side isn't handled: the result goes on as it is, like a flatMap with a
  // failure or a flatMapError with a success, without wrapping it again in a new program
  non-sealed interface FoldFrame<S> extends Frame<S> {
    @Nullable Function<Object, Program<S, ?, ?>> frameOnFailure();
    @Nullable Function<Object, Program<S, ?, ?>> frameOnSuccess();
  }

  non-sealed interface CatchFrame<S> extends Frame<S> {
    Function<Throwable, Program<S, ?, ?>> frameRecover();
  }

  non-sealed interface FinalizerFrame<S> extends Frame<S> {
    Function<Outcome<?, ?>, Program<S, Void, ?>> frameFinalizer();
  }

  // marks the end of an uncancelable region
  final class UnmaskFrame<S> implements Frame<S>, Region {}

  non-sealed interface RemaskFrame<S> extends Frame<S> {
    Region region();
  }
}
