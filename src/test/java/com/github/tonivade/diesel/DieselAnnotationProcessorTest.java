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
          return Program.inspect(state -> state.writeLine(line));
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
          return Program.inspect(state -> state.writeLine(line));
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
          return Program.inspect(state -> state.set(value));
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
          return Program.inspect(state -> state.writeLine(line));
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

      @Diesel(errorType = Exception.class)
      public interface Console {
        Result<Exception, String> readLine();
        Result<Exception, Void> writeLine(String line);
      }""");

    var expected = forSourceLines("test.ConsoleDsl",
      """
      package test;

      import com.github.tonivade.diesel.Program;
      import java.lang.Exception;
      import java.lang.String;
      import java.lang.SuppressWarnings;
      import java.lang.Void;
      import javax.annotation.processing.Generated;

      @Generated("com.github.tonivade.diesel.DieselAnnotationProcessor")
      public interface ConsoleDsl {
        @SuppressWarnings("unchecked")
        static <S extends Console, E extends Exception> Program<S, E, String> readLine() {
          return Program.accessResult(state -> state.readLine().mapError(e -> (E) e));
        }

        @SuppressWarnings("unchecked")
        static <S extends Console, E extends Exception> Program<S, E, Void> writeLine(String line) {
          return Program.accessResult(state -> state.writeLine(line).mapError(e -> (E) e));
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
  void shouldGenerateDslCodeWithCompletableFuture() {
    var file = forSourceLines("test.Http",
      """
      package test;

      import com.github.tonivade.diesel.Diesel;
      import java.util.concurrent.CompletableFuture;

      @Diesel
      public interface Http {
        CompletableFuture<String> get(String request);
      }""");

    var expected = forSourceLines("test.HttpDsl",
      """
      package test;

      import com.github.tonivade.diesel.Program;
      import java.lang.String;
      import java.lang.SuppressWarnings;
      import javax.annotation.processing.Generated;

      @Generated("com.github.tonivade.diesel.DieselAnnotationProcessor")
      public interface HttpDsl {
        @SuppressWarnings("unchecked")
        static <S extends Http, E> Program<S, E, String> get(String request) {
          return Program.accessFuture(state -> state.get(request));
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
  void shouldGenerateDslCodeWithGenericMethod() {
    var file = forSourceLines("test.Store",
      """
      package test;

      import com.github.tonivade.diesel.Diesel;

      @Diesel
      public interface Store {
        <T> T get(String key, Class<T> type);
        <T> void put(String key, T value);
      }""");

    var expected = forSourceLines("test.StoreDsl",
      """
      package test;

      import com.github.tonivade.diesel.Program;
      import java.lang.Class;
      import java.lang.String;
      import java.lang.SuppressWarnings;
      import java.lang.Void;
      import javax.annotation.processing.Generated;

      @Generated("com.github.tonivade.diesel.DieselAnnotationProcessor")
      public interface StoreDsl {
        @SuppressWarnings("unchecked")
        static <S extends Store, E, T> Program<S, E, T> get(String key, Class<T> type) {
          return Program.access(state -> state.get(key,type));
        }

        @SuppressWarnings("unchecked")
        static <S extends Store, E, T> Program<S, E, Void> put(String key, T value) {
          return Program.inspect(state -> state.put(key,value));
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
  void shouldGenerateDslCodeWithBoundedGenericMethod() {
    var file = forSourceLines("test.Store",
      """
      package test;

      import com.github.tonivade.diesel.Diesel;

      @Diesel
      public interface Store {
        <T extends Number> T get(String key);
        <K, V extends Comparable<V>> V lookup(K key);
      }""");

    var expected = forSourceLines("test.StoreDsl",
      """
      package test;

      import com.github.tonivade.diesel.Program;
      import java.lang.Comparable;
      import java.lang.Number;
      import java.lang.String;
      import java.lang.SuppressWarnings;
      import javax.annotation.processing.Generated;

      @Generated("com.github.tonivade.diesel.DieselAnnotationProcessor")
      public interface StoreDsl {
        @SuppressWarnings("unchecked")
        static <S extends Store, E, T extends Number> Program<S, E, T> get(String key) {
          return Program.access(state -> state.get(key));
        }

        @SuppressWarnings("unchecked")
        static <S extends Store, E, K, V extends Comparable<V>> Program<S, E, V> lookup(K key) {
          return Program.access(state -> state.lookup(key));
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
  void shouldGenerateDslCodeWithGenericMethodReturningResult() {
    var file = forSourceLines("test.Store",
      """
      package test;

      import com.github.tonivade.diesel.Diesel;
      import com.github.tonivade.diesel.Result;

      @Diesel(errorType = Exception.class)
      public interface Store {
        <T> Result<Exception, T> get(String key, Class<T> type);
      }""");

    var expected = forSourceLines("test.StoreDsl",
      """
      package test;

      import com.github.tonivade.diesel.Program;
      import java.lang.Class;
      import java.lang.Exception;
      import java.lang.String;
      import java.lang.SuppressWarnings;
      import javax.annotation.processing.Generated;

      @Generated("com.github.tonivade.diesel.DieselAnnotationProcessor")
      public interface StoreDsl {
        @SuppressWarnings("unchecked")
        static <S extends Store, E extends Exception, T> Program<S, E, T> get(String key, Class<T> type) {
          return Program.accessResult(state -> state.get(key,type).mapError(e -> (E) e));
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
  void genericMethodWithClashingTypeVariable() {
    var file = forSourceLines("test.Store",
      """
      package test;

      import com.github.tonivade.diesel.Diesel;

      @Diesel
      public interface Store {
        <E> E get(String key, Class<E> type);
      }""");

    assert_().about(javaSource()).that(file)
        .processedWith(new DieselAnnotationProcessor())
        .failsToCompile()
        .withErrorContaining("clash with the type variables generated by @Diesel");
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
