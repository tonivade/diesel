/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel.impl;

import static com.github.tonivade.diesel.Result.success;
import static com.github.tonivade.diesel.impl.Clock.nanoTime;
import static com.github.tonivade.diesel.impl.Clock.now;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

@MockitoSettings
class ClockTest {

  @Test
  void shouldReturnNanoTime(@Mock Clock.Service service) {
    when(service.nanoTime()).thenReturn(123456789012345L);

    var result = nanoTime().eval(service);

    assertThat(result).isEqualTo(success(123456789012345L));
  }

  @Test
  void shouldReturnInstant(@Mock Clock.Service service) {
    var now = Instant.now();
    when(service.now()).thenReturn(now);

    var result = now().eval(service);

    assertThat(result).isEqualTo(success(now));
  }
}
