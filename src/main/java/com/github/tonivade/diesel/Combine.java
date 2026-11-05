/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Program.supply;
import static com.github.tonivade.diesel.Program.suspend;
import static com.github.tonivade.diesel.Program.uncancelable;
import static com.github.tonivade.diesel.Program.unit;
import static java.util.function.Function.identity;

import com.github.tonivade.diesel.Program.Poll;
import com.github.tonivade.diesel.function.Finisher2;
import com.github.tonivade.diesel.function.Finisher3;
import com.github.tonivade.diesel.function.Finisher4;
import com.github.tonivade.diesel.function.Finisher5;
import com.github.tonivade.diesel.function.Finisher6;
import com.github.tonivade.diesel.function.Finisher7;
import com.github.tonivade.diesel.function.Finisher8;
import com.github.tonivade.diesel.function.Finisher9;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

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
    return p0.flatMap(_0 -> p1.map(_1 -> finisher.apply(_0, _1)));
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
    return p0.flatMap(_0 -> p1.flatMap(_1 -> p2.map(_2 -> finisher.apply(_0, _1, _2))));
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
    return p0.flatMap(_0 -> p1.flatMap(_1 -> p2.flatMap(_2 -> p3.map(_3 -> finisher.apply(_0, _1, _2, _3)))));
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
    return p0.flatMap(
        _0 -> p1.flatMap(_1 -> p2.flatMap(_2 -> p3.flatMap(_3 -> p4.map(_4 -> finisher.apply(_0, _1, _2, _3, _4))))));
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
    return p0.flatMap(_0 -> p1.flatMap(_1 -> p2
        .flatMap(_2 -> p3.flatMap(_3 -> p4.flatMap(_4 -> p5.map(_5 -> finisher.apply(_0, _1, _2, _3, _4, _5)))))));
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
    return p0.flatMap(_0 -> p1.flatMap(_1 -> p2.flatMap(_2 -> p3
        .flatMap(_3 -> p4.flatMap(_4 -> p5.flatMap(_5 -> p6.map(_6 -> finisher.apply(_0, _1, _2, _3, _4, _5, _6))))))));
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
    return p0.flatMap(_0 -> p1.flatMap(_1 -> p2.flatMap(_2 -> p3.flatMap(_3 -> p4.flatMap(
        _4 -> p5.flatMap(_5 -> p6.flatMap(_6 -> p7.map(_7 -> finisher.apply(_0, _1, _2, _3, _4, _5, _6, _7)))))))));
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
    return p0.flatMap(_0 -> p1.flatMap(_1 -> p2.flatMap(_2 -> p3.flatMap(_3 -> p4.flatMap(_4 -> p5.flatMap(_5 -> p6
        .flatMap(_6 -> p7.flatMap(_7 -> p8.map(_8 -> finisher.apply(_0, _1, _2, _3, _4, _5, _6, _7, _8))))))))));
  }

  /**
   * Chains all the given programs sequentially ignoring all their results.
   *
   * @param programs the programs to be chained
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @return a new program representing the chained computation
   */
  @SafeVarargs
  public static <S, E> Program<S, E, Unit> chainAll(Program<S, E, ?>... programs) {
    Program<S, E, Unit> result = unit();
    for (int i = programs.length - 1; i >= 0; i--) {
      result = programs[i].andThen(result);
    }
    return result;
  }

  /**
   * Sequences a collection of programs into a single program containing a collection of success values.
   *
   * @param programs the programs to be forked
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   * @return a collection of forked programs
   */
  @SafeVarargs
  public static <S, E, T> Program<S, E, Collection<T>> sequence(Program<S, E, T>... programs) {
    return sequence(List.of(programs));
  }

  /**
   * Sequences a collection of programs into a single program containing a collection of success values.
   *
   * @param programs the programs to be forked
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   * @return a collection of forked programs
   */
  public static <S, E, T> Program<S, E, Collection<T>> sequence(Collection<Program<S, E, T>> programs) {
    return traverse(identity(), programs);
  }

  /**
   * Traverses a collection of values, applying the provided function to each value
   * and sequencing the results into a single program containing a collection of success values.
   *
   * @param function the function used to map each value to a program
   * @param values the values to be traversed
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the input values
   * @param <R> the type of the result
   * @return a new program representing the traversed computation with sequenced results
   */
  @SafeVarargs
  public static <S, E, T, R> Program<S, E, Collection<R>> traverse(
      Function<? super T, ? extends Program<S, E, R>> function, T... values) {
    return traverse(function, List.of(values));
  }

  /**
   * Traverses a collection of values, applying the provided function to each value
   * and sequencing the results into a single program containing a collection of success values.
   *
   * @param function the function used to map each value to a program
   * @param values the values to be traversed
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the input values
   * @param <R> the type of the result
   * @return a new program representing the traversed computation with sequenced results
   */
  public static <S, E, T, R> Program<S, E, Collection<R>> traverse(
      Function<? super T, ? extends Program<S, E, R>> function, Collection<T> values) {
    // the accumulator is created on each evaluation, so the program can be evaluated more than once
    Program<S, E, Collection<R>> acc = supply(ArrayList::new);
    for (T t : values) {
      acc = append(acc, function.apply(t));
    }
    return acc;
  }

  /**
   * Catches all errors during the execution of the program and recovers using the provided function.
   *
   * @param program the program to be executed
   * @param recover the function used to recover from errors
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <F> the type of the new error
   * @param <T> the type of the result
   * @return a new program representing the computation with error handling
   */
  public static <S, E, F, T> Program<S, F, T> recover(Program<S, E, T> program,
      Function<? super E, ? extends Program<S, F, T>> recover) {
    return program.flatMapError(recover);
  }

  /**
   * Creates a new program that represents a computation that attempts to execute the given supplier and maps any
   * exceptions to errors using the provided function.
   *
   * @param supplier the supplier of the value
   * @param mapError the function used to map exceptions to errors
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   * @return a new program representing a computation that attempts to execute the supplier and maps exceptions to errors
   */
  public static <S, E, T> Program<S, E, T> attempt(
      Supplier<? extends T> supplier, Function<? super Throwable, ? extends E> mapError) {
    return recover(Program.attempt(supplier), mapError.andThen(Program::failure));
  }

  /**
   * Catches all errors during the execution of the program and recovers using the provided function.
   *
   * @param program the program to be executed
   * @param recover the function used to recover from errors
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   * @return a new program representing the computation with error handling
   */
  public static <S, E, T> Program<S, E, T> catchAll(Program<S, E, T> program,
      Function<? super Throwable, ? extends Program<S, E, T>> recover) {
    return program.catchAll(recover);
  }

  /**
   * Installs a finalizer in a program.
   *
   * @param program the program to install the finalizer
   * @param finalizer the finalizer to install in the program
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   * @return the same program but with the finalizer
   */
  public static <S, E, T> Program<S, E, T> withEnsuring(Program<S, E, T> program, Program<S, Void, ?> finalizer) {
    return program.ensuring(finalizer);
  }

  /**
   * Installs an on-cancel finalizer in a program.
   *
   * @param program the program to install the finalizer
   * @param finalizer the on-cancel finalizer to install in the program
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   * @return the same program but with the on-cancel finalizer
   */
  public static <S, E, T> Program<S, E, T> withOnCancel(Program<S, E, T> program, Program<S, Void, ?> finalizer) {
    return program.onCancel(finalizer);
  }

  /**
   * Creates a function that branches the program based on a condition.
   *
   * @param condition the condition used to branch the program
   * @param onTrue the function to be executed if the condition is true
   * @param otherwise the function to be executed if the condition is false
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the input value
   * @param <R> the type of the result
   * @return a function that branches the program based on a condition
   */
  public static <S, E, T, R> Function<T, Program<S, E, R>> branch(
      Predicate<? super T> condition,
      Function<? super T, ? extends Program<S, E, R>> onTrue,
      Function<? super T, ? extends Program<S, E, R>> otherwise) {
    return value -> {
      if (condition.test(value)) {
        return onTrue.apply(value);
      }
      return otherwise.apply(value);
    };
  }

  /**
   * Creates a function that branches the program based on a boolean condition.
   *
   * @param onTrue the program to be executed if the condition is true
   * @param otherwise the program to be executed if the condition is false
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the result
   * @return a function that branches the program based on a boolean condition
   */
  public static <S, E, T> Function<Boolean, Program<S, E, T>> branch(
      Supplier<? extends Program<S, E, T>> onTrue, Supplier<? extends Program<S, E, T>> otherwise) {
    return branch(r -> r, _ -> onTrue.get(), _ -> otherwise.get());
  }

  /**
   * Creates a new program that represents a computation that acquires a resource, uses it, and then releases it.
   *
   * @param acquire the supplier of the resource to be acquired
   * @param use the function used to use the acquired resource
   * @param <S> the type of the state
   * @param <T> the type of the resource
   * @param <R> the type of the result
   * @return a new program representing a computation that acquires a resource, uses it, and then releases it
   */
  public static <S, T extends AutoCloseable, R> Program<S, Throwable, R> bracket(
      Supplier<? extends T> acquire, Function<? super T, ? extends Program<S, Throwable, R>> use) {
    return bracket(
        Program.attempt(acquire),
        use,
        Combine::close);
  }

  /**
   * Creates a new program that represents a computation that acquires a resource, uses it, and then releases it
   *
   * <p>Acquiring and releasing the resource can't be cancelled, and once acquired the resource is
   * released whether {@code use} succeeds, fails, throws an exception or is cancelled.
   *
   * @param acquire the supplier of the resource to be acquired
   * @param use the function used to use the acquired resource
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the resource
   * @param <R> the type of the result
   * @return a new program representing a computation that acquires a resource, uses it, and then releases it
   */
  public static <S, E, T extends AutoCloseable, R> Program<S, E, R> bracket(
      Program<S, E, T> acquire,
      Function<? super T, ? extends Program<S, E, R>> use) {
    return bracket(acquire, use, Combine::close);
  }

  /**
   * Creates a new program that represents a computation that acquires a resource, uses it, and then releases it
   *
   * <p>Acquiring and releasing the resource can't be cancelled, and once acquired the resource is
   * released whether {@code use} succeeds, fails, throws an exception or is cancelled.
   *
   * @param acquire the supplier of the resource to be acquired
   * @param use the function used to use the acquired resource
   * @param release the function used to release the acquired resource. It can't fail with an
   *     error, only throw an exception, which replaces the result of {@code use}, or is added as
   *     suppressed to the exception thrown by {@code use}
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the resource
   * @param <R> the type of the result
   * @return a new program representing a computation that acquires a resource, uses it, and then releases it
   */
  public static <S, E, T, R> Program<S, E, R> bracket(
      Program<S, E, T> acquire,
      Function<? super T, ? extends Program<S, E, R>> use,
      Function<? super T, ? extends Program<S, Void, ?>> release) {
    return bracketFull(_ -> acquire, use, release);
  }

  /**
   * Creates a new program that acquires a resource, uses it, and then releases it, where the
   * acquisition can make some of its steps cancelable.
   *
   * <p>Like {@link #bracket(Program, Function, Function)}, the acquisition runs in an uncancelable
   * region, so once the resource is acquired there is no window where it could be lost before its
   * release is guaranteed. The acquire function receives a {@link Poll}: only the programs wrapped
   * with it can be cancelled, like waiting for a connection from an exhausted pool. Wrap only the
   * steps that wait, never the one that actually obtains the resource: if a cancellation arrives
   * inside a polled program, the resource isn't acquired yet, so it isn't released either. So don't
   * write {@code poll.apply(pool.open())}: a connection opened just when the cancellation arrives
   * would be lost without being released.
   *
   * <p>Once acquired, the resource is released whether {@code use} succeeds, fails, throws an
   * exception or is cancelled.
   *
   * @param acquire the function that receives the {@link Poll} and returns the acquisition
   * @param use the function used to use the acquired resource
   * @param release the function used to release the acquired resource. It can't fail with an
   *     error, only throw an exception, which replaces the result of {@code use}, or is added as
   *     suppressed to the exception thrown by {@code use}
   * @param <S> the type of the state
   * @param <E> the type of the error
   * @param <T> the type of the resource
   * @param <R> the type of the result
   * @return a new program representing a computation that acquires a resource, uses it, and then releases it
   */
  public static <S, E, T, R> Program<S, E, R> bracketFull(
      Function<? super Poll, ? extends Program<S, E, T>> acquire,
      Function<? super T, ? extends Program<S, E, R>> use,
      Function<? super T, ? extends Program<S, Void, ?>> release) {
    // the acquisition runs in the closed part of the region, except what it wraps with the poll.
    // Installing the finalizer never observes a cancellation, so there is no gap between acquiring
    // the resource and guaranteeing its release: a cancellation requested meanwhile takes effect
    // when the polled use starts, and the release runs
    return uncancelable(poll -> pipe(
        suspend(() -> acquire.apply(poll)),
        // use and release are called inside suspend, after the finalizer is installed: if they
        // throw while building their programs, the resource is released anyway
        resource -> poll.apply(suspend(() -> use.apply(resource))).ensuring(release(resource, release))
        ));
  }

  private static <S, T> Program<S, Void, Unit> release(
      T resource, Function<? super T, ? extends Program<S, Void, ?>> release) {
    return suspend(() -> release.apply(resource).discard());
  }

  private static <S, E, R> Program<S, E, Collection<R>> append(Program<S, E, Collection<R>> acc, Program<S, E, R> value) {
    return zip(acc, value, Combine::append);
  }

  private static <T> Collection<T> append(Collection<T> list, T value) {
    list.add(value);
    return list;
  }

  private static <S, E, T extends AutoCloseable> Program<S, E, Unit> close(T resource) {
    return Program.task(() -> {
      try {
        resource.close();
      } catch (Exception e) {
        Fatal.sneakyThrow(e);
      }
    });
  }
}
