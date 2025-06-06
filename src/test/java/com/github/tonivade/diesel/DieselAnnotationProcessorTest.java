/*
 * Copyright (c) 2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
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
          var result = (T) switch (this) {
            case ReadLine() -> state.readLine();
            case WriteLine(var line) ->  {
              state.writeLine(line);
              yield null;
            }
          };
          return Result.success(result);
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
