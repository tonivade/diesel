/*
 * Copyright (c) 2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static java.lang.System.console;

public sealed interface Console<T> extends Program.Dsl<Console.Service, T> {

  interface Service {
    @SuppressWarnings("preview")
    default void writeLine(String line) {
      console().println(line);
    }

    default String readLine() {
      return console().readLine();
    }
  }

  record WriteLine(String line) implements Console<Void> {}
  record ReadLine() implements Console<String> {}

  @SuppressWarnings("unchecked")
  static <S extends Service> Program<S, Void> writeLine(String line) {
    return (Program<S, Void>) new WriteLine(line);
  }

  @SuppressWarnings("unchecked")
  static <S extends Service> Program<S, String> readLine() {
    return (Program<S, String>) new ReadLine();
  }

  static <S extends Service> Program<S, String> prompt(String question) {
    Program<S, Void> writeLine = writeLine(question);
    return writeLine.andThen(readLine());
  }

  static <S extends Service> Program<S, Void> sayHello(String name) {
    return writeLine("Hello " + name);
  }

  static <S extends Service> Program<S, String> whatsYourName() {
    return prompt("What's your name?");
  }

  @Override
  @SuppressWarnings("unchecked")
  default T eval(Service service) {
    return (T) switch (this) {
      case WriteLine(var line) -> {
        service.writeLine(line);
        yield null;
      }
      case ReadLine _ -> service.readLine();
    };
  }
}
