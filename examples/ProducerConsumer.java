///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS com.github.tonivade:diesel:0.14-SNAPSHOT

/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package examples;

import static com.github.tonivade.diesel.Combine.pipe;
import static com.github.tonivade.diesel.Combine.traverse;
import static com.github.tonivade.diesel.Concurrent.parAll;
import static com.github.tonivade.diesel.Program.success;
import static com.github.tonivade.diesel.Program.suspend;
import static com.github.tonivade.diesel.Program.task;
import static com.github.tonivade.diesel.Program.unit;
import static com.github.tonivade.diesel.Tuple2.tuple;
import static com.github.tonivade.diesel.impl.Console.writeLine;

import com.github.tonivade.diesel.Deferred;
import com.github.tonivade.diesel.Program;
import com.github.tonivade.diesel.Queue;
import com.github.tonivade.diesel.Ref;
import com.github.tonivade.diesel.Unit;
import com.github.tonivade.diesel.impl.Console;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class ProducerConsumer {

  public static void main(String[] args) {
    int total = program().eval(new Console.Service() {}).getOrElseThrow();

    IO.println();
    IO.println("================================");
    IO.println("Processed messages: " + total);
    IO.println("================================");

    if (total != 10) {
      throw new AssertionError(
          "Expected 10 processed messages, but got " + total
      );
    }
  }

  static Program<Console.Service, Void, Integer> program() {
    return pipe(
        Queue.<Console.Service, Void, Integer>makeBounded(3),
        queue -> pipe(
            Ref.make(0),
            processed -> pipe(
                parAll(
                    pipe(
                        parAll(
                            produce(queue, List.of(1, 2, 3, 4, 5)),
                            produce(queue, List.of(6, 7, 8, 9, 10))
                        ),
                        _ -> queue.close()
                    ),
                    consume(1, queue, processed),
                    consume(2, queue, processed),
                    consume(3, queue, processed)
                ),
                _ -> processed.get()
            )
        )
    );
  }

  static Program<Console.Service, Void, Unit> produce(Queue<Integer> queue, List<Integer> values) {
    return pipe(
        traverse(queue::offer, values),
        _ -> unit()
    );
  }

  static Program<Console.Service, Void, Unit> consume(int id, Queue<Integer> queue, Ref<Integer> processed) {
    return suspend(() ->
        pipe(
            queue.takeOrClosed(),
            message -> {
              if (message.isEmpty()) {
                return writeLine("Consumer %d stopped", id);
              }

              return pipe(
                  writeLine("Consumer %d processed %d", id, message.get()),
                  _ -> processed.updateAndGet(n -> n + 1),
                  _ -> suspend(() -> consume(id, queue, processed))
              );
            }
        )
    );
  }
}