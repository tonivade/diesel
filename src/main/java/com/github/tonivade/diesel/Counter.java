package com.github.tonivade.diesel;

public sealed interface Counter<T> extends Program.Dsl<Counter.Service, T> {

  interface Service {
    Integer increment();
  }

  record Increment() implements Counter<Integer> {}

  @SuppressWarnings("unchecked")
  static <S extends Service> Program<S, Integer> increment() {
    return (Program<S, Integer>) new Increment();
  }

  @Override
  @SuppressWarnings("unchecked")
  default T eval(Service state) {
    return (T) switch (this) {
      case Increment _ -> state.increment();
    };
  }
}
