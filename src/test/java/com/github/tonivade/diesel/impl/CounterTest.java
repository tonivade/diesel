/*
 * Copyright (c) 2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel.impl;

import static com.github.tonivade.diesel.Result.success;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.github.tonivade.diesel.Program;
import com.github.tonivade.diesel.Result;
import com.github.tonivade.diesel.impl.Counter;
import com.github.tonivade.diesel.impl.Counter.Service;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CounterTest {

  @Test
  void shouldIncrementValue(@Mock Counter.Service<Integer> service) {
    when(service.increment()).thenReturn(3);

    var result = increment().eval(service);

    assertThat(result).isEqualTo(success(3));
  }

  @Test
  void shouldDecrementValue(@Mock Counter.Service<Integer> service) {
    when(service.decrement()).thenReturn(1);

    var result = decrement().eval(service);

    assertThat(result).isEqualTo(success(1));
  }

  private Program<Service<Integer>, Void, Integer> increment() {
    return Counter.increment();
  }

  private Program<Service<Integer>, Void, Integer> decrement() {
    return Counter.decrement();
  }
}
