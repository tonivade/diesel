/*
 * Copyright (c) 2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Console.prompt;
import static com.github.tonivade.diesel.Console.writeLine;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.github.tonivade.diesel.Console.Service;

@ExtendWith(MockitoExtension.class)
class ConsoleTest {

  static <S extends Service, E> Program<S, E, Void> sayHello(String name) {
    return writeLine("Hello " + name);
  }

  static <S extends Service, E> Program<S, E, String> whatsYourName() {
    return prompt("What's your name?");
  }

  @Test
  void shouldWriteAndReadFromConsole(@Mock Console.Service service) {
    when(service.readLine()).thenReturn("Toni");

    whatsYourName().flatMap(ConsoleTest::sayHello).eval(service);

    var inOrder = inOrder(service);
    inOrder.verify(service).writeLine("What's your name?");
    inOrder.verify(service).writeLine("Hello Toni");
  }
}
