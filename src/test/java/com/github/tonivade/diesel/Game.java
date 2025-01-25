/*
 * Copyright (c) 2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Console.writeLine;

import java.util.concurrent.atomic.AtomicInteger;

interface Game {

  static void main(String... args) {
    program().eval(new Context() {});
  }

  static Program<Context, Void> program() {
    return Console.<Context>prompt("Do you want to play a game? (Y/y)")
        .flatMap(Game::playOrExit);
  }

  static Program<Context, Void> playOrExit(String answer) {
    if (answer.equalsIgnoreCase("y")) {
      return randomNumber().andThen(loop());
    }
    return writeLine("Bye!");
  }

  static Program<Context, Void> randomNumber() {
    return Random.<Context>nextInt(10).flatMap(Reference::set);
  }

  static Program<Context, Void> loop() {
    return Console.<Context>prompt("Enter a number")
      .map(Integer::parseInt)
      .flatMap(Game::checkNumber)
      .flatMap(Game::winOrContinue);
  }

  static Program<Context, Boolean> checkNumber(int number) {
    return Reference.<Integer, Context>get().map(value -> value == number);
  }

  static Program<Context, Void> winOrContinue(boolean answer) {
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
