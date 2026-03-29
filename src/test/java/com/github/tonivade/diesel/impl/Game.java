/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel.impl;

import static com.github.tonivade.diesel.Program.attempt;
import static com.github.tonivade.diesel.Program.branch;
import static com.github.tonivade.diesel.Program.chain;
import static com.github.tonivade.diesel.Program.pipe;
import static com.github.tonivade.diesel.Program.recover;
import static com.github.tonivade.diesel.impl.Console.prompt;
import static com.github.tonivade.diesel.impl.Console.writeLine;
import static com.github.tonivade.diesel.impl.Random.nextInt;
import static com.github.tonivade.diesel.impl.Reference.get;
import java.util.concurrent.atomic.AtomicInteger;
import com.github.tonivade.diesel.Program;

interface Game {

  sealed interface Error {}

  record NumberFormatError(String input) implements Error {}

  static void main(String... args) {
    program().eval(new Context() {});
  }

  static Program<Context, Error, Void> program() {
    return pipe(
        prompt("Do you want to play a game? (Y/y)"),
        branch(answer -> answer.equalsIgnoreCase("y"),
            randomNumber().andThen(loop()),
            writeLine("Bye!"))
      );
  }

  static Program<Context, Error, Void> randomNumber() {
    return pipe(
        nextInt(10),
        Reference::set
      );
  }

  static Program<Context, Error, Void> loop() {
    return pipe(
        recover(readNumber(), _ -> pipe(writeLine("Invalid value"), _ -> readNumber())),
        Game::checkNumber,
        branch(writeLine("YOU WIN!"), loop())
      );
  }

  static Program<Context, Error, Integer> readNumber() {
    return pipe(
        prompt("Enter a number"),
        value -> attempt(()  -> Integer.parseInt(value), _ -> new NumberFormatError(value))
      );
  }

  static Program<Context, Error, Boolean> checkNumber(int number) {
    return chain(
        get(),
        value -> value == number
      );
  }

  abstract class Context implements Random.Service, Reference.Service<Integer>, Console.Service {

    private final AtomicInteger value = new AtomicInteger();

    @Override
    public void set(Integer value) {
      this.value.set(value);
    }

    @Override
    public Integer get() {
      return value.get();
    }
  }
}
