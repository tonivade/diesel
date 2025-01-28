/*
 * Copyright (c) 2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Result.failure;
import static com.github.tonivade.diesel.Result.success;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.github.tonivade.diesel.ProgramTest.TestDsl.Error;
import com.github.tonivade.diesel.ProgramTest.TestDsl.Operation;
import com.github.tonivade.diesel.ProgramTest.TestDsl.Service;
import com.github.tonivade.diesel.ProgramTest.TestDsl.UnknownError;

@ExtendWith(MockitoExtension.class)
class ProgramTest {

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
  void stackSafety(@Mock TestDsl.Service service) {
    var sum = safeSum(100000, 0);

    var result = sum.eval(service);

    assertThat(result).isEqualTo(success(705082704));
  }

  @Test
  void testUnsafe() {
    assertThatThrownBy(() -> unsafeSum(100000, 0)).isInstanceOf(StackOverflowError.class);
  }

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
    default Result<Error, Integer> eval(Service state) {
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
    return sum(n, sum).flatMap(next -> safeSum(n - 1, next));
  }

  private static Program<Service, Error, Integer> sum(Integer a, Integer b) {
    return Program.success(a + b);
  }
}
