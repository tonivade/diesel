/*
 * Copyright (c) 2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Result.success;
import static com.github.tonivade.diesel.Trampoline.done;

import java.util.concurrent.ThreadLocalRandom;

public sealed interface Random<T> extends Program.Dsl<Random.Service, Void, T> {

  public interface Service {
    default Integer nextInt(int bound) {
      return ThreadLocalRandom.current().nextInt(bound);
    }
  }

  record NextInt(int bound) implements Random<Integer> {}

  @SuppressWarnings("unchecked")
  static <S extends Service, E> Program<S, E, Integer> nextInt(int bound) {
    return (Program<S, E, Integer>) new NextInt(bound);
  }

  @Override
  @SuppressWarnings("unchecked")
  default Trampoline<Result<Void, T>> safeEval(Service state) {
    var result = (T) switch (this) {
      case NextInt(int bound) -> state.nextInt(bound);
    };
    return done(success(result));
  }
}
