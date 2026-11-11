/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Console.prompt;
import static com.github.tonivade.diesel.Console.writeLine;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import com.github.tonivade.diesel.Console.Service;

import java.io.EOFException;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

@MockitoSettings
class ConsoleTest {

  static <S extends Service, E> Program<S, E, Unit> sayHello(String name) {
    return writeLine("Hello " + name);
  }

  static <S extends Service, E> Program<S, E, String> whatsYourName() {
    return prompt("What's your name?");
  }

  @Test
  void shouldWriteAndReadFromConsole(@Mock Console.Service service) throws EOFException {
    when(service.readLine()).thenReturn("Toni");
    var program = whatsYourName().flatMap(ConsoleTest::sayHello);

    program.eval(service);

    var inOrder = inOrder(service);
    inOrder.verify(service).writeLine("What's your name?");
    inOrder.verify(service).writeLine("Hello Toni");
  }

  @Test
  void shouldThrowException(@Mock Console.Service service) throws EOFException {
    when(service.readLine()).thenThrow(EOFException.class);
    var program = whatsYourName().flatMap(ConsoleTest::sayHello);

    assertThatThrownBy(() -> program.eval(service))
      .isInstanceOf(EOFException.class);
  }
}
