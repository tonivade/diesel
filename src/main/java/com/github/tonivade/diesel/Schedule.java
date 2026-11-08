package com.github.tonivade.diesel;

import static java.util.Objects.requireNonNull;

import com.github.tonivade.diesel.Schedule.Decision.Continue;
import com.github.tonivade.diesel.Schedule.Decision.Done;

import java.time.Duration;

@FunctionalInterface
public interface Schedule<I> {

  sealed interface Decision<I> {

    record Done<I>() implements Decision<I> {}

    record Continue<I>(Duration delay, Schedule<I> next) implements Decision<I> {

      public Continue {
        requireNonNull(delay);
        requireNonNull(next);
      }
    }
  }

  Decision<I> next(I input);

  static <I> Schedule<I> stop() {
    return _ -> new Done<>();
  }

  static <I> Schedule<I> forever() {
    return spaced(Duration.ZERO);
  }

  static <I> Schedule<I> spaced(Duration delay) {
    return _ -> new Continue<>(delay, spaced(delay));
  }

  static <I> Schedule<I> recurs(int n) {
    if (n <= 0) {
      return stop();
    }
    return _ -> new Continue<>(Duration.ZERO, recurs(n - 1));
  }

  /** Intersección: continúa solo si ambos continúan, con el mayor delay. */
  default Schedule<I> and(Schedule<I> other) {
    return input -> switch (next(input)) {
      case Done<I> done -> done;
      case Continue<I>(var d1, var n1) -> switch (other.next(input)) {
        case Done<I> done -> done;
        case Continue<I>(var d2, var n2) ->
            new Continue<>(d1.compareTo(d2) >= 0 ? d1 : d2, n1.and(n2));
      };
    };
  }
}
