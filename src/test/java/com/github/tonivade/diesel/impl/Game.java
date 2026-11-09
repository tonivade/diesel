/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel.impl;

import static com.github.tonivade.diesel.Combine.attempt;
import static com.github.tonivade.diesel.Combine.branch;
import static com.github.tonivade.diesel.Combine.chain;
import static com.github.tonivade.diesel.Combine.pipe;
import static com.github.tonivade.diesel.Combine.recover;
import static com.github.tonivade.diesel.Console.prompt;
import static com.github.tonivade.diesel.Console.writeLine;
import static com.github.tonivade.diesel.Random.nextInt;

import com.github.tonivade.diesel.Console;
import com.github.tonivade.diesel.Program;
import com.github.tonivade.diesel.Random;
import com.github.tonivade.diesel.Ref;
import com.github.tonivade.diesel.Unit;

interface Game {

  sealed interface Error {}

  record NumberFormatError(String input) implements Error {}

  static void main(String... args) {
    game().eval(new Context() {});
  }

  static Program<Context, Error, Unit> game() {
    return pipe(
        Ref.make(0),
        reference -> pipe(
            prompt("Do you want to play a game? (Y/y)"),
            branch(answer -> answer.equalsIgnoreCase("y"),
                _ -> randomNumber(reference).andThen(loop(reference)),
                _ -> writeLine("Bye!")
                )
            )
        );
  }

  static Program<Context, Error, Unit> randomNumber(Ref<Integer> reference) {
    return pipe(
        nextInt(10),
        reference::set
      );
  }

  static Program<Context, Error, Unit> loop(Ref<Integer> reference) {
    return pipe(
        readNumber(),
        n -> checkNumber(reference, n),
        branch(
            () -> writeLine("YOU WIN!!"),
            () -> loop(reference)
          )
      );
  }

  static Program<Context, Error, Integer> readNumber() {
    return recover(
        pipe(
          prompt("Enter a number"),
          value -> attempt(()  -> Integer.parseInt(value), _ -> new NumberFormatError(value))
        ),
        _ -> pipe(writeLine("Invalid value"), _ -> readNumber())
      );
  }

  static Program<Context, Error, Boolean> checkNumber(Ref<Integer> reference, int number) {
    return chain(
        reference.get(),
        value -> value == number
      );
  }

  abstract class Context implements Random.Service, Console.Service {}
}
