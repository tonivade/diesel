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
      import java.lang.String;
      import java.lang.SuppressWarnings;
      import java.lang.Void;
      import javax.annotation.processing.Generated;

      @Generated("com.github.tonivade.diesel.DieselAnnotationProcessor")
      public interface ConsoleDsl {
        @SuppressWarnings("unchecked")
        static <S extends Console, E> Program<S, E, String> readLine() {
          return Program.access(state -> state.readLine());
        }

        @SuppressWarnings("unchecked")
        static <S extends Console, E> Program<S, E, Void> writeLine(String line) {
          return Program.access(state -> {
            state.writeLine(line);
            return null;
          });
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
      import java.lang.String;
      import java.lang.SuppressWarnings;
      import java.lang.Void;
      import javax.annotation.processing.Generated;

      @Generated("com.github.tonivade.diesel.DieselAnnotationProcessor")
      public interface ConsoleApi {
        @SuppressWarnings("unchecked")
        static <S extends Console, E> Program<S, E, String> readLine() {
          return Program.access(state -> state.readLine());
        }

        @SuppressWarnings("unchecked")
        static <S extends Console, E> Program<S, E, Void> writeLine(String line) {
          return Program.access(state -> {
            state.writeLine(line);
            return null;
          });
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
      import java.lang.Integer;
      import java.lang.SuppressWarnings;
      import java.lang.Void;
      import javax.annotation.processing.Generated;

      @Generated("com.github.tonivade.diesel.DieselAnnotationProcessor")
      public interface StateDsl {
        @SuppressWarnings("unchecked")
        static <S extends State, E> Program<S, E, Integer> get() {
          return Program.access(state -> state.get());
        }

        @SuppressWarnings("unchecked")
        static <S extends State, E> Program<S, E, Void> set(int value) {
          return Program.access(state -> {
            state.set(value);
            return null;
          });
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
      import java.lang.String;
      import java.lang.SuppressWarnings;
      import java.lang.Void;
      import javax.annotation.processing.Generated;

      @Generated("com.github.tonivade.diesel.DieselAnnotationProcessor")
      public interface ConsoleDsl {
        @SuppressWarnings("unchecked")
        static <S extends Console, E extends String> Program<S, E, String> readLine() {
          return Program.access(state -> state.readLine());
        }

        @SuppressWarnings("unchecked")
        static <S extends Console, E extends String> Program<S, E, Void> writeLine(String line) {
          return Program.access(state -> {
            state.writeLine(line);
            return null;
          });
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
      import java.lang.String;
      import java.lang.SuppressWarnings;
      import java.lang.Void;
      import javax.annotation.processing.Generated;

      @Generated("com.github.tonivade.diesel.DieselAnnotationProcessor")
      public interface ConsoleDsl {
        @SuppressWarnings("unchecked")
        static <S extends Console, E extends String> Program<S, E, String> readLine() {
          return Program.accessR(state -> state.readLine().mapError(e -> (E) e));
        }

        @SuppressWarnings("unchecked")
        static <S extends Console, E extends String> Program<S, E, Void> writeLine(String line) {
          return Program.accessR(state -> state.writeLine(line).mapError(e -> (E) e));
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
