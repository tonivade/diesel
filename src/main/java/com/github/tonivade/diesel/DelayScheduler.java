/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Scheduler of the delays of {@link Program#sleep(Duration, java.util.concurrent.Executor)}.
 *
 * <p>It only waits for the delays: the programs continue on their own executor. Unlike
 * {@link java.util.concurrent.CompletableFuture#delayedExecutor}, it returns a handle to cancel a
 * delay, and a cancelled delay is removed right away, so a cancelled sleep doesn't stay scheduled
 * until its delay expires.
 */
final class DelayScheduler {

  private static final ScheduledThreadPoolExecutor SCHEDULER = create();

  private DelayScheduler() {}

  static ScheduledFuture<?> schedule(Duration delay, Runnable action) {
    return SCHEDULER.schedule(action, delay.toNanos(), TimeUnit.NANOSECONDS);
  }

  /**
   * Number of delays waiting to expire, used by the tests.
   */
  static int pending() {
    return SCHEDULER.getQueue().size();
  }

  private static ScheduledThreadPoolExecutor create() {
    var scheduler = new ScheduledThreadPoolExecutor(1, runnable -> {
      var thread = new Thread(runnable, "diesel-delay-scheduler");
      // like the scheduler of delayedExecutor, it must not keep the JVM alive
      thread.setDaemon(true);
      return thread;
    });
    scheduler.setRemoveOnCancelPolicy(true);
    return scheduler;
  }
}
