/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Result.success;
import static com.github.tonivade.diesel.Validation.invalid;
import static com.github.tonivade.diesel.Validation.valid;
import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

class ValidatorTest {

  @Test
  void shouldCompose() {
    Validator<Object, String, Integer> isPositive = Validator.of(value -> {
      if (value > 0) {
        return valid();
      }
      return invalid("Value must be positive");
    });

    Validator<Object, String, Integer> isEven = Validator.of(value -> {
      if (value % 2 == 0) {
        return valid();
      }
      return invalid("Value must be even");
    });

    var positiveAndEven = isPositive.and(isEven);
    var positiveOrEven = isPositive.or(isEven);
    var positiveCombineEven = isPositive.combine(isEven);

    // Test with a positive even number
    assertEquals(positiveAndEven.apply(4).eval(null), success(valid()));
    assertEquals(positiveOrEven.apply(4).eval(null), success(valid()));
    assertEquals(positiveCombineEven.apply(4).eval(null), success(valid()));

    // Test with a positive odd number
    assertEquals(positiveAndEven.apply(3).eval(null), success(invalid("Value must be even")));
    assertEquals(positiveOrEven.apply(3).eval(null), success(valid()));
    assertEquals(positiveCombineEven.apply(3).eval(null), success(invalid(List.of("Value must be even"))));

    // Test with a negative even number
    assertEquals(positiveAndEven.apply(-2).eval(null), success(invalid("Value must be positive")));
    assertEquals(positiveOrEven.apply(-2).eval(null), success(valid()));
    assertEquals(positiveCombineEven.apply(-2).eval(null), success(invalid(List.of("Value must be positive"))));

    // Test with a negative odd number
    assertEquals(positiveAndEven.apply(-3).eval(null), success(invalid("Value must be positive")));
    assertEquals(positiveOrEven.apply(-3).eval(null), success(invalid("Value must be even")));
    assertEquals(positiveCombineEven.apply(-3).eval(null), success(invalid(List.of("Value must be positive", "Value must be even"))));
  }

  @Test
  void shouldValidateFromProgram() {
    Function<Integer, Program<Object, String, Integer>> lookup =
        value -> value > 0 ? Program.success(value) : Program.failure("not found");

    Validator<Object, String, Integer> mustExist = Validator.fromFailure(lookup);
    Validator<Object, String, Integer> mustExistMapped = Validator.fromFailure(lookup, error -> "error: " + error);
    Validator<Object, String, Integer> mustNotExist = Validator.fromSuccess(lookup, value -> value + " already exists");

    assertEquals(mustExist.apply(1).eval(null), success(valid()));
    assertEquals(mustExist.apply(-1).eval(null), success(invalid("not found")));

    assertEquals(mustExistMapped.apply(1).eval(null), success(valid()));
    assertEquals(mustExistMapped.apply(-1).eval(null), success(invalid("error: not found")));

    assertEquals(mustNotExist.apply(1).eval(null), success(invalid("1 already exists")));
    assertEquals(mustNotExist.apply(-1).eval(null), success(valid()));
  }

}
