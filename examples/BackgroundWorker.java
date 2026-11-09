///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS com.github.tonivade:diesel:0.14-SNAPSHOT

/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package examples;

import static com.github.tonivade.diesel.Combine.pipe;
import static com.github.tonivade.diesel.Combine.traverse;
import static com.github.tonivade.diesel.Program.suspend;
import static com.github.tonivade.diesel.Console.writeLine;

import com.github.tonivade.diesel.Program;
import com.github.tonivade.diesel.Queue;
import com.github.tonivade.diesel.Ref;
import com.github.tonivade.diesel.Resource;
import com.github.tonivade.diesel.Console;

import java.util.List;
import java.util.Optional;

/**
 * Demonstrates a background worker managed as a resource.
 *
 * <p>The worker processes messages from a bounded queue and is cancelled
 * automatically when its resource scope finishes.
 */
public class BackgroundWorker {

  public static void main(String... args) {
    int processed = program().eval(new Console.Service() {}).getOrElseThrow();

    IO.println();
    IO.println("Processed messages: " + processed);
  }

  static Program<Console.Service, Void, Integer> program() {
    return Ref.<Console.Service, Void, Integer>make(0)
        .flatMap(processed ->
            Resource.<Console.Service, Void, Queue<Integer>>make(
                Queue.makeBounded(3),
                Queue::close
            ).use(queue ->
                Resource.background(consume(queue, processed))
                    .use(worker ->
                        pipe(
                            traverse(queue::offer, List.of(1, 2, 3, 4, 5)),
                            _ -> queue.close(),
                            _ -> worker,
                            _ -> processed.get()
                        )
                    )
            )
        );
  }

  private static Program<Console.Service, Void, ?> consume(
      Queue<Integer> queue,
      Ref<Integer> processed
  ) {
    return suspend(() ->
        pipe(
            queue.takeOrClosed(),
            message -> process(message, queue, processed)
        )
    );
  }

  private static Program<Console.Service, Void, ?> process(
      Optional<Integer> message,
      Queue<Integer> queue,
      Ref<Integer> processed
  ) {
    if (message.isEmpty()) {
      return writeLine("Worker stopped");
    }

    int value = message.get();

    return pipe(
        writeLine("Processing: " + value),
        _ -> processed.updateAndGet(count -> count + 1),
        _ -> consume(queue, processed)
    );
  }
}
