/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel.impl;

import static com.github.tonivade.diesel.impl.Todo.program;
import static com.github.tonivade.diesel.impl.Todo.State.NOT_COMPLETED;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import com.github.tonivade.diesel.impl.Todo.TodoEntity;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

@MockitoSettings
class TodoTest {

  @Test
  void shouldCreateTodo(@Mock Todo.TodoContext context) {
    when(context.readLine()).thenReturn("1", "buy milk", "7");

    program().eval(context);

    var inOrder = inOrder(context);
    inOrder.verify(context).create(new TodoEntity(1, "buy milk", NOT_COMPLETED));
    inOrder.verify(context).writeLine("Bye!");
  }
}
