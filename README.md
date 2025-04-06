# diesel

Generate DSLs in Java using an annotation processor and combine them all together.

## Name

The name of the project is the literal spelling of DSL acronym.

## Requirement

This library is based on Java 21.

## Usage

The idea is to create DSLs for your application. You can annotate your interface with `@Diesel` and the annotation processor will generate the DSL with all the boilerplate code for you.

For example this interface:

```java
import com.github.tonivade.diesel.Diesel;

@Diesel
public interface Console {
  String readLine();
  void writeLine(String line);
}
```

will be transformed into:

```java
import com.github.tonivade.diesel.Program;
import com.github.tonivade.diesel.Result;
import javax.annotation.processing.Generated;

@Generated("com.github.tonivade.diesel.DieselAnnotationProcessor")
public sealed interface ConsoleDsl<T> extends Program.Dsl<Console, Void, T> {

  record ReadLine() implements ConsoleDsl<String> {
  }

  record WriteLine(String line) implements ConsoleDsl<Void> {
  }

  static <S extends Console, E> Program<S, E, String> readLine() {
    return (Program<S, E, String>) new ReadLine();
  }

  static <S extends Console, E> Program<S, E, Void> writeLine(String line) {
    return (Program<S, E, Void>) new WriteLine(line);
  }

  @Override
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
}
```

After that you could use the DSL inside a program:

```java
public static void main(String... args) {

  var program = writeLine("What's your name?")
    .flatMap(_ -> readLine())
    .flatMap(name -> writeLine("Hello " + name + "!"));

  // output of the program:
  // >> What's your name?
  // << Toni
  // >> Hello Toni!
  program.eval(new Console() {
    public void writeLine(String line) {
      System.out.println(line);
    }
    public String readLine() {
      return System.console().readLine();
    }
  });
}
```
