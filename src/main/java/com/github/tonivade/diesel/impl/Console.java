/*
 * Copyright (c) 2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel.impl;

import static com.github.tonivade.diesel.Program.pipe;
import static com.github.tonivade.diesel.Result.success;

import com.github.tonivade.diesel.Program;
import com.github.tonivade.diesel.Result;

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
    default void writeLine(String line) {
      IO.println(line);
    }

    /**
     * Reads a line of text from the console.
     *
     * @return The line of text that was read.
     */
    default String readLine() {
      return IO.readln();
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
    return pipe(writeLine(question), _ -> readLine());
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
