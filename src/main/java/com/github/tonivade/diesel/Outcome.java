/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static java.util.Objects.requireNonNull;

/**
 * How a program ended: with a value, with a typed error, with an exception, or cancelled.
 *
 * <p>Unlike {@link Result}, which only describes a value or a typed error, an outcome also
 * describes the two ways a program can end outside its error channel.
 *
 * @param <E> the type of the error
 * @param <T> the type of the value
 */
public sealed interface Outcome<E, T> {

  /**
   * The program ended with a value.
   *
   * @param value the value, not {@code null}
   * @param <E> the type of the error
   * @param <T> the type of the value
   */
  record Succeeded<E, T>(T value) implements Outcome<E, T> {

    /**
     * Creates the outcome, rejecting null.
     *
     * @param value the value
     * @throws NullPointerException if the value is {@code null}
     */
    public Succeeded {
      requireNonNull(value);
    }
  }

  /**
   * The program ended with a typed error.
   *
   * @param error the error, not {@code null}
   * @param <E> the type of the error
   * @param <T> the type of the value
   */
  record Failed<E, T>(E error) implements Outcome<E, T> {

    /**
     * Creates the outcome, rejecting null.
     *
     * @param error the error
     * @throws NullPointerException if the error is {@code null}
     */
    public Failed {
      requireNonNull(error);
    }
  }

  /**
   * The program ended with an exception.
   *
   * @param exception the exception, not {@code null}
   * @param <E> the type of the error
   * @param <T> the type of the value
   */
  record Errored<E, T>(Throwable exception) implements Outcome<E, T> {

    /**
     * Creates the outcome, rejecting null.
     *
     * @param exception the exception
     * @throws NullPointerException if the exception is {@code null}
     */
    public Errored {
      requireNonNull(exception);
    }
  }

  /**
   * The program was cancelled.
   *
   * @param <E> the type of the error
   * @param <T> the type of the value
   */
  record Canceled<E, T>() implements Outcome<E, T> {}

  /**
   * Creates the outcome of a program that ended with a value.
   *
   * @param value the value
   * @param <E> the type of the error
   * @param <T> the type of the value
   * @return a {@link Succeeded} outcome
   */
  static <E, T> Outcome<E, T> succeeded(T value) {
    return new Succeeded<>(value);
  }

  /**
   * Creates the outcome of a program that ended with a typed error.
   *
   * @param error the error
   * @param <E> the type of the error
   * @param <T> the type of the value
   * @return a {@link Failed} outcome
   */
  static <E, T> Outcome<E, T> failed(E error) {
    return new Failed<>(error);
  }

  /**
   * Creates the outcome of a program that ended with an exception.
   *
   * @param exception the exception
   * @param <E> the type of the error
   * @param <T> the type of the value
   * @return an {@link Errored} outcome
   */
  static <E, T> Outcome<E, T> errored(Throwable exception) {
    return new Errored<>(exception);
  }

  /**
   * Creates the outcome of a program that was cancelled.
   *
   * @param <E> the type of the error
   * @param <T> the type of the value
   * @return a {@link Canceled} outcome
   */
  static <E, T> Outcome<E, T> canceled() {
    return new Canceled<>();
  }

  /**
   * Converts a result into the outcome of a program that ended normally.
   *
   * @param result the result of the program
   * @param <E> the type of the error
   * @param <T> the type of the value
   * @return a {@link Succeeded} or a {@link Failed} outcome
   */
  static <E, T> Outcome<E, T> from(Result<E, T> result) {
    return result.fold(Outcome::failed, Outcome::succeeded);
  }
}
