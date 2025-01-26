/*
 * Copyright (c) 2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Result.success;
import static java.lang.System.console;

import org.jspecify.annotations.Nullable;

public sealed interface Console<T> extends Program.Dsl<Console.Service, Void, T> {

  interface Service {
    @SuppressWarnings({"preview", "NullAway"})
    default void writeLine(String line) {
      console().println(line);
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
    Program<S, E, Void> writeLine = writeLine(question);
    return writeLine.andThen(readLine());
  }

  static <S extends Service, E> Program<S, E, Void> sayHello(String name) {
    return writeLine("Hello " + name);
  }

  static <S extends Service, E> Program<S, E, String> whatsYourName() {
    return prompt("What's your name?");
  }

  @Override
  @SuppressWarnings("unchecked")
  @Nullable
  default Result<Void, T> eval(Service service) {
    var result = (T) switch (this) {
      case WriteLine(var line) -> {
        service.writeLine(line);
        yield null;
      }
      case ReadLine _ -> service.readLine();
    };
    return success(result);
  }
}
