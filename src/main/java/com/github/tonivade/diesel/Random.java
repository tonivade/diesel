package com.github.tonivade.diesel;

import java.util.concurrent.ThreadLocalRandom;

public sealed interface Random<T> extends Program.Dsl<Random.Service, T> {

  public interface Service {
    default Integer nextInt(int bound) {
      return ThreadLocalRandom.current().nextInt(bound);
    }
  }

  record NextInt(int bound) implements Random<Integer> {}

  @Override
  @SuppressWarnings("unchecked")
  default T eval(Service state) {
    return (T) switch (this) {
      case NextInt(int bound) -> state.nextInt(bound);
    };
  }
}
