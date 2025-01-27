/*
 * Copyright (c) 2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Clock.currentTime;
import static com.github.tonivade.diesel.Result.success;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class ClockTest {

  @Test
  void shouldReturnCurrentTime(@Mock Clock.Service service) {
    when(service.currentTime()).thenReturn(123456789012345L);

    var result = currentTime().eval(service);

    assertThat(result).isEqualTo(success(123456789012345L));
  }
}
