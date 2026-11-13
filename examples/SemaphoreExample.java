///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS com.github.tonivade:diesel:0.14-SNAPSHOT

/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package examples;

import static com.github.tonivade.diesel.Combine.pipe;
import static com.github.tonivade.diesel.Concurrent.parAll;
import static com.github.tonivade.diesel.Program.sleep;
import static com.github.tonivade.diesel.Program.task;

import com.github.tonivade.diesel.Delay;
import com.github.tonivade.diesel.Program;
import com.github.tonivade.diesel.Semaphore;

import java.util.List;

/**
 * Demonstrates limiting parallel work with a semaphore.
 *
 * <p>All jobs are started in parallel, but only two can execute their
 * critical section at the same time. The permit is released automatically
 * when the job completes, fails, or is cancelled.
 */
public class SemaphoreExample {

  private static final int MAX_CONCURRENT = 2;

  public static void main(String... args) {
    program().evalOrElseThrow();

    IO.println();
    IO.println("All jobs completed");
  }

  static Program<Void, Throwable, ?> program() {
    return pipe(
        Semaphore.<Void, Throwable>make(MAX_CONCURRENT),
        semaphore -> parAll(
            List.of(1, 2, 3, 4, 5, 6).stream()
                .map(id -> process(semaphore, id))
                .toList()
        )
    );
  }

  private static Program<Void, Throwable, ?> process(Semaphore semaphore, int id) {
    return semaphore.withPermit(
        pipe(
            task(() -> IO.println("Starting job " + id)),
            _ -> sleep(Delay.ofMillis(750)),
            _ -> task(() -> IO.println("Finished job " + id))
        )
    );
  }
}
