package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Counter.decrement;
import static com.github.tonivade.diesel.Counter.increment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CounterTest {

  @Test
  void shouldIncrementValue(@Mock Counter.Service service) {
    when(service.increment()).thenReturn(1, 2, 3);

    var result = increment().andThen(increment()).andThen(increment()).eval(service);

    assertThat(result).isEqualTo(3);
    verify(service, times(3)).increment();
  }

  @Test
  void shouldDecrementValue(@Mock Counter.Service service) {
    when(service.decrement()).thenReturn(3, 2, 1);

    var result = decrement().andThen(decrement()).andThen(decrement()).eval(service);

    assertThat(result).isEqualTo(1);
    verify(service, times(3)).decrement();
  }
}
