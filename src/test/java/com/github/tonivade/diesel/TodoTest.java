/*
 * Copyright (c) 2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Todo.program;
import static com.github.tonivade.diesel.Todo.State.NOT_COMPLETED;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.github.tonivade.diesel.Todo.TodoEntity;

@ExtendWith(MockitoExtension.class)
class TodoTest {

  @Test
  void shouldCreateTodo(@Mock Todo.Context context) {
    when(context.readLine()).thenReturn("1", "buy milk", "7");
    when(context.increment()).thenReturn(1);

    program().eval(context);

    var inOrder = inOrder(context);
    inOrder.verify(context).create(new TodoEntity(1, "buy milk", NOT_COMPLETED));
    inOrder.verify(context).writeLine("Bye!");
  }
}
