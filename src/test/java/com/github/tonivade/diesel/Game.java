/*
 * Copyright (c) 2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Console.prompt;
import static com.github.tonivade.diesel.Console.writeLine;

import java.util.concurrent.atomic.AtomicInteger;

interface Game {

  static void main(String... args) {
    var program = Console.<GameContext>whatsYourName()
        .flatMap(Console::sayHello)
        .andThen(prompt("Do you want to play a game? (Y/y)"))
        .flatMap(Game::playOrExit);

    program.eval(new GameContext());
  }

  static Program<GameContext, Void> playOrExit(String answer) {
    if (answer.equalsIgnoreCase("y")) {
      return randomNumber().andThen(loop());
    }
    return writeLine("Bye!");
  }

  static Program<GameContext, Void> randomNumber() {
    return Random.<GameContext>nextInt(10).flatMap(Reference::set);
  }

  static Program<GameContext, Void> loop() {
    return Console.<GameContext>prompt("Enter a number")
      .map(Integer::parseInt)
      .flatMap(Game::checkNumber)
      .flatMap(Game::winOrContinue);
  }

  static Program<GameContext, Boolean> checkNumber(int number) {
    return Reference.<Integer, GameContext>get().map(value -> value == number);
  }

  static Program<GameContext, Void> winOrContinue(boolean answer) {
    if (answer) {
      return writeLine("YOU WIN!!");
    }
    return loop();
  }

  final class GameContext implements Random.Service, Reference.Service<Integer>, Console.Service {

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
