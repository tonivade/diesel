# diesel

Generate DSLs in Java using an annotation processor and combine them all together.

## Name

The name of the project is the literal spelling of DSL acronym.

## Requirement

This library is based on Java 21.

## Usage

The idea is to create a DSL for your application. You can use `@Diesel` annotation to create a new DSL class and the annotation processor will generate all the boilerplate code for you.

For example this interface:

```java
@Diesel
public interface Calculator {
    int add(int a, int b);
}
```

will be transformed into:

```java
import com.github.tonivade.diesel.Program;
import com.github.tonivade.diesel.Result;

@Generated
public sealed interface CalculatorDsl extends Program.Dsl<Calculator, Void, Integer> {

    record Add(int a, int b) implements CalculatorDsl {}

    @SuppressWarnings("unchecked")
    static <S, E> Program<S, E, Integer> add(int a, int b) {
        return (Program<S, E, Integer>) new Add(a, b);
    }

    default Result<Void, Integer> dslEval(Calculator state) {
        return Result.success(switch (this) {
            case Add(var a, var b) -> state.add(a, b);
        });
    }
}
```

After that you could use the DSL inside a program:

```java
public static void main(String... args) {
    record Context() implements Calculator, Console.Service { 
        public int add(int a, int b) {
            return a + b;
        }
    }

    var readInt = Console.<Context, Void>prompt("enter value").map(Integer::parseInt);

    var program = Program.<Context, Void, Integer, Integer, Program<Context, Void, Integer>>map2(readInt, readInt, CalculatorDsl::add)
        .flatMap(Function.identity()).map(String::valueOf)
        .flatMap(Console::writeLine);

    program.eval(new Context());

    // this will show
    // > enter value
    // < 1
    // > enter value
    // < 2
    // > 3
}
```



