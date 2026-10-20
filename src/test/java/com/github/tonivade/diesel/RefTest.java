/*
 * Copyright (c) 2018-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Combine.pipe;
import static com.github.tonivade.diesel.Combine.zip;
import static com.github.tonivade.diesel.Tuple2.tuple;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class RefTest {

  @Test
  void makeCreatesANewRefForEachEvaluation() {
    var program = Ref.make(0);

    var first = program.evalOrElseThrow();
    var second = program.evalOrElseThrow();

    first.set(1).evalOrElseThrow();

    assertThat(first.get().evalOrElseThrow()).isEqualTo(1);
    assertThat(second.get().evalOrElseThrow()).isZero();
  }

  @Test
  void get() {
    var ref = Ref.of("Hello World!");

    var result = ref.get();

    assertThat(result.evalOrElseThrow()).isEqualTo("Hello World!");
  }

  @Test
  void set() {
    var ref = Ref.of("Hello World!");

    var set = ref.set("Something else");
    var result = set.andThen(ref.get());

    assertThat(result.evalOrElseThrow()).isEqualTo("Something else");
  }

  @Test
  void update() {
    var ref = Ref.of(1);

    var update = ref.update(x -> x + 1);
    var result = update.andThen(ref.get());

    assertThat(result.evalOrElseThrow()).isEqualTo(2);
  }

  @Test
  void getAndSet() {
    var ref = Ref.of("Hello World!");

    var result = zip(
        ref.getAndSet("Something else"),
        ref.get(),
        Tuple2::tuple
      );

    assertThat(result.evalOrElseThrow())
      .isEqualTo(tuple("Hello World!", "Something else"));
  }

  @Test
  void getAndUpdate() {
    var ref = Ref.of("Hello World!");

    var result = zip(
        ref.getAndUpdate(s -> s.toUpperCase(Locale.ROOT)),
        ref.get(),
        Tuple2::tuple
      );

    assertThat(result.evalOrElseThrow())
      .isEqualTo(tuple("Hello World!", "HELLO WORLD!"));
  }

  @Test
  void updateAndGet() {
    var ref = Ref.of("Hello World!");

    var result = ref.updateAndGet(s -> s.toUpperCase(Locale.ROOT));

    assertThat(result.evalOrElseThrow()).isEqualTo("HELLO WORLD!");
  }

  @Test
  void modify() {
    var program = pipe(
      Ref.make("Hello World!"),
      ref -> zip(
            ref.modify(value -> tuple("result", value.toUpperCase(Locale.ROOT))),
            ref.get(), Tuple2::tuple
          )
    );

    var result = program.evalOrElseThrow();

    assertThat(result).isEqualTo(tuple("result", "HELLO WORLD!"));
  }

  @Test
  void concurrentUpdatesAreNotLost() {
    var ref = Ref.of(0);
    var updates = IntStream.range(0, 1000)
        .mapToObj(_ -> ref.<Void, Void>updateAndGet(x -> x + 1))
        .toList();

    var result = Concurrent.parAll(updates).andThen(ref.get());

    assertThat(result.evalOrElseThrow()).isEqualTo(1000);
  }

  @Test
  void concurrentModifiesAreNotLost() {
    var ref = Ref.of(0);
    var updates = IntStream.range(0, 1000)
        .mapToObj(_ -> ref.<Void, Void, Integer>modify(x -> tuple(x, x + 1)))
        .toList();

    var previous = Concurrent.parSequence(updates).evalOrElseThrow();

    assertThat(Set.copyOf(previous)).hasSize(1000);
    assertThat(ref.get().evalOrElseThrow()).isEqualTo(1000);
  }
}
