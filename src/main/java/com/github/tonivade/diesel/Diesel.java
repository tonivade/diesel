/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Annotation to mark a class as a DSL. This annotation is used to generate the necessary code to implement the DSL.
 *
 * <p>Example usage:</p>
 *
 * <pre>
 * &#64;Diesel
 * public interface Console {
 *  String readLine();
 *  void writeLine(String line);
 * }
 * </pre>
 *
 * Then we can use the DSL to create programs like this:
 *
 * <pre>
 * import static ConsoleDsl.*;
 * import static com.github.tonivade.diesel.Program.*;
 *
 * void main() {
 *   var program = pipe(
 *     writeLine("What's your name?"),
 *     _ -> readLine(),
 *     name -> writeLine("Hello, " + name + "!")
 *   );
 *
 *   program.eval(new Console() {
 *     &#64;Override
 *     public void writeLine(String line) {
 *       IO.println(line);
 *     }
 *     &#64;Override
 *     public String readLine() {
 *       return IO.readln();
 *     }
 *   });
 * }
 * </pre>
 *
 * <p>In this example, the interface `Console` is marked as DSL. The annotation processor will
 * generate the necessary code to implement programs based on this DSL.</p>
 */
@Retention(SOURCE)
@Target(TYPE)
public @interface Diesel {

  String value() default "";

  Class<?> errorType() default Void.class;

}
