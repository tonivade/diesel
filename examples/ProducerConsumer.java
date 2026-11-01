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
import static com.github.tonivade.diesel.Tuple2.tuple;
import static com.github.tonivade.diesel.Unit.unit;

import com.github.tonivade.diesel.Deferred;
import com.github.tonivade.diesel.Program;
import com.github.tonivade.diesel.Queue;
import com.github.tonivade.diesel.Ref;
import com.github.tonivade.diesel.Unit;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class ProducerConsumer {

  public static void main(String[] args) {
    int total = program().evalOrElseThrow();

    System.out.println();
    System.out.println("================================");
    System.out.println("Processed messages: " + total);
    System.out.println("================================");

    if (total != 10) {
      throw new AssertionError(
          "Expected 10 processed messages, but got " + total
      );
    }
  }

  static Program<Void, Void, Integer> program() {
    return pipe(
        Queue.<Void, Void, Message>makeBounded(3),
        queue -> pipe(
            Ref.make(0),
            processed -> pipe(
                parAll(
                    pipe(
                        parAll(
                            produce(queue, List.of(1, 2, 3, 4, 5)),
                            produce(queue, List.of(6, 7, 8, 9, 10))
                        ),
                        _ -> queue.offer(Message.stop()).repeat(3)
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

  record Message(int value, boolean poison) {

    static Message data(int value) {
      return new Message(value, false);
    }

    static Message stop() {
      return new Message(0, true);
    }
  }

  static Program<Void, Void, Unit> produce(
      Queue<Void, Void, Message> queue,
      List<Integer> values
  ) {
    return traverse(
        value -> queue.offer(Message.data(value)),
        values
    ).discard();
  }

  static Program<Void, Void, Unit> consume(
      int id,
      Queue<Void, Void, Message> queue,
      Ref<Void, Void, Integer> processed
  ) {
    return suspend(() ->
        pipe(
            queue.take(),
            message -> {
              if (message.poison()) {
                return task(() -> System.out.printf("Consumer %d stopped%n", id));
              }

              return pipe(
                  task(() ->
                      System.out.printf(
                          "Consumer %d processed %d%n",
                          id,
                          message.value()
                      )
                  ),
                  _ -> processed.updateAndGet(n -> n + 1),
                  _ -> suspend(() -> consume(id, queue, processed))
              );
            }
        )
    );
  }
}