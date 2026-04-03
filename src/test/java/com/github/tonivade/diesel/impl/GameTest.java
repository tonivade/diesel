/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel.impl;

import static com.github.tonivade.diesel.impl.Game.game;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GameTest {

  @Test
  void testWin(@Mock Game.Context context) {
    when(context.nextInt(10)).thenReturn(3);
    when(context.get()).thenReturn(3);
    when(context.readLine()).thenReturn("y", "1", "2", "3");

    game().eval(context);

    var inOrder = inOrder(context);
    inOrder.verify(context).writeLine("Do you want to play a game? (Y/y)");
    inOrder.verify(context).set(3);
    inOrder.verify(context, times(3)).writeLine("Enter a number");
    inOrder.verify(context).writeLine("YOU WIN!!");
  }

  @Test
  void testInvalidValue(@Mock Game.Context context) {
    when(context.nextInt(10)).thenReturn(3);
    when(context.get()).thenReturn(3);
    when(context.readLine()).thenReturn("y", "c", "?", "3");

    game().eval(context);

    var inOrder = inOrder(context);
    inOrder.verify(context).writeLine("Do you want to play a game? (Y/y)");
    inOrder.verify(context).set(3);
    inOrder.verify(context).writeLine("Enter a number");
    inOrder.verify(context).writeLine("Invalid value");
    inOrder.verify(context, times(2)).writeLine("Enter a number");
    inOrder.verify(context).writeLine("YOU WIN!!");
  }

  @Test
  void testQuit(@Mock Game.Context context) {
    when(context.readLine()).thenReturn("n");

    game().eval(context);

    var inOrder = inOrder(context);
    inOrder.verify(context).writeLine("Do you want to play a game? (Y/y)");
    inOrder.verify(context).writeLine("Bye!");
  }
}
