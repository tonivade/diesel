/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.google.common.truth.Truth.assert_;
import static com.google.testing.compile.JavaFileObjects.forSourceLines;
import static com.google.testing.compile.JavaSourceSubjectFactory.javaSource;
import org.junit.jupiter.api.Test;

class DieselAnnotationProcessorTest {

  @Test
  void shouldGenerateDslCode() {
    var file = forSourceLines("test.Console",
      """
      package test;

      import com.github.tonivade.diesel.Diesel;

      @Diesel
      public interface Console {
        String readLine();
        void writeLine(String line);
      }""");

    var expected = forSourceLines("test.ConsoleApi",
      """
      package test;

      import com.github.tonivade.diesel.Program;
      import com.github.tonivade.diesel.Result;
      import java.lang.Override;
      import java.lang.String;
      import java.lang.SuppressWarnings;
      import java.lang.Void;
      import javax.annotation.processing.Generated;

      @Generated("com.github.tonivade.diesel.DieselAnnotationProcessor")
      public sealed interface ConsoleDsl<T> extends Program.Dsl<Console, Void, T> {
        @SuppressWarnings("unchecked")
        static <S extends Console, E> Program<S, E, String> readLine() {
          return (Program<S, E, String>) new ReadLine();
        }

        @SuppressWarnings("unchecked")
        static <S extends Console, E> Program<S, E, Void> writeLine(String line) {
          return (Program<S, E, Void>) new WriteLine(line);
        }

        @Override
        @SuppressWarnings("unchecked")
        default Result<Void, T> handle(Console state) {
          return switch (this) {
            case ReadLine() -> Result.<Void, T>success((T) state.readLine());
            case WriteLine(var line) -> {
              state.writeLine(line);
              yield Result.<Void, T>success((T) null);
            }
          };
        }

        record ReadLine() implements ConsoleDsl<String> {
        }

        record WriteLine(String line) implements ConsoleDsl<Void> {
        }
      }""");

    assert_().about(javaSource())
      .that(file)
      .processedWith(new DieselAnnotationProcessor())
      .compilesWithoutError()
      .and()
      .generatesSources(expected);
  }

  @Test
  void shouldGenerateDslCodeWithCustomName() {
    var file = forSourceLines("test.Console",
      """
      package test;

      import com.github.tonivade.diesel.Diesel;

      @Diesel(value = "ConsoleApi")
      public interface Console {
        String readLine();
        void writeLine(String line);
      }""");

    var expected = forSourceLines("test.ConsoleDsl",
      """
      package test;

      import com.github.tonivade.diesel.Program;
      import com.github.tonivade.diesel.Result;
      import java.lang.Override;
      import java.lang.String;
      import java.lang.SuppressWarnings;
      import java.lang.Void;
      import javax.annotation.processing.Generated;

      @Generated("com.github.tonivade.diesel.DieselAnnotationProcessor")
      public sealed interface ConsoleApi<T> extends Program.Dsl<Console, Void, T> {
        @SuppressWarnings("unchecked")
        static <S extends Console, E> Program<S, E, String> readLine() {
          return (Program<S, E, String>) new ReadLine();
        }

        @SuppressWarnings("unchecked")
        static <S extends Console, E> Program<S, E, Void> writeLine(String line) {
          return (Program<S, E, Void>) new WriteLine(line);
        }

        @Override
        @SuppressWarnings("unchecked")
        default Result<Void, T> handle(Console state) {
          return switch (this) {
            case ReadLine() -> Result.<Void, T>success((T) state.readLine());
            case WriteLine(var line) -> {
              state.writeLine(line);
              yield Result.<Void, T>success((T) null);
            }
          };
        }

        record ReadLine() implements ConsoleApi<String> {
        }

        record WriteLine(String line) implements ConsoleApi<Void> {
        }
      }""");

    assert_().about(javaSource())
      .that(file)
      .processedWith(new DieselAnnotationProcessor())
      .compilesWithoutError()
      .and()
      .generatesSources(expected);
  }

  @Test
  void shouldGenerateDslCodeWithPrimitiveTypes() {
    var file = forSourceLines("test.State",
      """
      package test;

      import com.github.tonivade.diesel.Diesel;

      @Diesel
      public interface State {
        int get();
        void set(int value);
      }""");

    var expected = forSourceLines("test.StateDsl",
      """
      package test;

      import com.github.tonivade.diesel.Program;
      import com.github.tonivade.diesel.Result;
      import java.lang.Integer;
      import java.lang.Override;
      import java.lang.SuppressWarnings;
      import java.lang.Void;
      import javax.annotation.processing.Generated;

      @Generated("com.github.tonivade.diesel.DieselAnnotationProcessor")
      public sealed interface StateDsl<T> extends Program.Dsl<State, Void, T> {
        @SuppressWarnings("unchecked")
        static <S extends State, E> Program<S, E, Integer> get() {
          return (Program<S, E, Integer>) new Get();
        }

        @SuppressWarnings("unchecked")
        static <S extends State, E> Program<S, E, Void> set(int value) {
          return (Program<S, E, Void>) new Set(value);
        }

        @Override
        @SuppressWarnings("unchecked")
        default Result<Void, T> handle(State state) {
          return switch (this) {
            case Get() -> Result.<Void, T>success((T) (Integer) state.get());
            case Set(var value) -> {
              state.set(value);
              yield Result.<Void, T>success((T) null);
            }
          };
        }

        record Get() implements StateDsl<Integer> {
        }

        record Set(int value) implements StateDsl<Void> {
        }
      }""");

    assert_().about(javaSource())
      .that(file)
      .processedWith(new DieselAnnotationProcessor())
      .compilesWithoutError()
      .and()
      .generatesSources(expected);
  }

  @Test
  void shouldGenerateDslCodeWithCustomErrorType() {
    var file = forSourceLines("test.Console",
      """
      package test;

      import com.github.tonivade.diesel.Diesel;

      @Diesel(errorType = String.class)
      public interface Console {
        String readLine();
        void writeLine(String line);
      }""");

    var expected = forSourceLines("test.ConsoleDsl",
      """
      package test;

      import com.github.tonivade.diesel.Program;
      import com.github.tonivade.diesel.Result;
      import java.lang.Override;
      import java.lang.String;
      import java.lang.SuppressWarnings;
      import java.lang.Void;
      import javax.annotation.processing.Generated;

      @Generated("com.github.tonivade.diesel.DieselAnnotationProcessor")
      public sealed interface ConsoleDsl<T> extends Program.Dsl<Console, String, T> {
        @SuppressWarnings("unchecked")
        static <S extends Console, E extends String> Program<S, E, String> readLine() {
          return (Program<S, E, String>) new ReadLine();
        }

        @SuppressWarnings("unchecked")
        static <S extends Console, E extends String> Program<S, E, Void> writeLine(String line) {
          return (Program<S, E, Void>) new WriteLine(line);
        }

        @Override
        @SuppressWarnings("unchecked")
        default Result<String, T> handle(Console state) {
          return switch (this) {
            case ReadLine() -> Result.<String, T>success((T) state.readLine());
            case WriteLine(var line) -> {
              state.writeLine(line);
              yield Result.<String, T>success((T) null);
            }
          };
        }

        record ReadLine() implements ConsoleDsl<String> {
        }

        record WriteLine(String line) implements ConsoleDsl<Void> {
        }
      }""");

    assert_().about(javaSource())
      .that(file)
      .processedWith(new DieselAnnotationProcessor())
      .compilesWithoutError()
      .and()
      .generatesSources(expected);
  }

  @Test
  void shouldGenerateDslCodeWithResult() {
    var file = forSourceLines("test.Console",
      """
      package test;

      import com.github.tonivade.diesel.Diesel;
      import com.github.tonivade.diesel.Result;

      @Diesel(errorType = String.class)
      public interface Console {
        Result<String, String> readLine();
        Result<String, Void> writeLine(String line);
      }""");

    var expected = forSourceLines("test.ConsoleDsl",
      """
      package test;

      import com.github.tonivade.diesel.Program;
      import com.github.tonivade.diesel.Result;
      import java.lang.Override;
      import java.lang.String;
      import java.lang.SuppressWarnings;
      import java.lang.Void;
      import javax.annotation.processing.Generated;

      @Generated("com.github.tonivade.diesel.DieselAnnotationProcessor")
      public sealed interface ConsoleDsl<T> extends Program.Dsl<Console, String, T> {
        @SuppressWarnings("unchecked")
        static <S extends Console, E extends String> Program<S, E, String> readLine() {
          return (Program<S, E, String>) new ReadLine();
        }

        @SuppressWarnings("unchecked")
        static <S extends Console, E extends String> Program<S, E, Void> writeLine(String line) {
          return (Program<S, E, Void>) new WriteLine(line);
        }

        @Override
        @SuppressWarnings("unchecked")
        default Result<String, T> handle(Console state) {
          return switch (this) {
            case ReadLine() -> (Result<String, T>) state.readLine();
            case WriteLine(var line) -> (Result<String, T>) state.writeLine(line);
          };
        }

        record ReadLine() implements ConsoleDsl<String> {
        }

        record WriteLine(String line) implements ConsoleDsl<Void> {
        }
      }""");

    assert_().about(javaSource())
      .that(file)
      .processedWith(new DieselAnnotationProcessor())
      .compilesWithoutError()
      .and()
      .generatesSources(expected);
  }

  @Test
  void annotationNotSupportedInClasses() {
    var file = forSourceLines("test.User",
      """
      package test;

      import com.github.tonivade.diesel.Diesel;

      @Diesel
      public class User {}""");

    assert_().about(javaSource()).that(file)
        .processedWith(new DieselAnnotationProcessor())
        .failsToCompile()
        .withErrorContaining("not supported");
  }

  @Test
  void annotationNotSupportedInRecords() {
    var file = forSourceLines("test.User",
      """
      package test;

      import com.github.tonivade.diesel.Diesel;

      @Diesel
      public record User() {}""");

    assert_().about(javaSource()).that(file)
        .processedWith(new DieselAnnotationProcessor())
        .failsToCompile()
        .withErrorContaining("not supported");
  }
}
