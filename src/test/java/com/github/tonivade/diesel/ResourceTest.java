/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Combine.pipe;
import static com.github.tonivade.diesel.Program.failure;
import static com.github.tonivade.diesel.Program.inspect;
import static com.github.tonivade.diesel.Program.never;
import static com.github.tonivade.diesel.Program.raise;
import static com.github.tonivade.diesel.Program.success;
import static com.github.tonivade.diesel.Program.supply;
import static com.github.tonivade.diesel.Program.suspend;
import static com.github.tonivade.diesel.Program.task;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class ResourceTest {

  private final List<String> log = new CopyOnWriteArrayList<>();

  // ---------------------------------------------------------------------------------------------
  // use
  // ---------------------------------------------------------------------------------------------

  @Test
  void useAcquiresUsesAndReleases() {
    var result = resource("a").use(this::use).eval();

    assertThat(result).isEqualTo(Result.success("a"));
    assertThat(log).containsExactly("acquire a", "use a", "release a");
  }

  @Test
  void releasesWhenUseFails() {
    var result = resource("a").use(_ -> failure("boom")).eval();

    assertThat(result).isEqualTo(Result.failure("boom"));
    assertThat(log).containsExactly("acquire a", "release a");
  }

  @Test
  void releasesWhenUseThrows() {
    var program = resource("a").use(_ -> raise(IllegalStateException::new));

    assertThatThrownBy(program::eval).isInstanceOf(IllegalStateException.class);
    assertThat(log).containsExactly("acquire a", "release a");
  }

  @Test
  void doesNotReleaseWhenAcquireFails() {
    var result = Resource.<Void, String, String>of(failure("boom")).use(this::use).eval();

    assertThat(result).isEqualTo(Result.failure("boom"));
    assertThat(log).isEmpty();
  }

  @Test
  void closesAnAutoCloseable() {
    AutoCloseable closeable = () -> log.add("close");

    Resource.<Void, String, AutoCloseable>from(success(closeable))
        .use(_ -> task(() -> log.add("use")))
        .eval();

    assertThat(log).containsExactly("use", "close");
  }

  @Test
  void canBeUsedMoreThanOnce() {
    var resource = resource("a");

    resource.use(this::use).eval();
    resource.use(this::use).eval();

    assertThat(log).containsExactly(
        "acquire a", "use a", "release a",
        "acquire a", "use a", "release a");
  }

  @Test
  void releasesWhenUseIsCancelled() {
    var started = new CompletableFuture<Void>();
    var program = resource("a").use(_ -> {
      started.complete(null);
      return never();
    });

    var future = program.fork().evalOrElseThrow();
    started.join();

    assertThat(future.cancel(true)).isTrue();
    assertThatThrownBy(future::join)
        .isInstanceOf(CancellationException.class);
    assertThat(log)
        .containsExactly("acquire a", "release a");
  }

  // ---------------------------------------------------------------------------------------------
  // composition
  // ---------------------------------------------------------------------------------------------

  @Test
  void mapKeepsTheRelease() {
    var result = resource("a").map(String::toUpperCase).use(this::use).eval();

    assertThat(result).isEqualTo(Result.success("A"));
    assertThat(log).containsExactly("acquire a", "use A", "release a");
  }

  @Test
  void flatMapReleasesInReverseOrder() {
    var result = resource("a")
        .flatMap(a -> resource(a + "b"))
        .flatMap(b -> resource(b + "c"))
        .use(this::use)
        .eval();

    assertThat(result).isEqualTo(Result.success("abc"));
    assertThat(log).containsExactly(
        "acquire a", "acquire ab", "acquire abc",
        "use abc",
        "release abc", "release ab", "release a");
  }

  @Test
  void flatMapReleasesTheFirstWhenTheSecondFails() {
    var result = resource("a")
        .flatMap(_ -> Resource.<Void, String, String>of(failure("boom")))
        .use(this::use)
        .eval();

    assertThat(result).isEqualTo(Result.failure("boom"));
    assertThat(log).containsExactly("acquire a", "release a");
  }

  @Test
  void flatMapReleasesTheFirstWhenTheSecondThrows() {
    var program = resource("a")
        .flatMap(_ -> Resource.<Void, String, String>of(raise(IllegalStateException::new)))
        .use(this::use);

    assertThatThrownBy(program::eval).isInstanceOf(IllegalStateException.class);
    assertThat(log).containsExactly("acquire a", "release a");
  }

  @Test
  void flatMapReleasesTheFirstWhenTheMapperThrows() {
    var program = resource("a")
        .<String>flatMap(_ -> {
          throw new IllegalStateException();
        })
        .use(this::use);

    assertThatThrownBy(program::eval).isInstanceOf(IllegalStateException.class);
    assertThat(log).containsExactly("acquire a", "release a");
  }

  @Test
  void combineKeepsBothOpenWhileUsed() {
    var result = resource("a").combine(resource("b"))
        .use(t -> use(t.first() + t.second()))
        .eval();

    assertThat(result).isEqualTo(Result.success("ab"));
    assertThat(log).containsExactly("acquire a", "acquire b", "use ab", "release b", "release a");
  }

  // ---------------------------------------------------------------------------------------------
  // errors
  // ---------------------------------------------------------------------------------------------

  @Test
  void mapErrorKeepsTheRelease() {
    var result = resource("a")
        .flatMap(_ -> Resource.of(failure("boom")))
        .mapError(String::length)
        .use(Program::success)
        .eval();

    assertThat(result).isEqualTo(Result.failure(4));
    assertThat(log).containsExactly("acquire a", "release a");
  }

  @Test
  void orElseAcquiresTheFirstOnlyOnce() {
    var result = resource("a").orElse(resource("b")).use(this::use).eval();

    assertThat(result).isEqualTo(Result.success("a"));
    assertThat(log).containsExactly("acquire a", "use a", "release a");
  }

  @Test
  void orElseFallsBackWhenTheFirstFails() {
    var result = Resource.<Void, String, String>of(failure("boom"))
        .orElse(resource("b"))
        .use(this::use)
        .eval();

    assertThat(result).isEqualTo(Result.success("b"));
    assertThat(log).containsExactly("acquire b", "use b", "release b");
  }

  @Test
  void eitherIsLeftWhenTheFirstSucceeds() {
    var result = resource("a").either(resource("b")).use(Program::success).eval();

    assertThat(result).isEqualTo(Result.success(Either.left("a")));
    assertThat(log).containsExactly("acquire a", "release a");
  }

  @Test
  void eitherIsRightWhenTheFirstFails() {
    var result = Resource.<Void, String, String>of(failure("boom"))
        .either(resource("b"))
        .use(Program::success)
        .eval();

    assertThat(result).isEqualTo(Result.success(Either.right("b")));
    assertThat(log).containsExactly("acquire b", "release b");
  }

  @Test
  void retryAcquiresAgainAfterAFailure() {
    var attempts = new AtomicInteger();
    var flaky = Resource.<Void, String, String>from(
        suspend(() -> attempts.incrementAndGet() == 1
            ? failure("boom")
            : success("a")),
        a -> log.add("release " + a));

    var result = flaky.retry().use(this::use).eval();

    assertThat(result).isEqualTo(Result.success("a"));
    assertThat(attempts).hasValue(2);
    assertThat(log).containsExactly("use a", "release a");
  }

  @Test
  void retryReleasesTheFirstBeforeAcquiringAgain() {
    var attempts = new AtomicInteger();
    var flaky = Resource.<Void, String, String>from(
        suspend(() -> attempts.incrementAndGet() == 1
            ? failure("boom")
            : success("b")),
        b -> log.add("release " + b));

    var result = resource("a").flatMap(_ -> flaky).retry().use(this::use).eval();

    assertThat(result).isEqualTo(Result.success("b"));
    assertThat(attempts).hasValue(2);
    assertThat(log).containsExactly(
        "acquire a", "release a",
        "acquire a", "use b", "release b", "release a");
  }

  // ---------------------------------------------------------------------------------------------
  // make
  // ---------------------------------------------------------------------------------------------

  @Test
  void makeReleasesWithAProgram() {
    var result = Resource.<Void, String, String>make(
            success("a"),
            a -> task(() -> log.add("release " + a)))
        .use(this::use)
        .eval();

    assertThat(result).isEqualTo(Result.success("a"));
    assertThat(log).containsExactly("use a", "release a");
  }

  @Test
  void makeAcquiresAndReleasesWithTheState() {
    var state = new CopyOnWriteArrayList<String>();

    var result = Resource.<List<String>, String, String>make(
            s -> {
              s.add("acquire a");
              return "a";
            },
            a -> inspect(s -> s.add("release " + a)))
        .use(Program::success)
        .eval(state);

    assertThat(result).isEqualTo(Result.success("a"));
    assertThat(state).containsExactly("acquire a", "release a");
  }

  @Test
  void theOuterIsReleasedEvenIfTheInnerReleaseThrows() {
    var inner = Resource.<Void, String, String>make(
        success("b"),
        _ -> raise(IllegalStateException::new));

    var program = resource("a").flatMap(_ -> inner).use(this::use);

    assertThatThrownBy(program::eval).isInstanceOf(IllegalStateException.class);
    assertThat(log).containsExactly("acquire a", "use b", "release a");
  }

  @Test
  void theInnerReleaseExceptionWinsWhenBothReleasesThrow() {
    var outer = Resource.<Void, String, String>make(
        success("a"),
        _ -> raise(() -> new IllegalStateException("outer")));
    var inner = Resource.<Void, String, String>make(
        success("b"),
        _ -> raise(() -> new IllegalArgumentException("inner")));

    var program = outer.flatMap(_ -> inner).use(this::use);

    // like try-with-resources: the first exception wins and the next ones are suppressed
    assertThatThrownBy(program::eval)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("inner")
        .satisfies(e -> assertThat(e.getSuppressed())
            .singleElement()
            .isInstanceOf(IllegalStateException.class)
            .extracting(Throwable::getMessage)
            .isEqualTo("outer"));
    assertThat(log).containsExactly("use b");
  }

  @Test
  void theUseExceptionWinsWhenTheReleaseThrows() {
    var resource = Resource.<Void, String, String>make(
        success("a"),
        _ -> raise(() -> new IllegalStateException("release")));

    var program = resource.use(_ -> raise(() -> new IllegalArgumentException("use")));

    assertThatThrownBy(program::eval)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("use")
        .satisfies(e -> assertThat(e.getSuppressed())
            .singleElement()
            .isInstanceOf(IllegalStateException.class));
  }

  @Test
  void theNextAcquisitionExceptionWinsWhenThePreviousReleaseThrows() {
    var outer = Resource.<Void, String, String>make(
        success("a"),
        _ -> raise(() -> new IllegalStateException("release")));
    var inner = Resource.<Void, String, String>make(
        raise(() -> new IllegalArgumentException("acquire")),
        _ -> Program.unit());

    var program = outer.flatMap(_ -> inner).use(this::use);

    assertThatThrownBy(program::eval)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("acquire")
        .satisfies(e -> assertThat(e.getSuppressed())
            .singleElement()
            .isInstanceOf(IllegalStateException.class));
  }

  // ---------------------------------------------------------------------------------------------
  // cancellation during acquisition
  // ---------------------------------------------------------------------------------------------

  @Test
  void aCancelledAcquisitionFinishesAndIsReleasedWithoutUsingIt() {
    var acquiring = new CompletableFuture<Void>();
    var gate = new CompletableFuture<Void>();
    var program = Resource.<Void, String, String>from(
            supply(() -> blockUntilOpen(acquiring, gate, "a")),
            a -> log.add("release " + a))
        .use(this::use);

    var future = program.fork().evalOrElseThrow();
    acquiring.join();

    assertThat(future.cancel(true)).isTrue();
    gate.complete(null);

    assertThatThrownBy(future::join).isInstanceOf(CancellationException.class);
    assertThat(log).containsExactly("acquire a", "release a");
  }

  @Test
  void aCancelledComposedAcquisitionReleasesEveryResourceInReverseOrder() {
    var acquiring = new CompletableFuture<Void>();
    var gate = new CompletableFuture<Void>();
    var blocking = Resource.<Void, String, String>from(
        supply(() -> blockUntilOpen(acquiring, gate, "b")),
        b -> log.add("release " + b));

    var program = resource("a").flatMap(_ -> blocking).use(this::use);

    var future = program.fork().evalOrElseThrow();
    acquiring.join();

    assertThat(future.cancel(true)).isTrue();
    gate.complete(null);

    assertThatThrownBy(future::join).isInstanceOf(CancellationException.class);
    assertThat(log).containsExactly("acquire a", "acquire b", "release b", "release a");
  }

  // ---------------------------------------------------------------------------------------------
  // makeFull
  // ---------------------------------------------------------------------------------------------

  @Test
  void aPolledWaitInTheAcquisitionCanBeCancelled() {
    var waiting = new CompletableFuture<Void>();
    var program = waitingForever(waiting).use(this::use);

    var future = program.fork().evalOrElseThrow();
    waiting.join();

    assertThat(future.cancel(true)).isTrue();
    assertThatThrownBy(future::join).isInstanceOf(CancellationException.class);
    assertThat(log).isEmpty(); // nothing was acquired, so nothing is released
  }

  @Test
  void aPolledWaitInTheNextAcquisitionCanBeCancelledAndReleasesThePrevious() {
    var waiting = new CompletableFuture<Void>();
    var program = resource("a").flatMap(_ -> waitingForever(waiting)).use(this::use);

    var future = program.fork().evalOrElseThrow();
    waiting.join();

    assertThat(future.cancel(true)).isTrue();
    assertThatThrownBy(future::join).isInstanceOf(CancellationException.class);
    assertThat(log).containsExactly("acquire a", "release a");
  }

  @Test
  void theDelayOfRetryCanBeCancelled() {
    var attempted = new CompletableFuture<Void>();
    var program = Resource.<Void, String, String>of(suspend(() -> {
          attempted.complete(null);
          return failure("boom");
        }))
        .retry(1, Duration.ofHours(1))
        .use(this::use);

    var future = program.fork().evalOrElseThrow();
    attempted.join();

    assertThat(future.cancel(true)).isTrue();
    // it doesn't wait for the hour of the delay
    assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS)).isInstanceOf(CancellationException.class);
    assertThat(log).isEmpty();
  }

  // ---------------------------------------------------------------------------------------------
  // helpers
  // ---------------------------------------------------------------------------------------------

  // a resource whose acquisition signals it's waiting and then waits forever, but cancelably
  private Resource<Void, String, String> waitingForever(CompletableFuture<Void> waiting) {
    return Resource.makeFull(
        poll -> pipe(
            task(() -> waiting.complete(null)),
            _ -> poll.apply(never())),
        value -> task(() -> log.add("release " + value)));
  }

  private Resource<Void, String, String> resource(String name) {
    return Resource.from(
        supply(() -> {
          log.add("acquire " + name);
          return name;
        }),
        value -> log.add("release " + value));
  }

  // an acquisition that signals it has started and then waits until the test opens the gate
  private String blockUntilOpen(
      CompletableFuture<Void> acquiring, CompletableFuture<Void> gate, String name) {
    acquiring.complete(null);
    gate.join();
    log.add("acquire " + name);
    return name;
  }

  private Program<Void, String, String> use(String value) {
    return pipe(
        task(() -> log.add("use " + value)),
        _ -> success(value)
        );
  }
}
