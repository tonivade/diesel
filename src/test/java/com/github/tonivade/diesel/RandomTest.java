package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Random.nextInt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RandomTest {

  @Test
  void shouldGenerateInt(@Mock Random.Service service) {
    when(service.nextInt(10)).thenReturn(8);

    var result = nextInt(10).eval(service);

    assertThat(result).isEqualTo(8);
  }
}
