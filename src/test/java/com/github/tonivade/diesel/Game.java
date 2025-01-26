/*
 * Copyright (c) 2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Console.prompt;
import static com.github.tonivade.diesel.Console.writeLine;
import static com.github.tonivade.diesel.Program.failure;
import static com.github.tonivade.diesel.Program.success;
import static com.github.tonivade.diesel.Random.nextInt;
import static com.github.tonivade.diesel.Reference.get;

import java.util.concurrent.atomic.AtomicInteger;

interface Game {

  sealed interface Error {}

  record NumberFormatError(String input) implements Error {}

  static void main(String... args) {
    program().eval(new Context() {});
  }

  static Program<Context, Error, Void> program() {
    return doYouWantToPlay().flatMap(Game::playOrExit);
  }

  static Program<Context, Error, String> doYouWantToPlay() {
    return prompt("Do you want to play a game? (Y/y)");
  }

  static Program<Context, Error, Void> playOrExit(String answer) {
    if (answer.equalsIgnoreCase("y")) {
      return randomNumber().andThen(loop());
    }
    return writeLine("Bye!");
  }

  static Program<Context, Error, Void> randomNumber() {
    return generateNumber().flatMap(Reference::set);
  }

  static Program<Context, Error, Integer> generateNumber() {
    return nextInt(10);
  }

  static Program<Context, Error, Void> loop() {
    return enterNumber()
        .flatMap(Game::parseInt)
        .foldMap(_ -> loop(), number -> checkNumber(number).flatMap(Game::winOrContinue));
  }

  static Program<Context, Error, String> enterNumber() {
    return prompt("Enter a number");
  }

  static Program<Context, Error, Integer> parseInt(String value) {
    try {
      return success(Integer.parseInt(value));
    } catch (NumberFormatException e) {
      return failure(new NumberFormatError(value));
    }
  }

  static Program<Context, Error, Boolean> checkNumber(int number) {
    return getNumber().map(value -> value == number);
  }

  static Program<Context, Error, Integer> getNumber() {
    return get();
  }

  static Program<Context, Error, Void> winOrContinue(boolean answer) {
    if (answer) {
      return writeLine("YOU WIN!!");
    }
    return loop();
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
