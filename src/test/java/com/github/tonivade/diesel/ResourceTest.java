/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Combine.pipe;
import static com.github.tonivade.diesel.Concurrent.parZip;
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

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

// a regression in the cancellation tests would leave them waiting forever instead of failing
@Timeout(value = 10, unit = TimeUnit.SECONDS)
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
    var result = Resource.<Void, String, String>eval(failure("boom"))
        .use(this::use)
        .eval();

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
  void theCheckedExceptionOfCloseIsThrownAsItIs() {
    var exception = new IOException("close");
    AutoCloseable closeable = () -> {
      throw exception;
    };

    var program = Resource.<Void, String, AutoCloseable>from(success(closeable))
        .use(_ -> success("used"));

    assertThatThrownBy(program::eval).isSameAs(exception);
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

  @Test
  void releasesAComposedResourceInReverseOrderWhenUseIsCancelled() {
    var started = new CompletableFuture<Void>();
    var program = resource("a").flatMap(_ -> resource("b")).use(_ -> {
      started.complete(null);
      return never();
    });

    var future = program.fork().evalOrElseThrow();
    started.join();

    assertThat(future.cancel(true)).isTrue();
    assertThatThrownBy(future::join)
        .isInstanceOf(CancellationException.class);
    assertThat(log)
        .containsExactly("acquire a", "acquire b", "release b", "release a");
  }

  @Test
  void useForeverKeepsTheResourceUntilItIsCancelled() {
    var acquired = new CompletableFuture<Void>();
    var program = resource("a")
        .evalTap(_ -> task(() -> acquired.complete(null)))
        .useForever();

    var future = program.fork().evalOrElseThrow();
    acquired.join();

    assertThat(future.cancel(true)).isTrue();
    assertThatThrownBy(future::join)
        .isInstanceOf(CancellationException.class);
    assertThat(log)
        .containsExactly("acquire a", "release a");
  }

  @Test
  void aLongChainBuiltInALoopIsStackSafe() {
    var resource = Resource.<Void, String, Integer>of(0);
    for (int i = 0; i < 50_000; i++) {
      resource = resource.map(x -> x + 1);
    }

    var result = resource.use(Program::success).eval();

    assertThat(result).isEqualTo(Result.success(50_000));
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
  void mapReleasesWhenTheMapperThrows() {
    var program = resource("a")
        .<String>map(_ -> {
          throw new IllegalStateException();
        })
        .use(this::use);

    assertThatThrownBy(program::eval).isInstanceOf(IllegalStateException.class);
    assertThat(log).containsExactly("acquire a", "release a");
  }

  @Test
  @SuppressWarnings("NullAway") // NullAway already rejects it at compile time, but not every caller uses it
  void mapRejectsANullAndReleases() {
    var program = resource("a")
        .<String>map(_ -> null)
        .use(this::use);

    assertThatThrownBy(program::eval).isInstanceOf(NullPointerException.class);
    assertThat(log).containsExactly("acquire a", "release a");
  }

  @Test
  void mapKeepsTheReleaseInAComposedResource() {
    var result = resource("a")
        .flatMap(a -> resource("b").map(b -> a + b))
        .map(String::toUpperCase)
        .use(this::use)
        .eval();

    assertThat(result).isEqualTo(Result.success("AB"));
    assertThat(log).containsExactly("acquire a", "acquire b", "use AB", "release b", "release a");
  }

  @Test
  void evalMapRunsTheProgramAndKeepsTheRelease() {
    var result = resource("a")
        .evalMap(a -> pipe(task(() -> log.add("eval " + a)), _ -> success(a + a)))
        .use(this::use)
        .eval();

    assertThat(result).isEqualTo(Result.success("aa"));
    assertThat(log).containsExactly("acquire a", "eval a", "use aa", "release a");
  }

  @Test
  void evalMapReleasesWhenTheProgramFails() {
    var result = resource("a")
        .<String>evalMap(_ -> failure("boom"))
        .use(this::use)
        .eval();

    assertThat(result).isEqualTo(Result.failure("boom"));
    assertThat(log).containsExactly("acquire a", "release a");
  }

  @Test
  void evalMapReleasesWhenTheProgramThrows() {
    var program = resource("a")
        .<String>evalMap(_ -> raise(IllegalStateException::new))
        .use(this::use);

    assertThatThrownBy(program::eval).isInstanceOf(IllegalStateException.class);
    assertThat(log).containsExactly("acquire a", "release a");
  }

  @Test
  void evalMapReleasesWhenTheMapperThrows() {
    var program = resource("a")
        .<String>evalMap(_ -> {
          throw new IllegalStateException();
        })
        .use(this::use);

    assertThatThrownBy(program::eval).isInstanceOf(IllegalStateException.class);
    assertThat(log).containsExactly("acquire a", "release a");
  }

  @Test
  void evalTapKeepsTheValue() {
    var result = resource("a")
        .evalTap(a -> task(() -> log.add("tap " + a)))
        .use(this::use)
        .eval();

    assertThat(result).isEqualTo(Result.success("a"));
    assertThat(log).containsExactly("acquire a", "tap a", "use a", "release a");
  }

  @Test
  void evalTapReleasesWhenTheProgramFails() {
    var result = resource("a")
        .evalTap(_ -> failure("boom"))
        .use(this::use)
        .eval();

    assertThat(result).isEqualTo(Result.failure("boom"));
    assertThat(log).containsExactly("acquire a", "release a");
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
        .flatMap(_ -> Resource.<Void, String, String>eval(failure("boom")))
        .use(this::use)
        .eval();

    assertThat(result).isEqualTo(Result.failure("boom"));
    assertThat(log).containsExactly("acquire a", "release a");
  }

  @Test
  void flatMapReleasesTheFirstWhenTheSecondThrows() {
    var program = resource("a")
        .flatMap(_ -> Resource.<Void, String, String>eval(raise(IllegalStateException::new)))
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
        .flatMap(_ -> Resource.eval(failure("boom")))
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
    var result = Resource.<Void, String, String>eval(failure("boom"))
        .orElse(resource("b"))
        .use(this::use)
        .eval();

    assertThat(result).isEqualTo(Result.success("b"));
    assertThat(log).containsExactly("acquire b", "use b", "release b");
  }

  @Test
  void foldMapReleasesTheFirstWhenTheMapperThrows() {
    var program = resource("a")
        .foldMap(
            _ -> resource("fallback"),
            _ -> {
              throw new IllegalStateException();
            })
        .use(this::use);

    assertThatThrownBy(program::eval).isInstanceOf(IllegalStateException.class);
    assertThat(log).containsExactly("acquire a", "release a");
  }

  @Test
  void foldMapPropagatesAnExceptionOfTheErrorMapper() {
    var program = Resource.<Void, String, String>eval(failure("boom"))
        .foldMap(
            _ -> {
              throw new IllegalStateException();
            },
            this::resource)
        .use(this::use);

    assertThatThrownBy(program::eval).isInstanceOf(IllegalStateException.class);
    assertThat(log).isEmpty();
  }

  @Test
  void flatMapErrorPropagatesAnExceptionOfTheMapper() {
    var program = Resource.<Void, String, String>eval(failure("boom"))
        .<String>flatMapError(_ -> {
          throw new IllegalStateException();
        })
        .use(this::use);

    assertThatThrownBy(program::eval).isInstanceOf(IllegalStateException.class);
    assertThat(log).isEmpty();
  }

  @Test
  void eitherIsLeftWhenTheFirstSucceeds() {
    var result = resource("a").either(resource("b")).use(Program::success).eval();

    assertThat(result).isEqualTo(Result.success(Either.left("a")));
    assertThat(log).containsExactly("acquire a", "release a");
  }

  @Test
  void eitherIsRightWhenTheFirstFails() {
    var result = Resource.<Void, String, String>eval(failure("boom"))
        .either(resource("b"))
        .use(Program::success)
        .eval();

    assertThat(result).isEqualTo(Result.success(Either.right("b")));
    assertThat(log).containsExactly("acquire b", "release b");
  }

  @Test
  void eitherFailsWithTheErrorOfTheAlternativeWhenBothFail() {
    var result = Resource.<Void, String, String>eval(failure("first"))
        .either(Resource.<Void, String, String>eval(failure("second")))
        .use(Program::success)
        .eval();

    assertThat(result).isEqualTo(Result.failure("second"));
    assertThat(log).isEmpty();
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

  @Test
  void retryWithZeroRetriesDoesNotAcquireAgain() {
    var attempts = new AtomicInteger();
    var failing = Resource.<Void, String, String>eval(
        suspend(() -> {
          attempts.incrementAndGet();
          return failure("boom");
        }));

    var result = failing.retry(0).use(this::use).eval();

    assertThat(result).isEqualTo(Result.failure("boom"));
    assertThat(attempts).hasValue(1);
    assertThat(log).isEmpty();
  }

  @Test
  void theDelayOfRetryCanBeCancelledAfterAComposedAcquisitionFails() {
    var attempted = new CompletableFuture<Void>();
    var failing = Resource.<Void, String, String>eval(suspend(() -> {
      attempted.complete(null);
      return failure("boom");
    }));
    var program = resource("a").flatMap(_ -> failing)
        .retry(1, Duration.ofHours(1))
        .use(this::use);

    var future = program.fork().evalOrElseThrow();
    attempted.join();

    assertThat(future.cancel(true)).isTrue();
    assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS)).isInstanceOf(CancellationException.class);
    // the first resource was released before the delay started, and it's not acquired again
    assertThat(log).containsExactly("acquire a", "release a");
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
  void theReleaseExceptionsAreSuppressedInsideEachOther() {
    var outer = Resource.<Void, String, String>make(
        success("a"),
        _ -> raise(() -> new IllegalStateException("outer")));
    var inner = Resource.<Void, String, String>make(
        success("b"),
        _ -> raise(() -> new IllegalStateException("inner")));

    var program = outer.flatMap(_ -> inner)
        .use(_ -> raise(() -> new IllegalArgumentException("use")));

    // both releases are a single finalizer, so the exception of the outer release is suppressed
    // inside the one of the inner release, and that one inside the exception of use
    assertThatThrownBy(program::eval)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("use")
        .satisfies(use -> assertThat(use.getSuppressed())
            .singleElement()
            .satisfies(release -> {
              assertThat(release).hasMessage("inner");
              assertThat(release.getSuppressed())
                  .singleElement()
                  .extracting(Throwable::getMessage)
                  .isEqualTo("outer");
            }));
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
  void aPolledWaitInTheFallbackOfFlatMapErrorCanBeCancelled() {
    var waiting = new CompletableFuture<Void>();
    var program = Resource.<Void, String, String>eval(failure("boom"))
        .flatMapError(_ -> waitingForever(waiting))
        .use(this::use);

    var future = program.fork().evalOrElseThrow();
    waiting.join();

    assertThat(future.cancel(true)).isTrue();
    assertThatThrownBy(future::join).isInstanceOf(CancellationException.class);
    assertThat(log).isEmpty(); // neither the failed nor the fallback resource was acquired
  }

  @Test
  void aPolledWaitInTheAlternativeOfEitherCanBeCancelled() {
    var waiting = new CompletableFuture<Void>();
    var program = Resource.<Void, String, String>eval(failure("boom"))
        .either(waitingForever(waiting))
        .use(Program::success);

    var future = program.fork().evalOrElseThrow();
    waiting.join();

    assertThat(future.cancel(true)).isTrue();
    assertThatThrownBy(future::join).isInstanceOf(CancellationException.class);
    assertThat(log).isEmpty(); // neither the failed nor the alternative resource was acquired
  }

  @Test
  void theDelayOfRetryCanBeCancelled() {
    var attempted = new CompletableFuture<Void>();
    var program = Resource.<Void, String, String>eval(suspend(() -> {
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
  // background
  // ---------------------------------------------------------------------------------------------

  @Test
  void backgroundJoinsTheResultOfTheProgram() {
    var result = Resource.<Void, String, Integer>background(success(42))
        .use(join -> join)
        .eval();

    assertThat(result).isEqualTo(Result.success(42));
  }

  @Test
  void backgroundIsCancelledWhenUseEnds() {
    var started = new CompletableFuture<Void>();
    var program = Resource.background(worker(started))
        .use(_ -> pipe(
            task(started::join),
            _ -> task(() -> log.add("use"))));

    program.eval();

    // the release waits for the background program to stop
    assertThat(log).containsExactly("use", "worker stopped");
  }

  @Test
  void backgroundStopsBeforeTheResourcesItDependsOnAreReleased() {
    var started = new CompletableFuture<Void>();
    Program<Void, String, Unit> slowToStop = pipe(
            task(() -> started.complete(null)),
            _ -> Program.<Void, String, Unit>never())
        .ensuring(pipe(
            Program.<Void, Void>sleep(Duration.ofMillis(100)),
            _ -> task(() -> log.add("worker stopped"))));

    var program = resource("a")
        .flatMap(a -> Resource.background(slowToStop).map(_ -> a))
        .use(_ -> task(started::join));

    program.eval();

    assertThat(log).containsExactly("acquire a", "worker stopped", "release a");
  }

  @Test
  void backgroundIsCancelledWhenUseIsCancelled() {
    var started = new CompletableFuture<Void>();
    var program = Resource.background(worker(started)).use(_ -> never());

    var future = program.fork().evalOrElseThrow();
    started.join();

    assertThat(future.cancel(true)).isTrue();
    assertThatThrownBy(future::join).isInstanceOf(CancellationException.class);
    assertThat(log).containsExactly("worker stopped");
  }

  @Test
  void aFailedBackgroundProgramDoesNotFailTheRelease() {
    var stopped = new CompletableFuture<Void>();
    Program<Void, String, Unit> failing = Program.<Void, String, Unit>raise(IllegalStateException::new)
        .ensuring(task(() -> stopped.complete(null)));

    var result = Resource.background(failing)
        .use(_ -> pipe(
            task(stopped::join),
            _ -> success("done")))
        .eval();

    assertThat(result).isEqualTo(Result.success("done"));
  }

  @Test
  void joiningAFailedBackgroundProgramFails() {
    var program = Resource.background(Program.<Void, String, Unit>raise(IllegalStateException::new))
        .use(join -> join);

    assertThatThrownBy(program::eval).isInstanceOf(IllegalStateException.class);
  }

  // ---------------------------------------------------------------------------------------------
  // parallel use
  // ---------------------------------------------------------------------------------------------

  @Test
  void resourcesUsedInParallelAreAllReleased() {
    var result = parZip(
        resource("a").use(this::use),
        resource("b").use(this::use),
        String::concat)
        .eval();

    assertThat(result).isEqualTo(Result.success("ab"));
    assertThat(log)
        .hasSize(6)
        .containsSubsequence("acquire a", "use a", "release a")
        .containsSubsequence("acquire b", "use b", "release b");
  }

  @Test
  void aResourceUsedInParallelIsReleasedWhenTheOtherUseFails() {
    var started = new CompletableFuture<Void>();
    var waiting = resource("a").use(_ -> pipe(
        task(() -> started.complete(null)),
        _ -> Program.<Void, String, String>never()));
    var failing = resource("b").use(_ -> pipe(
        task(started::join),
        _ -> Program.<Void, String, String>failure("boom")));

    var result = parZip(waiting, failing, (a, b) -> a + b).eval();

    assertThat(result).isEqualTo(Result.failure("boom"));
    assertThat(log)
        .hasSize(4)
        .containsSubsequence("acquire a", "release a")
        .containsSubsequence("acquire b", "release b");
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

  // a background program that signals it has started, waits forever and logs when it stops
  private Program<Void, String, Unit> worker(CompletableFuture<Void> started) {
    return pipe(
            task(() -> started.complete(null)),
            _ -> Program.<Void, String, Unit>never())
        .ensuring(task(() -> log.add("worker stopped")));
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
