///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS com.github.tonivade:diesel:0.14

/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package examples;

import static com.github.tonivade.diesel.Program.failure;
import static com.github.tonivade.diesel.Program.success;
import static com.github.tonivade.diesel.Program.suspend;

import com.github.tonivade.diesel.Delay;
import com.github.tonivade.diesel.Program;
import com.github.tonivade.diesel.Schedule;
import com.github.tonivade.diesel.Random;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Demonstrates retrying a program using an exponential backoff schedule
 * with jitter.
 */
public class ScheduleRetry {

  public static void main(String... args) {
    var response = request().retry(schedule())
        .eval(new Random.Service() {})
        .getOrElseThrow();

    IO.println("Response: " + response);
  }

  private static Schedule<Random.Service, String> schedule() {
    return Schedule.<Random.Service, String>exponential(Delay.ofMillis(200), 2.0)
        .jittered(0.2, Random.nextDouble())
        .and(Schedule.recurs(3));
  }

  private static Program<Random.Service, String, String> request() {
    var attempts = new AtomicInteger();

    return suspend(() -> {
      int attempt = attempts.incrementAndGet();
      IO.println("Request attempt " + attempt);

      if (attempt < 4) {
        return failure("Service temporarily unavailable");
      }

      return success("Request completed");
    });
  }
}