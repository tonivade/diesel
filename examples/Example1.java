///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS com.github.tonivade:diesel:0.14-SNAPSHOT

/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package examples;

import static com.github.tonivade.diesel.Combine.pipe;
import static com.github.tonivade.diesel.Program.join;

import com.github.tonivade.diesel.Deferred;
import com.github.tonivade.diesel.Program;
import com.github.tonivade.diesel.Ref;
import com.github.tonivade.diesel.Source;

import java.util.List;

class Example1 {

  public static void main(String... args) {
    var summary = processNumbers().evalOrElseThrow();

    System.out.printf(
        "Source + Ref: %s (processed %d elements)%n",
        summary.values(), summary.processed());

    var message = awaitSignal().evalOrElseThrow();

    IO.println("Deferred: " + message);
  }

  private static Program<Void, Void, Summary> processNumbers() {
    return pipe(
      Ref.make(0),
      processed -> pipe(
        Source.<Void, Void, Integer>iterate(1, n -> n + 1)
            .filter(n -> n % 2 == 0)
            .map(n -> n * n)
            .take(5)
            .mapEval(value ->
                processed.updateAndGet(count -> count + 1).map(_ -> value))
            .toList(),
        values -> processed.get()
            .map(count -> new Summary(values, count))
      )
    );
  }

  private static Program<Void, Void, String> awaitSignal() {
    return pipe(
      Deferred.<Void, Void, String>make(),
      signal -> pipe(
        signal.get().fork(),
        waiter -> signal.complete("all systems go").andThen(join(waiter))
      )
    );
  }

  private record Summary(List<Integer> values, int processed) {}
}
