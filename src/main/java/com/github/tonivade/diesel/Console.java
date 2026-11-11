/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Combine.pipe;
import static com.github.tonivade.diesel.Program.accessProgram;
import static com.github.tonivade.diesel.Program.inspect;
import static com.github.tonivade.diesel.Program.raise;
import static com.github.tonivade.diesel.Program.success;

import java.io.EOFException;
import java.io.IOException;

/**
 * Represents a console interface that can be used to write to the console or read from the console.
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
     * @throws EOFException at the end of stream
     */
    default String readLine() throws EOFException {
      var line = IO.readln();
      if (line == null) {
        throw new EOFException();
      }
      return line;
    }
  }

  /**
   * Creates a new console operation that writes a line of text to the console.
   *
   * @param line The line of text to write.
   * @param <S> The type of service.
   * @param <E> The type of error.
   * @return A new console operation that writes a line of text to the console.
   */
  static <S extends Service, E> Program<S, E, Unit> writeLine(String line) {
    return inspect(state -> state.writeLine(line));
  }

  /**
   * Creates a new console operation that format and writes a line of text to the console.
   *
   * @param line The line of text to write with the format.
   * @param params arguments for the formatter
   * @param <S> The type of service.
   * @param <E> The type of error.
   * @return A new console operation that format and writes a line of text to the console.
   */
  static <S extends Service, E> Program<S, E, Unit> writeLine(String line, Object...params) {
    return inspect(state -> state.writeLine(line.formatted(params)));
  }

  /**
   * Creates a new console operation that reads a line of text from the console.
   *
   * @param <S> The type of service.
   * @param <E> The type of error.
   * @return A new console operation that reads a line of text from the console.
   */
  static <S extends Service, E> Program<S, E, String> readLine() {
    return accessProgram(s -> {
      try {
        return success(s.readLine());
      } catch (IOException e) {
        return raise(() -> e);
      }
    });
  }

  /**
   * Creates a new console operation that prompts the user with a question and reads their response.
   *
   * @param question The question to prompt the user with.
   * @param <S> The type of service.
   * @param <E> The type of error.
   * @return A new console operation that prompts the user with a question and reads their response.
   */
  static <S extends Service, E> Program<S, E, String> prompt(String question) {
    return pipe(writeLine(question), _ -> readLine());
  }
}
