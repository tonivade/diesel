/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonviade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ResultTest {

  @Test
  void resultUnitContainsUnitValue() {
    assertThat(Result.<String>unit())
        .isEqualTo(Result.success(Unit.unit()));
  }

  @Test
  @SuppressWarnings("NullAway")
  void successRejectsNull() {
    assertThatThrownBy(() -> Result.<String, String>success(null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @SuppressWarnings("NullAway")
  void failureRejectsNull() {
    assertThatThrownBy(() -> Result.<String, Integer>failure(null))
        .isInstanceOf(NullPointerException.class);
  }

}
