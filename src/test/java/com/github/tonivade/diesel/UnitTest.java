/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonviade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UnitTest {

  @Test
  void unitIsASingleton() {
    assertThat(Unit.unit()).isSameAs(Unit.INSTANCE);
  }

}
