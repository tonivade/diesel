package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Game.program;
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

    program().eval(context);

    var inOrder = inOrder(context);
    inOrder.verify(context).writeLine("Do you want to play a game? (Y/y)");
    inOrder.verify(context).set(3);
    inOrder.verify(context, times(3)).writeLine("Enter a number");
    inOrder.verify(context).writeLine("YOU WIN!!");
  }

  @Test
  void testQuit(@Mock Game.Context context) {
    when(context.readLine()).thenReturn("n");

    program().eval(context);

    var inOrder = inOrder(context);
    inOrder.verify(context).writeLine("Do you want to play a game? (Y/y)");
    inOrder.verify(context).writeLine("Bye!");
  }
}
