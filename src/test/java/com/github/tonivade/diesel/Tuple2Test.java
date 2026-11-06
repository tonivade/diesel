/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Tuple2.tuple;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class Tuple2Test {

  @Test
  void holdsBothValues() {
    var tuple = tuple("a", 1);

    assertThat(tuple.first()).isEqualTo("a");
    assertThat(tuple.second()).isEqualTo(1);
  }

  @Test
  @SuppressWarnings("NullAway") // NullAway already rejects it at compile time, but not every caller uses it
  void rejectsANullFirstValue() {
    assertThatThrownBy(() -> tuple(null, "b"))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @SuppressWarnings("NullAway") // NullAway already rejects it at compile time, but not every caller uses it
  void rejectsANullSecondValue() {
    assertThatThrownBy(() -> tuple("a", null))
        .isInstanceOf(NullPointerException.class);
  }
}
