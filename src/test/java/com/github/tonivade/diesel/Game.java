package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Console.prompt;
import static com.github.tonivade.diesel.Console.writeLine;

import java.util.concurrent.ThreadLocalRandom;

sealed interface Game<T> extends Program.Dsl<Game.State, T> {

  interface State {
    void next();
    boolean check(int number);
  }

  record RandomNumber() implements Game<Void> {}
  record CheckNumber(int number) implements Game<Boolean> {}

  @SuppressWarnings("unchecked")
  static <S extends State> Program<S, Void> randomNumber() {
    return (Program<S, Void>) new RandomNumber();
  }

  @SuppressWarnings("unchecked")
  static <S extends State> Program<S, Boolean> checkNumber(int number) {
    return (Program<S, Boolean>) new CheckNumber(number);
  }

  @Override
  @SuppressWarnings("unchecked")
  default T eval(State state) {
    return (T) switch (this) {
      case RandomNumber _ -> {
        state.next();
        yield null;
      }
      case CheckNumber(var number) -> state.check(number);
    };
  }

  static Program<GameContext, Void> loop() {
    return Console.<GameContext>prompt("Enter a number")
      .map(Integer::parseInt)
      .flatMap(Game::checkNumber)
      .flatMap(Game::winOrContinue);
  }

  static Program<GameContext, Void> winOrContinue(boolean answer) {
    if (answer) {
      return writeLine("YOU WIN!!");
    }
    return loop();
  }

  static Program<GameContext, Void> playOrExit(String answer) {
    if (answer.equalsIgnoreCase("y")) {
      return Game.<GameContext>randomNumber().andThen(loop());
    }
    return writeLine("Bye!");
  }

  static void main() {
    var program = Console.<GameContext>whatsYourName()
        .flatMap(Console::sayHello)
        .andThen(prompt("Do you want to play a game? (Y/y)"))
        .flatMap(Game::playOrExit);

    program.eval(new GameContext());
  }

  final class GameContext implements Game.State, Console.Service {

    private int value;

    @Override
    public void next() {
      this.value = ThreadLocalRandom.current().nextInt(10);
    }

    @Override
    public boolean check(int number) {
      return number == value;
    }
  }
}
