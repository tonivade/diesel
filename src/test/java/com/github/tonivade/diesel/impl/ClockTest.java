/*
 * Copyright (c) 2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel.impl;

import static com.github.tonivade.diesel.Result.success;
import static com.github.tonivade.diesel.impl.Clock.currentTime;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.github.tonivade.diesel.Result;
import com.github.tonivade.diesel.impl.Clock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClockTest {

  @Test
  void shouldReturnCurrentTime(@Mock Clock.Service service) {
    when(service.currentTime()).thenReturn(123456789012345L);

    var result = currentTime().eval(service);

    assertThat(result).isEqualTo(success(123456789012345L));
  }
}
