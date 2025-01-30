/*
 * Copyright (c) 2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Result.success;

public sealed interface Clock<T> extends Program.Dsl<Clock.Service, Void, T> {

  interface Service {
    Long currentTime();
  }

  record CurrentTime() implements Clock<Long> {}

  @SuppressWarnings("unchecked")
  static <S extends Service, E> Program<S, E, Long> currentTime() {
    return (Program<S, E, Long>) new CurrentTime();
  }

  @Override
  @SuppressWarnings("unchecked")
  default Result<Void, T> dslEval(Service state) {
    return success((T) switch (this) {
      case CurrentTime _ -> state.currentTime();
    });
  }
}
