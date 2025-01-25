package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Console.whatsYourName;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConsoleTest {

  @Test
  void shouldWriteAndReadFromConsole(@Mock Console.Service service) {
    when(service.readLine()).thenReturn("Toni");

    whatsYourName().flatMap(Console::sayHello).eval(service);

    var inOrder = inOrder(service);
    inOrder.verify(service).writeLine("What's your name?");
    inOrder.verify(service).writeLine("Hello Toni");
  }
}
