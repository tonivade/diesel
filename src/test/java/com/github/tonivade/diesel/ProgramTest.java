/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Program.bracket;
import static com.github.tonivade.diesel.Program.chainAll;
import static com.github.tonivade.diesel.Program.delay;
import static com.github.tonivade.diesel.Program.effectR;
import static com.github.tonivade.diesel.Program.either;
import static com.github.tonivade.diesel.Program.failure;
import static com.github.tonivade.diesel.Program.memoizeRecursive;
import static com.github.tonivade.diesel.Program.parAll;
import static com.github.tonivade.diesel.Program.parSequence;
import static com.github.tonivade.diesel.Program.parZip;
import static com.github.tonivade.diesel.Program.raise;
import static com.github.tonivade.diesel.Program.recover;
import static com.github.tonivade.diesel.Program.sequence;
import static com.github.tonivade.diesel.Program.sleep;
import static com.github.tonivade.diesel.Program.success;
import static com.github.tonivade.diesel.Program.supply;
import static com.github.tonivade.diesel.Program.suspend;
import static com.github.tonivade.diesel.Program.task;
import static com.github.tonivade.diesel.Program.unit;
import static com.github.tonivade.diesel.Program.zip;
import static java.util.function.Predicate.not;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import com.github.tonivade.diesel.ProgramTest.TestDsl.UnknownError;

@MockitoSettings
class ProgramTest {

  @Test
  void shouldRepeat(@Mock TestDsl.Service service) {
    when(service.operation()).thenReturn(Result.success(1));

    newOperation().repeat(3).eval(service);

    verify(service, times(4)).operation();
  }

  @Test
  void shouldRetry(@Mock TestDsl.Service service) {
    when(service.operation()).thenReturn(Result.failure(newUnknownError()));

    newOperation().retry(3).eval(service);

    verify(service, times(4)).operation();
  }

  @Test
  void shouldRetryOnlyIfFails(@Mock TestDsl.Service service) {
    when(service.operation())
      .thenReturn(Result.failure(newUnknownError()))
      .thenReturn(Result.success(1));

    newOperation().retry(3).eval(service);

    verify(service, times(2)).operation();
  }

  @Test
  void shouldBeStackSafety() {
    var sum = safeSum(100000, 0);

    var result = sum.getOrElseThrow();

    assertThat(result).isEqualTo(705082704);
  }

  @Test
  void shouldGenerateFibSequence() {
    assertThat(fib(0).getOrElseThrow()).isEqualTo(1);
    assertThat(fib(1).getOrElseThrow()).isEqualTo(1);
    assertThat(fib(2).getOrElseThrow()).isEqualTo(2);
    assertThat(fib(3).getOrElseThrow()).isEqualTo(3);
    assertThat(fib(4).getOrElseThrow()).isEqualTo(5);
    assertThat(fib(5).getOrElseThrow()).isEqualTo(8);
    assertThat(fib(6).getOrElseThrow()).isEqualTo(13);
    assertThat(fib(7).getOrElseThrow()).isEqualTo(21);
    assertThat(fib(8).getOrElseThrow()).isEqualTo(34);
    assertThat(fib(9).getOrElseThrow()).isEqualTo(55);
    assertThat(fib(10).getOrElseThrow()).isEqualTo(89);
    assertThat(fib(20).getOrElseThrow()).isEqualTo(10946);
    assertThat(fib(21).getOrElseThrow()).isEqualTo(17711);
    assertThat(fib(22).getOrElseThrow()).isEqualTo(28657);
  }

  @Test
  void shouldGenerateFibSequenceWithMemoization() {
    assertThat(fibMemoized.apply(0).getOrElseThrow()).isEqualTo(1);
    assertThat(fibMemoized.apply(1).getOrElseThrow()).isEqualTo(1);
    assertThat(fibMemoized.apply(2).getOrElseThrow()).isEqualTo(2);
    assertThat(fibMemoized.apply(3).getOrElseThrow()).isEqualTo(3);
    assertThat(fibMemoized.apply(4).getOrElseThrow()).isEqualTo(5);
    assertThat(fibMemoized.apply(5).getOrElseThrow()).isEqualTo(8);
    assertThat(fibMemoized.apply(6).getOrElseThrow()).isEqualTo(13);
    assertThat(fibMemoized.apply(7).getOrElseThrow()).isEqualTo(21);
    assertThat(fibMemoized.apply(8).getOrElseThrow()).isEqualTo(34);
    assertThat(fibMemoized.apply(9).getOrElseThrow()).isEqualTo(55);
    assertThat(fibMemoized.apply(10).getOrElseThrow()).isEqualTo(89);
    assertThat(fibMemoized.apply(20).getOrElseThrow()).isEqualTo(10946);
    assertThat(fibMemoized.apply(21).getOrElseThrow()).isEqualTo(17711);
    assertThat(fibMemoized.apply(22).getOrElseThrow()).isEqualTo(28657);
  }

  @Test
  void testUnsafe() {
    assertThatThrownBy(() -> unsafeSum(100000, 0)).isInstanceOf(StackOverflowError.class);
  }

  @Test
  void shouldSleep() {
    var duration = Duration.ofSeconds(2);

    var result = sleep(duration).timed().getOrElseThrow();

    assertThat(result.duration())
      .isCloseTo(duration, Duration.ofMillis(100));
  }

  @Test
  void shouldDelay() {
    var duration = Duration.ofSeconds(2);

    var result = delay(duration, () -> 10).timed().getOrElseThrow();

    assertThat(result.duration())
      .isCloseTo(duration, Duration.ofMillis(100));
    assertThat(result.value())
      .isEqualTo(10);
  }

  @Test
  void shouldSerialize() {
    var p1 = delay(Duration.ofSeconds(2), () -> 10);
    var p2 = delay(Duration.ofSeconds(2), () -> "hello");

    var result = zip(p1, p2, Tuple::new).timed().getOrElseThrow();

    assertThat(result.duration())
      .isCloseTo(Duration.ofSeconds(4), Duration.ofMillis(100));
    assertThat(result.value())
      .isEqualTo(new Tuple<>(10, "hello"));
  }

  @Test
  void shouldParallelize() {
    var p1 = delay(Duration.ofSeconds(2), () -> 10);
    var p2 = delay(Duration.ofSeconds(2), () -> "hello");

    var result = parZip(p1, p2, Tuple::new).timed().getOrElseThrow();

    assertThat(result.duration())
      .isCloseTo(Duration.ofSeconds(2), Duration.ofMillis(100));
    assertThat(result.value())
      .isEqualTo(new Tuple<>(10, "hello"));
  }

  @Test
  void shouldRace() {
    var p1 = delay(Duration.ofSeconds(20), () -> 10);
    var p2 = delay(Duration.ofSeconds(2), () -> "hello");

    var result = either(p1, p2).timed().getOrElseThrow();

    assertThat(result.duration())
      .isCloseTo(Duration.ofSeconds(2), Duration.ofMillis(100));
    assertThat(result.value())
      .isEqualTo(Either.right("hello"));
  }

  @Test
  void shouldTimeout() {
    var p1 = delay(Duration.ofSeconds(20), () -> 10);
    var p2 = p1.timeout(Duration.ofSeconds(1));

    assertThatThrownBy(() -> p2.getOrElseThrow())
      .isInstanceOf(TimeoutException.class);
  }

  @Test
  void shouldNotTimeout() {
    var p1 = delay(Duration.ofSeconds(2), () -> 10);
    var p2 = p1.timeout(Duration.ofSeconds(10));

    var result = p2.getOrElseThrow();

    assertThat(result).isEqualTo(10);
  }

  @Test
  void shouldRaiseException() {
    var program = raise(UnsupportedOperationException::new);

    assertThatThrownBy(() -> program.getOrElseThrow())
      .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void shouldExecuteAllPrograms(@Mock Supplier<String> supplier) {
    when(supplier.get()).thenReturn("hi!");

    chainAll(supply(supplier), supply(supplier), supply(supplier)).getOrElseThrow();

    verify(supplier, times(3)).get();
  }

  @Test
  void shouldExecuteAllProgramsAndCollectsResult(@Mock Supplier<String> supplier) {
    when(supplier.get()).thenReturn("1", "2", "3");

    var result = sequence(supply(supplier), supply(supplier), supply(supplier)).getOrElseThrow();

    assertThat(result).isEqualTo(List.of("1", "2", "3"));
    verify(supplier, times(3)).get();
  }

  @Test
  void shouldExecuteAllProgramsInParallel(@Mock Supplier<String> supplier) {
    when(supplier.get()).thenReturn("hi!");

    var result = parAll(
        delay(Duration.ofSeconds(1), supplier),
        delay(Duration.ofSeconds(2), supplier),
        delay(Duration.ofSeconds(3), supplier)).timed().getOrElseThrow();

    assertThat(result.duration())
      .isCloseTo(Duration.ofSeconds(3), Duration.ofMillis(100));
    verify(supplier, times(3)).get();
  }

  @Test
  void shouldExecuteAllProgramsInParallelAndCollectsResult(@Mock Supplier<String> supplier) {
    when(supplier.get()).thenReturn("1", "2", "3");

    var result = parSequence(
        delay(Duration.ofSeconds(1), supplier),
        delay(Duration.ofSeconds(2), supplier),
        delay(Duration.ofSeconds(3), supplier)).timed().getOrElseThrow();

    assertThat(result.duration())
      .isCloseTo(Duration.ofSeconds(3), Duration.ofMillis(100));
    assertThat(result.value()).isEqualTo(List.of("1", "2", "3"));
    verify(supplier, times(3)).get();
  }

  @Test
  void shouldCatchException() {
    var program = raise(UnsupportedOperationException::new).catchAll(_ -> success(10));

    var result = program.getOrElseThrow();

    assertThat(result).isEqualTo(10);
  }

  @Test
  void shouldCatchExceptionFromTask() {
    var program = task(() -> {
        throw new UnsupportedOperationException();
      }).catchAll(_ -> unit());

    var result = program.getOrElseThrow();

    assertThat(result).isNull();
  }

  @Test
  void shouldValidate() {
    var validator = Program.<Void, String, Tuple<Integer, String>>validator(
        Validator.of(Tuple::a, Objects::nonNull, _ -> "cannot be null"),
        Validator.of(Tuple::b, not(String::isEmpty), _ -> "cannot be empty"));

    var result1 = validator.apply(new Tuple<>(1, "hola")).eval(null);
    var result2 = validator.apply(new Tuple<>(1, "")).eval(null);
    var result3 = validator.apply(new Tuple<>(null, "hola")).eval(null);
    var result4 = validator.apply(new Tuple<>(null, "")).eval(null);

    assertThat(result1).isEqualTo(Result.success(new Tuple<>(1, "hola")));
    assertThat(result2).isEqualTo(Result.failure(List.of("cannot be empty")));
    assertThat(result3).isEqualTo(Result.failure(List.of("cannot be null")));
    assertThat(result4).isEqualTo(Result.failure(List.of("cannot be null", "cannot be empty")));
  }

  @Test
  void shouldReleaseResource(@Mock AutoCloseable resource) throws Exception {
    var program = bracket(() -> resource, Program::success);

    var result = program.getOrElseThrow();

    assertThat(result).isEqualTo(resource);
    verify(resource).close();
  }

  @Test
  void shouldReleaseResourceOnFailure(@Mock AutoCloseable resource) throws Exception {
    var program = bracket(() -> resource, _ -> failure(new UnsupportedOperationException()));

    var result = program.eval(null);

    assertThat(result).isInstanceOf(Result.Failure.class);
    verify(resource).close();
  }

  @Test
  void shouldRecoverFromError(@Mock TestDsl.Service service) {
    when(service.operation())
      .thenReturn(Result.failure(newUnknownError()));

    var program = recover(newOperation(), _ -> success(1));

    var result = program.eval(service);

    assertThat(result).isEqualTo(Result.success(1));
  }

  record Tuple<A, B>(@Nullable A a, @Nullable B b) {}

  static Program<TestDsl.Service, TestDsl.Error, Integer> newOperation() {
    return effectR(TestDsl.Service::operation);
  }

  static UnknownError newUnknownError() {
    return new TestDsl.UnknownError();
  }

  interface TestDsl {

    sealed interface Error {}
    record UnknownError() implements Error {}

    interface Service {
      Result<Error, Integer> operation();
    }
  }

  static int unsafeSum(int n, int sum) {
    if (n == 0) {
      return sum;
    }
    return unsafeSum(n - 1, n + sum);
  }

  static Program<Void, Void, Integer> safeSum(int n, int sum) {
    if (n == 0) {
      return success(sum);
    }
    return suspend(() -> safeSum(n - 1, n + sum));
  }

  static Program<Void, Void, Integer> fib(int n) {
    if (n == 0 || n == 1) {
      return success(1);
    }
    var fib2 = suspend(() -> fib(n - 2));
    var fib1 = suspend(() -> fib(n - 1));
    return zip(fib2, fib1, Integer::sum);
  }

  Function<Integer, Program<Void, Void, Integer>> fibMemoized =
      memoizeRecursive(self -> n -> {
        if (n == 0 || n == 1) {
          return success(1);
        }
        var fib2 = suspend(() -> self.apply(n - 2));
        var fib1 = suspend(() -> self.apply(n - 1));
        return zip(fib2, fib1, Integer::sum);
      });
}
