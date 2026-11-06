/*
 * Copyright (c) 2025-2026, Antonio Gabriel Muñoz Conejo <me at tonivade dot es>
 * Distributed under the terms of the MIT License
 */
package com.github.tonivade.diesel;

import static com.github.tonivade.diesel.Result.success;
import static com.github.tonivade.diesel.Validation.invalid;
import static com.github.tonivade.diesel.Validation.valid;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

class ValidatorTest {

  @Test
  void shouldCompose() {
    Validator<Object, String, Integer> isPositive = Validator.fromValidation(value -> {
      if (value > 0) {
        return valid();
      }
      return invalid("Value must be positive");
    });

    Validator<Object, String, Integer> isEven = Validator.fromValidation(value -> {
      if (value % 2 == 0) {
        return valid();
      }
      return invalid("Value must be even");
    });

    var positiveAndEven = isPositive.and(isEven);
    var positiveOrEven = isPositive.or(isEven);
    var positiveCombineEven = isPositive.combine(isEven);

    // Test with a positive even number
    assertThat(positiveAndEven.apply(4).eval(null)).isEqualTo(success(valid()));
    assertThat(positiveOrEven.apply(4).eval(null)).isEqualTo(success(valid()));
    assertThat(positiveCombineEven.apply(4).eval(null)).isEqualTo(success(valid()));

    // Test with a positive odd number
    assertThat(positiveAndEven.apply(3).eval(null)).isEqualTo(success(invalid("Value must be even")));
    assertThat(positiveOrEven.apply(3).eval(null)).isEqualTo(success(valid()));
    assertThat(positiveCombineEven.apply(3).eval(null)).isEqualTo(success(invalid(List.of("Value must be even"))));

    // Test with a negative even number
    assertThat(positiveAndEven.apply(-2).eval(null)).isEqualTo(success(invalid("Value must be positive")));
    assertThat(positiveOrEven.apply(-2).eval(null)).isEqualTo(success(valid()));
    assertThat(positiveCombineEven.apply(-2).eval(null)).isEqualTo(success(invalid(List.of("Value must be positive"))));

    // Test with a negative odd number
    assertThat(positiveAndEven.apply(-3).eval(null)).isEqualTo(success(invalid("Value must be positive")));
    assertThat(positiveOrEven.apply(-3).eval(null)).isEqualTo(success(invalid("Value must be even")));
    assertThat(positiveCombineEven.apply(-3).eval(null)).isEqualTo(success(invalid(List.of("Value must be positive", "Value must be even"))));
  }

  @Test
  void shouldValidateFromProgram() {
    Function<Integer, Program<Object, String, Integer>> lookup =
        value -> value > 0 ? Program.success(value) : Program.failure("not found");

    Validator<Object, String, Integer> mustExist = Validator.fromFailure(lookup);
    Validator<Object, String, Integer> mustExistMapped = Validator.fromFailure(lookup, error -> "error: " + error);
    Validator<Object, String, Integer> mustNotExist = Validator.fromSuccess(lookup, value -> value + " already exists");

    assertThat(mustExist.apply(1).eval(null)).isEqualTo(success(valid()));
    assertThat(mustExist.apply(-1).eval(null)).isEqualTo(success(invalid("not found")));

    assertThat(mustExistMapped.apply(1).eval(null)).isEqualTo(success(valid()));
    assertThat(mustExistMapped.apply(-1).eval(null)).isEqualTo(success(invalid("error: not found")));

    assertThat(mustNotExist.apply(1).eval(null)).isEqualTo(success(invalid("1 already exists")));
    assertThat(mustNotExist.apply(-1).eval(null)).isEqualTo(success(valid()));
  }

}
