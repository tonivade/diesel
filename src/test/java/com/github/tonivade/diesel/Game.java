/*
 * Copyright (c) 2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Console.writeLine;
import static com.github.tonivade.diesel.Program.failure;
import static com.github.tonivade.diesel.Program.success;

import java.util.concurrent.atomic.AtomicInteger;

interface Game {

  sealed interface Error {}

  record NumberFormatError(String input) implements Error {}

  @SuppressWarnings("preview")
  static void main(String... args) {
    var result = program().eval(new Context() {});
    System.console().println(result);
  }

  static Program<Context, Error, Void> program() {
    return Console.<Context, Error>prompt("Do you want to play a game? (Y/y)")
        .flatMap(Game::playOrExit);
  }

  static Program<Context, Error, Void> playOrExit(String answer) {
    if (answer.equalsIgnoreCase("y")) {
      return randomNumber().andThen(loop());
    }
    return writeLine("Bye!");
  }

  static Program<Context, Error, Void> randomNumber() {
    return Random.<Context, Error>nextInt(10).flatMap(Reference::set);
  }

  static Program<Context, Error, Void> loop() {
    return Console.<Context, Error>prompt("Enter a number")
      .flatMap(Game::parseInt)
      .foldMap(_ -> loop(), number -> checkNumber(number).flatMap(Game::winOrContinue));
  }

  static Program<Context, Error, Integer> parseInt(String value) {
    try {
      return success(Integer.parseInt(value));
    } catch (NumberFormatException e) {
      return failure(new NumberFormatError(value));
    }
  }

  static Program<Context, Error, Boolean> checkNumber(int number) {
    return Reference.<Integer, Context, Error>get().map(value -> value == number);
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
