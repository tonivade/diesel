/*
 * Copyright (c) 2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Result.success;
import static java.lang.System.console;

public sealed interface Console<T> extends Program.Dsl<Console.Service, Void, T> {

  interface Service {
    @SuppressWarnings("NullAway")
    default void writeLine(String line) {
      System.out.println(line);
    }

    @SuppressWarnings("NullAway")
    default String readLine() {
      return console().readLine();
    }
  }

  record WriteLine(String line) implements Console<Void> {}
  record ReadLine() implements Console<String> {}

  @SuppressWarnings("unchecked")
  static <S extends Service, E> Program<S, E, Void> writeLine(String line) {
    return (Program<S, E, Void>) new WriteLine(line);
  }

  @SuppressWarnings("unchecked")
  static <S extends Service, E> Program<S, E, String> readLine() {
    return (Program<S, E, String>) new ReadLine();
  }

  static <S extends Service, E> Program<S, E, String> prompt(String question) {
    return Console.<S, E>writeLine(question).andThen(readLine());
  }

  static <S extends Service, E> Program<S, E, Void> sayHello(String name) {
    return writeLine("Hello " + name);
  }

  static <S extends Service, E> Program<S, E, String> whatsYourName() {
    return prompt("What's your name?");
  }

  @Override
  @SuppressWarnings("unchecked")
  default Result<Void, T> dslEval(Service service) {
    return success((T) switch (this) {
      case WriteLine(var line) -> {
        service.writeLine(line);
        yield null;
      }
      case ReadLine() -> service.readLine();
    });
  }
}
