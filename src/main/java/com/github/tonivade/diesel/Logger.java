/*
 * Copyright (c) 2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Result.success;

public sealed interface Logger extends Program.Dsl<Logger.Service, Void, Void> {

  interface Service {
    void info(String message);
    void warn(String message);
    void error(String message, Throwable error);
  }

  record Info(String message) implements Logger {}
  record Warn(String message) implements Logger {}
  record Error(String message, Throwable error) implements Logger {}

  @SuppressWarnings("unchecked")
  static <S extends Service, E> Program<S, E, Void> info(String message) {
    return (Program<S, E, Void>) new Info(message);
  }

  @SuppressWarnings("unchecked")
  static <S extends Service, E> Program<S, E, Void> warn(String message) {
    return (Program<S, E, Void>) new Warn(message);
  }

  @SuppressWarnings("unchecked")
  static <S extends Service, E> Program<S, E, Void> error(String message, Throwable error) {
    return (Program<S, E, Void>) new Error(message, error);
  }

  @Override
  default Result<Void, Void> dslEval(Service state) {
    return success(switch (this) {
      case Info(var message) -> {
        state.info(message);
        yield null;
      }
      case Warn(var message) -> {
        state.warn(message);
        yield null;
      }
      case Error(var message, var error) -> {
        state.error(message, error);
        yield null;
      }
    });
  }
}
