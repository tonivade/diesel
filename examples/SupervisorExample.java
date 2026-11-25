///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS com.github.tonivade:diesel:${diesel.version:0.15-SNAPSHOT}
//REPOS mavenlocal,mavencentral

/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package examples;

import static com.github.tonivade.diesel.Combine.delayed;
import static com.github.tonivade.diesel.Combine.ensuring;
import static com.github.tonivade.diesel.Combine.pipe;
import static com.github.tonivade.diesel.Combine.traverse;
import static com.github.tonivade.diesel.Console.writeLine;
import static com.github.tonivade.diesel.Program.success;
import static com.github.tonivade.diesel.Program.suspend;

import com.github.tonivade.diesel.Console;
import com.github.tonivade.diesel.Delay;
import com.github.tonivade.diesel.Program;
import com.github.tonivade.diesel.Supervisor;

import java.util.Collection;
import java.util.List;

/**
 * Demonstrates background programs whose lifetime is tied to a {@link Supervisor} instead of to
 * the program that starts them.
 *
 * <p>A small server handles some requests. Each request answers right away and sends a
 * notification in the background, which keeps running after the request has finished. The server
 * also runs a heartbeat while it's up.
 *
 * <p>Two supervisors, two shutdown policies:
 * <ul>
 *   <li>The notifications are supervised with {@code make(true)}: on shutdown, the server waits
 *   until all of them have been sent.</li>
 *   <li>The heartbeat is supervised with {@code make()}: on shutdown, it's cancelled and runs its
 *   finalizer.</li>
 * </ul>
 */
public class SupervisorExample {

  public static void main(String... args) {
    Collection<String> responses = program().eval(new Console.Service() {}).getOrElseThrow();

    IO.println();
    IO.println("Responses: " + responses);
  }

  static Program<Console.Service, Void, Collection<String>> program() {
    return Supervisor.<Console.Service, Void>make().use(
        daemons -> Supervisor.<Console.Service, Void>make(true).use(
            notifications -> pipe(
                daemons.supervise(heartbeat()),
                _ -> traverse(request -> handle(notifications, request), List.of(1, 2, 3)),
                responses -> ensuring(
                    success(responses),
                    writeLine("Shutting down, waiting for the pending notifications")))));
  }

  // answers right away: the notification is sent in the background and outlives the request
  private static Program<Console.Service, Void, String> handle(Supervisor notifications, int request) {
    return pipe(
        writeLine("Handling request " + request),
        _ -> notifications.supervise(notify(request)),
        _ -> success("response " + request));
  }

  private static Program<Console.Service, Void, ?> notify(int request) {
    return delayed(
        Delay.ofMillis(200L * request),
        writeLine("Notification sent for request " + request));
  }

  // runs until the supervisor that started it is released
  private static Program<Console.Service, Void, ?> heartbeat() {
    return ensuring(beat(1), writeLine("Heartbeat stopped"));
  }

  private static Program<Console.Service, Void, ?> beat(int beat) {
    return pipe(
        delayed(Delay.ofMillis(150), writeLine("Heartbeat " + beat)),
        _ -> suspend(() -> beat(beat + 1)));
  }
}
