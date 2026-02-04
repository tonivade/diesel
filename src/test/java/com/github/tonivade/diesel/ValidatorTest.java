/*
 * Copyright (c) 2018-2024, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Result.success;
import static com.github.tonivade.diesel.Validator.invalid;
import static com.github.tonivade.diesel.Validator.valid;
import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class ValidatorTest {

  @Test
  void shouldCompose() {
    Validator<Object, String, Integer> isPositive = value -> {
      if (value > 0) {
        return valid();
      }
      return invalid("Value must be positive");
    };

    Validator<Object, String, Integer> isEven = value -> {
      if (value % 2 == 0) {
        return valid();
      }
      return invalid("Value must be even");
    };

    var positiveAndEven = isPositive.and(isEven);
    var positiveOrEven = isPositive.or(isEven);
    var positiveCombineEven = isPositive.combine(isEven);

    // Test with a positive even number
    assertEquals(positiveAndEven.apply(4).eval(null), success(Validator.VALID));
    assertEquals(positiveOrEven.apply(4).eval(null), success(Validator.VALID));
    assertEquals(positiveCombineEven.apply(4).eval(null), success(Validator.VALID));

    // Test with a positive odd number
    assertEquals(positiveAndEven.apply(3).eval(null), success(Either.right("Value must be even")));
    assertEquals(positiveOrEven.apply(3).eval(null), success(Validator.VALID));
    assertEquals(positiveCombineEven.apply(3).eval(null), success(Either.right(List.of("Value must be even"))));

    // Test with a negative even number
    assertEquals(positiveAndEven.apply(-2).eval(null), success(Either.right("Value must be positive")));
    assertEquals(positiveOrEven.apply(-2).eval(null), success(Validator.VALID));
    assertEquals(positiveCombineEven.apply(-2).eval(null), success(Either.right(List.of("Value must be positive"))));

    // Test with a negative odd number
    assertEquals(positiveAndEven.apply(-3).eval(null), success(Either.right("Value must be positive")));
    assertEquals(positiveOrEven.apply(-3).eval(null), success(Either.right("Value must be even")));
    assertEquals(positiveCombineEven.apply(-3).eval(null), success(Either.right(List.of("Value must be positive", "Value must be even"))));
  }

}
