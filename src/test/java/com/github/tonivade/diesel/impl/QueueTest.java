/*
 * Copyright (c) 2025, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel.impl;

import static com.github.tonivade.diesel.Result.success;
import static com.github.tonivade.diesel.impl.Queue.offer;
import static com.github.tonivade.diesel.impl.Queue.take;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class QueueTest {

  @Test
  void test() {
    var program = offer("item1")
        .andThen(offer("item2"))
        .andThen(offer("item3"))
        .andThen(take());

    var result = program.eval(new Context(new ArrayList<>()));

    assertThat(result).isEqualTo(success("item3"));
  }

  record Context(List<String> list) implements Queue.Service<String> {

    @Override
    public void offer(String item) {
      list.add(item);
    }

    @Override
    public String take() {
      return list.removeLast();
    }
  }

}
