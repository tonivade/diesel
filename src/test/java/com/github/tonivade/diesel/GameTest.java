package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Game.program;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GameTest {

  @Test
  void test(@Mock Game.Context context) {
    when(context.nextInt(10)).thenReturn(3);
    when(context.get()).thenReturn(3);
    when(context.readLine()).thenReturn("Toni", "y", "1", "2", "3");

    program().eval(context);

    verify(context).set(3);
    verify(context).writeLine("What's your name?");
    verify(context).writeLine("Hello Toni");
    verify(context).writeLine("Do you want to play a game? (Y/y)");
    verify(context, times(3)).writeLine("Enter a number");
    verify(context).writeLine("YOU WIN!!");
  }
}
