/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Random.nextDouble;
import static com.github.tonivade.diesel.Random.nextInt;
import static com.github.tonivade.diesel.Result.success;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

@MockitoSettings
class RandomTest {

  @Test
  void shouldGenerateInt(@Mock Random.Service service) {
    when(service.nextInt(10)).thenReturn(8);

    var result = nextInt(10).eval(service);

    assertThat(result).isEqualTo(success(8));
  }

  @Test
  void shouldGenerateDouble(@Mock Random.Service service) {
    when(service.nextDouble()).thenReturn(0.1);

    var result = nextDouble().eval(service);

    assertThat(result).isEqualTo(success(0.1));
  }
}
