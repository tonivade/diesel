/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel.impl;

import com.github.tonivade.diesel.Program;

/**
 * The Logger interface provides a domain-specific language (DSL) for logging operations.
 * It defines a set of logging actions that can be executed against a Logger service.
 *
 * @see Logger.Service
 */
public interface Logger {

  /**
   * The Logger service interface defines the methods available for logging.
   */
  interface Service {
    /**
     * Logs an informational message.
     *
     * @param message the message to log
     */
    void info(String message);

    /**
     * Logs a warning message.
     *
     * @param message the message to log
     */
    void warn(String message);

    /**
     * Logs an error message with an associated exception.
     *
     * @param message the message to log
     * @param error  the exception to log
     */
    void error(String message, Throwable error);
  }

  /**
   * Creates a logging action for an informational message.
   *
   * @param message the message to log
   * @param <S>     the type of the Logger service
   * @param <E>     the type of the error
   * @return a Program that logs the message when executed
   */
  static <S extends Service, E> Program<S, E, Void> info(String message) {
    return Program.effect(state -> {
      state.info(message);
      return null;
    });
  }

  /**
   * Creates a logging action for a warning message.
   *
   * @param message the message to log
   * @param <S>     the type of the Logger service
   * @param <E>     the type of the error
   * @return a Program that logs the message when executed
   */
  static <S extends Service, E> Program<S, E, Void> warn(String message) {
    return Program.effect(state -> {
      state.warn(message);
      return null;
    });
  }

  /**
   * Creates a logging action for an error message with an associated exception.
   *
   * @param message the message to log
   * @param error   the exception to log
   * @param <S>     the type of the Logger service
   * @param <E>     the type of the error
   * @return a Program that logs the message and exception when executed
   */
  static <S extends Service, E> Program<S, E, Void> error(String message, Throwable error) {
    return Program.effect(state -> {
      state.error(message, error);
      return null;
    });
  }
}
