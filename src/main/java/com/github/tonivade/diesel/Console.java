/*
 * Copyright (c) 2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Result.success;
import static java.lang.System.console;

/**
 * Represents a console interface that can be used to write to the console or read from the console.
 *
 * @param <T> The type of result that will be returned from the console operations.
 */
public sealed interface Console<T> extends Program.Dsl<Console.Service, Void, T> {

  /**
   * Service interface that provides methods to write to the console and read from the console.
   */
  interface Service {
    /**
     * Writes a line of text to the console.
     *
     * @param line The line of text to write.
     */
    @SuppressWarnings("NullAway")
    default void writeLine(String line) {
      System.out.println(line);
    }

    /**
     * Reads a line of text from the console.
     *
     * @return The line of text that was read.
     */
    @SuppressWarnings("NullAway")
    default String readLine() {
      return console().readLine();
    }
  }

  /**
   * Represents a console operation that writes a line of text to the console.
   */
  record WriteLine(String line) implements Console<Void> {}

  /**
   * Represents a console operation that reads a line of text from the console.
   */
  record ReadLine() implements Console<String> {}

  /**
   * Creates a new console operation that writes a line of text to the console.
   *
   * @param line The line of text to write.
   * @param <S>  The type of service.
   * @param <E>  The type of error.
   * @return A new console operation that writes a line of text to the console.
   */
  @SuppressWarnings("unchecked")
  static <S extends Service, E> Program<S, E, Void> writeLine(String line) {
    return (Program<S, E, Void>) new WriteLine(line);
  }

  /**
   * Creates a new console operation that reads a line of text from the console.
   *
   * @param <S>  The type of service.
   * @param <E>  The type of error.
   * @return A new console operation that reads a line of text from the console.
   */
  @SuppressWarnings("unchecked")
  static <S extends Service, E> Program<S, E, String> readLine() {
    return (Program<S, E, String>) new ReadLine();
  }

  /**
   * Creates a new console operation that prompts the user with a question and reads their response.
   *
   * @param question The question to prompt the user with.
   * @param <S>      The type of service.
   * @param <E>      The type of error.
   * @return A new console operation that prompts the user with a question and reads their response.
   */
  static <S extends Service, E> Program<S, E, String> prompt(String question) {
    return Console.<S, E>writeLine(question).andThen(readLine());
  }

  /**
   * Creates a new console operation that writes a greeting message to the console.
   *
   * @param name The name to include in the greeting message.
   * @param <S>  The type of service.
   * @param <E>  The type of error.
   * @return A new console operation that writes a greeting message to the console.
   */
  static <S extends Service, E> Program<S, E, Void> sayHello(String name) {
    return writeLine("Hello " + name);
  }

  /**
   * Creates a new console operation that prompts the user for their name and returns their response.
   *
   * @param <S>  The type of service.
   * @param <E>  The type of error.
   * @return A new console operation that prompts the user for their name and returns their response.
   */
  static <S extends Service, E> Program<S, E, String> whatsYourName() {
    return prompt("What's your name?");
  }

  /**
   * Evaluates this console operation using the provided service.
   *
   * @param service The service to use for evaluation.
   * @return The result of the evaluation.
   */
  @Override
  @SuppressWarnings("unchecked")
  default Result<Void, T> handle(Service service) {
    return success((T) switch (this) {
      case WriteLine(var line) -> {
        service.writeLine(line);
        yield null;
      }
      case ReadLine() -> service.readLine();
    });
  }
}
