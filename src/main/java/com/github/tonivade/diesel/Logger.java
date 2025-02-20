/*
 * Copyright (c) 2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Result.success;

/**
 * The Logger interface provides a domain-specific language (DSL) for logging operations.
 * It defines a set of logging actions that can be executed against a Logger service.
 *
 * @see Logger.Service
 */
public sealed interface Logger extends Program.Dsl<Logger.Service, Void, Void> {

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
   * Represents an informational logging action.
   *
   * @param message the message to log
   */
  record Info(String message) implements Logger {}

  /**
   * Represents a warning logging action.
   *
   * @param message the message to log
   */
  record Warn(String message) implements Logger {}

  /**
   * Represents an error logging action with an associated exception.
   *
   * @param message the message to log
   * @param error   the exception to log
   */
  record Error(String message, Throwable error) implements Logger {}

  /**
   * Creates a logging action for an informational message.
   *
   * @param message the message to log
   * @param <S>     the type of the Logger service
   * @param <E>     the type of the error
   * @return a Program that logs the message when executed
   */
  @SuppressWarnings("unchecked")
  static <S extends Service, E> Program<S, E, Void> info(String message) {
    return (Program<S, E, Void>) new Info(message);
  }

  /**
   * Creates a logging action for a warning message.
   *
   * @param message the message to log
   * @param <S>     the type of the Logger service
   * @param <E>     the type of the error
   * @return a Program that logs the message when executed
   */
  @SuppressWarnings("unchecked")
  static <S extends Service, E> Program<S, E, Void> warn(String message) {
    return (Program<S, E, Void>) new Warn(message);
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
  @SuppressWarnings("unchecked")
  static <S extends Service, E> Program<S, E, Void> error(String message, Throwable error) {
    return (Program<S, E, Void>) new Error(message, error);
  }

  /**
   * Evaluates the logging action against the provided Logger service.
   *
   * @param service the Logger service
   * @return a Result indicating the outcome of the logging operation
   */
  @Override
  default Result<Void, Void> handle(Service service) {
    return success(switch (this) {
      case Info(var message) -> {
        service.info(message);
        yield null;
      }
      case Warn(var message) -> {
        service.warn(message);
        yield null;
      }
      case Error(var message, var error) -> {
        service.error(message, error);
        yield null;
      }
    });
  }
}
