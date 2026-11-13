///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS com.github.tonivade:diesel:0.14

/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package examples;

import static com.github.tonivade.diesel.Combine.delayed;
import static com.github.tonivade.diesel.Concurrent.parZip;
import static com.github.tonivade.diesel.Program.supply;

import com.github.tonivade.diesel.Delay;
import com.github.tonivade.diesel.Program;

public class ParallelRequests {

  public static void main(String... args) {
    var dashboard = loadDashboard().evalOrElseThrow();

    IO.println();
    IO.println("Dashboard:");
    IO.println("  User: " + dashboard.profile().name());
    IO.println("  Orders: " + dashboard.orders().size());
    IO.println("  Preferences: " + dashboard.preferences());
  }

  /**
   * Loads independent pieces of data concurrently and combines them
   * into a single dashboard.
   */
  static Program<Void, Throwable, Dashboard> loadDashboard() {
    return parZip(
        loadProfile(),
        loadOrders(),
        loadPreferences(),
        Dashboard::new
    );
  }

  private static Program<Void, Throwable, Profile> loadProfile() {
    return delayed(
        Delay.ofMillis(500),
        supply(() -> {
          IO.println("Profile loaded");
          return new Profile(1, "Alice");
        }));
  }

  private static Program<Void, Throwable, Orders> loadOrders() {
    return delayed(
        Delay.ofMillis(1000),
        supply(() -> {
          IO.println("Orders loaded");
          return new Orders(3);
        }));
  }

  private static Program<Void, Throwable, Preferences> loadPreferences() {
    return delayed(
        Delay.ofMillis(700),
        supply(() -> {
          IO.println("Preferences loaded");
          return new Preferences("dark", "en");
        }));
  }

  private record Profile(int id, String name) {}

  private record Orders(int size) {}

  private record Preferences(String theme, String language) {}

  private record Dashboard(
      Profile profile,
      Orders orders,
      Preferences preferences
  ) {}
}
