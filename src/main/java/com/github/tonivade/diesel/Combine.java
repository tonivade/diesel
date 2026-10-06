/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import com.github.tonivade.diesel.function.Finisher2;
import com.github.tonivade.diesel.function.Finisher3;
import com.github.tonivade.diesel.function.Finisher4;
import com.github.tonivade.diesel.function.Finisher5;
import com.github.tonivade.diesel.function.Finisher6;
import com.github.tonivade.diesel.function.Finisher7;
import com.github.tonivade.diesel.function.Finisher8;
import com.github.tonivade.diesel.function.Finisher9;

import java.util.function.Function;

/**
 * Sequential combinators to compose {@link Program}s.
 */
public final class Combine {

  private Combine() {}

  /**
   * Executes the given program and passes its result to the next function, which returns
   * the next program to execute, and so on, returning the result of the last one.
   * <p>
   * Equivalent to chaining {@code flatMap} calls. The execution stops at the first program that
   * fails, and the resulting program fails with that error.
   *
   * @param p0 the program to execute first
   * @param p1 the function that receives the result of the previous step and returns the next program
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T0> the result type of {@code p0}
   * @param <T1> the result type of step {@code p1}
   * @return a new program with the result of the last step
   */
  public static <S, E, T0, T1> Program<S, E, T1> pipe(
      Program<S, E, T0> p0,
      Function<? super T0, ? extends Program<S, E, T1>> p1) {
    return p0.flatMap(p1);
  }

  /**
   * Executes the given program and passes its result to the next function, which returns
   * the next program to execute, and so on, returning the result of the last one.
   * <p>
   * Equivalent to chaining {@code flatMap} calls. The execution stops at the first program that
   * fails, and the resulting program fails with that error.
   *
   * @param p0 the program to execute first
   * @param p1 the function that receives the result of the previous step and returns the next program
   * @param p2 the function that receives the result of the previous step and returns the next program
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T0> the result type of {@code p0}
   * @param <T1> the result type of step {@code p1}
   * @param <T2> the result type of step {@code p2}
   * @return a new program with the result of the last step
   */
  public static <S, E, T0, T1, T2> Program<S, E, T2> pipe(
      Program<S, E, T0> p0,
      Function<? super T0, ? extends Program<S, E, T1>> p1,
          Function<? super T1, ? extends Program<S, E, T2>> p2) {
    return p0.flatMap(p1).flatMap(p2);
  }

  /**
   * Executes the given program and passes its result to the next function, which returns
   * the next program to execute, and so on, returning the result of the last one.
   * <p>
   * Equivalent to chaining {@code flatMap} calls. The execution stops at the first program that
   * fails, and the resulting program fails with that error.
   *
   * @param p0 the program to execute first
   * @param p1 the function that receives the result of the previous step and returns the next program
   * @param p2 the function that receives the result of the previous step and returns the next program
   * @param p3 the function that receives the result of the previous step and returns the next program
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T0> the result type of {@code p0}
   * @param <T1> the result type of step {@code p1}
   * @param <T2> the result type of step {@code p2}
   * @param <T3> the result type of step {@code p3}
   * @return a new program with the result of the last step
   */
  public static <S, E, T0, T1, T2, T3> Program<S, E, T3> pipe(
      Program<S, E, T0> p0,
      Function<? super T0, ? extends Program<S, E, T1>> p1,
          Function<? super T1, ? extends Program<S, E, T2>> p2,
              Function<? super T2, ? extends Program<S, E, T3>> p3) {
    return p0.flatMap(p1).flatMap(p2).flatMap(p3);
  }

  /**
   * Executes the given program and passes its result to the next function, which returns
   * the next program to execute, and so on, returning the result of the last one.
   * <p>
   * Equivalent to chaining {@code flatMap} calls. The execution stops at the first program that
   * fails, and the resulting program fails with that error.
   *
   * @param p0 the program to execute first
   * @param p1 the function that receives the result of the previous step and returns the next program
   * @param p2 the function that receives the result of the previous step and returns the next program
   * @param p3 the function that receives the result of the previous step and returns the next program
   * @param p4 the function that receives the result of the previous step and returns the next program
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T0> the result type of {@code p0}
   * @param <T1> the result type of step {@code p1}
   * @param <T2> the result type of step {@code p2}
   * @param <T3> the result type of step {@code p3}
   * @param <T4> the result type of step {@code p4}
   * @return a new program with the result of the last step
   */
  public static <S, E, T0, T1, T2, T3, T4> Program<S, E, T4> pipe(
      Program<S, E, T0> p0,
      Function<? super T0, ? extends Program<S, E, T1>> p1,
          Function<? super T1, ? extends Program<S, E, T2>> p2,
              Function<? super T2, ? extends Program<S, E, T3>> p3,
                  Function<? super T3, ? extends Program<S, E, T4>> p4) {
    return p0.flatMap(p1).flatMap(p2).flatMap(p3).flatMap(p4);
  }

  /**
   * Executes the given program and passes its result to the next function, which returns
   * the next program to execute, and so on, returning the result of the last one.
   * <p>
   * Equivalent to chaining {@code flatMap} calls. The execution stops at the first program that
   * fails, and the resulting program fails with that error.
   *
   * @param p0 the program to execute first
   * @param p1 the function that receives the result of the previous step and returns the next program
   * @param p2 the function that receives the result of the previous step and returns the next program
   * @param p3 the function that receives the result of the previous step and returns the next program
   * @param p4 the function that receives the result of the previous step and returns the next program
   * @param p5 the function that receives the result of the previous step and returns the next program
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T0> the result type of {@code p0}
   * @param <T1> the result type of step {@code p1}
   * @param <T2> the result type of step {@code p2}
   * @param <T3> the result type of step {@code p3}
   * @param <T4> the result type of step {@code p4}
   * @param <T5> the result type of step {@code p5}
   * @return a new program with the result of the last step
   */
  public static <S, E, T0, T1, T2, T3, T4, T5> Program<S, E, T5> pipe(
      Program<S, E, T0> p0,
      Function<? super T0, ? extends Program<S, E, T1>> p1,
          Function<? super T1, ? extends Program<S, E, T2>> p2,
              Function<? super T2, ? extends Program<S, E, T3>> p3,
                  Function<? super T3, ? extends Program<S, E, T4>> p4,
                      Function<? super T4, ? extends Program<S, E, T5>> p5) {
    return p0.flatMap(p1).flatMap(p2).flatMap(p3).flatMap(p4).flatMap(p5);
  }

  /**
   * Executes the given program and passes its result to the next function, which returns
   * the next program to execute, and so on, returning the result of the last one.
   * <p>
   * Equivalent to chaining {@code flatMap} calls. The execution stops at the first program that
   * fails, and the resulting program fails with that error.
   *
   * @param p0 the program to execute first
   * @param p1 the function that receives the result of the previous step and returns the next program
   * @param p2 the function that receives the result of the previous step and returns the next program
   * @param p3 the function that receives the result of the previous step and returns the next program
   * @param p4 the function that receives the result of the previous step and returns the next program
   * @param p5 the function that receives the result of the previous step and returns the next program
   * @param p6 the function that receives the result of the previous step and returns the next program
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T0> the result type of {@code p0}
   * @param <T1> the result type of step {@code p1}
   * @param <T2> the result type of step {@code p2}
   * @param <T3> the result type of step {@code p3}
   * @param <T4> the result type of step {@code p4}
   * @param <T5> the result type of step {@code p5}
   * @param <T6> the result type of step {@code p6}
   * @return a new program with the result of the last step
   */
  public static <S, E, T0, T1, T2, T3, T4, T5, T6> Program<S, E, T6> pipe(
      Program<S, E, T0> p0,
      Function<? super T0, ? extends Program<S, E, T1>> p1,
          Function<? super T1, ? extends Program<S, E, T2>> p2,
              Function<? super T2, ? extends Program<S, E, T3>> p3,
                  Function<? super T3, ? extends Program<S, E, T4>> p4,
                      Function<? super T4, ? extends Program<S, E, T5>> p5,
                          Function<? super T5, ? extends Program<S, E, T6>> p6) {
    return p0.flatMap(p1).flatMap(p2).flatMap(p3).flatMap(p4).flatMap(p5).flatMap(p6);
  }

  /**
   * Executes the given program and passes its result to the next function, which returns
   * the next program to execute, and so on, returning the result of the last one.
   * <p>
   * Equivalent to chaining {@code flatMap} calls. The execution stops at the first program that
   * fails, and the resulting program fails with that error.
   *
   * @param p0 the program to execute first
   * @param p1 the function that receives the result of the previous step and returns the next program
   * @param p2 the function that receives the result of the previous step and returns the next program
   * @param p3 the function that receives the result of the previous step and returns the next program
   * @param p4 the function that receives the result of the previous step and returns the next program
   * @param p5 the function that receives the result of the previous step and returns the next program
   * @param p6 the function that receives the result of the previous step and returns the next program
   * @param p7 the function that receives the result of the previous step and returns the next program
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T0> the result type of {@code p0}
   * @param <T1> the result type of step {@code p1}
   * @param <T2> the result type of step {@code p2}
   * @param <T3> the result type of step {@code p3}
   * @param <T4> the result type of step {@code p4}
   * @param <T5> the result type of step {@code p5}
   * @param <T6> the result type of step {@code p6}
   * @param <T7> the result type of step {@code p7}
   * @return a new program with the result of the last step
   */
  public static <S, E, T0, T1, T2, T3, T4, T5, T6, T7> Program<S, E, T7> pipe(
      Program<S, E, T0> p0,
      Function<? super T0, ? extends Program<S, E, T1>> p1,
          Function<? super T1, ? extends Program<S, E, T2>> p2,
              Function<? super T2, ? extends Program<S, E, T3>> p3,
                  Function<? super T3, ? extends Program<S, E, T4>> p4,
                      Function<? super T4, ? extends Program<S, E, T5>> p5,
                          Function<? super T5, ? extends Program<S, E, T6>> p6,
                              Function<? super T6, ? extends Program<S, E, T7>> p7) {
    return p0.flatMap(p1).flatMap(p2).flatMap(p3).flatMap(p4).flatMap(p5).flatMap(p6).flatMap(p7);
  }

  /**
   * Executes the given program and passes its result to the next function, which returns
   * the next program to execute, and so on, returning the result of the last one.
   * <p>
   * Equivalent to chaining {@code flatMap} calls. The execution stops at the first program that
   * fails, and the resulting program fails with that error.
   *
   * @param p0 the program to execute first
   * @param p1 the function that receives the result of the previous step and returns the next program
   * @param p2 the function that receives the result of the previous step and returns the next program
   * @param p3 the function that receives the result of the previous step and returns the next program
   * @param p4 the function that receives the result of the previous step and returns the next program
   * @param p5 the function that receives the result of the previous step and returns the next program
   * @param p6 the function that receives the result of the previous step and returns the next program
   * @param p7 the function that receives the result of the previous step and returns the next program
   * @param p8 the function that receives the result of the previous step and returns the next program
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T0> the result type of {@code p0}
   * @param <T1> the result type of step {@code p1}
   * @param <T2> the result type of step {@code p2}
   * @param <T3> the result type of step {@code p3}
   * @param <T4> the result type of step {@code p4}
   * @param <T5> the result type of step {@code p5}
   * @param <T6> the result type of step {@code p6}
   * @param <T7> the result type of step {@code p7}
   * @param <T8> the result type of step {@code p8}
   * @return a new program with the result of the last step
   */
  public static <S, E, T0, T1, T2, T3, T4, T5, T6, T7, T8> Program<S, E, T8> pipe(
      Program<S, E, T0> p0,
      Function<? super T0, ? extends Program<S, E, T1>> p1,
          Function<? super T1, ? extends Program<S, E, T2>> p2,
              Function<? super T2, ? extends Program<S, E, T3>> p3,
                  Function<? super T3, ? extends Program<S, E, T4>> p4,
                      Function<? super T4, ? extends Program<S, E, T5>> p5,
                          Function<? super T5, ? extends Program<S, E, T6>> p6,
                              Function<? super T6, ? extends Program<S, E, T7>> p7,
                                  Function<? super T7, ? extends Program<S, E, T8>> p8) {
    return p0.flatMap(p1).flatMap(p2).flatMap(p3).flatMap(p4).flatMap(p5).flatMap(p6).flatMap(p7).flatMap(p8);
  }

  /**
   * Executes the given program and transforms its result by applying the given functions
   * in order, each one receiving the result of the previous one.
   * <p>
   * Equivalent to chaining {@code map} calls. If the program fails, the functions are not applied
   * and the resulting program fails with that error.
   *
   * @param p0 the program to execute first
   * @param p1 the function that transforms the result of the previous step
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T0> the result type of {@code p0}
   * @param <T1> the result type of step {@code p1}
   * @return a new program with the result of the last step
   */
  public static <S, E, T0, T1> Program<S, E, T1> chain(
      Program<S, E, T0> p0,
      Function<? super T0, ? extends T1> p1) {
    return p0.map(p1);
  }

  /**
   * Executes the given program and transforms its result by applying the given functions
   * in order, each one receiving the result of the previous one.
   * <p>
   * Equivalent to chaining {@code map} calls. If the program fails, the functions are not applied
   * and the resulting program fails with that error.
   *
   * @param p0 the program to execute first
   * @param p1 the function that transforms the result of the previous step
   * @param p2 the function that transforms the result of the previous step
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T0> the result type of {@code p0}
   * @param <T1> the result type of step {@code p1}
   * @param <T2> the result type of step {@code p2}
   * @return a new program with the result of the last step
   */
  public static <S, E, T0, T1, T2> Program<S, E, T2> chain(
      Program<S, E, T0> p0,
      Function<? super T0, ? extends T1> p1,
      Function<? super T1, ? extends T2> p2) {
    return p0.map(p1).map(p2);
  }

  /**
   * Executes the given program and transforms its result by applying the given functions
   * in order, each one receiving the result of the previous one.
   * <p>
   * Equivalent to chaining {@code map} calls. If the program fails, the functions are not applied
   * and the resulting program fails with that error.
   *
   * @param p0 the program to execute first
   * @param p1 the function that transforms the result of the previous step
   * @param p2 the function that transforms the result of the previous step
   * @param p3 the function that transforms the result of the previous step
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T0> the result type of {@code p0}
   * @param <T1> the result type of step {@code p1}
   * @param <T2> the result type of step {@code p2}
   * @param <T3> the result type of step {@code p3}
   * @return a new program with the result of the last step
   */
  public static <S, E, T0, T1, T2, T3> Program<S, E, T3> chain(
      Program<S, E, T0> p0,
      Function<? super T0, ? extends T1> p1,
      Function<? super T1, ? extends T2> p2,
      Function<? super T2, ? extends T3> p3) {
    return p0.map(p1).map(p2).map(p3);
  }

  /**
   * Executes the given program and transforms its result by applying the given functions
   * in order, each one receiving the result of the previous one.
   * <p>
   * Equivalent to chaining {@code map} calls. If the program fails, the functions are not applied
   * and the resulting program fails with that error.
   *
   * @param p0 the program to execute first
   * @param p1 the function that transforms the result of the previous step
   * @param p2 the function that transforms the result of the previous step
   * @param p3 the function that transforms the result of the previous step
   * @param p4 the function that transforms the result of the previous step
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T0> the result type of {@code p0}
   * @param <T1> the result type of step {@code p1}
   * @param <T2> the result type of step {@code p2}
   * @param <T3> the result type of step {@code p3}
   * @param <T4> the result type of step {@code p4}
   * @return a new program with the result of the last step
   */
  public static <S, E, T0, T1, T2, T3, T4> Program<S, E, T4> chain(
      Program<S, E, T0> p0,
      Function<? super T0, ? extends T1> p1,
      Function<? super T1, ? extends T2> p2,
      Function<? super T2, ? extends T3> p3,
      Function<? super T3, ? extends T4> p4) {
    return p0.map(p1).map(p2).map(p3).map(p4);
  }

  /**
   * Executes the given program and transforms its result by applying the given functions
   * in order, each one receiving the result of the previous one.
   * <p>
   * Equivalent to chaining {@code map} calls. If the program fails, the functions are not applied
   * and the resulting program fails with that error.
   *
   * @param p0 the program to execute first
   * @param p1 the function that transforms the result of the previous step
   * @param p2 the function that transforms the result of the previous step
   * @param p3 the function that transforms the result of the previous step
   * @param p4 the function that transforms the result of the previous step
   * @param p5 the function that transforms the result of the previous step
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T0> the result type of {@code p0}
   * @param <T1> the result type of step {@code p1}
   * @param <T2> the result type of step {@code p2}
   * @param <T3> the result type of step {@code p3}
   * @param <T4> the result type of step {@code p4}
   * @param <T5> the result type of step {@code p5}
   * @return a new program with the result of the last step
   */
  public static <S, E, T0, T1, T2, T3, T4, T5> Program<S, E, T5> chain(
      Program<S, E, T0> p0,
      Function<? super T0, ? extends T1> p1,
      Function<? super T1, ? extends T2> p2,
      Function<? super T2, ? extends T3> p3,
      Function<? super T3, ? extends T4> p4,
      Function<? super T4, ? extends T5> p5) {
    return p0.map(p1).map(p2).map(p3).map(p4).map(p5);
  }

  /**
   * Executes the given program and transforms its result by applying the given functions
   * in order, each one receiving the result of the previous one.
   * <p>
   * Equivalent to chaining {@code map} calls. If the program fails, the functions are not applied
   * and the resulting program fails with that error.
   *
   * @param p0 the program to execute first
   * @param p1 the function that transforms the result of the previous step
   * @param p2 the function that transforms the result of the previous step
   * @param p3 the function that transforms the result of the previous step
   * @param p4 the function that transforms the result of the previous step
   * @param p5 the function that transforms the result of the previous step
   * @param p6 the function that transforms the result of the previous step
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T0> the result type of {@code p0}
   * @param <T1> the result type of step {@code p1}
   * @param <T2> the result type of step {@code p2}
   * @param <T3> the result type of step {@code p3}
   * @param <T4> the result type of step {@code p4}
   * @param <T5> the result type of step {@code p5}
   * @param <T6> the result type of step {@code p6}
   * @return a new program with the result of the last step
   */
  public static <S, E, T0, T1, T2, T3, T4, T5, T6> Program<S, E, T6> chain(
      Program<S, E, T0> p0,
      Function<? super T0, ? extends T1> p1,
      Function<? super T1, ? extends T2> p2,
      Function<? super T2, ? extends T3> p3,
      Function<? super T3, ? extends T4> p4,
      Function<? super T4, ? extends T5> p5,
      Function<? super T5, ? extends T6> p6) {
    return p0.map(p1).map(p2).map(p3).map(p4).map(p5).map(p6);
  }

  /**
   * Executes the given program and transforms its result by applying the given functions
   * in order, each one receiving the result of the previous one.
   * <p>
   * Equivalent to chaining {@code map} calls. If the program fails, the functions are not applied
   * and the resulting program fails with that error.
   *
   * @param p0 the program to execute first
   * @param p1 the function that transforms the result of the previous step
   * @param p2 the function that transforms the result of the previous step
   * @param p3 the function that transforms the result of the previous step
   * @param p4 the function that transforms the result of the previous step
   * @param p5 the function that transforms the result of the previous step
   * @param p6 the function that transforms the result of the previous step
   * @param p7 the function that transforms the result of the previous step
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T0> the result type of {@code p0}
   * @param <T1> the result type of step {@code p1}
   * @param <T2> the result type of step {@code p2}
   * @param <T3> the result type of step {@code p3}
   * @param <T4> the result type of step {@code p4}
   * @param <T5> the result type of step {@code p5}
   * @param <T6> the result type of step {@code p6}
   * @param <T7> the result type of step {@code p7}
   * @return a new program with the result of the last step
   */
  public static <S, E, T0, T1, T2, T3, T4, T5, T6, T7> Program<S, E, T7> chain(
      Program<S, E, T0> p0,
      Function<? super T0, ? extends T1> p1,
      Function<? super T1, ? extends T2> p2,
      Function<? super T2, ? extends T3> p3,
      Function<? super T3, ? extends T4> p4,
      Function<? super T4, ? extends T5> p5,
      Function<? super T5, ? extends T6> p6,
      Function<? super T6, ? extends T7> p7) {
    return p0.map(p1).map(p2).map(p3).map(p4).map(p5).map(p6).map(p7);
  }

  /**
   * Executes the given program and transforms its result by applying the given functions
   * in order, each one receiving the result of the previous one.
   * <p>
   * Equivalent to chaining {@code map} calls. If the program fails, the functions are not applied
   * and the resulting program fails with that error.
   *
   * @param p0 the program to execute first
   * @param p1 the function that transforms the result of the previous step
   * @param p2 the function that transforms the result of the previous step
   * @param p3 the function that transforms the result of the previous step
   * @param p4 the function that transforms the result of the previous step
   * @param p5 the function that transforms the result of the previous step
   * @param p6 the function that transforms the result of the previous step
   * @param p7 the function that transforms the result of the previous step
   * @param p8 the function that transforms the result of the previous step
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T0> the result type of {@code p0}
   * @param <T1> the result type of step {@code p1}
   * @param <T2> the result type of step {@code p2}
   * @param <T3> the result type of step {@code p3}
   * @param <T4> the result type of step {@code p4}
   * @param <T5> the result type of step {@code p5}
   * @param <T6> the result type of step {@code p6}
   * @param <T7> the result type of step {@code p7}
   * @param <T8> the result type of step {@code p8}
   * @return a new program with the result of the last step
   */
  public static <S, E, T0, T1, T2, T3, T4, T5, T6, T7, T8> Program<S, E, T8> chain(
      Program<S, E, T0> p0,
      Function<? super T0, ? extends T1> p1,
      Function<? super T1, ? extends T2> p2,
      Function<? super T2, ? extends T3> p3,
      Function<? super T3, ? extends T4> p4,
      Function<? super T4, ? extends T5> p5,
      Function<? super T5, ? extends T6> p6,
      Function<? super T6, ? extends T7> p7,
      Function<? super T7, ? extends T8> p8) {
    return p0.map(p1).map(p2).map(p3).map(p4).map(p5).map(p6).map(p7).map(p8);
  }

  /**
   * Executes the given programs sequentially and combines their results using the finisher function.
   * <p>
   * The execution stops at the first program that fails, and the resulting program fails with
   * that error. The programs after it are not executed.
   *
   * @param p0 a program to combine
   * @param p1 a program to combine
   * @param finisher the function used to combine the results
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T0> the success type of {@code p0}
   * @param <T1> the success type of {@code p1}
   * @param <R> the type of the combined result
   * @return a new program with the combined result, or the first failure
   */
  public static <S, E, T0, T1, R> Program<S, E, R> zip(
      Program<S, E, T0> p0,
      Program<S, E, T1> p1,
      Finisher2<T0, T1, R> finisher) {
    return p0.flatMap(_0 ->
    p1.map(_1 -> finisher.apply(_0, _1))
        );
  }

  /**
   * Executes the given programs sequentially and combines their results using the finisher function.
   * <p>
   * The execution stops at the first program that fails, and the resulting program fails with
   * that error. The programs after it are not executed.
   *
   * @param p0 a program to combine
   * @param p1 a program to combine
   * @param p2 a program to combine
   * @param finisher the function used to combine the results
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T0> the success type of {@code p0}
   * @param <T1> the success type of {@code p1}
   * @param <T2> the success type of {@code p2}
   * @param <R> the type of the combined result
   * @return a new program with the combined result, or the first failure
   */
  public static <S, E, T0, T1, T2, R> Program<S, E, R> zip(
      Program<S, E, T0> p0,
      Program<S, E, T1> p1,
      Program<S, E, T2> p2,
      Finisher3<T0, T1, T2, R> finisher) {
    return p0.flatMap(_0 ->
    p1.flatMap(_1 ->
    p2.map(_2 -> finisher.apply(_0, _1, _2))
        ));
  }

  /**
   * Executes the given programs sequentially and combines their results using the finisher function.
   * <p>
   * The execution stops at the first program that fails, and the resulting program fails with
   * that error. The programs after it are not executed.
   *
   * @param p0 a program to combine
   * @param p1 a program to combine
   * @param p2 a program to combine
   * @param p3 a program to combine
   * @param finisher the function used to combine the results
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T0> the success type of {@code p0}
   * @param <T1> the success type of {@code p1}
   * @param <T2> the success type of {@code p2}
   * @param <T3> the success type of {@code p3}
   * @param <R> the type of the combined result
   * @return a new program with the combined result, or the first failure
   */
  public static <S, E, T0, T1, T2, T3, R> Program<S, E, R> zip(
      Program<S, E, T0> p0,
      Program<S, E, T1> p1,
      Program<S, E, T2> p2,
      Program<S, E, T3> p3,
      Finisher4<T0, T1, T2, T3, R> finisher) {
    return p0.flatMap(_0 ->
    p1.flatMap(_1 ->
    p2.flatMap(_2 ->
    p3.map(_3 -> finisher.apply(_0, _1, _2, _3))
        )));
  }

  /**
   * Executes the given programs sequentially and combines their results using the finisher function.
   * <p>
   * The execution stops at the first program that fails, and the resulting program fails with
   * that error. The programs after it are not executed.
   *
   * @param p0 a program to combine
   * @param p1 a program to combine
   * @param p2 a program to combine
   * @param p3 a program to combine
   * @param p4 a program to combine
   * @param finisher the function used to combine the results
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T0> the success type of {@code p0}
   * @param <T1> the success type of {@code p1}
   * @param <T2> the success type of {@code p2}
   * @param <T3> the success type of {@code p3}
   * @param <T4> the success type of {@code p4}
   * @param <R> the type of the combined result
   * @return a new program with the combined result, or the first failure
   */
  public static <S, E, T0, T1, T2, T3, T4, R> Program<S, E, R> zip(
      Program<S, E, T0> p0,
      Program<S, E, T1> p1,
      Program<S, E, T2> p2,
      Program<S, E, T3> p3,
      Program<S, E, T4> p4,
      Finisher5<T0, T1, T2, T3, T4, R> finisher) {
    return p0.flatMap(_0 ->
    p1.flatMap(_1 ->
    p2.flatMap(_2 ->
    p3.flatMap(_3 ->
    p4.map(_4 -> finisher.apply(_0, _1, _2, _3, _4))
        ))));
  }

  /**
   * Executes the given programs sequentially and combines their results using the finisher function.
   * <p>
   * The execution stops at the first program that fails, and the resulting program fails with
   * that error. The programs after it are not executed.
   *
   * @param p0 a program to combine
   * @param p1 a program to combine
   * @param p2 a program to combine
   * @param p3 a program to combine
   * @param p4 a program to combine
   * @param p5 a program to combine
   * @param finisher the function used to combine the results
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T0> the success type of {@code p0}
   * @param <T1> the success type of {@code p1}
   * @param <T2> the success type of {@code p2}
   * @param <T3> the success type of {@code p3}
   * @param <T4> the success type of {@code p4}
   * @param <T5> the success type of {@code p5}
   * @param <R> the type of the combined result
   * @return a new program with the combined result, or the first failure
   */
  public static <S, E, T0, T1, T2, T3, T4, T5, R> Program<S, E, R> zip(
      Program<S, E, T0> p0,
      Program<S, E, T1> p1,
      Program<S, E, T2> p2,
      Program<S, E, T3> p3,
      Program<S, E, T4> p4,
      Program<S, E, T5> p5,
      Finisher6<T0, T1, T2, T3, T4, T5, R> finisher) {
    return p0.flatMap(_0 ->
    p1.flatMap(_1 ->
    p2.flatMap(_2 ->
    p3.flatMap(_3 ->
    p4.flatMap(_4 ->
    p5.map(_5 -> finisher.apply(_0, _1, _2, _3, _4, _5))
        )))));
  }

  /**
   * Executes the given programs sequentially and combines their results using the finisher function.
   * <p>
   * The execution stops at the first program that fails, and the resulting program fails with
   * that error. The programs after it are not executed.
   *
   * @param p0 a program to combine
   * @param p1 a program to combine
   * @param p2 a program to combine
   * @param p3 a program to combine
   * @param p4 a program to combine
   * @param p5 a program to combine
   * @param p6 a program to combine
   * @param finisher the function used to combine the results
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T0> the success type of {@code p0}
   * @param <T1> the success type of {@code p1}
   * @param <T2> the success type of {@code p2}
   * @param <T3> the success type of {@code p3}
   * @param <T4> the success type of {@code p4}
   * @param <T5> the success type of {@code p5}
   * @param <T6> the success type of {@code p6}
   * @param <R> the type of the combined result
   * @return a new program with the combined result, or the first failure
   */
  public static <S, E, T0, T1, T2, T3, T4, T5, T6, R> Program<S, E, R> zip(
      Program<S, E, T0> p0,
      Program<S, E, T1> p1,
      Program<S, E, T2> p2,
      Program<S, E, T3> p3,
      Program<S, E, T4> p4,
      Program<S, E, T5> p5,
      Program<S, E, T6> p6,
      Finisher7<T0, T1, T2, T3, T4, T5, T6, R> finisher) {
    return p0.flatMap(_0 ->
    p1.flatMap(_1 ->
    p2.flatMap(_2 ->
    p3.flatMap(_3 ->
    p4.flatMap(_4 ->
    p5.flatMap(_5 ->
    p6.map(_6 -> finisher.apply(_0, _1, _2, _3, _4, _5, _6))
        ))))));
  }

  /**
   * Executes the given programs sequentially and combines their results using the finisher function.
   * <p>
   * The execution stops at the first program that fails, and the resulting program fails with
   * that error. The programs after it are not executed.
   *
   * @param p0 a program to combine
   * @param p1 a program to combine
   * @param p2 a program to combine
   * @param p3 a program to combine
   * @param p4 a program to combine
   * @param p5 a program to combine
   * @param p6 a program to combine
   * @param p7 a program to combine
   * @param finisher the function used to combine the results
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T0> the success type of {@code p0}
   * @param <T1> the success type of {@code p1}
   * @param <T2> the success type of {@code p2}
   * @param <T3> the success type of {@code p3}
   * @param <T4> the success type of {@code p4}
   * @param <T5> the success type of {@code p5}
   * @param <T6> the success type of {@code p6}
   * @param <T7> the success type of {@code p7}
   * @param <R> the type of the combined result
   * @return a new program with the combined result, or the first failure
   */
  public static <S, E, T0, T1, T2, T3, T4, T5, T6, T7, R> Program<S, E, R> zip(
      Program<S, E, T0> p0,
      Program<S, E, T1> p1,
      Program<S, E, T2> p2,
      Program<S, E, T3> p3,
      Program<S, E, T4> p4,
      Program<S, E, T5> p5,
      Program<S, E, T6> p6,
      Program<S, E, T7> p7,
      Finisher8<T0, T1, T2, T3, T4, T5, T6, T7, R> finisher) {
    return p0.flatMap(_0 ->
    p1.flatMap(_1 ->
    p2.flatMap(_2 ->
    p3.flatMap(_3 ->
    p4.flatMap(_4 ->
    p5.flatMap(_5 ->
    p6.flatMap(_6 ->
    p7.map(_7 -> finisher.apply(_0, _1, _2, _3, _4, _5, _6, _7))
        )))))));
  }

  /**
   * Executes the given programs sequentially and combines their results using the finisher function.
   * <p>
   * The execution stops at the first program that fails, and the resulting program fails with
   * that error. The programs after it are not executed.
   *
   * @param p0 a program to combine
   * @param p1 a program to combine
   * @param p2 a program to combine
   * @param p3 a program to combine
   * @param p4 a program to combine
   * @param p5 a program to combine
   * @param p6 a program to combine
   * @param p7 a program to combine
   * @param p8 a program to combine
   * @param finisher the function used to combine the results
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T0> the success type of {@code p0}
   * @param <T1> the success type of {@code p1}
   * @param <T2> the success type of {@code p2}
   * @param <T3> the success type of {@code p3}
   * @param <T4> the success type of {@code p4}
   * @param <T5> the success type of {@code p5}
   * @param <T6> the success type of {@code p6}
   * @param <T7> the success type of {@code p7}
   * @param <T8> the success type of {@code p8}
   * @param <R> the type of the combined result
   * @return a new program with the combined result, or the first failure
   */
  public static <S, E, T0, T1, T2, T3, T4, T5, T6, T7, T8, R> Program<S, E, R> zip(
      Program<S, E, T0> p0,
      Program<S, E, T1> p1,
      Program<S, E, T2> p2,
      Program<S, E, T3> p3,
      Program<S, E, T4> p4,
      Program<S, E, T5> p5,
      Program<S, E, T6> p6,
      Program<S, E, T7> p7,
      Program<S, E, T8> p8,
      Finisher9<T0, T1, T2, T3, T4, T5, T6, T7, T8, R> finisher) {
    return p0.flatMap(_0 ->
    p1.flatMap(_1 ->
    p2.flatMap(_2 ->
    p3.flatMap(_3 ->
    p4.flatMap(_4 ->
    p5.flatMap(_5 ->
    p6.flatMap(_6 ->
    p7.flatMap(_7 ->
    p8.map(_8 -> finisher.apply(_0, _1, _2, _3, _4, _5, _6, _7, _8))
        ))))))));
  }
}
