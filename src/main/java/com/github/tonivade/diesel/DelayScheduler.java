/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Scheduler of the delays of {@link Program#sleep(Delay)}.
 *
 * <p>Unlike {@link java.util.concurrent.CompletableFuture#delayedExecutor}, it returns a handle to
 * cancel a delay, and a cancelled delay is removed right away, so a cancelled sleep doesn't stay
 * scheduled until its delay expires.
 */
final class DelayScheduler {

  private static final ScheduledThreadPoolExecutor SCHEDULER = create();

  private DelayScheduler() {}

  static ScheduledFuture<?> schedule(Delay delay, Runnable action) {
    return SCHEDULER.schedule(action, delay.nanos(), TimeUnit.NANOSECONDS);
  }

  /**
   * Number of delays that expire in the given delay or later, used by the tests: they use long
   * delays, which no other test does, so they only see their own delays.
   */
  static long pendingLongerThan(Delay min) {
    return SCHEDULER.getQueue().stream()
        .filter(Delayed.class::isInstance)
        .map(Delayed.class::cast)
        .filter(delay -> delay.getDelay(TimeUnit.NANOSECONDS) >= min.nanos())
        .count();
  }

  private static ScheduledThreadPoolExecutor create() {
    // a daemon thread, like the scheduler of delayedExecutor, that doesn't inherit anything from
    // whichever thread creates it: it lives as long as the JVM
    var factory = Thread.ofPlatform()
        .daemon()
        .name("diesel-delay-scheduler")
        .inheritInheritableThreadLocals(false)
        .factory();
    var scheduler = new ScheduledThreadPoolExecutor(1, runnable -> {
      var thread = factory.newThread(runnable);
      thread.setContextClassLoader(DelayScheduler.class.getClassLoader());
      return thread;
    });
    scheduler.setRemoveOnCancelPolicy(true);
    return scheduler;
  }
}
