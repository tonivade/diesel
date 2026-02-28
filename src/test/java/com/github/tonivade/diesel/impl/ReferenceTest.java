/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel.impl;

import static com.github.tonivade.diesel.Result.success;
import static com.github.tonivade.diesel.impl.Reference.set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReferenceTest {

  @Test
  void shouldSetValue(@Mock Reference.Service<Integer> service) {

    set(10).eval(service);

    verify(service).set(10);
  }

  @Test
  void shouldGetValue(@Mock Reference.Service<Integer> service) {
    when(service.get()).thenReturn(10);

    var value = Reference.<Integer, Reference.Service<Integer>, Error>get().eval(service);

    assertThat(value).isEqualTo(success(10));
  }

  @Test
  void shouldUpdateValue(@Mock Reference.Service<Integer> service) {
    when(service.get()).thenReturn(10);

    Reference.<Integer, Reference.Service<Integer>, Error>update(x -> x + 1).eval(service);

    verify(service).set(11);
  }
}
