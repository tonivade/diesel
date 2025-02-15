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
@Diesel
public interface Calculator {
    int add(int a, int b);
    int sub(int a, int b);
}
```

will be transformed into:

```java
import com.github.tonivade.diesel.Program;
import com.github.tonivade.diesel.Result;

@Generated
public sealed interface CalculatorDsl extends Program.Dsl<Calculator, Void, Integer> {

    record Add(int a, int b) implements CalculatorDsl {}
    record Sub(int a, int b) implements CalculatorDsl {}

    @SuppressWarnings("unchecked")
    static <S, E> Program<S, E, Integer> add(int a, int b) {
        return (Program<S, E, Integer>) new Add(a, b);
    }

    @SuppressWarnings("unchecked")
    static <S, E> Program<S, E, Integer> sub(int a, int b) {
        return (Program<S, E, Integer>) new Sub(a, b);
    }

    default Result<Void, Integer> dslEval(Calculator state) {
        return Result.success(switch (this) {
            case Add(var a, var b) -> state.add(a, b);
            case Sub(var a, var b) -> state.sub(a, b);
        });
    }
}
```

After that you could use the DSL inside a program:

```java
public static void main(String... args) {

    var program = add(4, 1).flatMap(a -> sub(a, 2));

    var result = program.eval(new Calculator() {
        public int add(int a, int b) {
            return a + b;
        }

        public int sub(int a, int b) {
            return a - b;
        }
    });

    // will print Success[value=3]
    System.out.println(result);
}
```



