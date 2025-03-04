/*
 * Copyright (c) 2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Program.delay;
import static com.github.tonivade.diesel.Program.raise;
import static com.github.tonivade.diesel.Program.sleep;
import static com.github.tonivade.diesel.Result.failure;
import static com.github.tonivade.diesel.Result.success;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.github.tonivade.diesel.ProgramTest.TestDsl.Operation;
import com.github.tonivade.diesel.ProgramTest.TestDsl.UnknownError;

@ExtendWith(MockitoExtension.class)
class ProgramTest {

  Executor executor = Executors.newVirtualThreadPerTaskExecutor();

  @Test
  void shouldRepeat(@Mock TestDsl.Service service) {
    when(service.operation()).thenReturn(success(1));

    newOperation().repeat(3).eval(service);

    verify(service, times(4)).operation();
  }

  @Test
  void shouldRetry(@Mock TestDsl.Service service) {
    when(service.operation()).thenReturn(failure(newUnknownError()));

    newOperation().retry(3).eval(service);

    verify(service, times(4)).operation();
  }

  @Test
  void shouldRetryOnlyIfFails(@Mock TestDsl.Service service) {
    when(service.operation())
      .thenReturn(failure(newUnknownError()))
      .thenReturn(success(1));

    newOperation().retry(3).eval(service);

    verify(service, times(2)).operation();
  }

  @Test
  void shouldBeStackSafety(@Mock TestDsl.Service service) {
    var sum = safeSum(100000, 0);

    var result = sum.eval(service);

    assertThat(result).isEqualTo(success(705082704));
  }

  @Test
  void testUnsafe() {
    assertThatThrownBy(() -> unsafeSum(100000, 0)).isInstanceOf(StackOverflowError.class);
  }

  @Test
  void shouldSleep() {
    var duration = Duration.ofSeconds(2);

    var result = sleep(duration, executor).timed().eval(null);

    assertThat(result.getOrElseThrow().duration())
      .isCloseTo(duration, Duration.ofMillis(100));
  }

  @Test
  void shouldDelay() {
    var duration = Duration.ofSeconds(2);

    var result = delay(duration, () -> 10, executor).timed().eval(null);

    assertThat(result.getOrElseThrow().duration())
      .isCloseTo(duration, Duration.ofMillis(100));
    assertThat(result.getOrElseThrow().value())
      .isEqualTo(10);
  }

  @Test
  void shouldSerialize() {
    var p1 = delay(Duration.ofSeconds(2), () -> 10, executor);
    var p2 = delay(Duration.ofSeconds(2), () -> "hello", executor);

    var result = Program.map2(p1, p2, Tuple::new).timed().eval(null);

    assertThat(result.getOrElseThrow().duration())
      .isCloseTo(Duration.ofSeconds(4), Duration.ofMillis(100));
    assertThat(result.getOrElseThrow().value())
      .isEqualTo(new Tuple<>(10, "hello"));
  }

  @Test
  void shouldParallelize() {
    var p1 = delay(Duration.ofSeconds(2), () -> 10, executor);
    var p2 = delay(Duration.ofSeconds(2), () -> "hello", executor);

    var result = Program.parMap2(p1, p2, Tuple::new, executor).timed().eval(null);

    assertThat(result.getOrElseThrow().duration())
      .isCloseTo(Duration.ofSeconds(2), Duration.ofMillis(100));
    assertThat(result.getOrElseThrow().value())
      .isEqualTo(new Tuple<>(10, "hello"));
  }

  @Test
  void shouldRace() {
    var p1 = delay(Duration.ofSeconds(20), () -> 10, executor);
    var p2 = delay(Duration.ofSeconds(2), () -> "hello", executor);

    var result = Program.either(p1, p2, executor).timed().eval(null);

    assertThat(result.getOrElseThrow().duration())
      .isCloseTo(Duration.ofSeconds(2), Duration.ofMillis(100));
    assertThat(result.getOrElseThrow().value())
      .isEqualTo(Either.right("hello"));
  }

  @Test
  void shouldTimeout() {
    var p1 = delay(Duration.ofSeconds(20), () -> 10, executor);
    var p2 = p1.timeout(Duration.ofSeconds(1), executor);

    assertThatThrownBy(() -> p2.eval(null))
      .isInstanceOf(TimeoutException.class);
  }

  @Test
  void shouldNotTimeout() {
    var p1 = delay(Duration.ofSeconds(2), () -> 10, executor);
    var p2 = p1.timeout(Duration.ofSeconds(10), executor);

    var result = p2.eval(null);

    assertThat(result).isEqualTo(Result.success(10));
  }

  @Test
  void shouldRaiseException() {
    var program = raise(UnsupportedOperationException::new);

    assertThatThrownBy(() -> program.eval(null))
      .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void shouldCatchException() {
    var program = raise(UnsupportedOperationException::new).catchAll(__ -> Program.success(10));

    var result = program.eval(null);

    assertThat(result).isEqualTo(Result.success(10));
  }

  record Tuple<A, B>(A a, B b) {}

  static Operation newOperation() {
    return new TestDsl.Operation();
  }

  static UnknownError newUnknownError() {
    return new TestDsl.UnknownError();
  }

  sealed interface TestDsl extends Program.Dsl<TestDsl.Service, TestDsl.Error, Integer> {

    sealed interface Error {}
    record UnknownError() implements Error {}

    interface Service {
      Result<Error, Integer> operation();
    }

    record Operation() implements TestDsl {}

    @Override
    default Result<Error, Integer> handle(Service state) {
      return state.operation();
    }
  }

  static Integer unsafeSum(Integer n, Integer sum) {
    if (n == 0) {
      return sum;
    }
    return unsafeSum(n - 1, n + sum);
  }

  static Program<TestDsl.Service, TestDsl.Error, Integer> safeSum(Integer n, Integer sum) {
    if (n == 0) {
      return Program.success(sum);
    }
    return Program.suspend(() -> safeSum(n - 1, n + sum));
  }
}
