/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel.impl;

import static com.github.tonivade.diesel.Program.pipe;
import com.github.tonivade.diesel.Program;

/**
 * Represents a console interface that can be used to write to the console or read from the console.
 *
 * @param <T> The type of result that will be returned from the console operations.
 */
public interface Console {

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
   * Creates a new console operation that writes a line of text to the console.
   *
   * @param line The line of text to write.
   * @param <S>  The type of service.
   * @param <E>  The type of error.
   * @return A new console operation that writes a line of text to the console.
   */
  static <S extends Service, E> Program<S, E, Void> writeLine(String line) {
    return Program.access(state -> {
      state.writeLine(line);
      return null;
    });
  }

  /**
   * Creates a new console operation that reads a line of text from the console.
   *
   * @param <S>  The type of service.
   * @param <E>  The type of error.
   * @return A new console operation that reads a line of text from the console.
   */
  static <S extends Service, E> Program<S, E, String> readLine() {
    return Program.access(state -> state.readLine());
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
}
