/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Combine.chain;
import static com.github.tonivade.diesel.Program.success;
import static java.util.Objects.requireNonNull;

import com.github.tonivade.diesel.Schedule.Decision.Continue;
import com.github.tonivade.diesel.Schedule.Decision.Done;
import com.github.tonivade.diesel.impl.Random;

import java.time.Duration;

@FunctionalInterface
public interface Schedule<S, I> {

  sealed interface Decision<S, I> {

    record Done<S, I>() implements Decision<S, I> {}

    record Continue<S, I>(Duration delay, Schedule<S, I> next) implements Decision<S, I> {

      public Continue {
        requireNonNull(delay);
        requireNonNull(next);
      }
    }
  }

  Program<S, Void, Decision<S, I>> next(I input);

  static <S, I> Schedule<S, I> stop() {
    return _ -> success(new Done<>());
  }

  static <S, I> Schedule<S, I> forever() {
    return spaced(Duration.ZERO);
  }

  static <S, I> Schedule<S, I> spaced(Duration delay) {
    return _ -> success(new Continue<>(delay, spaced(delay)));
  }

  static <S, I> Schedule<S, I> exponential(Duration base, double factor) {
    return _ -> success(new Continue<>(base, exponential(scale(base, factor), factor)));
  }

  static <S extends Random.Service, I> Schedule<S, I> jittered(Schedule<S, I> schedule, double factor) {
    return input -> schedule.next(input).flatMap(decision -> switch (decision) {
      case Done<S, I> done -> success(done);
      case Continue<S, I>(var delay, var next) ->
        chain(
            Random.nextDouble(),
            random -> {
              var multiplier = 1 + (random * 2 - 1) * factor;
              return new Continue<>(
                  scale(delay, multiplier),
                  jittered(next, factor));
            });
    });
  }

  static <S, I> Schedule<S, I> recurs(int n) {
    if (n <= 0) {
      return stop();
    }
    return _ -> success(new Continue<>(Duration.ZERO, recurs(n - 1)));
  }

  default Schedule<S, I> and(Schedule<S, I> other) {
    return input -> next(input).flatMap(decision1 -> switch (decision1) {
      case Done<S, I> done -> success(done);
      case Continue<S, I>(var d1, var n1) ->
        other.next(input).flatMap(decision2 -> switch (decision2) {
          case Done<S, I> done -> success(done);
          case Continue<S, I>(var d2, var n2) ->
              success(new Continue<>(d1.compareTo(d2) >= 0 ? d1 : d2, n1.and(n2)));
        });
    });
  }

  private static Duration scale(Duration d, double f) {
    return Duration.ofNanos(Math.round(d.toNanos() * f));
  }
}
