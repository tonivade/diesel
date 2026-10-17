/*
 * Copyright (c) 2018-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Combine.pipe;
import static com.github.tonivade.diesel.Combine.zip;
import static com.github.tonivade.diesel.Tuple2.tuple;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class RefTest {

  @Test
  void makeCreatesANewRefForEachEvaluation() {
    var program = Ref.make(0);

    var first = program.evalOrElseThrow();
    var second = program.evalOrElseThrow();

    first.set(1).evalOrElseThrow();

    assertEquals(1, first.get().evalOrElseThrow());
    assertEquals(0, second.get().evalOrElseThrow());
  }

  @Test
  void get() {
    var ref = Ref.of("Hello World!");

    var result = ref.get();

    assertEquals("Hello World!", result.evalOrElseThrow());
  }

  @Test
  void set() {
    var ref = Ref.of("Hello World!");

    var set = ref.set("Something else");
    var result = set.andThen(ref.get());

    assertEquals("Something else", result.evalOrElseThrow());
  }

  @Test
  void lazySet() {
    var ref = Ref.of("Hello World!");

    var lazySet = ref.lazySet("Something else");
    var result = lazySet.andThen(ref.get());

    assertEquals("Something else", result.evalOrElseThrow());
  }

  @Test
  void getAndSet() {
    var ref = Ref.of("Hello World!");

    var result = ref.getAndSet("Something else");
    var afterUpdate = result.andThen(ref.get());

    assertEquals("Hello World!", result.evalOrElseThrow());
    assertEquals("Something else", afterUpdate.evalOrElseThrow());
  }

  @Test
  void getAndUpdate() {
    var ref = Ref.of("Hello World!");

    var result = ref.getAndUpdate(String::toUpperCase);
    var afterUpdate = result.andThen(ref.get());

    assertEquals("Hello World!", result.evalOrElseThrow());
    assertEquals("HELLO WORLD!", afterUpdate.evalOrElseThrow());
  }

  @Test
  void updateAndGet() {
    var ref = Ref.of("Hello World!");

    var result = ref.updateAndGet(String::toUpperCase);

    assertEquals("HELLO WORLD!", result.evalOrElseThrow());
  }

  @Test
  void modify() {
    var program = pipe(
      Ref.make("Hello World!"),
      ref -> zip(
            ref.modify(value -> tuple("result", value.toUpperCase())),
            ref.get(), Tuple2::tuple
          )
      );

    var result = program.evalOrElseThrow();

    assertEquals(result, tuple("result", "HELLO WORLD!"));
  }
}
