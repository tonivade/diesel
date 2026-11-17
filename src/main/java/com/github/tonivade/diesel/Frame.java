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

  final class Region {}

  record TransformFrame<S>(
    Function<Result<?, ?>, Result<?, ?>> transform) implements Frame<S> {
  }

  // a null branch means that side isn't handled: the result goes on as it is, like a flatMap with a
  // failure or a flatMapError with a success, without wrapping it again in a new program
  record FoldFrame<S>(
    @Nullable Function<Object, Program<S, ?, ?>> onFailure,
    @Nullable Function<Object, Program<S, ?, ?>> onSuccess) implements Frame<S> {
  }

  record CatchFrame<S>(Function<Throwable, Program<S, ?, ?>> recover) implements Frame<S> {}

  record FinalizerFrame<S>(Function<Outcome<?, ?>, Program<S, Void, ?>> finalizer) implements Frame<S> {}

  // marks the end of an uncancelable region
  record UnmaskFrame<S>(Region region) implements Frame<S> {}

  record RemaskFrame<S>(Region region) implements Frame<S> {}

  static <S> TransformFrame<S> transform(Function<Result<?, ?>, Result<?, ?>> transform) {
    return new TransformFrame<>(transform);
  }

  static <S> FoldFrame<S> fold(
      @Nullable Function<Object, Program<S, ?, ?>> onFailure,
      @Nullable Function<Object, Program<S, ?, ?>> onSuccess) {
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
