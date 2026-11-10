
///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS com.github.tonivade:diesel:0.14-SNAPSHOT

/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package examples;

import static com.github.tonivade.diesel.Program.failure;
import static com.github.tonivade.diesel.Program.success;
import static com.github.tonivade.diesel.Program.suspend;
import static com.github.tonivade.diesel.Program.task;

import com.github.tonivade.diesel.Delay;
import com.github.tonivade.diesel.Program;
import com.github.tonivade.diesel.Resource;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Demonstrates resource acquisition retries and fallback resources.
 */
public class RetryAndFallback {

  public static void main(String... args) {
    String connection = connect().evalOrElseThrow();

    IO.println("Connected using: " + connection);
  }

  static Program<Void, String, String> connect() {
    var attempts = new AtomicInteger();

    Resource<Void, String, Connection> primary =
        Resource.make(
            acquirePrimary(attempts),
            connection -> task(connection::close));

    Resource<Void, String, Connection> fallback =
        Resource.make(
            acquireFallback(),
            connection -> task(connection::close));

    return primary
        .retry(2, Delay.ofMillis(200))
        .orElse(fallback)
        .use(connection -> success(connection.name()));
  }

  private static Program<Void, String, Connection> acquirePrimary(AtomicInteger attempts) {
    return suspend(() -> {
      int attempt = attempts.incrementAndGet();
      IO.println("Trying primary connection (attempt " + attempt + ")");
      return failure("Primary connection unavailable");
    });
  }

  private static Program<Void, String, Connection> acquireFallback() {
    return suspend(() -> {
      IO.println("Acquiring fallback connection");
      return success(new Connection("fallback"));
    });
  }

  private record Connection(String name) {

    void close() {
      IO.println("Closing " + name + " connection");
    }
  }
}
