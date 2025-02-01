package com.github.tonivade.diesel;

import static com.google.common.truth.Truth.assert_;
import static com.google.testing.compile.JavaFileObjects.forSourceLines;
import static com.google.testing.compile.JavaSourceSubjectFactory.javaSource;
import javax.tools.JavaFileObject;

import org.junit.jupiter.api.Test;

class DieselAnnotationProcessorTest {

  @Test
  void recordTest() {
    JavaFileObject file = forSourceLines("test.Console",
            """
            package test;

            import com.github.tonivade.diesel.Diesel;

            @Diesel
            public interface Console {
              String readLine();
              void writeLine(String line);
            }""");

    JavaFileObject expected = forSourceLines("test.ConsoleDsl",
            """
            package test;

            import com.github.tonivade.diesel.Program;
            import javax.annotation.Generated;

            @Generated
            public sealed interface ConsoleDsl<E, T> extends Program.Dsl<Console, E, T> {

              record WriteLine(String line) implements ConsoleDsl<Void, Void> {}
              record ReadLine() implements ConsoleDsl<Void, String> {}

              @SuppressWarnings("unchecked")
              static <S extends Console, E> Program<S, E, Void> writeLine(String line) {
                return (Program<S, E, Void>) new WriteLine(line);
              }

              @SuppressWarnings("unchecked")
              static <S extends Console, E> Program<S, E, String> readLine() {
                return (Program<S, E, String>) new ReadLine();
              }

              @Override
              @SuppressWarnings("unchecked")
              default Result<Void, T> dslEval(Console console) {
                return success((T) switch (this) {
                  case WriteLine(var line) -> {
                    console.writeLine(line);
                    yield null;
                  }
                  case ReadLine() -> console.readLine();
                });
              }
            }""");

    assert_().about(javaSource()).that(file)
        .processedWith(new DieselAnnotationProcessor())
        .compilesWithoutError().and().generatesSources(expected);
  }

}
